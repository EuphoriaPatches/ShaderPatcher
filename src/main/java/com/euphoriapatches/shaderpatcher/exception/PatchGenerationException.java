package com.euphoriapatches.shaderpatcher.exception;

import java.nio.file.Path;

/**
 * Generating a patch failed, or its self-check found a difference.
 *
 * @author SpacEagle17
 */
public class PatchGenerationException extends ShaderPatcherException {
    private static final long serialVersionUID = 1L;

    public PatchGenerationException(String message, Path path, Throwable cause) {
        super(message, path, cause);
    }
}
