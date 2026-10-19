package com.euphoriapatches.shaderpatcher.internal;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Not public API. File name helpers.
 *
 * @author SpacEagle17
 */
public final class PackNames {
    // Removes copy suffixes such as " (1)", " - Copy", "_copy", " (copy)" at the end of a name
    private static final Pattern COPY_SUFFIX = Pattern.compile("(?i)(?:[\\s_-]+(?:\\(copy\\)|copy|\\(\\d+\\)|\\d+))+$");

    private PackNames() {
    }

    public static String stripZipExtension(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    public static String cleanBaseName(String baseName) {
        String cleaned = COPY_SUFFIX.matcher(baseName).replaceAll("");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    /** Checks that a name is usable as a single folder name. */
    public static void requireFolderName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalStateException("OutputNaming returned an unusable folder name: \"" + name + "\"");
        }
    }
}
