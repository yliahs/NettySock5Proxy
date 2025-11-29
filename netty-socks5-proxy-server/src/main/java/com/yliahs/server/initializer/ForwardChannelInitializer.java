package com.yliahs.server.initializer;

import com.yliahs.server.handler.Dest2ClientHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class ForwardChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final ChannelHandlerContext clientChannelContext;

    public ForwardChannelInitializer(ChannelHandlerContext clientChannelContext) {
        this.clientChannelContext = clientChannelContext;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline channelPipeline = ch.pipeline();
        channelPipeline.addLast(new Dest2ClientHandler(clientChannelContext));
    }
}
