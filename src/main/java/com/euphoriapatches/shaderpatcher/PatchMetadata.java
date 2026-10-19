package com.euphoriapatches.shaderpatcher;

/**
 * What a patch file says about itself: names, versions and the fingerprints of the base and the result.
 *
 * @author SpacEagle17
 */
public final class PatchMetadata {
    private final int tarFormat;
    private final String patchName;
    private final String patchVersion;
    private final String baseName;
    private final String baseVersion;
    private final BaseFingerprint base;
    private final BaseFingerprint patched;
    private final String generator;

    public PatchMetadata(int tarFormat, String patchName, String patchVersion, String baseName, String baseVersion,
                         BaseFingerprint base, BaseFingerprint patched, String generator) {
        PatchSpec.requireText("patchName", patchName, true);
        PatchSpec.requireText("patchVersion", patchVersion == null ? "" : patchVersion, false);
        PatchSpec.requireText("baseName", baseName, false);
        PatchSpec.requireText("baseVersion", baseVersion, false);
        PatchSpec.requireText("generator", generator, false);
        if (base == null || patched == null) {
            throw new IllegalArgumentException("base and patched fingerprints are required");
        }
        this.tarFormat = tarFormat;
        this.patchName = patchName;
        this.patchVersion = patchVersion == null ? "" : patchVersion;
        this.baseName = baseName;
        this.baseVersion = baseVersion;
        this.base = base;
        this.patched = patched;
        this.generator = generator;
    }

    public int getTarFormat() {
        return tarFormat;
    }

    public String getPatchName() {
        return patchName;
    }

    public String getPatchVersion() {
        return patchVersion;
    }

    public String getBaseName() {
        return baseName;
    }

    public String getBaseVersion() {
        return baseVersion;
    }

    public BaseFingerprint getBaseFingerprint() {
        return base;
    }

    public BaseFingerprint getPatchedFingerprint() {
        return patched;
    }

    public String getGenerator() {
        return generator;
    }

    public String getPatchedName() {
        return patchName + patchVersion;
    }

    public String getPatchFileName() {
        return getPatchedName() + ".patch";
    }

    @Override
    public String toString() {
        return "PatchMetadata{patch=" + getPatchedName() + ", base=" + (baseName == null ? "?" : baseName)
                + (baseVersion == null ? "" : baseVersion) + ", baseFingerprint=[" + base + "], patchedFingerprint=["
                + patched + "], tarFormat=" + tarFormat + ", generator=" + generator + '}';
    }
}
