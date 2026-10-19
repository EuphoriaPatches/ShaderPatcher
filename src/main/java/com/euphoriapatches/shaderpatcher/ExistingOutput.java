package com.euphoriapatches.shaderpatcher;

/**
 * What to do when the output folder for the patched pack already exists.
 *
 * @author SpacEagle17
 */
public enum ExistingOutput {
    /** Throw {@link com.euphoriapatches.shaderpatcher.exception.OutputExistsException} and touch nothing. */
    FAIL,
    /** Delete the existing folder once the new one is fully built, then move the new one into place. */
    REPLACE
}
