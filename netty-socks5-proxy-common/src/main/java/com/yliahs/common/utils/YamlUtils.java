package com.yliahs.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;

public class YamlUtils {
    public static final ObjectMapper Mapper = new ObjectMapper(new YAMLFactory());

    private static ObjectMapper getMapper() {
        return Mapper;
    }

    public static String toYaml(Object obj) {
        try {
            ObjectMapper mapper = getMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromYaml(String json, Class<T> clazz) {
        try {
            ObjectMapper mapper = getMapper();
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T fromYaml(InputStream in, Class<T> clazz) {
        try {
            ObjectMapper mapper = getMapper();
            return mapper.readValue(in, clazz);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
