package com.yliahs.client.temp2.endpoint;

import com.yliahs.client.temp2.handler.Socks5UdpHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class Socks5UdpEndpoint {

    private static final Logger log = LoggerFactory.getLogger(Socks5UdpEndpoint.class);

    private final EventLoopGroup workerGroup;
    private final InetSocketAddress clientAddress;
    private Channel channel;

    public Socks5UdpEndpoint(EventLoopGroup workerGroup, InetSocketAddress clientAddress) {
        this.workerGroup = workerGroup;
        this.clientAddress = clientAddress;
    }

    public ChannelFuture start(String bindHost) {
        Bootstrap b = new Bootstrap();
        b.group(workerGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new Socks5UdpHandler(clientAddress));

        return b.bind(bindHost, 0).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                log.info("UDP Endpoint started at {}", channel.localAddress());
            } else {
                log.error("Failed to start UDP Endpoint", future.cause());
            }
        });
    }

    public void close() {
        if (channel != null) {
            channel.close();
        }
    }
}
