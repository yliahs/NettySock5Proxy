package com.yliahs.client.temp2.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;

public final class Socks5UdpUtils {

    private Socks5UdpUtils() {
    }

    /**
     * 解析 SOCKS5 UDP 头部。如果不是有效的头部，返回 null。
     * 注意：此方法会读取 ByteBuf，如果失败会重置 readerIndex。
     */
    public static Socks5UdpMessage unwrap(ByteBuf content) {
        if (content.readableBytes() < 4) {
            return null;
        }

        content.markReaderIndex();
        int rsv = content.readUnsignedShort();
        int frag = content.readByte();

        if (rsv != 0 || frag != 0) {
            content.resetReaderIndex();
            return null;
        }

        try {
            Socks5AddressType addressType = Socks5AddressType.valueOf(content.readByte());
            String dstAddr;
            if (addressType == Socks5AddressType.IPv4) {
                dstAddr = NetUtil.intToIpAddress(content.readInt());
            } else if (addressType == Socks5AddressType.DOMAIN) {
                int fieldLength = content.readUnsignedByte();
                dstAddr = content.readCharSequence(fieldLength, CharsetUtil.US_ASCII).toString();
            } else if (addressType == Socks5AddressType.IPv6) {
                byte[] bytes = new byte[16];
                content.readBytes(bytes);
                dstAddr = NetUtil.bytesToIpAddress(bytes);
            } else {
                content.resetReaderIndex();
                return null;
            }

            int dstPort = content.readUnsignedShort();
            ByteBuf payload = content.readSlice(content.readableBytes()).retain();

            return new Socks5UdpMessage(payload, new InetSocketAddress(dstAddr, dstPort));
        } catch (Exception e) {
            content.resetReaderIndex();
            return null;
        }
    }

    /**
     * 封装 SOCKS5 UDP 头部。
     */
    public static ByteBuf wrap(ByteBufAllocator alloc, InetSocketAddress sender, ByteBuf payload) {
        ByteBuf header = alloc.buffer();
        header.writeShort(0); // RSV
        header.writeByte(0);  // FRAG

        if (sender.getAddress() instanceof Inet4Address) {
            header.writeByte(Socks5AddressType.IPv4.byteValue());
            header.writeBytes(sender.getAddress().getAddress());
        } else if (sender.getAddress() instanceof Inet6Address) {
            header.writeByte(Socks5AddressType.IPv6.byteValue());
            header.writeBytes(sender.getAddress().getAddress());
        } else {
            // Fallback to IPv4 0.0.0.0 if unresolved or unknown
            header.writeByte(Socks5AddressType.IPv4.byteValue());
            header.writeInt(0);
        }
        header.writeShort(sender.getPort());

        return Unpooled.wrappedBuffer(header, payload);
    }
}

