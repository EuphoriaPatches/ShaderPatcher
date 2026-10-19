package com.euphoriapatches.shaderpatcher.generate;

import com.euphoriapatches.shaderpatcher.PatchSpec;
import com.euphoriapatches.shaderpatcher.internal.PackArchiver;

import java.util.function.Consumer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Input for {@link PatchGenerator#generate(GenerateRequest)}.
 *
 * @author SpacEagle17
 */
public final class GenerateRequest {
    private final PatchSpec spec;
    private final Path base;
    private final Path edited;
    private final Predicate<String> editedExclude;
    private final List<Path> outputDirs;
    private final Consumer<String> debugLog;

    private GenerateRequest(Builder b) {
        this.spec = b.spec;
        this.base = b.base;
        this.edited = b.edited;
        this.editedExclude = b.editedExclude;
        this.outputDirs = Collections.unmodifiableList(new ArrayList<>(b.outputDirs));
        this.debugLog = b.debugLog;
    }

    public static Builder builder(PatchSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec is required");
        }
        return new Builder(spec);
    }

    PatchSpec spec() {
        return spec;
    }

    Path base() {
        return base;
    }

    Path edited() {
        return edited;
    }

    Predicate<String> editedExclude() {
        return editedExclude;
    }

    List<Path> outputDirs() {
        return outputDirs;
    }

    Consumer<String> debugLog() {
        return debugLog;
    }

    /** Builder for {@link GenerateRequest}. */
    public static final class Builder {
        private final PatchSpec spec;
        private Path base;
        private Path edited;
        private Predicate<String> editedExclude;
        private final List<Path> outputDirs = new ArrayList<>();
        private Consumer<String> debugLog = PackArchiver.NO_LOG;

        private Builder(PatchSpec spec) {
            this.spec = spec;
        }

        public Builder base(Path base) {
            this.base = base;
            return this;
        }

        public Builder edited(Path edited) {
            this.edited = edited;
            return this;
        }

        /** Leaves dev-only files or any file specified by the filter out of the edited pack; the filter gets entry names relative to the pack root. */
        public Builder editedExclude(Predicate<String> exclude) {
            this.editedExclude = exclude;
            return this;
        }

        public Builder addOutputDir(Path dir) {
            if (dir == null) {
                throw new IllegalArgumentException("dir is required");
            }
            this.outputDirs.add(dir);
            return this;
        }

        /** Receives diagnostic lines for a debug log. Default: none. */
        public Builder debugLog(Consumer<String> debugLog) {
            if (debugLog == null) {
                throw new IllegalArgumentException("debugLog is required");
            }
            this.debugLog = debugLog;
            return this;
        }

        public GenerateRequest build() {
            if (base == null || edited == null) {
                throw new IllegalStateException("base and edited are required");
            }
            if (outputDirs.isEmpty()) {
                throw new IllegalStateException("at least one output folder is required (addOutputDir)");
            }
            return new GenerateRequest(this);
        }
    }
}
