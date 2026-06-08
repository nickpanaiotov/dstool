package org.dstool.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Locates a PKCS#11 middleware library on macOS by probing well-known install paths.
 *
 * <p>The list is a best-effort starting point for StampIT / Bulgarian QES tokens and
 * should be confirmed against a real installation; users can always override it with
 * {@code --pkcs11-lib}.</p>
 */
public final class Pkcs11Locator {

    private static final List<String> CANDIDATES = List.of(
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

    private Pkcs11Locator() {
    }

    /** First readable candidate library, if any. */
    public static Optional<Path> autodetect() {
        return CANDIDATES.stream()
                .map(Path::of)
                .filter(Files::isReadable)
                .findFirst();
    }

    /** The probed paths, for diagnostics. */
    public static List<String> candidates() {
        return CANDIDATES;
    }
}
