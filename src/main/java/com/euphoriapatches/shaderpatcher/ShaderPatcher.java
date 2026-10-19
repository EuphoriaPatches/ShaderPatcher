package com.euphoriapatches.shaderpatcher;

import com.euphoriapatches.shaderpatcher.exception.BaseHashMismatchException;
import com.euphoriapatches.shaderpatcher.exception.BaseSizeMismatchException;
import com.euphoriapatches.shaderpatcher.exception.InvalidPackException;
import com.euphoriapatches.shaderpatcher.exception.OutputExistsException;
import com.euphoriapatches.shaderpatcher.exception.PackIoException;
import com.euphoriapatches.shaderpatcher.exception.PatchCorruptException;
import com.euphoriapatches.shaderpatcher.exception.PatchMetadataMismatchException;
import com.euphoriapatches.shaderpatcher.exception.PatchNotFoundException;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;
import com.euphoriapatches.shaderpatcher.internal.BsdiffAdapter;
import com.euphoriapatches.shaderpatcher.internal.HashUtils;
import com.euphoriapatches.shaderpatcher.internal.PackArchiver;
import com.euphoriapatches.shaderpatcher.internal.PackExtractor;
import com.euphoriapatches.shaderpatcher.internal.PackNames;
import com.euphoriapatches.shaderpatcher.internal.PatchFileFormat;
import com.euphoriapatches.shaderpatcher.internal.TempDirs;

import java.util.function.Consumer;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Verifies a base shader pack and turns it into a patched pack using a patch file made by {@code PatchGenerator}.
 * The base (zip or folder) is only read, never modified. An instance is thread-safe.
 * A lot of this library is taken from the EuphoriaPatcher mod originally and repurposed here
 *
 * <pre>
 * ShaderPatcher patcher = ShaderPatcher.builder(PatchSource.classpath(MyMod.class, "MyPatch_1.0.patch")).build();
 * try {
 *     patcher.apply(Paths.get("shaderpacks/Base_r1.zip"), Paths.get("shaderpacks"));
 * } catch (BaseMismatchException e) {
 *     // the user's base pack is not the right one
 * } catch (ShaderPatcherException e) {
 *     // anything else
 * }
 * </pre>
 *
 * @author SpacEagle17
 */
public final class ShaderPatcher {
    private final PatchSource source;
    private final PatchMetadata metadata;
    private final OutputNaming naming;
    private final ExistingOutput existingOutput;
    private final Consumer<String> log;

    private ShaderPatcher(Builder b, PatchMetadata metadata) {
        this.source = b.source;
        this.metadata = metadata;
        this.naming = b.naming;
        this.existingOutput = b.existingOutput;
        this.log = b.log;
    }

    /** Starts a patcher for the patch file behind {@code source}. */
    public static Builder builder(PatchSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        return new Builder(source);
    }

    /** Names, versions and fingerprints stored in the patch file. */
    public PatchMetadata getMetadata() {
        return metadata;
    }

    /** Verifies the base pack and writes the patched pack to {@code targetDir/<name>}. Appears complete or not at all. */
    public PatchResult apply(Path base, Path targetDir) throws ShaderPatcherException {
        requireNonNull(base, "base");
        requireNonNull(targetDir, "targetDir");

        Path output = targetDir.resolve(outputName(base));
        if (existingOutput == ExistingOutput.FAIL && Files.exists(output)) {
            throw new OutputExistsException(output);
        }

        Path work = newWorkDir();
        try {
            PatchFileFormat.Parsed patch = readPatch();
            log.accept("Applying " + metadata.getPatchedName() + " to " + base);

            Path baseArchive = verifiedArchive(base, work);
            byte[] patchedArchive = applyPatch(baseArchive, patch);
            writeOutput(patchedArchive, base, targetDir, output);
            return new PatchResult(output, metadata.getBaseFingerprint(), metadata);
        } catch (IOException e) {
            throw new PackIoException("Failed to write the patched pack to " + output + ": " + e.getMessage(), output, e);
        } finally {
            TempDirs.deleteQuietly(work);
        }
    }

    /** A valid base never unpacks to more than the archive size stored in the patch. */
    private long expectedSize() {
        return metadata.getBaseFingerprint().getSize();
    }

