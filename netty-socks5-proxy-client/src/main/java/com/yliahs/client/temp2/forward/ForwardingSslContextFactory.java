package com.yliahs.client.temp2.forward;

import com.yliahs.client.temp2.config.ProxyConfiguration;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.Objects;

/**
 * Builds client-side SSL context for forwarding connections.
 */
public final class ForwardingSslContextFactory {

    private static final Logger log = LoggerFactory.getLogger(ForwardingSslContextFactory.class);

    private ForwardingSslContextFactory() {
    }

    public static SslContext build(ProxyConfiguration.Forwarding forwarding) throws SSLException {
        Objects.requireNonNull(forwarding, "forwarding");
        ProxyConfiguration.Forwarding.Tls tls = forwarding.getTls();
        if (tls == null || !tls.isEnabled()) {
            return null;
        }

        SslContextBuilder builder = SslContextBuilder.forClient();

        if (tls.isInsecureTrustManager()) {
            builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            log.warn("Forwarding TLS: insecureTrustManager enabled – DO NOT use in production environments.");
        } else if (tls.getTrustCertCollectionPath() != null) {
            File trustCert = new File(tls.getTrustCertCollectionPath());
            builder.trustManager(trustCert);
        } else {
            log.info("Forwarding TLS: using system default trust store.");
        }

        if (tls.getKeyCertChainPath() != null && tls.getKeyFilePath() != null) {
            File cert = new File(tls.getKeyCertChainPath());
            File key = new File(tls.getKeyFilePath());
            builder.keyManager(cert, key, tls.getKeyPassword());
            builder.clientAuth(ClientAuth.NONE);
        }

        return builder.build();
    }
}

