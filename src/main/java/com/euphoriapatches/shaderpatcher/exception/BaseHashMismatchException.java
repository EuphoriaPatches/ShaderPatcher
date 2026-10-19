package com.euphoriapatches.shaderpatcher.exception;

import com.euphoriapatches.shaderpatcher.BaseFingerprint;

import java.nio.file.Path;

/**
 * Right size, but the content differs: the base pack was edited or is unofficial.
 *
 * @author SpacEagle17
 */
public class BaseHashMismatchException extends BaseMismatchException {
    private static final long serialVersionUID = 1L;

    public BaseHashMismatchException(Path path, BaseFingerprint expected, BaseFingerprint actual) {
        super("Base pack hash mismatch for " + path + ": size matches (" + expected.getSize() + " bytes) but SHA-256 is "
                + actual.getSha256() + ", expected " + expected.getSha256(), path, expected, actual);
    }
}
