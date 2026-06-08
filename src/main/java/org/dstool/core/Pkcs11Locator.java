package org.dstool.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Locates a PKCS#11 middleware library by probing well-known install paths for the
 * current operating system (macOS or Linux).
 *
 * <p>The lists are a best-effort starting point for StampIT / Bulgarian QES tokens and
 * should be confirmed against a real installation; users can always override them with
 * {@code --pkcs11-lib}.</p>
 */
public final class Pkcs11Locator {

    private static final List<String> MACOS = List.of(
            // OpenSC (most common; covers many CCID smart-card readers)
            "/Library/OpenSC/lib/opensc-pkcs11.so",
            "/opt/homebrew/lib/opensc-pkcs11.so",
            "/usr/local/lib/opensc-pkcs11.so",
            // Thales/Gemalto IDPrime & SafeNet Authentication Client
            "/usr/local/lib/libIDPrimePKCS11.dylib",
            "/usr/local/lib/libeTPkcs11.dylib",
            "/Library/Frameworks/eToken.framework/Versions/Current/libeToken.dylib",
            // Charismathics / CardOS
            "/usr/local/lib/libcmP11.dylib",
            // Bit4id
            "/Applications/PKIManager-bit4id.app/Contents/Resources/etc/libbit4xpki.dylib"
    );

    private static final List<String> LINUX = List.of(
            // OpenSC (Debian/Ubuntu multiarch, Fedora/RHEL, generic)
            "/usr/lib/x86_64-linux-gnu/opensc-pkcs11.so",
            "/usr/lib/aarch64-linux-gnu/opensc-pkcs11.so",
            "/usr/lib64/opensc-pkcs11.so",
            "/usr/lib/opensc-pkcs11.so",
            "/usr/lib/pkcs11/opensc-pkcs11.so",
            "/usr/local/lib/opensc-pkcs11.so",
            // Thales/Gemalto IDPrime & SafeNet Authentication Client
            "/usr/lib/x86_64-linux-gnu/libIDPrimePKCS11.so",
            "/usr/lib/libIDPrimePKCS11.so",
            "/usr/lib/libeTPkcs11.so",
            // Bit4id
            "/usr/lib/x86_64-linux-gnu/libbit4xpki.so",
            "/usr/lib/libbit4xpki.so"
    );

    private Pkcs11Locator() {
    }

    private static List<String> candidatesForOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux") ? LINUX : MACOS;
    }

    /** First readable candidate library for the current OS, if any. */
    public static Optional<Path> autodetect() {
        return candidatesForOs().stream()
                .map(Path::of)
                .filter(Files::isReadable)
                .findFirst();
    }

    /** The probed paths for the current OS, for diagnostics. */
    public static List<String> candidates() {
        return candidatesForOs();
    }
}
