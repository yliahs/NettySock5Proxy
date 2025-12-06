package com.yliahs.client.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.NetUtil;

public class ForwardInitHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        sendInitialRequest(ctx);
    }

    private void sendInitialRequest(ChannelHandlerContext ctx) {
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeByte(1);

        ctx.writeAndFlush(buf);
    }


}
