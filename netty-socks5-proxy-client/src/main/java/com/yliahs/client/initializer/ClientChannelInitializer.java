package com.yliahs.client.initializer;

import com.yliahs.client.config.ClientConfig;
import com.yliahs.client.handler.Sock5dynamicRouteHandler;
import com.yliahs.client.temp2.handler.IdleTimeoutHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class ClientChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final ClientConfig clientConfig;

    public ClientChannelInitializer(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline channelPipeline = ch.pipeline();
        ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
//        channelPipeline.addLast(new LogRecordHandler(100,100,100,100));
        channelPipeline.addLast(
                new IdleStateHandler(100, 0, 0, TimeUnit.SECONDS));
        channelPipeline.addLast(new IdleTimeoutHandler());
        channelPipeline.addLast(new Sock5dynamicRouteHandler());

    }
}
