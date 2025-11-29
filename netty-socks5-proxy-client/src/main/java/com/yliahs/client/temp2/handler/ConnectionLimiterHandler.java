package com.yliahs.client.temp2.handler;

import com.yliahs.client.temp2.concurrent.ConnectionLimiter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 限制并发连接数量。
 */
public class ConnectionLimiterHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ConnectionLimiterHandler.class);

    private final ConnectionLimiter limiter;

    public ConnectionLimiterHandler(ConnectionLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!limiter.tryAcquire()) {
            log.warn("连接数达到上限，拒绝来自 {} 的连接", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        limiter.release();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.debug("ConnectionLimiterHandler 捕获异常", cause);
        ctx.fireExceptionCaught(cause);
    }
}

