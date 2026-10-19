package com.euphoriapatches.shaderpatcher;

import com.euphoriapatches.shaderpatcher.internal.PackNames;

/**
 * Chooses the name of the output folder: {@link #BASE_PLUS_PATCH} (default) or {@link #PATCH_ONLY}.
 *
 * @author SpacEagle17
 */
public interface OutputNaming {

    /** {@code <base name> + <patchName><patchVersion>}, for example {@code ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5}. */
    OutputNaming BASE_PLUS_PATCH = context -> {
        String base = PackNames.cleanBaseName(PackNames.stripZipExtension(context.getBaseFileName()));
        return base + " + " + context.getMetadata().getPatchedName();
    };

    /** Just {@code <patchName><patchVersion>}, ignoring the base pack's name. */
    OutputNaming PATCH_ONLY = context -> context.getMetadata().getPatchedName();

    String name(NamingContext context);

    /** Input for {@link OutputNaming#name(NamingContext)}. */
    final class NamingContext {
        private final String baseFileName;
        private final PatchMetadata metadata;

        NamingContext(String baseFileName, PatchMetadata metadata) {
            this.baseFileName = baseFileName;
            this.metadata = metadata;
        }

        public String getBaseFileName() {
            return baseFileName;
        }

        public PatchMetadata getMetadata() {
            return metadata;
        }
    }
}
