package com.yliahs.client.temp2;

import io.netty.util.AttributeKey;

/**
 * Channel Attribute Keys。
 */
public final class Socks5ServerAttributes {

    public static final AttributeKey<Boolean> AUTHENTICATED =
            AttributeKey.valueOf("socks5.authenticated");
    public static final AttributeKey<String> USERNAME =
            AttributeKey.valueOf("socks5.username");

    private Socks5ServerAttributes() {
    }
}

