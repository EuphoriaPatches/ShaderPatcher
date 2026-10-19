package com.euphoriapatches.shaderpatcher.exception;

import java.nio.file.Path;

/**
 * The output folder already exists and the policy is {@code ExistingOutput.FAIL}. Nothing was changed.
 *
 * @author SpacEagle17
 */
public class OutputExistsException extends ShaderPatcherException {
    private static final long serialVersionUID = 1L;

    public OutputExistsException(Path path) {
        super("Output already exists: " + path, path, null);
    }
}
