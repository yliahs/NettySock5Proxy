package com.yliahs.server.codec;

import io.netty.handler.codec.socksx.v5.Socks5AddressType;

public record ForwardConnectRequest(Socks5AddressType addressType, String host, int port) {

}
