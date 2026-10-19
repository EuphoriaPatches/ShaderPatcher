package com.euphoriapatches.shaderpatcher.exception;

import com.euphoriapatches.shaderpatcher.BaseFingerprint;

import java.nio.file.Path;

/**
 * The base pack is not the one the patch was made for; the user has to supply the right one.
 *
 * @author SpacEagle17
 */
public abstract class BaseMismatchException extends ShaderPatcherException {
    private static final long serialVersionUID = 1L;

    private final BaseFingerprint expected;
    private final BaseFingerprint actual;

    BaseMismatchException(String message, Path path, BaseFingerprint expected, BaseFingerprint actual) {
        super(message, path, null);
        this.expected = expected;
        this.actual = actual;
    }

    public BaseFingerprint getExpected() {
        return expected;
    }

    public BaseFingerprint getActual() {
        return actual;
    }
}
