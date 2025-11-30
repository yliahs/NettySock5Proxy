package com.yliahs.client.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;

import java.util.List;

public class Socks5InitialRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5InitialRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5InitialRequest msg) throws Exception {
        List<Socks5AuthMethod> methods = msg.authMethods();

        Socks5InitialResponse initialResponse;
        if (msg.decoderResult().isSuccess() &&
                msg.version().equals(SocksVersion.SOCKS5) &&
                methods.contains(Socks5AuthMethod.NO_AUTH)) {
            initialResponse = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
            ctx.writeAndFlush(initialResponse);
        } else {
            initialResponse = new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED);
            ctx.writeAndFlush(initialResponse).addListener(ChannelFutureListener.CLOSE);
        }
    }

}
