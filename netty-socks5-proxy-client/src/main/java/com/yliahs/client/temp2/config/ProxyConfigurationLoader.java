package com.yliahs.client.temp2.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 从 YAML / JSON 文件加载 {@link ProxyConfiguration}。
 */
public final class ProxyConfigurationLoader {

    private static final Logger log = LoggerFactory.getLogger(ProxyConfigurationLoader.class);

    private final ObjectMapper mapper;

    public ProxyConfigurationLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory());
    }

    public ProxyConfiguration load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        log.info("加载配置文件: {}", path.toAbsolutePath());
        try (InputStream in = Files.newInputStream(path)) {
            return mapper.readValue(in, ProxyConfiguration.class);
        }
    }
}

