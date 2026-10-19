package com.euphoriapatches.shaderpatcher.internal;

import com.euphoriapatches.shaderpatcher.exception.InvalidPackException;
import com.euphoriapatches.shaderpatcher.exception.PackIoException;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;

import java.util.function.Consumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Not public API. Turns "a zip or a folder" into the deterministic TAR everything else works on.
 *
 * @author SpacEagle17
 */
public final class PackArchiver {
    /** A log that discards everything. */
    public static final Consumer<String> NO_LOG = s -> { };

    private PackArchiver() {
    }

    /** Builds the deterministic archive of a pack. A zip may not unpack to more than {@code maxUnpackedBytes}. */
    public static Path toTar(Path pack, Path work, String label, Predicate<String> exclude, Consumer<String> log,
                             long maxUnpackedBytes) throws ShaderPatcherException {
        if (!Files.exists(pack)) {
            throw new InvalidPackException("Pack does not exist: " + pack, pack, null);
        }
        Path folder;
        if (Files.isDirectory(pack)) {
            folder = pack;
        } else {
            folder = work.resolve(label + "-extracted");
            PackExtractor.extractZip(pack, folder, log, maxUnpackedBytes);
        }

        Path tar = work.resolve(label + ".tar");
        try {
            DeterministicTar.write(folder, tar, exclude);
        } catch (IOException e) {
            throw new PackIoException("Failed to archive " + pack + ": " + e.getMessage(), pack, e);
        }
        return tar;
    }
}
