package com.euphoriapatches.shaderpatcher.internal;

import com.euphoriapatches.shaderpatcher.BaseFingerprint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Not public API. SHA-256 helpers.
 *
 * @author SpacEagle17
 */
public final class HashUtils {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HashUtils() {
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // every Java platform is required to provide SHA-256
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String sha256Hex(byte[] bytes) {
        return hex(sha256().digest(bytes));
    }

    public static String sha256Hex(Path file) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    public static BaseFingerprint fingerprint(Path file) throws IOException {
        return BaseFingerprint.of(Files.size(file), sha256Hex(file));
    }

    public static BaseFingerprint fingerprint(byte[] bytes) {
        return BaseFingerprint.of(bytes.length, sha256Hex(bytes));
    }

    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }
}
