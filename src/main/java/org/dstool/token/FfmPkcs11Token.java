package org.dstool.token;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.Digest;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.dstool.token.pkcs11.Ck;
import org.dstool.token.pkcs11.Pkcs11Exception;
import org.dstool.token.pkcs11.Pkcs11Library;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A DSS {@link SignatureTokenConnection} backed by a PKCS#11 smart card / USB token,
 * reached through the pure-FFM {@link Pkcs11Library} (no JNI, so it works in a native image).
 *
 * <p>It does <em>not</em> extend {@code AbstractSignatureTokenConnection}: that base class
 * signs through JCA with a {@code java.security.PrivateKey}, which a smart card never exposes.
 * Instead we compute the digest ourselves and let the card perform the raw private-key
 * operation ({@code CKM_RSA_PKCS} over a DER {@code DigestInfo}, or {@code CKM_ECDSA} over the
 * raw digest).</p>
 */
public final class FfmPkcs11Token implements SignatureTokenConnection {

    private final Pkcs11Library library;
    private final long session;
    private final List<DSSPrivateKeyEntry> keys;
    private boolean closed;

    /** A PKCS#11 slot containing a token, with its label (e.g. "… (Digital Signature PIN)"). */
    public record SlotInfo(int index, long slotId, String label) {
    }

    /**
     * Lists the slots that have a token, with their labels — without logging in (no PIN).
     * Useful to tell apart, e.g., an authentication slot from a qualified-signature slot.
     */
    public static List<SlotInfo> listSlots(java.nio.file.Path libraryPath) {
        Pkcs11Library lib = Pkcs11Library.load(libraryPath);
        try {
            lib.initialize();
            long[] slots = lib.slotsWithToken();
            List<SlotInfo> out = new ArrayList<>(slots.length);
            for (int i = 0; i < slots.length; i++) {
                out.add(new SlotInfo(i, slots[i], lib.tokenLabel(slots[i])));
            }
            return out;
        } finally {
            lib.close();
        }
    }

    /**
     * Opens a specific slot, logs in and enumerates the signing keys.
     *
     * @param libraryPath absolute path to the PKCS#11 {@code .dylib}
     * @param pin         the PIN for that slot (the caller should zero its own copy afterwards)
     * @param slotIndex   0-based index into {@link #listSlots} (cards often expose the
     *                    qualified-signature key on a separate slot/PIN)
     */
    public FfmPkcs11Token(java.nio.file.Path libraryPath, char[] pin, int slotIndex) {
        this.library = Pkcs11Library.load(libraryPath);
        try {
            library.initialize();
            long[] slots = library.slotsWithToken();
            if (slots.length == 0) {
                throw new Pkcs11Exception("Не е открит токен в четеца (няма слот с карта).");
            }
            if (slotIndex < 0 || slotIndex >= slots.length) {
                throw new Pkcs11Exception("Невалиден слот " + slotIndex
                        + " (налични: 0.." + (slots.length - 1) + ").");
            }
            this.session = library.openReadOnlySession(slots[slotIndex]);
            library.login(session, pin);
            this.keys = enumerateKeys();
        } catch (RuntimeException e) {
            library.close();
            throw e;
        }
    }

    @Override
    public List<DSSPrivateKeyEntry> getKeys() {
        return keys;
    }

    @Override
    public SignatureValue sign(ToBeSigned toBeSigned, DigestAlgorithm digestAlgorithm, DSSPrivateKeyEntry keyEntry) {
        byte[] digest = digest(digestAlgorithm, toBeSigned.getBytes());
        return signDigest(digest, digestAlgorithm, (Pkcs11Key) keyEntry);
    }

    @Override
    public SignatureValue sign(ToBeSigned toBeSigned, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry keyEntry) {
        return sign(toBeSigned, signatureAlgorithm.getDigestAlgorithm(), keyEntry);
    }

    @Override
    public SignatureValue signDigest(Digest digest, DSSPrivateKeyEntry keyEntry) {
        return signDigest(digest.getValue(), digest.getAlgorithm(), (Pkcs11Key) keyEntry);
    }

