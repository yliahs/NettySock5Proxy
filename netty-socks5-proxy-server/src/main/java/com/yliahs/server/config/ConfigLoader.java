package com.yliahs.server.config;

import com.yliahs.server.utils.YamlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
    public static <T> T load(Class<T> clazz, String path) throws IOException {
        Path fullPath = Path.of(path);
        try (InputStream inputStream = Files.newInputStream(fullPath)) {
            return YamlUtils.fromYaml(inputStream, clazz);
        }
    }
}
