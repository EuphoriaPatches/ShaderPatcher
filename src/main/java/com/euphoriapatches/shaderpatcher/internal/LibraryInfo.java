package com.euphoriapatches.shaderpatcher.internal;

/**
 * Not public API. Identifies this library build.
 *
 * @author SpacEagle17
 */
public final class LibraryInfo {

    private LibraryInfo() {
    }

    public static String generatorId() {
        String version = LibraryInfo.class.getPackage() != null
                ? LibraryInfo.class.getPackage().getImplementationVersion() : null;
        return "shaderpatcher/" + (version != null ? version : "dev");
    }
}
