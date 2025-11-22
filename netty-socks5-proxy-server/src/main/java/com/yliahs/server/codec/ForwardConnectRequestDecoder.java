package com.yliahs.server.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.util.List;

public class ForwardConnectRequestDecoder extends ByteToMessageDecoder {

    private static final byte PROTOCOL_VERSION = 1;
    private static final byte COMMAND_CONNECT = 1;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) {
            return;
        }
        in.markReaderIndex();

        byte version = in.readByte();
        byte command = in.readByte();
        byte addrTypeByte = in.readByte();

        if (version != PROTOCOL_VERSION || command != COMMAND_CONNECT) {
            in.resetReaderIndex();
            throw new IllegalStateException("Unsupported handshake: version=" + version + " command=" + command);
        }

        Socks5AddressType addressType = Socks5AddressType.valueOf(addrTypeByte);
        String host;

        if (addressType == Socks5AddressType.IPv4) {
            if (in.readableBytes() < 4 + 2) {
                in.resetReaderIndex();
                return;
            }
            host = NetUtil.intToIpAddress(in.readInt());
        } else if (addressType == Socks5AddressType.IPv6) {
            if (in.readableBytes() < 16 + 2) {
                in.resetReaderIndex();
                return;
            }
            byte[] bytes = new byte[16];
            in.readBytes(bytes);
            host = NetUtil.bytesToIpAddress(bytes);
        } else if (addressType == Socks5AddressType.DOMAIN) {
            if (in.readableBytes() < 1) {
                in.resetReaderIndex();
                return;
            }
            int length = in.readUnsignedByte();
            if (in.readableBytes() < length + 2) {
                in.resetReaderIndex();
                return;
            }
            host = in.readCharSequence(length, CharsetUtil.US_ASCII).toString();
        } else {
            in.resetReaderIndex();
            throw new IllegalStateException("Unsupported address type: " + addressType);
        }

        int port = in.readUnsignedShort();
        out.add(new ForwardConnectRequest(addressType, host, port));
    }
}