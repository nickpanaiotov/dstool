package org.dstool.core;

import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;

/**
 * Core CAdES signing logic, independent of the CLI and of how the token is backed
 * (PKCS#11 smart card, PKCS#12 file, ...). Produces a CAdES-BASELINE-B signature.
 *
 * <p>Attached vs. detached is the only branch: {@link SignatureFormat#packaging()}.
 * The {@code getDataToSign → token.sign → signDocument} dance is identical for both.
 * For a single (non-parallel) detached signature DSS binds the detached content from
 * {@code toSign} automatically, so {@code setDetachedContents(...)} must <em>not</em>
 * be called here.</p>
 */
public final class CadesSigner {

    private final CAdESService service = new CAdESService(new CommonCertificateVerifier());

    /**
     * Signs {@code toSign} and returns the signed document.
     *
     * @param toSign the document to sign
     * @param token  an open token connection able to sign with {@code key}
     * @param key    the signing identity selected on the token
     * @param format attached (ENVELOPING/.p7m) or detached (DETACHED/.p7s)
     * @param digest digest algorithm (e.g. SHA-256)
     */
    public DSSDocument sign(DSSDocument toSign,
                            SignatureTokenConnection token,
                            DSSPrivateKeyEntry key,
                            SignatureFormat format,
                            DigestAlgorithm digest) {

        CAdESSignatureParameters params = new CAdESSignatureParameters();
        params.setSignatureLevel(SignatureLevel.CAdES_BASELINE_B);
        params.setSignaturePackaging(format.packaging());
        params.setDigestAlgorithm(digest);
        params.setSigningCertificate(key.getCertificate());
        params.setCertificateChain(key.getCertificateChain());

        ToBeSigned dataToSign = service.getDataToSign(toSign, params);
        SignatureValue signatureValue = token.sign(dataToSign, digest, key);
        return service.signDocument(toSign, params, signatureValue);
    }
}
