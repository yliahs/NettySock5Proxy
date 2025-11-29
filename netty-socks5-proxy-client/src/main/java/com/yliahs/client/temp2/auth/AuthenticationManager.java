package com.yliahs.client.temp2.auth;

import com.yliahs.client.temp2.config.AuthType;
import com.yliahs.client.temp2.config.ProxyConfiguration;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户认证管理器。
 */
public class AuthenticationManager {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationManager.class);

    private final AuthType authType;
    private final Map<String, String> credentials;

    public AuthenticationManager(ProxyConfiguration.Authentication authentication) {
        Objects.requireNonNull(authentication, "authentication");
        this.authType = authentication.getType();
        this.credentials = buildCredentialMap(authentication);
    }

    private Map<String, String> buildCredentialMap(ProxyConfiguration.Authentication authentication) {
        if (authentication.getUsers().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> creds = new ConcurrentHashMap<>();
        for (ProxyConfiguration.UserCredential user : authentication.getUsers()) {
            String username = user.getUsername();
            if (username == null || username.isBlank()) {
                continue;
            }
            String passwordHash = extractPasswordHash(user);
            if (passwordHash != null) {
                creds.put(username, passwordHash);
            }
        }
        return creds;
    }

    private String extractPasswordHash(ProxyConfiguration.UserCredential user) {
        String username = user.getUsername();
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            return user.getPasswordHash();
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            throw new IllegalArgumentException("用户 " + username + " 配置了明文密码，请改用 passwordHash (BCrypt)");
        }
        log.warn("用户 [{}] 未设置 passwordHash，认证将始终失败", username);
        return null;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public boolean isAuthenticationRequired() {
        return authType == AuthType.USERNAME_PASSWORD;
    }

    public boolean authenticate(String username, String password) {
        if (!isAuthenticationRequired()) {
            return true;
        }
        if (username == null || password == null) {
            return false;
        }
        String expectedHash = credentials.get(username);
        if (expectedHash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, expectedHash);
        } catch (IllegalArgumentException e) {
            log.warn("用户 [{}] 的 passwordHash 无效", username, e);
            return false;
        }
    }
}

