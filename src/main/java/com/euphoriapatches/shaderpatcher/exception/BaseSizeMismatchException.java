package com.euphoriapatches.shaderpatcher.exception;

import com.euphoriapatches.shaderpatcher.BaseFingerprint;

import java.nio.file.Path;

/**
 * The base pack has another size: wrong version, newer or dev release, or an incomplete download.
 *
 * @author SpacEagle17
 */
public class BaseSizeMismatchException extends BaseMismatchException {
    private static final long serialVersionUID = 1L;

    public BaseSizeMismatchException(Path path, BaseFingerprint expected, BaseFingerprint actual) {
        super("Base pack size mismatch for " + path + ": expected " + expected.getSize() + " bytes, got "
                + actual.getSize() + " bytes", path, expected, actual);
    }
}
