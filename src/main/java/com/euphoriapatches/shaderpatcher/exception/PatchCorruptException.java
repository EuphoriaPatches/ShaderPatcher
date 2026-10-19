package com.euphoriapatches.shaderpatcher.exception;

import java.nio.file.Path;

/**
 * The patch file is damaged, in an unknown format, or produces the wrong result.
 *
 * @author SpacEagle17
 */
public class PatchCorruptException extends ShaderPatcherException {
    private static final long serialVersionUID = 1L;

    public PatchCorruptException(String message, Path path, Throwable cause) {
        super(message, path, cause);
    }
}
