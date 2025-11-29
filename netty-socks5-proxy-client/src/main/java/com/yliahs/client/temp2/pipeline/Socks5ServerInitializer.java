package com.yliahs.client.temp2.pipeline;

import com.yliahs.client.temp2.acl.AccessControlManager;
import com.yliahs.client.temp2.auth.AuthenticationManager;
import com.yliahs.client.temp2.concurrent.ConnectionLimiter;
import com.yliahs.client.temp2.config.ProxyConfiguration;
import com.yliahs.client.temp2.handler.ConnectionLimiterHandler;
import com.yliahs.client.temp2.handler.IdleTimeoutHandler;
import com.yliahs.client.temp2.handler.Socks5InitialRequestHandler;
import com.yliahs.client.temp2.security.TargetAddressValidator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * SOCKS5 服务器 pipeline 初始化。
 */
public class Socks5ServerInitializer extends ChannelInitializer<SocketChannel> {

    public static final String HANDLER_CONNECTION_LIMITER = "connectionLimiter";
    public static final String HANDLER_IDLE_STATE = "idleState";
    public static final String HANDLER_IDLE_TIMEOUT = "idleTimeout";
    public static final String HANDLER_HANDSHAKE_TIMEOUT = "handshakeTimeout";
    public static final String HANDLER_LOGGING = "logging";
    public static final String HANDLER_ENCODER = "socks5Encoder";
    public static final String HANDLER_INITIAL_DECODER = "socks5InitialRequestDecoder";
    public static final String HANDLER_INITIAL_REQUEST = "socks5InitialRequestHandler";
    public static final String HANDLER_PASSWORD_DECODER = "socks5PasswordAuthDecoder";
    public static final String HANDLER_PASSWORD_REQUEST = "socks5PasswordAuthHandler";
    public static final String HANDLER_COMMAND_DECODER = "socks5CommandRequestDecoder";
    public static final String HANDLER_COMMAND_REQUEST = "socks5CommandRequestHandler";
    public static final String HANDLER_RELAY = "socks5RelayHandler";
    private static final Logger log = LoggerFactory.getLogger(Socks5ServerInitializer.class);
    private final ProxyConfiguration configuration;
    private final AuthenticationManager authenticationManager;
    private final AccessControlManager accessControlManager;
    private final ConnectionLimiter connectionLimiter;
    private final TargetAddressValidator targetAddressValidator;
    private final io.netty.handler.ssl.SslContext forwardingSslContext;

    public Socks5ServerInitializer(ProxyConfiguration configuration,
                                   AuthenticationManager authenticationManager,
                                   AccessControlManager accessControlManager,
                                   ConnectionLimiter connectionLimiter,
                                   TargetAddressValidator targetAddressValidator,
                                   io.netty.handler.ssl.SslContext forwardingSslContext) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.authenticationManager = Objects.requireNonNull(authenticationManager, "authenticationManager");
        this.accessControlManager = Objects.requireNonNull(accessControlManager, "accessControlManager");
        this.connectionLimiter = Objects.requireNonNull(connectionLimiter, "connectionLimiter");
        this.targetAddressValidator = Objects.requireNonNull(targetAddressValidator, "targetAddressValidator");
        this.forwardingSslContext = forwardingSslContext;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(HANDLER_CONNECTION_LIMITER, new ConnectionLimiterHandler(connectionLimiter));

        ProxyConfiguration.Timeouts timeouts = configuration.getTimeouts();
        if (timeouts.getIdleSeconds() > 0) {
            pipeline.addLast(HANDLER_IDLE_STATE,
                    new IdleStateHandler(timeouts.getIdleSeconds(), 0, 0, TimeUnit.SECONDS));
            pipeline.addLast(HANDLER_IDLE_TIMEOUT, new IdleTimeoutHandler());
        }
        if (timeouts.getHandshakeSeconds() > 0) {
            pipeline.addLast(HANDLER_HANDSHAKE_TIMEOUT, new ReadTimeoutHandler(timeouts.getHandshakeSeconds()));
        }

        addWireLoggingIfEnabled(pipeline);
        pipeline.addLast(HANDLER_ENCODER, Socks5ServerEncoder.DEFAULT);
        pipeline.addLast(HANDLER_INITIAL_DECODER, new Socks5InitialRequestDecoder());
        pipeline.addLast(HANDLER_INITIAL_REQUEST,
                new Socks5InitialRequestHandler(configuration, authenticationManager, accessControlManager, targetAddressValidator, forwardingSslContext));
    }

    private void addWireLoggingIfEnabled(ChannelPipeline pipeline) {
        ProxyConfiguration.Logging loggingConfig = configuration.getLogging();
        if (loggingConfig == null || !loggingConfig.isEnableWireLogging()) {
            return;
        }
        LogLevel level = parseLogLevel(loggingConfig.getWireLogLevel());
        pipeline.addLast(HANDLER_LOGGING, new LoggingHandler(level));
    }

    private LogLevel parseLogLevel(String configured) {
        if (configured == null || configured.isBlank()) {
            return LogLevel.DEBUG;
        }
        try {
            return LogLevel.valueOf(configured.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("无效的 wireLogLevel [{}]，使用 DEBUG", configured);
            return LogLevel.DEBUG;
        }
    }
}