    @Override
    public SignatureValue signDigest(Digest digest, SignatureAlgorithm signatureAlgorithm, DSSPrivateKeyEntry keyEntry) {
        return signDigest(digest.getValue(), digest.getAlgorithm(), (Pkcs11Key) keyEntry);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            library.logout(session);
            library.closeSession(session);
        } finally {
            library.close();
        }
    }

    // --- signing ------------------------------------------------------------

    private SignatureValue signDigest(byte[] digest, DigestAlgorithm digestAlgorithm, Pkcs11Key key) {
        EncryptionAlgorithm enc = key.getEncryptionAlgorithm();
        byte[] raw;
        if (enc == EncryptionAlgorithm.RSA) {
            // RSASSA-PKCS1-v1_5: the card signs a DER-encoded DigestInfo with CKM_RSA_PKCS.
            raw = library.sign(session, Ck.CKM_RSA_PKCS, key.handle(), digestInfo(digestAlgorithm, digest));
        } else if (enc == EncryptionAlgorithm.ECDSA) {
            // CKM_ECDSA returns the raw R||S; DSS/CAdES expects a DER Ecdsa-Sig-Value.
            byte[] rs = library.sign(session, Ck.CKM_ECDSA, key.handle(), digest);
            raw = ecdsaRawToDer(rs);
        } else {
            throw new Pkcs11Exception("Неподдържан тип ключ: " + enc);
        }
        return new SignatureValue(SignatureAlgorithm.getAlgorithm(enc, digestAlgorithm), raw);
    }

    private static byte[] digest(DigestAlgorithm digestAlgorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(digestAlgorithm.getJavaName()).digest(data);
        } catch (Exception e) {
            throw new Pkcs11Exception("Неуспешно изчисляване на дайджест: " + e.getMessage());
        }
    }

    /** DER {@code DigestInfo ::= SEQUENCE { AlgorithmIdentifier, OCTET STRING digest }}. */
    private static byte[] digestInfo(DigestAlgorithm digestAlgorithm, byte[] digest) {
        try {
            AlgorithmIdentifier algId =
                    new AlgorithmIdentifier(new ASN1ObjectIdentifier(digestAlgorithm.getOid()), DERNull.INSTANCE);
            return new org.bouncycastle.asn1.x509.DigestInfo(algId, digest).getEncoded("DER");
        } catch (IOException e) {
            throw new Pkcs11Exception("Неуспешно кодиране на DigestInfo: " + e.getMessage());
        }
    }

    /** Converts a raw {@code R||S} ECDSA signature into a DER {@code Ecdsa-Sig-Value}. */
    private static byte[] ecdsaRawToDer(byte[] rs) {
        int half = rs.length / 2;
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(rs, 0, half));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(rs, half, rs.length));
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new ASN1Integer(r));
        v.add(new ASN1Integer(s));
        try {
            return new DERSequence(v).getEncoded("DER");
        } catch (IOException e) {
            throw new Pkcs11Exception("Неуспешно кодиране на ECDSA подпис: " + e.getMessage());
        }
    }

    // --- key enumeration ----------------------------------------------------

    private List<DSSPrivateKeyEntry> enumerateKeys() {
        // Certificates first (public objects): keep DER + CKA_ID, build CertificateTokens.
        List<byte[]> certIds = new ArrayList<>();
        List<CertificateToken> certs = new ArrayList<>();
        for (long h : library.findByClass(session, Ck.CKO_CERTIFICATE)) {
            byte[] der = library.getAttribute(session, h, Ck.CKA_VALUE);
            if (der == null || der.length == 0) {
                continue;
            }
            certIds.add(library.getAttribute(session, h, Ck.CKA_ID));
            certs.add(DSSUtils.loadCertificate(der));
        }

        List<DSSPrivateKeyEntry> result = new ArrayList<>();
        for (long keyHandle : library.findByClass(session, Ck.CKO_PRIVATE_KEY)) {
            byte[] keyId = library.getAttribute(session, keyHandle, Ck.CKA_ID);
            int idx = indexOfId(certIds, keyId);
            if (idx < 0) {
                continue; // no matching certificate -> cannot build a CAdES signature
            }
            CertificateToken leaf = certs.get(idx);
            EncryptionAlgorithm enc = encryptionAlgorithm(library.getAttributeULong(session, keyHandle, Ck.CKA_KEY_TYPE));
            if (enc == null) {
                continue;
            }
            result.add(new Pkcs11Key(keyHandle, leaf, buildChain(leaf, certs), enc));
        }
        return List.copyOf(result);
    }

    private static int indexOfId(List<byte[]> ids, byte[] id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < ids.size(); i++) {
            if (Arrays.equals(ids.get(i), id)) {
                return i;
            }
        }
        return -1;
    }

    private static EncryptionAlgorithm encryptionAlgorithm(Long ckKeyType) {
        if (ckKeyType == null) {
            return null;
        }
        if (ckKeyType == Ck.CKK_RSA) {
            return EncryptionAlgorithm.RSA;
        }
        if (ckKeyType == Ck.CKK_EC) {
            return EncryptionAlgorithm.ECDSA;
        }
        return null;
    }

    /** Best-effort chain: follow issuer links among the token's certificates, leaf-first. */
    private static CertificateToken[] buildChain(CertificateToken leaf, List<CertificateToken> pool) {
        List<CertificateToken> chain = new ArrayList<>();
        CertificateToken current = leaf;
        while (current != null && !chain.contains(current)) {
            chain.add(current);
            if (isSelfSigned(current)) {
                break;
            }
            current = issuerOf(current, pool);
        }
        return chain.toArray(new CertificateToken[0]);
    }

    private static CertificateToken issuerOf(CertificateToken cert, List<CertificateToken> pool) {
        for (CertificateToken candidate : pool) {
            if (candidate != cert
                    && cert.getCertificate().getIssuerX500Principal()
                    .equals(candidate.getCertificate().getSubjectX500Principal())) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isSelfSigned(CertificateToken cert) {
        return cert.getCertificate().getIssuerX500Principal()
                .equals(cert.getCertificate().getSubjectX500Principal());
    }
}
