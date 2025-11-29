package com.yliahs.client.temp2.handler;

import com.yliahs.client.temp2.acl.AccessControlManager;
import com.yliahs.client.temp2.config.ProxyConfiguration;
import com.yliahs.client.temp2.pipeline.Socks5ServerInitializer;
import com.yliahs.client.temp2.security.TargetAddressValidator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;

/**
 * Pipeline 安装工具。
 */
public final class CommandPipelineHelper {

    private CommandPipelineHelper() {
    }

    public static void install(ChannelHandlerContext ctx,
                               ProxyConfiguration configuration,
                               AccessControlManager accessControlManager,
                               TargetAddressValidator targetAddressValidator,
                               io.netty.handler.ssl.SslContext forwardingSslContext) {
        ChannelPipeline pipeline = ctx.pipeline();

        replaceOrAdd(pipeline,
                Socks5ServerInitializer.HANDLER_INITIAL_DECODER,
                Socks5ServerInitializer.HANDLER_COMMAND_DECODER,
                Socks5CommandRequestDecoder::new);

        replaceOrAdd(pipeline,
                Socks5ServerInitializer.HANDLER_INITIAL_REQUEST,
                Socks5ServerInitializer.HANDLER_COMMAND_REQUEST,
                () -> new Socks5CommandRequestHandler(configuration, accessControlManager, targetAddressValidator, forwardingSslContext));

        removeIfExists(pipeline, Socks5ServerInitializer.HANDLER_PASSWORD_DECODER);
        removeIfExists(pipeline, Socks5ServerInitializer.HANDLER_PASSWORD_REQUEST);
        removeIfExists(pipeline, Socks5ServerInitializer.HANDLER_HANDSHAKE_TIMEOUT);
    }

    private static void ensureHandler(ChannelPipeline pipeline, String name, HandlerSupplier supplier) {
        if (pipeline.get(name) == null) {
            pipeline.addLast(name, supplier.get());
        }
    }

    private static void replaceOrAdd(ChannelPipeline pipeline,
                                     String existingName,
                                     String targetName,
                                     HandlerSupplier supplier) {
        if (pipeline.get(existingName) != null) {
            pipeline.replace(existingName, targetName, supplier.get());
        } else if (pipeline.get(targetName) == null) {
            ensureHandler(pipeline, targetName, supplier);
        }
    }

    private static void removeIfExists(ChannelPipeline pipeline, String name) {
        if (pipeline.get(name) != null) {
            pipeline.remove(name);
        }
    }

    @FunctionalInterface
    private interface HandlerSupplier {
        ChannelHandler get();
    }
}

