package com.yliahs.tools;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.security.PrivateKey;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.util.io.pem.PemObject;

public class GenerateCerts {

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        String outputDir = args.length > 0 ? args[0] : "certs";
        Path caDir = Path.of(outputDir, "ca");
        Path remoteDir = Path.of(outputDir, "remote");
        Path localDir = Path.of(outputDir, "local");

        Files.createDirectories(caDir);
        Files.createDirectories(remoteDir);
        Files.createDirectories(localDir);

        // ===== Root CA =====
        System.out.println("Generating Root CA...");
        KeyPair caKeyPair = generateKeyPair(4096);
        X509Certificate caCert = generateSelfSignedCertificate(
                caKeyPair,
                "CN=Forwarding-Root-CA",
                825
        );

        writePem(caDir.resolve("ca-key.pem"), caKeyPair.getPrivate());
        writePem(caDir.resolve("ca-cert.pem"), caCert);

        // ===== Remote Server Cert =====
        System.out.println("Generating remote server certificate...");
        KeyPair serverKeyPair = generateKeyPair(2048);
        X509Certificate serverCert = generateSignedCertificate(
                "CN=remote-forward-proxy",
                serverKeyPair,
                caCert,
                caKeyPair.getPrivate(),
                "remote-forward-proxy"
        );

        writePem(remoteDir.resolve("server-key.pem"), serverKeyPair.getPrivate());
        writePem(remoteDir.resolve("server-cert.pem"), serverCert);

        // ===== Local Client Cert =====
        System.out.println("Generating local client certificate...");
        KeyPair clientKeyPair = generateKeyPair(2048);
        X509Certificate clientCert = generateSignedCertificate(
                "CN=local-forward-client",
                clientKeyPair,
                caCert,
                caKeyPair.getPrivate(),
                null
        );

        writePem(localDir.resolve("client-key.pem"), clientKeyPair.getPrivate());
        writePem(localDir.resolve("client-cert.pem"), clientCert);

        // ===== Print Summary =====
        System.out.println();
        System.out.println("Generated certificates:");
        System.out.println("  CA certificate:      " + caDir.resolve("ca-cert.pem"));
        System.out.println("  Remote server cert:  " + remoteDir.resolve("server-cert.pem"));
        System.out.println("  Remote server key:   " + remoteDir.resolve("server-key.pem"));
        System.out.println("  Local client cert:   " + localDir.resolve("client-cert.pem"));
        System.out.println("  Local client key:    " + localDir.resolve("client-key.pem"));
        System.out.println();
    }

    // ------------------ Utility Methods ------------------

    private static KeyPair generateKeyPair(int size) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(size);
        return generator.generateKeyPair();
    }

    private static X509Certificate generateSelfSignedCertificate(
            KeyPair keyPair,
            String subjectDN,
            int days
    ) throws Exception {

        X500Name subject = new X500Name(subjectDN);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60);
        Date notAfter = new Date(System.currentTimeMillis() + days * 86400000L);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic()
        );

        builder.addExtension(
                Extension.basicConstraints,
                true,
                new BasicConstraints(true)
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());

        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static X509Certificate generateSignedCertificate(
            String subjectDN,
            KeyPair subjectKeys,
            X509Certificate caCert,
            PrivateKey caPrivateKey,
            String sanDns // Nullable
    ) throws Exception {

        X500Name subject = new X500Name(subjectDN);
        BigInteger serial = BigInteger.valueOf(System.nanoTime());

        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60);
        Date notAfter = new Date(System.currentTimeMillis() + 825 * 86400000L);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caCert,
                serial,
                notBefore,
                notAfter,
                subject,
                subjectKeys.getPublic()
        );

        if (sanDns != null) {
            GeneralNames san = new GeneralNames(
                    new GeneralName[]{
                            new GeneralName(GeneralName.dNSName, sanDns),
                            new GeneralName(GeneralName.iPAddress, "127.0.0.1")
                    }
            );
            builder.addExtension(Extension.subjectAlternativeName, false, san);
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(caPrivateKey);

        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static void writePem(Path path, Object obj) throws IOException {
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(path.toFile()))) {
            if (obj instanceof PrivateKey) {
                // 明确使用PKCS#8格式写入私钥
                writer.writeObject(new JcaMiscPEMGenerator(obj, null).generate());
            } else {
                writer.writeObject(obj);
            }
        }
    }
}
