package com.euphoriapatches.shaderpatcher.exception;

import java.nio.file.Path;

/**
 * Disk access failed (permissions, full disk, a locked file). Not a problem of the pack or the patch.
 *
 * @author SpacEagle17
 */
public class PackIoException extends ShaderPatcherException {
    private static final long serialVersionUID = 1L;

    public PackIoException(String message, Path path, Throwable cause) {
        super(message, path, cause);
    }
}
