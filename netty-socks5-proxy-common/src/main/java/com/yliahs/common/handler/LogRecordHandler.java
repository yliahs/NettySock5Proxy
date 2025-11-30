package com.yliahs.common.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Enumeration;

public class LogRecordHandler extends ChannelTrafficShapingHandler {
    private static final Logger logger = LoggerFactory.getLogger(LogRecordHandler.class);

    private long startTime;
    private long endTime;

    public LogRecordHandler(long writeLimit, long readLimit, long checkInterval, long maxTime) {
        super(writeLimit, readLimit, checkInterval, maxTime);
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        startTime = System.currentTimeMillis();
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        endTime = System.currentTimeMillis();
        log(ctx);
        super.channelInactive(ctx);
    }

    private void log(ChannelHandlerContext ctx) {
        InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
        InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();

        long readByte = trafficCounter().cumulativeReadBytes();
        long writeByte = trafficCounter().cumulativeWrittenBytes();

        logger.info("{},{},{},{}:{},{}:{},{},{}",
                startTime,
                endTime,
                getLocalAddress(),
                localAddress.getPort(),
                remoteAddress.getAddress().getHostAddress(),
                remoteAddress.getPort(),
                readByte,
                writeByte,
                (readByte + writeByte));
    }

    /**
     * 获取本机的IP
     *
     * @return Ip地址
     */
    private String getLocalAddress() {
        try {
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements(); ) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                if (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            logger.debug("Error when getting host ip address: <{}>.", e.getMessage());
        }
        return "127.0.0.1";
    }
}


