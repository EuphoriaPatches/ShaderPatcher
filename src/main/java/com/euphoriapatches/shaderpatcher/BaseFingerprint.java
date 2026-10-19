package com.euphoriapatches.shaderpatcher;

import java.io.Serializable;
import java.util.Locale;

/**
 * Size and SHA-256 of a pack's normalized archive; the same content gives the same fingerprint as zip or folder.
 *
 * @author SpacEagle17
 */
public final class BaseFingerprint implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long size;
    private final String sha256;

    private BaseFingerprint(long size, String sha256) {
        this.size = size;
        this.sha256 = sha256;
    }

    public static BaseFingerprint of(long size, String sha256) {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative: " + size);
        }
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 hexadecimal characters: " + sha256);
        }
        return new BaseFingerprint(size, sha256.toLowerCase(Locale.ROOT));
    }

    public long getSize() {
        return size;
    }

    public String getSha256() {
        return sha256;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseFingerprint)) return false;
        BaseFingerprint other = (BaseFingerprint) o;
        return size == other.size && sha256.equals(other.sha256);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(size) + sha256.hashCode();
    }

    @Override
    public String toString() {
        return size + " bytes, sha256 " + sha256;
    }
}
