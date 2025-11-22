package com.yliahs.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class RemoteProxyConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(RemoteProxyConfigLoader.class);
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public RemoteProxyConfig load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        log.info("Loading remote proxy config: {}", path.toAbsolutePath());
        try (InputStream in = Files.newInputStream(path)) {
            return mapper.readValue(in, RemoteProxyConfig.class);
        }
    }
}