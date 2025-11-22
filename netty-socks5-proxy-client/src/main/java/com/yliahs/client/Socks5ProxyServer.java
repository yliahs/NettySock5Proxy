package com.yliahs.client;

import com.yliahs.client.bootstrap.Socks5ServerBootstrap;
import com.yliahs.client.config.ProxyConfiguration;
import com.yliahs.client.config.ProxyConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用入口。
 */
public final class Socks5ProxyServer {

    private static final Logger log = LoggerFactory.getLogger(Socks5ProxyServer.class);
    private static final String DEFAULT_CONFIG_PATH = "application.yml";

    private Socks5ProxyServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        Path configPath = resolveConfigPath(args);
        ProxyConfiguration configuration = new ProxyConfiguration();
        if (configPath != null) {
            try {
                configuration = new ProxyConfigurationLoader().load(configPath);
            } catch (IOException e) {
                log.error("加载配置失败: {}", configPath.toAbsolutePath(), e);
                System.exit(1);
                return;
            }
        } else {
            log.info("未提供配置文件，使用默认配置");
        }

        Socks5ServerBootstrap bootstrap = new Socks5ServerBootstrap(configuration);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered, stopping SOCKS5 proxy...");
            bootstrap.stop();
        }));

        try {
            bootstrap.start().sync().channel().closeFuture().sync();
        } finally {
            bootstrap.stop();
        }
    }

    private static Path resolveConfigPath(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Paths.get(args[0]);
        }

        Path defaultPath = Paths.get(DEFAULT_CONFIG_PATH);
        if (Files.exists(defaultPath)) {
            return defaultPath;
        }
        return null;
    }
}

