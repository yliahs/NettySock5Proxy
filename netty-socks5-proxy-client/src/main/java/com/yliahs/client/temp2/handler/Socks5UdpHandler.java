package com.yliahs.client.temp2.handler;

import com.yliahs.client.temp2.codec.Socks5UdpMessage;
import com.yliahs.client.temp2.codec.Socks5UdpUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class Socks5UdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger log = LoggerFactory.getLogger(Socks5UdpHandler.class);

    private InetSocketAddress clientAddress;

    public Socks5UdpHandler(InetSocketAddress clientAddress) {
        this.clientAddress = clientAddress;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        InetSocketAddress sender = packet.sender();

        // 判断是否来自 Client
        boolean isFromClient = sender.getAddress().equals(clientAddress.getAddress());

        if (isFromClient) {
            if (clientAddress.getPort() != sender.getPort()) {
                clientAddress = sender;
            }
            handleClientPacket(ctx, packet);
        } else {
            handleTargetPacket(ctx, packet);
        }
    }

    private void handleClientPacket(ChannelHandlerContext ctx, DatagramPacket packet) {
        Socks5UdpMessage msg = Socks5UdpUtils.unwrap(packet.content());
        if (msg == null) {
            return;
        }

        try {
            ctx.writeAndFlush(new DatagramPacket(msg.content().retain(), msg.address()));
        } finally {
            msg.release();
        }
    }

    private void handleTargetPacket(ChannelHandlerContext ctx, DatagramPacket packet) {
        ByteBuf wrapped = Socks5UdpUtils.wrap(ctx.alloc(), packet.sender(), packet.content().retain());
        ctx.writeAndFlush(new DatagramPacket(wrapped, clientAddress));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("UDP Handler exception", cause);
    }
}
