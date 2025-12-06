package com.yliahs.client.handler;

import com.yliahs.client.config.ClientConfig;
import com.yliahs.client.initializer.ForwardChannelInitializer;
import com.yliahs.common.handler.Client2DestHandler;
import com.yliahs.common.handler.Dest2ClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {

    private static final Logger logger = LoggerFactory.getLogger(Socks5CommandRequestHandler.class);

    private final ClientConfig clientConfig;

    public Socks5CommandRequestHandler(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }


    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) throws Exception {
        if (Objects.equals(msg.type(), Socks5CommandType.CONNECT)) {
            handleTcpConnect(ctx, msg);
        } else {
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.IPv4))
                    .addListener(ChannelFutureListener.CLOSE);
            ctx.close();
        }
    }


    private void handleTcpConnect(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) {
//        if (clientConfig.getForwardingConfig().isEnabled()) {
//            handleTcpForward(ctx, msg);
//        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        //将目标服务器信息转发给客户端
                        ch.pipeline().addLast(new Dest2ClientHandler(ctx));
                    }
                });
        logger.trace("连接目标服务器");
        ChannelFuture future = bootstrap.connect(msg.dstAddr(), msg.dstPort());
        future.addListener(new ChannelFutureListener() {

            public void operationComplete(final ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    logger.trace("成功连接目标服务器");
                    ctx.pipeline().addLast(new Client2DestHandler(future));
                    Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4);
                    ctx.writeAndFlush(commandResponse);
                } else {
                    Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4);
                    ctx.writeAndFlush(commandResponse);
                    ctx.close();
                }
            }
        });
    }

    private void handleTcpForward(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) {
        ClientConfig.ForwardingConfig forwardingConfig = clientConfig.getForwardingConfig();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, forwardingConfig.getConnectTimeoutMillis())
                .handler(new ForwardChannelInitializer(ctx));

        ChannelFuture connectFuture = bootstrap.connect(forwardingConfig.getHost(), forwardingConfig.getPort());
    }

}
