package com.euphoriapatches.shaderpatcher.generate;

import com.euphoriapatches.shaderpatcher.PatchMetadata;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Result of {@link PatchGenerator#generate(GenerateRequest)}.
 *
 * @author SpacEagle17
 */
public final class GeneratedPatch {
    private final PatchMetadata metadata;
    private final List<Path> patchFiles;

    GeneratedPatch(PatchMetadata metadata, List<Path> patchFiles) {
        this.metadata = metadata;
        this.patchFiles = Collections.unmodifiableList(patchFiles);
    }

    public PatchMetadata getMetadata() {
        return metadata;
    }

    public List<Path> getPatchFiles() {
        return patchFiles;
    }
}