    private String outputName(Path base) {
        String name = naming.name(new OutputNaming.NamingContext(fileNameOf(base), metadata));
        PackNames.requireFolderName(name);
        return name;
    }

    /** Archives the base pack and checks it against the fingerprint the patch expects. Returns the archive file. */
    private Path verifiedArchive(Path base, Path work) throws ShaderPatcherException {
        Path archive = PackArchiver.toTar(base, work, "base", null, log, expectedSize());
        checkTar(archive, base, true);
        return archive;
    }

    /** Turns the base archive into the patched archive and checks the result against the hash stored in the patch. */
    private byte[] applyPatch(Path baseArchive, PatchFileFormat.Parsed patch) throws ShaderPatcherException, IOException {
        byte[] patched;
        try {
            patched = BsdiffAdapter.patch(Files.readAllBytes(baseArchive), patch.getPayload());
        } catch (IOException e) {
            // everything here works on in-memory data, so a failure means the payload is unusable
            throw new PatchCorruptException(e.getMessage() + " (patch " + source.getLocation() + ")", source.getFile(), e);
        }

        BaseFingerprint fingerprint = HashUtils.fingerprint(patched);
        if (!fingerprint.equals(metadata.getPatchedFingerprint())) {
            throw new PatchCorruptException("Patched archive does not match the patch's recorded result: expected "
                    + metadata.getPatchedFingerprint() + ", got " + fingerprint + " (patch " + source.getLocation() + ")",
                    source.getFile(), null);
        }
        return patched;
    }

    /** Unpacks the patched archive into a staging folder next to the target, then renames it to {@code output}. */
    private void writeOutput(byte[] patchedArchive, Path base, Path targetDir, Path output)
            throws ShaderPatcherException, IOException {
        Files.createDirectories(targetDir);
        Path staging = Files.createTempDirectory(targetDir, ".shaderpatcher-");
        try {
            PackExtractor.extractTar(new ByteArrayInputStream(patchedArchive), staging, base, log);

            if (existingOutput == ExistingOutput.REPLACE) {
                TempDirs.deleteRecursively(output);
            } else if (Files.exists(output)) {
                throw new OutputExistsException(output); // appeared while we were working
            }
            TempDirs.moveWithRetry(staging, output);
        } finally {
            TempDirs.deleteQuietly(staging); // nothing left to delete once it has been moved
        }
    }

    /** Checks that the base pack is the one this patch is for, without applying. */
    public BaseFingerprint verifyBase(Path base) throws ShaderPatcherException {
        return verify(base, true);
    }

    /** Quiet {@link #verifyBase(Path)} for scanning many candidates: false instead of throwing. */
    public boolean matchesBase(Path base) {
        return matchesBase(base, true);
    }

    /** Quiet check that can skip the hash. */
    public boolean matchesBase(Path base, boolean verifyHash) {
        try {
            verify(base, verifyHash);
            return true;
        } catch (ShaderPatcherException e) {
            return false;
        }
    }

    /** Computes the fingerprint of a pack. */
    public static BaseFingerprint fingerprint(Path pack) throws ShaderPatcherException {
        requireNonNull(pack, "pack");
        Path work = newWorkDir();
        try {
            Path tar = PackArchiver.toTar(pack, work, "pack", null, PackArchiver.NO_LOG, Long.MAX_VALUE);
            return HashUtils.fingerprint(tar);
        } catch (IOException e) {
            throw new PackIoException("Failed to read the archive of " + pack + ": " + e.getMessage(), pack, e);
        } finally {
            TempDirs.deleteQuietly(work);
        }
    }

    /** Copies or unpacks a pack, so the caller can prepare a private copy first. */
    public static void extract(Path pack, Path targetDir) throws ShaderPatcherException {
        requireNonNull(pack, "pack");
        requireNonNull(targetDir, "targetDir");
        if (!Files.exists(pack)) {
            throw new InvalidPackException("Pack does not exist: " + pack, pack, null);
        }
        if (Files.isDirectory(pack)) {
            PackExtractor.copyDirectory(pack, targetDir);
        } else {
            PackExtractor.extractZip(pack, targetDir, PackArchiver.NO_LOG, Long.MAX_VALUE);
        }
    }

