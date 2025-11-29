package com.yliahs.client.temp2.endpoint;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class Socks5BindEndpoint {

    private static final Logger log = LoggerFactory.getLogger(Socks5BindEndpoint.class);

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private Channel serverChannel;

    public Socks5BindEndpoint(EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        this.bossGroup = bossGroup;
        this.workerGroup = workerGroup;
    }

    public ChannelFuture bind(String bindHost, Promise<Channel> incomingConnectionPromise) {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (!incomingConnectionPromise.isDone()) {
                            ch.config().setAutoRead(false);
                            incomingConnectionPromise.setSuccess(ch);
                        } else {
                            ch.close();
                        }
                    }
                });

        return b.bind(bindHost, 0).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                serverChannel = f.channel();
                log.info("BIND Endpoint listening on {}", serverChannel.localAddress());
            }
        });
    }

    public InetSocketAddress getLocalAddress() {
        return serverChannel != null ? (InetSocketAddress) serverChannel.localAddress() : null;
    }

    public void close() {
        if (serverChannel != null) {
            serverChannel.close();
        }
    }
}
