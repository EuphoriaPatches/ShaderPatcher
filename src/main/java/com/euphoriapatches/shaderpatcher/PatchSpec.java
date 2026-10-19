package com.euphoriapatches.shaderpatcher;

/**
 * Names and versions of a patch to generate. When applying, they are read from the patch file instead.
 *
 * @author SpacEagle17
 */
public final class PatchSpec {
    private final String patchName;
    private final String patchVersion;
    private final String baseName;
    private final String baseVersion;

    private PatchSpec(Builder b) {
        this.patchName = b.patchName;
        this.patchVersion = b.patchVersion;
        this.baseName = b.baseName;
        this.baseVersion = b.baseVersion;
    }

    public static Builder builder() {
        return new Builder();
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

    public String getPatchFileName() {
        return patchName + patchVersion + ".patch";
    }

    /** Builder for {@link PatchSpec}. */
    public static final class Builder {
        private String patchName;
        private String patchVersion = "";
        private String baseName;
        private String baseVersion;

        private Builder() {
        }

        public Builder patchName(String patchName) {
            this.patchName = patchName;
            return this;
        }

        public Builder patchVersion(String patchVersion) {
            this.patchVersion = patchVersion;
            return this;
        }

        public Builder baseName(String baseName) {
            this.baseName = baseName;
            return this;
        }

        public Builder baseVersion(String baseVersion) {
            this.baseVersion = baseVersion;
            return this;
        }

        public PatchSpec build() {
            requireText("patchName", patchName, true);
            requireText("patchVersion", patchVersion, false);
            requireText("baseName", baseName, false);
            requireText("baseVersion", baseVersion, false);
            return new PatchSpec(this);
        }
    }

    static void requireText(String field, String value, boolean nonEmpty) {
        if (value == null) {
            if (nonEmpty) {
                throw new IllegalArgumentException(field + " is required");
            }
            return;
        }
        if (nonEmpty && value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        if (value.length() > 512) {
            throw new IllegalArgumentException(field + " is too long (max 512 characters)");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException(field + " must not contain control characters or line breaks");
            }
        }
    }
}
