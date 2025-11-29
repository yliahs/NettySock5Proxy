package com.yliahs.client.temp2.bootstrap;

import com.yliahs.client.temp2.acl.AccessControlManager;
import com.yliahs.client.temp2.auth.AuthenticationManager;
import com.yliahs.client.temp2.concurrent.ConnectionLimiter;
import com.yliahs.client.temp2.config.ProxyConfiguration;
import com.yliahs.client.temp2.forward.ForwardingSslContextFactory;
import com.yliahs.client.temp2.pipeline.Socks5ServerInitializer;
import com.yliahs.client.temp2.security.TargetAddressValidator;
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
import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * 封装 Netty 服务端启动逻辑。
 */
public class Socks5ServerBootstrap {

    private static final Logger log = LoggerFactory.getLogger(Socks5ServerBootstrap.class);

    private final ProxyConfiguration configuration;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private ChannelFuture bindFuture;

    public Socks5ServerBootstrap(ProxyConfiguration configuration) {
        this(configuration, new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()), new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()));
    }

    public Socks5ServerBootstrap(ProxyConfiguration configuration,
                                 EventLoopGroup bossGroup,
                                 EventLoopGroup workerGroup) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.bossGroup = Objects.requireNonNull(bossGroup, "bossGroup");
        this.workerGroup = Objects.requireNonNull(workerGroup, "workerGroup");
    }

    public ChannelFuture start() {
        AuthenticationManager authManager = new AuthenticationManager(configuration.getAuthentication());
        AccessControlManager aclManager = new AccessControlManager(configuration.getAccessControl());
        ConnectionLimiter connectionLimiter = new ConnectionLimiter(configuration.getMaxConcurrentConnections());
        TargetAddressValidator addressValidator = new TargetAddressValidator(configuration.getSecurity());

        ProxyConfiguration.Forwarding forwarding = configuration.getForwarding();
        SslContext forwardingSslContext = null;
        if (forwarding != null && forwarding.isEnabled()) {
            log.info("Forwarding ENABLED -> remoteHost={} remotePort={} timeout={}ms",
                    forwarding.getRemoteHost(), forwarding.getRemotePort(), forwarding.getConnectTimeoutMillis());
            try {
                forwardingSslContext = ForwardingSslContextFactory.build(forwarding);
                if (forwarding.getTls() != null && forwarding.getTls().isEnabled()) {
                    log.info("Forwarding TLS ENABLED -> trustCert={} clientCert={} insecureTrust={} ",
                            forwarding.getTls().getTrustCertCollectionPath(),
                            forwarding.getTls().getKeyCertChainPath(),
                            forwarding.getTls().isInsecureTrustManager());
                }
            } catch (SSLException e) {
                throw new IllegalStateException("Failed to initialise forwarding TLS context", e);
            }
        } else {
            log.info("Forwarding DISABLED -> 使用本地直连模式");
        }

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new Socks5ServerInitializer(configuration, authManager, aclManager, connectionLimiter, addressValidator, forwardingSslContext))
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

        InetSocketAddress address = new InetSocketAddress(configuration.getBindAddress(), configuration.getPort());
        log.info("启动 SOCKS5 代理，监听 {}:{}", configuration.getBindAddress(), configuration.getPort());
        bindFuture = bootstrap.bind(address);
        bindFuture.addListener(future -> {
            if (future.isSuccess()) {
                log.info("SOCKS5 代理已启动: {}", address);
            } else {
                log.error("SOCKS5 代理启动失败: {}", address, future.cause());
            }
        });
        return bindFuture;
    }

    public void stop() {
        if (bindFuture != null && bindFuture.channel().isOpen()) {
            bindFuture.channel().close().awaitUninterruptibly();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}