    private BaseFingerprint verify(Path base, boolean verifyHash) throws ShaderPatcherException {
        requireNonNull(base, "base");
        Path work = newWorkDir();
        try {
            Path tar = PackArchiver.toTar(base, work, "base", null, log, expectedSize());
            return checkTar(tar, base, verifyHash);
        } finally {
            TempDirs.deleteQuietly(work);
        }
    }

    private BaseFingerprint checkTar(Path tar, Path base, boolean verifyHash) throws ShaderPatcherException {
        BaseFingerprint expected = metadata.getBaseFingerprint();
        try {
            BaseFingerprint actual = HashUtils.fingerprint(tar);
            if (actual.getSize() != expected.getSize()) {
                throw new BaseSizeMismatchException(base, expected, actual);
            }
            if (verifyHash && !actual.equals(expected)) {
                throw new BaseHashMismatchException(base, expected, actual);
            }
            return actual;
        } catch (IOException e) {
            throw new PackIoException("Failed to fingerprint " + base + ": " + e.getMessage(), base, e);
        }
    }

    private PatchFileFormat.Parsed readPatch() throws ShaderPatcherException {
        try (InputStream in = source.openStream()) {
            return PatchFileFormat.read(in, source.getLocation(), source.getFile());
        } catch (FileNotFoundException | NoSuchFileException e) {
            throw new PatchNotFoundException(source.getLocation(), source.getFile(), e);
        } catch (IOException e) {
            throw new PatchNotFoundException(source.getLocation() + " (" + e.getMessage() + ")", source.getFile(), e);
        }
    }

    private static Path newWorkDir() throws PackIoException {
        try {
            return TempDirs.create("shaderpatcher-");
        } catch (IOException e) {
            throw new PackIoException("Cannot create a temporary folder: " + e.getMessage(), null, e);
        }
    }

    private static String fileNameOf(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    /** Builder for {@link ShaderPatcher}. */
    public static final class Builder {
        private final PatchSource source;
        private OutputNaming naming = OutputNaming.BASE_PLUS_PATCH;
        private ExistingOutput existingOutput = ExistingOutput.FAIL;
        private Consumer<String> log = PackArchiver.NO_LOG;
        private String expectedPatchName;
        private String expectedPatchVersion;

        private Builder(PatchSource source) {
            this.source = source;
        }

        public Builder outputNaming(OutputNaming naming) {
            requireNonNull(naming, "naming");
            this.naming = naming;
            return this;
        }

        public Builder existingOutput(ExistingOutput existingOutput) {
            requireNonNull(existingOutput, "existingOutput");
            this.existingOutput = existingOutput;
            return this;
        }

        public Builder debugLog(Consumer<String> debugLog) {
            requireNonNull(debugLog, "debugLog");
            this.log = debugLog;
            return this;
        }

        /** Optional check: {@link #build()} fails if the patch has another name. */
        public Builder expectPatchName(String patchName) {
            this.expectedPatchName = patchName;
            return this;
        }

        /** Optional check: {@link #build()} fails if the patch has another version (wrong file packaged). */
        public Builder expectPatchVersion(String patchVersion) {
            this.expectedPatchVersion = patchVersion;
            return this;
        }

        /** Opens the patch and reads and validates its header (a few hundred bytes). */
        public ShaderPatcher build() throws ShaderPatcherException {
            PatchMetadata metadata;
            try (InputStream in = source.openStream()) {
                metadata = PatchFileFormat.readMetadata(in, source.getLocation(), source.getFile());
            } catch (FileNotFoundException | NoSuchFileException e) {
                throw new PatchNotFoundException(source.getLocation(), source.getFile(), e);
            } catch (IOException e) {
                throw new PatchNotFoundException(source.getLocation() + " (" + e.getMessage() + ")", source.getFile(), e);
            }
            if (expectedPatchName != null && !expectedPatchName.equals(metadata.getPatchName())) {
                throw new PatchMetadataMismatchException("patchName", expectedPatchName, metadata.getPatchName());
            }
            if (expectedPatchVersion != null && !expectedPatchVersion.equals(metadata.getPatchVersion())) {
                throw new PatchMetadataMismatchException("patchVersion", expectedPatchVersion, metadata.getPatchVersion());
            }
            return new ShaderPatcher(this, metadata);
        }
    }
}
