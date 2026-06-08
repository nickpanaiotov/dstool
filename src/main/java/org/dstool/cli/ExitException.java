package org.dstool.cli;

/**
 * Carries a process exit code alongside a user-facing (Bulgarian) message.
 *
 * <p>Exit codes: {@code 0} success, {@code 1} runtime/signing failure,
 * {@code 2} usage or input error, {@code 3} PKCS#11 driver not found.</p>
 */
public final class ExitException extends RuntimeException {

    private final int code;

    public ExitException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
