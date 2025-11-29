package com.yliahs.client.temp2.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理空闲连接关闭。
 */
public class IdleTimeoutHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(IdleTimeoutHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleStateEvent) {
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                log.info("连接空闲超时，关闭 {}", ctx.channel());
                ctx.close();
                return;
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}

