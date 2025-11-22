package com.yliahs.server.handler;

import com.yliahs.server.codec.ForwardConnectRequestDecoder;
import com.yliahs.server.config.RemoteProxyConfig;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteProxyServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger log = LoggerFactory.getLogger(RemoteProxyServerInitializer.class);

    private final RemoteProxyConfig config;
    private final SslContext sslContext;

    public RemoteProxyServerInitializer(RemoteProxyConfig config, SslContext sslContext) {
        this.config = config;
        this.sslContext = sslContext;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslContext != null && config.getTls() != null && config.getTls().isEnabled()) {
            SslHandler sslHandler = sslContext.newHandler(ch.alloc());
            sslHandler.handshakeFuture().addListener(future -> {
                if (future.isSuccess()) {
                    log.debug("Remote TLS handshake succeeded: {}", ch.remoteAddress());
                } else {
                    log.warn("Remote TLS handshake failed: {}", ch.remoteAddress(), future.cause());
                }
            });
            pipeline.addLast("tls", sslHandler);
        }
        pipeline.addLast("forwardDecoder", new ForwardConnectRequestDecoder());
        pipeline.addLast("forwardHandler", new RemoteForwardHandler(config));
    }
}
