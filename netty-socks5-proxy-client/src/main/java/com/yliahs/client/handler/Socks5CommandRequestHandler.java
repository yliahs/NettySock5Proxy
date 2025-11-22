package com.yliahs.client.handler;

import com.yliahs.client.Socks5ServerAttributes;
import com.yliahs.client.acl.AccessControlManager;
import com.yliahs.client.config.ProxyConfiguration;
import com.yliahs.client.endpoint.Socks5BindEndpoint;
import com.yliahs.client.endpoint.Socks5UdpEndpoint;
import com.yliahs.client.forward.ForwardingClientHandler;
import com.yliahs.client.forward.ForwardingHandshakeException;
import com.yliahs.client.pipeline.Socks5ServerInitializer;
import com.yliahs.client.relay.RelayHandler;
import com.yliahs.client.security.TargetAddressValidator;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 处理 SOCKS5 Command 请求（CONNECT, UDP_ASSOCIATE, BIND）。
 */
public class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {

    private static final Logger log = LoggerFactory.getLogger(Socks5CommandRequestHandler.class);
    private static final String OUTBOUND_TIMEOUT = "outboundReadTimeout";

    private final ProxyConfiguration configuration;
    private final AccessControlManager accessControlManager;
    private final TargetAddressValidator targetAddressValidator;
    private final io.netty.handler.ssl.SslContext forwardingSslContext;

