package com.yliahs.common.handler;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class Client2DestHandler extends ChannelInboundHandlerAdapter {

    private final ChannelFuture destChannelFuture;

    public Client2DestHandler(ChannelFuture destChannelFuture) {
        this.destChannelFuture = destChannelFuture;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        destChannelFuture.channel().writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        destChannelFuture.channel().close();
    }
}
