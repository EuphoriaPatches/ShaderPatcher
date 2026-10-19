package com.euphoriapatches.shaderpatcher;

import java.nio.file.Path;

/**
 * Outcome of a successful {@link ShaderPatcher#apply}.
 *
 * @author SpacEagle17
 */
public final class PatchResult {
    private final Path outputDir;
    private final BaseFingerprint baseFingerprint;
    private final PatchMetadata metadata;

    PatchResult(Path outputDir, BaseFingerprint baseFingerprint, PatchMetadata metadata) {
        this.outputDir = outputDir;
        this.baseFingerprint = baseFingerprint;
        this.metadata = metadata;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public BaseFingerprint getBaseFingerprint() {
        return baseFingerprint;
    }

    public PatchMetadata getMetadata() {
        return metadata;
    }
}