    public Socks5CommandRequestHandler(ProxyConfiguration configuration,
                                       AccessControlManager accessControlManager,
                                       TargetAddressValidator targetAddressValidator,
                                       io.netty.handler.ssl.SslContext forwardingSslContext) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.accessControlManager = Objects.requireNonNull(accessControlManager, "accessControlManager");
        this.targetAddressValidator = Objects.requireNonNull(targetAddressValidator, "targetAddressValidator");
        this.forwardingSslContext = forwardingSslContext;
    }

    private static boolean isSupportedAddressType(Socks5AddressType type) {
        return type == Socks5AddressType.IPv4
                || type == Socks5AddressType.IPv6
                || type == Socks5AddressType.DOMAIN;
    }

    static void closeOnFlush(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(io.netty.buffer.Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5CommandRequest request) {
        if (!Boolean.TRUE.equals(ctx.channel().attr(Socks5ServerAttributes.AUTHENTICATED).get())) {
            log.warn("未经授权的命令请求，直接关闭");
            ctx.close();
            return;
        }

        if (request.type() == Socks5CommandType.CONNECT) {
            handleConnect(ctx, request);
        } else if (request.type() == Socks5CommandType.UDP_ASSOCIATE) {
            handleUdpAssociate(ctx, request);
        } else if (request.type() == Socks5CommandType.BIND) {
            handleBind(ctx, request);
        } else {
            log.info("不支持的命令: {}", request.type());
            sendCommandResponse(ctx, Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, DefaultSocks5CommandRequest request) {
        String dstAddr = request.dstAddr();
        int dstPort = request.dstPort();
        Socks5AddressType addressType = request.dstAddrType();
        String username = ctx.channel().attr(Socks5ServerAttributes.USERNAME).get();
        String client = String.valueOf(ctx.channel().remoteAddress());

        if (validateRequest(ctx, request)) {
            return;
        }

        ctx.channel().config().setAutoRead(false);
        log.info("CONNECT 请求: user={} client={} target={}:{}, addrType={}",
                username == null ? "-" : username,
                client,
                dstAddr,
                dstPort,
                addressType);

        ProxyConfiguration.Forwarding forwarding = configuration.getForwarding();
        if (forwarding != null && forwarding.isEnabled()) {
            handleConnectForwarding(ctx, request, forwarding, username, client);
            return;
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getTimeouts().getConnectTimeoutMillis())
                .option(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (configuration.getTimeouts().getIdleSeconds() > 0) {
                            ch.pipeline().addLast(OUTBOUND_TIMEOUT,
                                    new io.netty.handler.timeout.ReadTimeoutHandler(configuration.getTimeouts().getIdleSeconds(), TimeUnit.SECONDS));
                        }
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });

        ChannelFuture connectFuture = connect(bootstrap, request);
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                Socks5CommandStatus status = mapErrorToStatus(future.cause());
                log.warn("CONNECT 失败: user={} client={} target={}:{}, status={}, error={}",
                        username == null ? "-" : username,
                        client,
                        dstAddr,
                        dstPort,
                        status,
                        future.cause().toString());
                sendCommandResponse(ctx, status, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
                return;
            }
            Channel outboundChannel = future.channel();
            InetSocketAddress remoteAddress = (InetSocketAddress) outboundChannel.remoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                if (targetAddressValidator.isBlockedAfterConnect(remoteAddress.getAddress())) {
                    log.warn("阻止域名解析后访问受限地址 {} 来自 {}", remoteAddress.getAddress().getHostAddress(), ctx.channel().remoteAddress());
                    closeOnFlush(outboundChannel);
                    sendCommandResponse(ctx, Socks5CommandStatus.FORBIDDEN, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
                    return;
                }
            }
            ctx.pipeline().addLast(Socks5ServerInitializer.HANDLER_RELAY, new RelayHandler(outboundChannel));

            InetSocketAddress boundAddress = (InetSocketAddress) outboundChannel.localAddress();
            Socks5AddressType responseType = determineAddressType(boundAddress);
            String responseHost = extractHost(boundAddress, responseType);
            int responsePort = boundAddress.getPort();

            ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                            Socks5CommandStatus.SUCCESS, responseType, responseHost, responsePort))
                    .addListener((ChannelFutureListener) responseFuture -> {
                        if (responseFuture.isSuccess()) {
                            ctx.channel().config().setAutoRead(true);
                            outboundChannel.read();
                            log.info("CONNECT 成功: user={} client={} target={}:{}, outboundLocal={}({}:{})",
                                    username == null ? "-" : username,
                                    client,
                                    dstAddr,
                                    dstPort,
                                    responseType,
                                    responseHost,
                                    responsePort);
                        } else {
                            log.warn("返回成功响应失败，关闭链路", responseFuture.cause());
                            closeOnFlush(outboundChannel);
                        }
                    });
            ctx.pipeline().remove(this);
            removeIfExists(ctx.pipeline(), Socks5ServerInitializer.HANDLER_COMMAND_DECODER);
        });
    }

    private void handleConnectForwarding(ChannelHandlerContext ctx,
                                         DefaultSocks5CommandRequest request,
                                         ProxyConfiguration.Forwarding forwarding,
                                         String username,
                                         String client) {
        String dstAddr = request.dstAddr();
        int dstPort = request.dstPort();
        Socks5AddressType addressType = request.dstAddrType();

        Promise<Channel> handshakePromise = ctx.channel().eventLoop().newPromise();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, forwarding.getConnectTimeoutMillis())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (forwardingSslContext != null && forwarding.getTls() != null && forwarding.getTls().isEnabled()) {
                            ch.pipeline().addLast("tls",
                                    forwardingSslContext.newHandler(ch.alloc(), forwarding.getRemoteHost(), forwarding.getRemotePort()));
                        }
                        ch.pipeline().addLast(new ForwardingClientHandler(
                                ctx.channel(),
                                addressType,
                                dstAddr,
                                dstPort,
                                handshakePromise));
                    }
                });

        ChannelFuture connectFuture = bootstrap.connect(forwarding.getRemoteHost(), forwarding.getRemotePort());
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                Socks5CommandStatus status = mapErrorToStatus(future.cause());
                log.warn("FORWARD CONNECT 失败: user={} client={} target={}:{}, status={}, error={}",
                        username == null ? "-" : username,
                        client,
                        dstAddr,
                        dstPort,
                        status,
                        future.cause().toString());
                sendCommandResponse(ctx, status, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
                return;
            }
            Channel forwardChannel = future.channel();
            handshakePromise.addListener(handshakeFuture -> {
                if (handshakeFuture.isSuccess()) {
                    Channel remoteChannel = (Channel) handshakeFuture.getNow();
                    ctx.pipeline().addLast(Socks5ServerInitializer.HANDLER_RELAY, new RelayHandler(remoteChannel));
                    remoteChannel.pipeline().addLast(new RelayHandler(ctx.channel()));

                    ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                                    Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "0.0.0.0", 0))
                            .addListener((ChannelFutureListener) responseFuture -> {
                                if (responseFuture.isSuccess()) {
                                    ctx.channel().config().setAutoRead(true);
                                    remoteChannel.read();
                                    log.info("FORWARD CONNECT 成功: user={} client={} target={}:{}",
                                            username == null ? "-" : username,
                                            client,
                                            dstAddr,
                                            dstPort);
                                } else {
                                    log.warn("返回成功响应失败，关闭链路", responseFuture.cause());
                                    closeOnFlush(remoteChannel);
                                }
                            });
                    ctx.pipeline().remove(Socks5CommandRequestHandler.this);
                    removeIfExists(ctx.pipeline(), Socks5ServerInitializer.HANDLER_COMMAND_DECODER);
                } else {
                    Throwable cause = handshakeFuture.cause();
                    Socks5CommandStatus status = Socks5CommandStatus.FAILURE;
                    if (cause instanceof ForwardingHandshakeException fhe) {
                        try {
                            status = Socks5CommandStatus.valueOf(fhe.getStatus());
                        } catch (IllegalArgumentException ignore) {
                        }
                    }
                    log.warn("FORWARD CONNECT 握手失败: target={}:{} status={} error={}",
                            dstAddr, dstPort, status, cause != null ? cause.toString() : "unknown");
                    sendCommandResponse(ctx, status, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
                    closeOnFlush(forwardChannel);
                }
            });
        });
    }

    private void handleUdpAssociate(ChannelHandlerContext ctx, DefaultSocks5CommandRequest request) {
        ProxyConfiguration.Forwarding forwarding = configuration.getForwarding();
        if (forwarding != null && forwarding.isEnabled()) {
            log.info("UDP_ASSOCIATE 被拒绝：当前处于远端转发模式，不支持远端 UDP。");
            sendCommandResponse(ctx, Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
            return;
        }

        String dstAddr = request.dstAddr();
        int dstPort = request.dstPort();
        String username = ctx.channel().attr(Socks5ServerAttributes.USERNAME).get();
        String client = String.valueOf(ctx.channel().remoteAddress());

        log.info("UDP_ASSOCIATE 请求: user={} client={} target={}:{}",
                username == null ? "-" : username,
                client,
                dstAddr,
                dstPort);

        if (validateRequest(ctx, request)) {
            return;
        }

        InetSocketAddress clientAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        Socks5UdpEndpoint udpEndpoint = new Socks5UdpEndpoint(ctx.channel().eventLoop(), clientAddress);

        udpEndpoint.start(configuration.getBindAddress()).addListener(future -> {
            if (future.isSuccess()) {
                Channel udpChannel = ((ChannelFuture) future).channel();
                InetSocketAddress boundAddress = (InetSocketAddress) udpChannel.localAddress();

                Socks5AddressType responseType = determineAddressType(boundAddress);
                String responseHost = extractHost(boundAddress, responseType);
                int responsePort = boundAddress.getPort();

                log.info("UDP ASSOCIATE 建立: client={} bind={}:{}", clientAddress, responseHost, responsePort);

                ctx.channel().closeFuture().addListener(f -> {
                    log.debug("TCP 连接关闭，停止 UDP Endpoint");
                    udpEndpoint.close();
                });

                ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS, responseType, responseHost, responsePort));

                removeIfExists(ctx.pipeline(), Socks5ServerInitializer.HANDLER_COMMAND_DECODER);
            } else {
                log.error("启动 UDP Endpoint 失败", future.cause());
                sendCommandResponse(ctx, Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
            }
        });
    }

    private void handleBind(ChannelHandlerContext ctx, DefaultSocks5CommandRequest request) {
        ProxyConfiguration.Forwarding forwarding = configuration.getForwarding();
        if (forwarding != null && forwarding.isEnabled()) {
            log.info("BIND 被拒绝：当前处于远端转发模式，不支持远端 BIND。");
            sendCommandResponse(ctx, Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
            return;
        }

        String username = ctx.channel().attr(Socks5ServerAttributes.USERNAME).get();
        String client = String.valueOf(ctx.channel().remoteAddress());

        log.info("BIND 请求: user={} client={}", username == null ? "-" : username, client);

        if (validateRequest(ctx, request)) {
            return;
        }

        Promise<Channel> incomingPromise = ctx.executor().newPromise();
        Socks5BindEndpoint bindEndpoint = new Socks5BindEndpoint(ctx.channel().eventLoop(), ctx.channel().eventLoop());

        bindEndpoint.bind(configuration.getBindAddress(), incomingPromise).addListener(future -> {
            if (future.isSuccess()) {
                InetSocketAddress bindAddr = bindEndpoint.getLocalAddress();
                Socks5AddressType type = determineAddressType(bindAddr);
                String host = extractHost(bindAddr, type);
                int port = bindAddr.getPort();

                log.info("BIND 监听成功: {}", bindAddr);

                // 1. Send First Reply
                ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS, type, host, port));

                // 2. Wait for Incoming Connection
                incomingPromise.addListener(incomingFuture -> {
                    if (incomingFuture.isSuccess()) {
                        Channel incomingChannel = (Channel) incomingFuture.get();
                        InetSocketAddress remoteAddr = (InetSocketAddress) incomingChannel.remoteAddress();

                        log.info("BIND 收到连接: {}", remoteAddr);

                        // 3. Send Second Reply
                        Socks5AddressType type2 = determineAddressType(remoteAddr);
                        String host2 = extractHost(remoteAddr, type2);

                        ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                                Socks5CommandStatus.SUCCESS, type2, host2, remoteAddr.getPort()));

                        // 4. Setup Relay
                        ctx.pipeline().addLast(Socks5ServerInitializer.HANDLER_RELAY, new RelayHandler(incomingChannel));
                        incomingChannel.pipeline().addLast(new RelayHandler(ctx.channel()));

                        // 5. Start Traffic
                        ctx.channel().config().setAutoRead(true);
                        incomingChannel.config().setAutoRead(true);

                        ctx.pipeline().remove(this);
                        removeIfExists(ctx.pipeline(), Socks5ServerInitializer.HANDLER_COMMAND_DECODER);

                        bindEndpoint.close();
                    } else {
                        log.warn("BIND 等待连接失败", incomingFuture.cause());
                        ctx.close();
                        bindEndpoint.close();
                    }
                });
            } else {
                log.error("BIND 失败", future.cause());
                sendCommandResponse(ctx, Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
            }
        });
    }

    private boolean validateRequest(ChannelHandlerContext ctx, DefaultSocks5CommandRequest request) {
        Socks5AddressType addressType = request.dstAddrType();
        String dstAddr = request.dstAddr();
        int dstPort = request.dstPort();

        if (!isSupportedAddressType(addressType)) {
            sendCommandResponse(ctx, Socks5CommandStatus.ADDRESS_UNSUPPORTED, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
            return true;
        }

        if (targetAddressValidator.isPortBlocked(dstPort)) {
            log.warn("阻止访问受限端口 {}:{} 来自 {}", dstAddr, dstPort, ctx.channel().remoteAddress());
            sendCommandResponse(ctx, Socks5CommandStatus.FORBIDDEN, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
            return true;
        }

        if (targetAddressValidator.isBlockedBeforeConnect(addressType, dstAddr)) {
            log.warn("阻止访问受限地址 {}:{} 来自 {}", dstAddr, dstPort, ctx.channel().remoteAddress());
            sendCommandResponse(ctx, Socks5CommandStatus.FORBIDDEN, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
            return true;
        }

        if (!accessControlManager.isAllowed(dstAddr.toLowerCase(Locale.ROOT))) {
            log.info("ACL 拒绝访问: {}:{}", dstAddr, dstPort);
            sendCommandResponse(ctx, Socks5CommandStatus.FORBIDDEN, Socks5AddressType.IPv4, "0.0.0.0", 0, true);
            return true;
        }
        return false;
    }

    private ChannelFuture connect(Bootstrap bootstrap, DefaultSocks5CommandRequest request) {
        return bootstrap.connect(request.dstAddr(), request.dstPort());
    }

    private Socks5CommandStatus mapErrorToStatus(Throwable cause) {
        if (cause instanceof UnknownHostException) {
            return Socks5CommandStatus.HOST_UNREACHABLE;
        }
        if (cause instanceof ConnectException) {
            return Socks5CommandStatus.CONNECTION_REFUSED;
        }
        String message = cause.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
            return Socks5CommandStatus.NETWORK_UNREACHABLE;
        }
        return Socks5CommandStatus.FAILURE;
    }

    private void sendCommandResponse(ChannelHandlerContext ctx,
                                     Socks5CommandStatus status,
                                     Socks5AddressType type,
                                     String host,
                                     int port,
                                     boolean close) {
        ctx.writeAndFlush(new DefaultSocks5CommandResponse(status, type, host, port))
                .addListener(close ? ChannelFutureListener.CLOSE : ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    private Socks5AddressType determineAddressType(InetSocketAddress address) {
        if (address == null) {
            return Socks5AddressType.IPv4;
        }
        if (address.getAddress() instanceof Inet4Address) {
            return Socks5AddressType.IPv4;
        }
        if (address.getAddress() instanceof Inet6Address) {
            return Socks5AddressType.IPv6;
        }
        return Socks5AddressType.DOMAIN;
    }

    private String extractHost(InetSocketAddress address, Socks5AddressType type) {
        if (address == null) {
            return "0.0.0.0";
        }
        if (type == Socks5AddressType.IPv4 || type == Socks5AddressType.IPv6) {
            return address.getAddress().getHostAddress();
        }
        if (type == Socks5AddressType.DOMAIN) {
            return address.getHostString();
        }
        return "0.0.0.0";
    }

    private void removeIfExists(io.netty.channel.ChannelPipeline pipeline, String name) {
        if (pipeline.get(name) != null) {
            pipeline.remove(name);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("命令处理异常，关闭连接", cause);
        ctx.close();
    }
}
