package com.euphoriapatches.shaderpatcher.exception;

import java.nio.file.Path;

/**
 * The patch file cannot be opened, usually a packaging mistake in the calling program.
 *
 * @author SpacEagle17
 */
public class PatchNotFoundException extends ShaderPatcherException {
    private static final long serialVersionUID = 1L;

    public PatchNotFoundException(String description, Path path, Throwable cause) {
        super("Patch file not found: " + description, path, cause);
    }
}
