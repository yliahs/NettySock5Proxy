package com.yliahs.client.service;

import com.yliahs.client.handler.Socks5CommandRequestHandler;
import com.yliahs.client.handler.Socks5InitialRequestHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;

public class Sock5HandlerService {

    public static void handshake(ChannelHandlerContext channelHandlerContext)
    {
        ChannelPipeline channelPipeline = channelHandlerContext.pipeline();
        channelPipeline.addLast(new Socks5InitialRequestDecoder());
        channelPipeline.addLast(new Socks5InitialRequestHandler());
        channelPipeline.addLast(Socks5ServerEncoder.DEFAULT);
    }

    public static void connected(ChannelHandlerContext channelHandlerContext)
    {
        ChannelPipeline channelPipeline = channelHandlerContext.pipeline();
//        channelPipeline.remove(new Socks5InitialRequestDecoder());
//        channelPipeline.remove(new Socks5InitialRequestHandler());
        channelPipeline.addLast(new Socks5CommandRequestDecoder());
        channelPipeline.addLast(new Socks5CommandRequestHandler(null));
    }
}
