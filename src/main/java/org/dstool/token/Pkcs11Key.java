package org.dstool.token;

import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;

/**
 * One signing identity on a PKCS#11 token: a private-key object handle paired with
 * its certificate (matched by {@code CKA_ID}) and certificate chain.
 */
public final class Pkcs11Key implements DSSPrivateKeyEntry {

    private final long privateKeyHandle;
    private final CertificateToken certificate;
    private final CertificateToken[] certificateChain;
    private final EncryptionAlgorithm encryptionAlgorithm;

    public Pkcs11Key(long privateKeyHandle,
                     CertificateToken certificate,
                     CertificateToken[] certificateChain,
                     EncryptionAlgorithm encryptionAlgorithm) {
        this.privateKeyHandle = privateKeyHandle;
        this.certificate = certificate;
        this.certificateChain = certificateChain;
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    /** The PKCS#11 object handle of the private key (used for {@code C_SignInit}). */
    public long handle() {
        return privateKeyHandle;
    }

    @Override
    public CertificateToken getCertificate() {
        return certificate;
    }

    @Override
    public CertificateToken[] getCertificateChain() {
        return certificateChain;
    }

    @Override
    public EncryptionAlgorithm getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }
}
