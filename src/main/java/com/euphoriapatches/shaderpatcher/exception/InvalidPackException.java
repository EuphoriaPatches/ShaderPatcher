package com.euphoriapatches.shaderpatcher.exception;

import java.nio.file.Path;

/**
 * The base pack cannot be read
 *
 * @author SpacEagle17
 */
public class InvalidPackException extends ShaderPatcherException {
    private static final long serialVersionUID = 1L;

    public InvalidPackException(String message, Path path, Throwable cause) {
        super(message, path, cause);
    }
}
