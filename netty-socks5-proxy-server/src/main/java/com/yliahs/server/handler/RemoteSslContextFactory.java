package com.yliahs.server.handler;

import com.yliahs.server.config.RemoteProxyConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.Objects;

public final class RemoteSslContextFactory {

    private static final Logger log = LoggerFactory.getLogger(RemoteSslContextFactory.class);

    private RemoteSslContextFactory() {
    }

    public static SslContext build(RemoteProxyConfig config) throws SSLException {
        Objects.requireNonNull(config, "config");
        RemoteProxyConfig.Tls tls = config.getTls();
        if (tls == null || !tls.isEnabled()) {
            return null;
        }

        File certChain = new File(Objects.requireNonNull(tls.getKeyCertChainPath(), "tls.keyCertChainPath is required"));
        File keyFile = new File(Objects.requireNonNull(tls.getKeyFilePath(), "tls.keyFilePath is required"));

        SslContextBuilder builder = SslContextBuilder.forServer(certChain, keyFile, tls.getKeyPassword());

        if (tls.getTrustCertCollectionPath() != null) {
            File trustCert = new File(tls.getTrustCertCollectionPath());
            builder.trustManager(trustCert);
            if (tls.isClientAuth()) {
                builder.clientAuth(ClientAuth.REQUIRE);
            }
        } else if (tls.isClientAuth()) {
            throw new IllegalArgumentException("Client authentication requires tls.trustCertCollectionPath");
        }

        return builder.build();
    }
}

