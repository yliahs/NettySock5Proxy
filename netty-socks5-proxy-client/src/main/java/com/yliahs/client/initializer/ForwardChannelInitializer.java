package com.yliahs.client.initializer;

import com.yliahs.common.handler.Dest2ClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class ForwardChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final ChannelHandlerContext channelHandlerContext;

    public ForwardChannelInitializer(ChannelHandlerContext ctx) {
        this.channelHandlerContext = ctx;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline channelPipeline = ch.pipeline();
        channelPipeline.addLast(new Dest2ClientHandler(channelHandlerContext));
    }
}
