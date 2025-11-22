package com.yliahs.server;

import com.yliahs.server.config.RemoteProxyConfig;
import com.yliahs.server.config.RemoteProxyConfigLoader;
import com.yliahs.server.handler.RemoteProxyServerInitializer;
import com.yliahs.server.handler.RemoteSslContextFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RemoteProxyServer {

    private static final Logger log = LoggerFactory.getLogger(RemoteProxyServer.class);
    private final RemoteProxyConfig config;

    public RemoteProxyServer(RemoteProxyConfig config) {
        this.config = config;
    }

    public static void main(String[] args) throws Exception {
        RemoteProxyConfig config = loadConfig(args);
        new RemoteProxyServer(config).start();
    }

    private static RemoteProxyConfig loadConfig(String[] args) throws Exception {
        if (args != null && args.length > 0) {
            Path path = Path.of(args[0]);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Config file not found: " + path.toAbsolutePath());
            }
            return new RemoteProxyConfigLoader().load(path);
        }
        log.info("No config file specified, using default remote proxy configuration");
        return new RemoteProxyConfig();
    }

    public void start() throws InterruptedException, SSLException {
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            SslContext sslContext = RemoteSslContextFactory.build(config);

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new RemoteProxyServerInitializer(config, sslContext))
                    .childOption(ChannelOption.AUTO_READ, true);

            String mode = sslContext != null ? "TLS" : "PLAIN";
            ChannelFuture bindFuture = bootstrap.bind(config.getBindAddress(), config.getPort()).sync();
            log.info("Remote forward proxy listening on {}:{} ({})",
                    config.getBindAddress(), config.getPort(), mode);
            bindFuture.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
