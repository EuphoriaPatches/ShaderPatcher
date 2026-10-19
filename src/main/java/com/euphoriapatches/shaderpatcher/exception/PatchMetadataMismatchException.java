package com.euphoriapatches.shaderpatcher.exception;

/**
 * The patch is not the one the caller expected (see {@code expectPatchName} / {@code expectPatchVersion}).
 *
 * @author SpacEagle17
 */
public class PatchMetadataMismatchException extends ShaderPatcherException {
    private static final long serialVersionUID = 1L;

    private final String field;
    private final String expected;
    private final String actual;

    public PatchMetadataMismatchException(String field, String expected, String actual) {
        super("Patch " + field + " mismatch: expected \"" + expected + "\" but the patch file says \"" + actual + "\"",
                null, null);
        this.field = field;
        this.expected = expected;
        this.actual = actual;
    }

    public String getField() {
        return field;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }
}
