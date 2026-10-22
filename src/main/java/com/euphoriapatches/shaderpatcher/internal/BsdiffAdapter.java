package com.euphoriapatches.shaderpatcher.internal;

import io.sigpipe.jbsdiff.Diff;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Not public API. Core of the patching functionality, uses the bsdiff library.
 *
 * @author SpacEagle17
 */
public final class BsdiffAdapter {

    private BsdiffAdapter() {
    }

    public static byte[] diff(byte[] oldBytes, byte[] newBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, newBytes.length / 4));
        try {
            Diff.diff(oldBytes, newBytes, out);
        } catch (InvalidHeaderException | IOException e) {
            throw new IOException("bsdiff failed: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    public static byte[] patch(byte[] oldBytes, byte[] patch) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(oldBytes.length + 1024);
        try {
            Patch.patch(oldBytes, patch, out);
        } catch (InvalidHeaderException | IOException e) {
            throw new IOException("bspatch failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IOException("bspatch failed on a malformed patch: " + e, e);
        }
        return out.toByteArray();
    }
}
