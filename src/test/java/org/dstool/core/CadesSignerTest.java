package org.dstool.core;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M0: proves CAdES-BASELINE-B signing (attached + detached) works on JDK 25 with
 * DSS 6.4, using a software PKCS#12 key — no smart card required. The same
 * {@link CadesSigner} is later driven by the FFM PKCS#11 token against a real card.
 */
class CadesSignerTest {

    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final byte[] CONTENT = "Заявление до общината — тест на подпис.".getBytes(StandardCharsets.UTF_8);

    private final CadesSigner signer = new CadesSigner();

    @BeforeAll
    static void registerBc() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void attachedSignatureEmbedsContentAndVerifies(@TempDir Path tmp) throws Exception {
        try (Pkcs12SignatureToken token = openToken(tmp)) {
            DSSPrivateKeyEntry key = token.getKeys().get(0);
            DSSDocument toSign = new InMemoryDocument(CONTENT);

            DSSDocument signed = signer.sign(toSign, token, key, SignatureFormat.ATTACHED, DigestAlgorithm.SHA256);
            byte[] p7m = readAll(signed);

            // Attached: CMS carries the eContent, so it verifies without supplying the original.
            CMSSignedData cms = new CMSSignedData(p7m);
            assertNotNull(cms.getSignedContent(), "attached CMS must embed the content");
            assertVerifies(cms, key);
        }
    }

    @Test
    void detachedSignatureVerifiesAgainstOriginal(@TempDir Path tmp) throws Exception {
        try (Pkcs12SignatureToken token = openToken(tmp)) {
            DSSPrivateKeyEntry key = token.getKeys().get(0);
            DSSDocument toSign = new InMemoryDocument(CONTENT);

            DSSDocument signed = signer.sign(toSign, token, key, SignatureFormat.DETACHED, DigestAlgorithm.SHA256);
            byte[] p7s = readAll(signed);

            // Detached: CMS has no eContent; the original must be supplied to verify.
            CMSSignedData detached = new CMSSignedData(new CMSProcessableByteArray(CONTENT), p7s);
            assertEquals(1, detached.getSignerInfos().size());
            assertVerifies(detached, key);
        }
    }

    // --- helpers -----------------------------------------------------------

    private static Pkcs12SignatureToken openToken(Path tmp) throws Exception {
        File p12 = generatePkcs12(tmp);
        return new Pkcs12SignatureToken(p12, new KeyStore.PasswordProtection(PASSWORD));
    }

    private static File generatePkcs12(Path tmp) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X500Principal dn = new X500Principal("CN=DSTool Test, O=DSTool, C=BG");
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 86_400_000L);
        Date notAfter = new Date(now + 365L * 86_400_000L);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn, BigInteger.valueOf(now), notBefore, notAfter, dn, kp.getPublic());
        ContentSigner cs = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(kp.getPrivate());
        X509CertificateHolder holder = builder.build(cs);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(holder);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("dstool", kp.getPrivate(), PASSWORD, new Certificate[]{cert});

        File p12 = tmp.resolve("dstool-test.p12").toFile();
        try (OutputStream os = Files.newOutputStream(p12.toPath())) {
            ks.store(os, PASSWORD);
        }
        return p12;
    }

    private static void assertVerifies(CMSSignedData cms, DSSPrivateKeyEntry key) throws Exception {
        X509Certificate cert = key.getCertificate().getCertificate();
        List<SignerInformation> signers = List.copyOf(cms.getSignerInfos().getSigners());
        assertTrue(signers.size() >= 1, "at least one signer expected");
        for (SignerInformation si : signers) {
            assertTrue(si.verify(new JcaSimpleSignerInfoVerifierBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(cert)),
                    "signature must verify against the signing certificate");
        }
    }

    private static byte[] readAll(DSSDocument document) throws Exception {
        return document.openStream().readAllBytes();
    }
}
