package com.yliahs.client.config;

public class ClientConfig {
    private int port = 10900;
    private int connectTimeoutMillis = 10000;
    private String bindAddress = "0.0.0.0";
    private ForwardingConfig forwardingConfig = new ForwardingConfig();
    private TlsConfig tlsConfig = new TlsConfig();

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

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public ForwardingConfig getForwardingConfig() {
        return forwardingConfig;
    }

    public void setForwardingConfig(ForwardingConfig forwardingConfig) {
        this.forwardingConfig = forwardingConfig;
    }

    public TlsConfig getTlsConfig() {
        return tlsConfig;
    }

    public void setTlsConfig(TlsConfig tlsConfig) {
        this.tlsConfig = tlsConfig;
    }

    public static class TlsConfig {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ForwardingConfig {
        private boolean enabled = false;
        private int port = 1090;
        private String host = "127.0.0.1";
        private int connectTimeoutMillis = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }
    }
}
