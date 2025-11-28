package com.yliahs.server.handler;

import com.yliahs.server.codec.ForwardConnectRequest;
import com.yliahs.server.codec.ForwardConnectRequestDecoder;
import com.yliahs.server.config.RemoteProxyConfig;
import com.yliahs.server.relay.RelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Locale;

public class RemoteForwardHandler extends SimpleChannelInboundHandler<ForwardConnectRequest> {

    private static final Logger log = LoggerFactory.getLogger(RemoteForwardHandler.class);
    private static final byte PROTOCOL_VERSION = 1;

    private final RemoteProxyConfig config;

    public RemoteForwardHandler(RemoteProxyConfig config) {
        this.config = config;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ForwardConnectRequest msg) {
        log.info("Received forward request: {}:{} type={} from={}",
                msg.host(), msg.port(), msg.addressType(), ctx.channel().remoteAddress());
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });

        ChannelFuture connectFuture = bootstrap.connect(msg.host(), msg.port());
        connectFuture.addListener((ChannelFutureListener) future -> {
            // 确保在正确的事件循环线程中执行
            if (ctx.executor().inEventLoop()) {
                handleConnectResult(ctx, msg, future);
            } else {
                ctx.executor().execute(() -> handleConnectResult(ctx, msg, future));
            }
        });
    }

    private void handleConnectResult(ChannelHandlerContext ctx, ForwardConnectRequest msg, ChannelFuture future) {
        // 再次检查context是否仍然有效
        if (!ctx.channel().isActive()) {
            return;
        }

        if (!future.isSuccess()) {
            byte status = mapErrorToStatusByte(future.cause());
            log.warn("Remote connect failed: {}:{} status={} error={}",
                    msg.host(), msg.port(), status, future.cause().toString());
            sendResponse(ctx, status);
            ctx.close();
            return;
        }
        
        Channel targetChannel = future.channel();
        
        // 更安全的方式移除handler和添加新handler
        try {
            ChannelPipeline pipeline = ctx.pipeline();
            if (pipeline.get(ForwardConnectRequestDecoder.class) != null) {
                pipeline.remove(ForwardConnectRequestDecoder.class);
            }
            // 使用名称而不是类来替换handler，更加可靠
            pipeline.replace("forwardHandler", "relay", new RelayHandler(targetChannel));
        } catch (Exception e) {
            log.warn("Failed to modify pipeline", e);
            targetChannel.close();
            ctx.close();
            return;
        }
        
        sendResponse(ctx, (byte) 0);
        targetChannel.read();
    }

    private void sendResponse(ChannelHandlerContext ctx, byte status) {
        ByteBuf buf = ctx.alloc().buffer(2);
        buf.writeByte(PROTOCOL_VERSION);
        buf.writeByte(status);
        ctx.writeAndFlush(buf);
    }

    private byte mapErrorToStatusByte(Throwable cause) {
        Socks5CommandStatus status = mapErrorToStatus(cause);
        // Reuse SOCKS5 status code ordinal values (mirrors local side mapping)
        return status.byteValue();
    }

    private Socks5CommandStatus mapErrorToStatus(Throwable cause) {
        if (cause instanceof UnknownHostException) {
            return Socks5CommandStatus.HOST_UNREACHABLE;
        }
        if (cause instanceof ConnectException) {
            return Socks5CommandStatus.CONNECTION_REFUSED;
        }
        String message = cause.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
            return Socks5CommandStatus.NETWORK_UNREACHABLE;
        }
        return Socks5CommandStatus.FAILURE;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Remote forward handler error", cause);
        ctx.close();
    }
}