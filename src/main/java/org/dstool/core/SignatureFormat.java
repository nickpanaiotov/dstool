package org.dstool.core;

import eu.europa.esig.dss.enumerations.SignaturePackaging;

/**
 * CAdES packaging supported by DSTool.
 *
 * <ul>
 *   <li>{@link #ATTACHED} — ENVELOPING: the signed content is embedded in the
 *       {@code .p7m} container, producing a single self-contained file.</li>
 *   <li>{@link #DETACHED} — DETACHED: only the signature is written ({@code .p7s});
 *       the original document is left untouched and must be kept alongside it for
 *       verification.</li>
 * </ul>
 */
public enum SignatureFormat {

    ATTACHED(SignaturePackaging.ENVELOPING, ".p7m"),
    DETACHED(SignaturePackaging.DETACHED, ".p7s");

    private final SignaturePackaging packaging;
    private final String extension;

    SignatureFormat(SignaturePackaging packaging, String extension) {
        this.packaging = packaging;
        this.extension = extension;
    }

    /** DSS packaging used for this format. */
    public SignaturePackaging packaging() {
        return packaging;
    }

    /** Output file extension conventionally used for this format. */
    public String extension() {
        return extension;
    }
}
