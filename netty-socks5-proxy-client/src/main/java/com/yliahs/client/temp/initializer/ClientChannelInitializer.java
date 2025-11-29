package com.yliahs.client.temp.initializer;

import com.yliahs.client.temp.config.ClientConfig;
import com.yliahs.client.temp.handler.Socks5CommandRequestHandler;
import com.yliahs.client.temp.handler.Socks5InitialRequestHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;

public class ClientChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final ClientConfig clientConfig;

    public ClientChannelInitializer(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline channelPipeline = ch.pipeline();
        channelPipeline.addLast(Socks5ServerEncoder.DEFAULT);
        channelPipeline.addLast(new Socks5InitialRequestDecoder());
        channelPipeline.addLast(new Socks5InitialRequestHandler());
        channelPipeline.addLast(new Socks5CommandRequestDecoder());
        channelPipeline.addLast(new Socks5CommandRequestHandler());
    }
}
