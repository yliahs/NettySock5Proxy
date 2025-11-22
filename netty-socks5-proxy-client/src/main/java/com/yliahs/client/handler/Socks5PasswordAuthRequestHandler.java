package com.yliahs.client.handler;

import com.yliahs.client.Socks5ServerAttributes;
import com.yliahs.client.acl.AccessControlManager;
import com.yliahs.client.auth.AuthenticationManager;
import com.yliahs.client.config.ProxyConfiguration;
import com.yliahs.client.pipeline.Socks5ServerInitializer;
import com.yliahs.client.security.TargetAddressValidator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理用户名密码认证。
 */
public class Socks5PasswordAuthRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5PasswordAuthRequest> {

    private static final Logger log = LoggerFactory.getLogger(Socks5PasswordAuthRequestHandler.class);

    private final ProxyConfiguration configuration;
    private final AuthenticationManager authenticationManager;
    private final AccessControlManager accessControlManager;
    private final TargetAddressValidator targetAddressValidator;
    private final io.netty.handler.ssl.SslContext forwardingSslContext;

    public Socks5PasswordAuthRequestHandler(ProxyConfiguration configuration,
                                            AuthenticationManager authenticationManager,
                                            AccessControlManager accessControlManager,
                                            TargetAddressValidator targetAddressValidator,
                                            io.netty.handler.ssl.SslContext forwardingSslContext) {
        this.configuration = configuration;
        this.authenticationManager = authenticationManager;
        this.accessControlManager = accessControlManager;
        this.targetAddressValidator = targetAddressValidator;
        this.forwardingSslContext = forwardingSslContext;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5PasswordAuthRequest msg) {
        boolean authenticated = authenticationManager.authenticate(msg.username(), msg.password());
        if (authenticated) {
            ctx.channel().attr(Socks5ServerAttributes.AUTHENTICATED).set(Boolean.TRUE);
            ctx.channel().attr(Socks5ServerAttributes.USERNAME).set(msg.username());
            CommandPipelineHelper.install(ctx, configuration, accessControlManager, targetAddressValidator, forwardingSslContext);
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS))
                    .addListener(future -> {
                        if (!future.isSuccess()) {
                            ctx.close();
                        }
                    });
            cleanup(ctx.pipeline());
        } else {
            log.warn("认证失败，连接: {}", ctx.channel().remoteAddress());
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                    .addListener(future -> ctx.close());
        }
    }

    private void cleanup(ChannelPipeline pipeline) {
        removeIfExists(pipeline, Socks5ServerInitializer.HANDLER_PASSWORD_DECODER);
        removeIfExists(pipeline, Socks5ServerInitializer.HANDLER_PASSWORD_REQUEST);
    }

    private void removeIfExists(ChannelPipeline pipeline, String name) {
        if (pipeline.get(name) != null) {
            pipeline.remove(name);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("用户名密码认证阶段异常", cause);
        ctx.close();
    }
}

