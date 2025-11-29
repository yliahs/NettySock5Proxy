package com.yliahs.client.temp2.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SOCKS5 代理的整体配置模型。
 */
public class ProxyConfiguration {

    private String bindAddress = "0.0.0.0";
    private int port = 1080;
    private int maxConcurrentConnections = 1024;
    private Timeouts timeouts = new Timeouts();
    private Authentication authentication = new Authentication();
    private AccessControl accessControl = new AccessControl();
    private Security security = new Security();
    private Logging logging = new Logging();
    private Forwarding forwarding = new Forwarding();

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMaxConcurrentConnections() {
        return maxConcurrentConnections;
    }

    public void setMaxConcurrentConnections(int maxConcurrentConnections) {
        this.maxConcurrentConnections = maxConcurrentConnections;
    }

    public Timeouts getTimeouts() {
        return timeouts;
    }

    public void setTimeouts(Timeouts timeouts) {
        this.timeouts = timeouts;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }

    public AccessControl getAccessControl() {
        return accessControl;
    }

    public void setAccessControl(AccessControl accessControl) {
        this.accessControl = accessControl;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Logging getLogging() {
        return logging;
    }

    public void setLogging(Logging logging) {
        this.logging = logging;
    }

    public Forwarding getForwarding() {
        return forwarding;
    }

    public void setForwarding(Forwarding forwarding) {
        this.forwarding = forwarding;
    }

    public static class Timeouts {
        private int handshakeSeconds = 15;
        private int idleSeconds = 300;
        private int connectTimeoutMillis = 10000;

        public int getHandshakeSeconds() {
            return handshakeSeconds;
        }

        public void setHandshakeSeconds(int handshakeSeconds) {
            this.handshakeSeconds = handshakeSeconds;
        }

        public int getIdleSeconds() {
            return idleSeconds;
        }

        public void setIdleSeconds(int idleSeconds) {
            this.idleSeconds = idleSeconds;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }
    }

    public static class Authentication {
        private AuthType type = AuthType.NONE;
        private List<UserCredential> users = new ArrayList<>();

        public AuthType getType() {
            return type;
        }

        public void setType(AuthType type) {
            this.type = type;
        }

        public List<UserCredential> getUsers() {
            return Collections.unmodifiableList(users);
        }

        public void setUsers(List<UserCredential> users) {
            this.users = new ArrayList<>(Objects.requireNonNullElse(users, Collections.emptyList()));
        }
    }

    public static class UserCredential {
        private String username;
        private String password;
        private String passwordHash;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }
    }

    public static class AccessControl {
        private List<String> allow = new ArrayList<>();
        private List<String> deny = new ArrayList<>();
        private boolean defaultAllow = true;

        public List<String> getAllow() {
            return Collections.unmodifiableList(allow);
        }

        public void setAllow(List<String> allow) {
            this.allow = new ArrayList<>(Objects.requireNonNullElse(allow, Collections.emptyList()));
        }

        public List<String> getDeny() {
            return Collections.unmodifiableList(deny);
        }

        public void setDeny(List<String> deny) {
            this.deny = new ArrayList<>(Objects.requireNonNullElse(deny, Collections.emptyList()));
        }

        public boolean isDefaultAllow() {
            return defaultAllow;
        }

        public void setDefaultAllow(boolean defaultAllow) {
            this.defaultAllow = defaultAllow;
        }
    }

    public static class Security {
        private boolean blockPrivateNetworks = true;
        private boolean allowLoopback = false;
        private List<String> blockedSubnets = new ArrayList<>(List.of(
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16",
                "169.254.0.0/16",
                "127.0.0.0/8",
                "::1/128",
                "fc00::/7",
                "fe80::/10"
        ));
        private List<Integer> blockedPorts = new ArrayList<>(List.of(
                0, 22, 23, 25, 53, 135, 139, 161, 445, 3306, 3389, 5432, 5900, 6379, 11211
        ));

        public boolean isBlockPrivateNetworks() {
            return blockPrivateNetworks;
        }

        public void setBlockPrivateNetworks(boolean blockPrivateNetworks) {
            this.blockPrivateNetworks = blockPrivateNetworks;
        }

        public boolean isAllowLoopback() {
            return allowLoopback;
        }

        public void setAllowLoopback(boolean allowLoopback) {
            this.allowLoopback = allowLoopback;
        }

        public List<String> getBlockedSubnets() {
            return Collections.unmodifiableList(blockedSubnets);
        }

        public void setBlockedSubnets(List<String> blockedSubnets) {
            this.blockedSubnets = new ArrayList<>(Objects.requireNonNullElse(blockedSubnets, Collections.emptyList()));
        }

        public List<Integer> getBlockedPorts() {
            return Collections.unmodifiableList(blockedPorts);
        }

        public void setBlockedPorts(List<Integer> blockedPorts) {
            this.blockedPorts = new ArrayList<>(Objects.requireNonNullElse(blockedPorts, Collections.emptyList()));
        }
    }

    public static class Logging {
        private boolean enableWireLogging = false;
        private String wireLogLevel = "DEBUG";

        public boolean isEnableWireLogging() {
            return enableWireLogging;
        }

        public void setEnableWireLogging(boolean enableWireLogging) {
            this.enableWireLogging = enableWireLogging;
        }

        public String getWireLogLevel() {
            return wireLogLevel;
        }

        public void setWireLogLevel(String wireLogLevel) {
            this.wireLogLevel = wireLogLevel;
        }
    }

    public static class Forwarding {
        private boolean enabled = false;
        private String remoteHost = "127.0.0.1";
        private int remotePort = 1090;
        private int connectTimeoutMillis = 10000;
        private Tls tls = new Tls();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRemoteHost() {
            return remoteHost;
        }

        public void setRemoteHost(String remoteHost) {
            this.remoteHost = remoteHost;
        }

        public int getRemotePort() {
            return remotePort;
        }

        public void setRemotePort(int remotePort) {
            this.remotePort = remotePort;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public Tls getTls() {
            return tls;
        }

        public void setTls(Tls tls) {
            this.tls = tls;
        }

        public static class Tls {
            private boolean enabled = false;
            /**
             * PEM encoded certificate chain trusted for the remote endpoint.
             * Optional when insecureTrustManager=true.
             */
            private String trustCertCollectionPath;
            /**
             * If true, trust all certificates (for development only).
             */
            private boolean insecureTrustManager = false;
            /**
             * Optional client certificate chain (PEM) for mutual TLS.
             */
            private String keyCertChainPath;
            /**
             * Optional client private key (PEM) for mutual TLS.
             */
            private String keyFilePath;
            private String keyPassword;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getTrustCertCollectionPath() {
                return trustCertCollectionPath;
            }

            public void setTrustCertCollectionPath(String trustCertCollectionPath) {
                this.trustCertCollectionPath = trustCertCollectionPath;
            }

            public boolean isInsecureTrustManager() {
                return insecureTrustManager;
            }

            public void setInsecureTrustManager(boolean insecureTrustManager) {
                this.insecureTrustManager = insecureTrustManager;
            }

            public String getKeyCertChainPath() {
                return keyCertChainPath;
            }

            public void setKeyCertChainPath(String keyCertChainPath) {
                this.keyCertChainPath = keyCertChainPath;
            }

            public String getKeyFilePath() {
                return keyFilePath;
            }

            public void setKeyFilePath(String keyFilePath) {
                this.keyFilePath = keyFilePath;
            }

            public String getKeyPassword() {
                return keyPassword;
            }

            public void setKeyPassword(String keyPassword) {
                this.keyPassword = keyPassword;
            }
        }
    }
}

