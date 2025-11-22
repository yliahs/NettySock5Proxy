package com.yliahs.client.forward;

import com.yliahs.client.relay.RelayHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 与远端代理服务器进行握手，并在成功后将当前 channel 转换为纯粹的 relay。
 */
public class ForwardingClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(ForwardingClientHandler.class);
    private static final byte PROTOCOL_VERSION = 1;
    private static final byte COMMAND_CONNECT = 1;

    private final Channel clientChannel;
    private final Socks5AddressType addressType;
    private final String dstAddr;
    private final int dstPort;
    private final Promise<Channel> handshakePromise;
    private boolean handshakeCompleted = false;

    public ForwardingClientHandler(Channel clientChannel,
                                   Socks5AddressType addressType,
                                   String dstAddr,
                                   int dstPort,
                                   Promise<Channel> handshakePromise) {
        this.clientChannel = clientChannel;
        this.addressType = addressType;
        this.dstAddr = dstAddr;
        this.dstPort = dstPort;
        this.handshakePromise = handshakePromise;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        if (sslHandler != null) {
            sslHandler.handshakeFuture().addListener(future -> {
                if (future.isSuccess()) {
                    log.debug("Forwarding TLS handshake succeeded: {}", ctx.channel().remoteAddress());
                    sendInitialRequest(ctx);
                } else {
                    fail(ctx, future.cause());
                }
            });
        } else {
            sendInitialRequest(ctx);
        }
    }

    private void sendInitialRequest(ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive()) {
            return;
        }
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeByte(PROTOCOL_VERSION);
        buf.writeByte(COMMAND_CONNECT);
        buf.writeByte(addressType.byteValue());

        if (addressType == Socks5AddressType.IPv4) {
            byte[] bytes = NetUtil.createByteArrayFromIpAddressString(dstAddr);
            if (bytes == null || bytes.length != 4) {
                fail(ctx, new IllegalArgumentException("Invalid IPv4 address: " + dstAddr));
                buf.release();
                return;
            }
            buf.writeBytes(bytes);
        } else if (addressType == Socks5AddressType.IPv6) {
            byte[] bytes = NetUtil.createByteArrayFromIpAddressString(dstAddr);
            if (bytes == null || bytes.length != 16) {
                fail(ctx, new IllegalArgumentException("Invalid IPv6 address: " + dstAddr));
                buf.release();
                return;
            }
            buf.writeBytes(bytes);
        } else if (addressType == Socks5AddressType.DOMAIN) {
            byte[] domainBytes = dstAddr.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            if (domainBytes.length > 255) {
                fail(ctx, new IllegalArgumentException("Domain too long: " + dstAddr));
                buf.release();
                return;
            }
            buf.writeByte(domainBytes.length);
            buf.writeBytes(domainBytes);
        } else {
            fail(ctx, new IllegalArgumentException("Unsupported address type: " + addressType));
            buf.release();
            return;
        }

        buf.writeShort(dstPort & 0xFFFF);
        ctx.writeAndFlush(buf);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        if (handshakeCompleted) {
            // 应该不会走到这里，移除 handler 后不会再接收
            return;
        }
        if (msg.readableBytes() < 2) {
            return;
        }
        byte version = msg.readByte();
        byte status = msg.readByte();
        if (version != PROTOCOL_VERSION) {
            fail(ctx, new IllegalStateException("Unsupported protocol version: " + version));
            return;
        }
        if (status != 0) {
            fail(ctx, new ForwardingHandshakeException(status,
                    "Remote proxy rejected connection, status=" + status));
            return;
        }
        handshakeCompleted = true;
        ctx.pipeline().remove(this);
        ctx.pipeline().addLast(new RelayHandler(clientChannel));
        handshakePromise.trySuccess(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!handshakePromise.isDone()) {
            fail(ctx, new IllegalStateException("Connection closed before handshake completed"));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        fail(ctx, cause);
    }

    private void fail(ChannelHandlerContext ctx, Throwable cause) {
        if (!handshakePromise.isDone()) {
            handshakePromise.tryFailure(cause);
        }
        if (cause != null) {
            log.debug("Forwarding channel closing due to failure", cause);
        }
        ctx.close();
    }
}

