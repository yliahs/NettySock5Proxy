package com.yliahs.client.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.util.internal.StringUtil;

import java.net.InetSocketAddress;

public class Socks5UdpMessage extends DefaultByteBufHolder {
    private final InetSocketAddress address;

    public Socks5UdpMessage(ByteBuf data, InetSocketAddress address) {
        super(data);
        this.address = address;
    }

    public InetSocketAddress address() {
        return address;
    }

    @Override
    public Socks5UdpMessage copy() {
        return new Socks5UdpMessage(content().copy(), address);
    }

    @Override
    public Socks5UdpMessage duplicate() {
        return new Socks5UdpMessage(content().duplicate(), address);
    }

    @Override
    public Socks5UdpMessage retainedDuplicate() {
        return new Socks5UdpMessage(content().retainedDuplicate(), address);
    }

    @Override
    public Socks5UdpMessage replace(ByteBuf content) {
        return new Socks5UdpMessage(content, address);
    }

    @Override
    public Socks5UdpMessage retain() {
        super.retain();
        return this;
    }

    @Override
    public Socks5UdpMessage retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public Socks5UdpMessage touch() {
        super.touch();
        return this;
    }

    @Override
    public Socks5UdpMessage touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(address: " + address + ", content: " + content() + ')';
    }
}

