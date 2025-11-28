package com.yliahs.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class Dest2ClientHandler extends ChannelInboundHandlerAdapter {

    private final ChannelHandlerContext clientChannelContext;

    public Dest2ClientHandler(ChannelHandlerContext clientChannelContext) {
        this.clientChannelContext = clientChannelContext;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object destMsg) {
        clientChannelContext.writeAndFlush(destMsg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        clientChannelContext.channel().close();
    }
}
