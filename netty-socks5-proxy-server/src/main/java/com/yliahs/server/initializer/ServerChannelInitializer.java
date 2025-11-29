package com.yliahs.server.initializer;

import com.yliahs.server.codec.ForwardConnectRequestDecoder;
import com.yliahs.server.config.ServerConfig;
import com.yliahs.server.handler.ForwardHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final ServerConfig serverConfig;

    public ServerChannelInitializer(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline channelPipeline = ch.pipeline();
//        channelPipeline.addLast(new ForwardConnectRequestDecoder());
        channelPipeline.addLast(new ForwardHandler());
    }
}
