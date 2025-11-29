package com.yliahs.client.temp2.handler;

import com.yliahs.client.temp2.Socks5ServerAttributes;
import com.yliahs.client.temp2.acl.AccessControlManager;
import com.yliahs.client.temp2.auth.AuthenticationManager;
import com.yliahs.client.temp2.config.AuthType;
import com.yliahs.client.temp2.config.ProxyConfiguration;
import com.yliahs.client.temp2.pipeline.Socks5ServerInitializer;
import com.yliahs.client.temp2.security.TargetAddressValidator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 处理 SOCKS5 Method Negotiation。
 */
public class Socks5InitialRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5InitialRequest> {

    private static final Logger log = LoggerFactory.getLogger(Socks5InitialRequestHandler.class);

    private final ProxyConfiguration configuration;
    private final AuthenticationManager authenticationManager;
    private final AccessControlManager accessControlManager;
    private final TargetAddressValidator targetAddressValidator;
    private final io.netty.handler.ssl.SslContext forwardingSslContext;

    public Socks5InitialRequestHandler(ProxyConfiguration configuration,
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
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5InitialRequest msg) {
        List<Socks5AuthMethod> methods = msg.authMethods();
        log.debug("客户端支持的认证方法: {}", methods);

        Socks5AuthMethod method = selectAuthMethod(methods);
        if (method == null || method == Socks5AuthMethod.UNACCEPTED) {
            log.debug("没有可接受的认证方法，关闭连接");
            sendResponseAndClose(ctx, Socks5AuthMethod.UNACCEPTED);
            return;
        }

        if (method == Socks5AuthMethod.NO_AUTH) {
            ctx.channel().attr(Socks5ServerAttributes.AUTHENTICATED).set(Boolean.TRUE);
            CommandPipelineHelper.install(ctx, configuration, accessControlManager, targetAddressValidator, forwardingSslContext);
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
                    .addListener(future -> {
                        if (!future.isSuccess()) {
                            ctx.close();
                        }
                    });
            cleanup(ctx.pipeline());
            return;
        }

        if (method == Socks5AuthMethod.PASSWORD) {
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD))
                    .addListener(future -> {
                        if (!future.isSuccess()) {
                            ctx.close();
                        }
                    });
            installPasswordHandlers(ctx.pipeline());
            return;
        }

        sendResponseAndClose(ctx, Socks5AuthMethod.UNACCEPTED);
    }

    private Socks5AuthMethod selectAuthMethod(List<Socks5AuthMethod> methods) {
        if (authenticationManager.getAuthType() == AuthType.NONE) {
            return methods.contains(Socks5AuthMethod.NO_AUTH)
                    ? Socks5AuthMethod.NO_AUTH
                    : Socks5AuthMethod.UNACCEPTED;
        }
        if (authenticationManager.getAuthType() == AuthType.USERNAME_PASSWORD) {
            return methods.contains(Socks5AuthMethod.PASSWORD)
                    ? Socks5AuthMethod.PASSWORD
                    : Socks5AuthMethod.UNACCEPTED;
        }
        return Socks5AuthMethod.UNACCEPTED;
    }

    private void installPasswordHandlers(ChannelPipeline pipeline) {
        removeIfExists(pipeline, Socks5ServerInitializer.HANDLER_PASSWORD_DECODER);
        removeIfExists(pipeline, Socks5ServerInitializer.HANDLER_PASSWORD_REQUEST);
        pipeline.addAfter(Socks5ServerInitializer.HANDLER_INITIAL_REQUEST,
                Socks5ServerInitializer.HANDLER_PASSWORD_DECODER,
                new Socks5PasswordAuthRequestDecoder());
        pipeline.addAfter(Socks5ServerInitializer.HANDLER_PASSWORD_DECODER,
                Socks5ServerInitializer.HANDLER_PASSWORD_REQUEST,
                new Socks5PasswordAuthRequestHandler(configuration, authenticationManager, accessControlManager, targetAddressValidator, forwardingSslContext));
        cleanup(pipeline);
    }

    private void sendResponseAndClose(ChannelHandlerContext ctx, Socks5AuthMethod method) {
        ctx.writeAndFlush(new DefaultSocks5InitialResponse(method))
                .addListener(future -> ctx.close());
    }

    private void cleanup(ChannelPipeline pipeline) {
        removeIfExists(pipeline, Socks5ServerInitializer.HANDLER_INITIAL_DECODER);
        removeIfExists(pipeline, Socks5ServerInitializer.HANDLER_INITIAL_REQUEST);
    }

    private void removeIfExists(ChannelPipeline pipeline, String name) {
        if (pipeline.get(name) != null) {
            pipeline.remove(name);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("协商阶段异常", cause);
        ctx.close();
    }
}

