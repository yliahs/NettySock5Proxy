package com.yliahs.server.config;

public class RemoteProxyConfig {

    private String bindAddress = "0.0.0.0";
    private int port = 1090;
    private int connectTimeoutMillis = 10000;
    private Tls tls = new Tls();

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
        private String keyCertChainPath;
        private String keyFilePath;
        private String keyPassword;
        private String trustCertCollectionPath;
        private boolean clientAuth = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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

        public String getTrustCertCollectionPath() {
            return trustCertCollectionPath;
        }

        public void setTrustCertCollectionPath(String trustCertCollectionPath) {
            this.trustCertCollectionPath = trustCertCollectionPath;
        }

        public boolean isClientAuth() {
            return clientAuth;
        }

        public void setClientAuth(boolean clientAuth) {
            this.clientAuth = clientAuth;
        }
    }
}
