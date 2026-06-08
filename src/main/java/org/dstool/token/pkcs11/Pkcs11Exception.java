package org.dstool.token.pkcs11;

/**
 * Thrown when a PKCS#11 (Cryptoki) call returns a non-zero {@code CK_RV}.
 */
public class Pkcs11Exception extends RuntimeException {

    private final long ckrv;

    public Pkcs11Exception(String operation, long ckrv) {
        super(operation + " неуспешно: " + Ck.rvName(ckrv) + " (0x" + Long.toHexString(ckrv) + ")");
        this.ckrv = ckrv;
    }

    public Pkcs11Exception(String message) {
        super(message);
        this.ckrv = -1L;
    }

    /** The raw {@code CK_RV} return value, or -1 if not applicable. */
    public long ckrv() {
        return ckrv;
    }
}
