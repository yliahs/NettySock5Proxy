package com.yliahs.server;


import com.yliahs.common.config.ConfigLoader;
import com.yliahs.server.config.ServerConfig;

public class ServerApplication {
    static void main() throws Exception {
        ServerConfig serverConfig = ConfigLoader.load(ServerConfig.class, "server.yml");
        ProxyServer proxyServer = new ProxyServer(serverConfig);
        proxyServer.start();
    }
}
