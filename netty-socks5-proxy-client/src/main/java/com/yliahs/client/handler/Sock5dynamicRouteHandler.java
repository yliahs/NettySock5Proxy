package com.yliahs.client.handler;

import com.yliahs.client.service.Sock5HandlerService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class Sock5dynamicRouteHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        Sock5HandlerService.handshake(ctx);
        ctx.pipeline().remove(this);
        super.channelRegistered(ctx);
    }
}
