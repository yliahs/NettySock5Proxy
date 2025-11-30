package com.yliahs.client.handler;

import com.yliahs.client.config.ClientConfig;
import com.yliahs.client.temp2.endpoint.Socks5UdpEndpoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Objects;

public class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {

    private static final Logger logger = LoggerFactory.getLogger(Socks5CommandRequestHandler.class);

    private final ClientConfig clientConfig;

    public Socks5CommandRequestHandler(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }


    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) throws Exception {
        if (Objects.equals(msg.type(), Socks5CommandType.CONNECT)) {
            handleTcpConnect(ctx, msg);
        } else if (Objects.equals(msg.type(), Socks5CommandType.UDP_ASSOCIATE)) {
            handleUdpAssociate(ctx, msg);
        } else {
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.IPv4))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }


    private void handleTcpConnect(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        //将目标服务器信息转发给客户端
                        ch.pipeline().addLast(new Dest2ClientHandler(ctx));
                    }
                });
        logger.trace("连接目标服务器");
        ChannelFuture future = bootstrap.connect(msg.dstAddr(), msg.dstPort());
        future.addListener(new ChannelFutureListener() {

            public void operationComplete(final ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    logger.trace("成功连接目标服务器");
                    ctx.pipeline().addLast(new Client2DestHandler(future));
                    Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4);
                    ctx.writeAndFlush(commandResponse);
                } else {
                    Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4);
                    ctx.writeAndFlush(commandResponse);
                }
            }
        });
    }

    private void handleUdpAssociate(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) {
        InetSocketAddress clientAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        Socks5UdpEndpoint udpEndpoint = new Socks5UdpEndpoint(ctx.channel().eventLoop(), clientAddress);

        udpEndpoint.start(clientConfig.getBindAddress()).addListener(future -> {
            if (future.isSuccess()) {
                Channel udpChannel = ((ChannelFuture) future).channel();
                InetSocketAddress boundAddress = (InetSocketAddress) udpChannel.localAddress();

                ctx.channel().closeFuture().addListener(f -> {
                    udpEndpoint.close();
                });

                ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                        Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, boundAddress.getAddress().getHostAddress(), boundAddress.getPort()));

            } else {
                ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        });
    }


    /**
     * 将目标服务器信息转发给客户端
     *
     * @author huchengyi
     *
     */
    private static class Dest2ClientHandler extends ChannelInboundHandlerAdapter {

        private ChannelHandlerContext clientChannelContext;

        public Dest2ClientHandler(ChannelHandlerContext clientChannelContext) {
            this.clientChannelContext = clientChannelContext;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx2, Object destMsg) throws Exception {
            logger.trace("将目标服务器信息转发给客户端");
            clientChannelContext.writeAndFlush(destMsg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx2) throws Exception {
            logger.trace("目标服务器断开连接");
            clientChannelContext.channel().close();
        }
    }

    /**
     * 将客户端的消息转发给目标服务器端
     *
     * @author huchengyi
     *
     */
    private static class Client2DestHandler extends ChannelInboundHandlerAdapter {

        private ChannelFuture destChannelFuture;

        public Client2DestHandler(ChannelFuture destChannelFuture) {
            this.destChannelFuture = destChannelFuture;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            logger.trace("将客户端的消息转发给目标服务器端");
            destChannelFuture.channel().writeAndFlush(msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            logger.trace("客户端断开连接");
            destChannelFuture.channel().close();
        }
    }
}
