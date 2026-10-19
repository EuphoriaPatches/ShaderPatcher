package com.euphoriapatches.shaderpatcher.exception;

import java.nio.file.Path;

/**
 * Root of all failures the library throws. The library never logs or prints, it throws one of these instead.
 *
 * @author SpacEagle17
 */
public class ShaderPatcherException extends Exception {
    private static final long serialVersionUID = 1L;

    private final transient Path path;

    public ShaderPatcherException(String message, Path path, Throwable cause) {
        super(message, cause);
        this.path = path;
    }

    public Path getPath() {
        return path;
    }
}
