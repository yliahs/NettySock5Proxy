package com.yliahs.client;

import com.yliahs.client.config.ClientConfig;
import com.yliahs.common.config.ConfigLoader;

public class ClientApplication {
    static void main() throws Exception {
        ClientConfig clientConfig = ConfigLoader.load(ClientConfig.class, "/Users/henry/IdeaProjects/NettySock5Proxy/netty-socks5-proxy-client/src/main/resources/client.yml");
        ProxyClient proxyClient = new ProxyClient(clientConfig);
        proxyClient.start();
    }
}
