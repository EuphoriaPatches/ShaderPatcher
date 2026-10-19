package com.euphoriapatches.shaderpatcher.generate;

import com.euphoriapatches.shaderpatcher.BaseFingerprint;
import com.euphoriapatches.shaderpatcher.PatchMetadata;
import com.euphoriapatches.shaderpatcher.PatchSpec;
import com.euphoriapatches.shaderpatcher.exception.PackIoException;
import com.euphoriapatches.shaderpatcher.exception.PatchGenerationException;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;
import com.euphoriapatches.shaderpatcher.internal.BsdiffAdapter;
import com.euphoriapatches.shaderpatcher.internal.DeterministicTar;
import com.euphoriapatches.shaderpatcher.internal.HashUtils;
import com.euphoriapatches.shaderpatcher.internal.LibraryInfo;
import com.euphoriapatches.shaderpatcher.internal.PackArchiver;
import com.euphoriapatches.shaderpatcher.internal.PatchFileFormat;
import com.euphoriapatches.shaderpatcher.internal.TempDirs;

import java.util.function.Consumer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dev-tool: creates a patch file from the original base pack and the edited pack.
 *
 * @author SpacEagle17
 */
public final class PatchGenerator {

    private PatchGenerator() {
    }

    /** Generates a patch and writes it as {@code patchName + patchVersion + ".patch"} into every requested folder. */
    public static GeneratedPatch generate(GenerateRequest request) throws ShaderPatcherException {
        PatchSpec spec = request.spec();
        Consumer<String> log = request.debugLog();
        Path work;
        try {
            work = TempDirs.create("shaderpatcher-gen-");
        } catch (IOException e) {
            throw new PackIoException("Cannot create a temporary folder: " + e.getMessage(), null, e);
        }

        try {
            log.accept("Archiving the base pack " + request.base());
            Path baseTarFile = PackArchiver.toTar(request.base(), work, "base", null, log, Long.MAX_VALUE);
            log.accept("Archiving the edited pack " + request.edited());
            Path editedTarFile = PackArchiver.toTar(request.edited(), work, "edited", request.editedExclude(), log, Long.MAX_VALUE);

            byte[] baseTar = Files.readAllBytes(baseTarFile);
            byte[] editedTar = Files.readAllBytes(editedTarFile);
            BaseFingerprint baseFingerprint = HashUtils.fingerprint(baseTar);
            BaseFingerprint editedFingerprint = HashUtils.fingerprint(editedTar);

            log.accept("Computing the patch");
            byte[] payload;
            try {
                payload = BsdiffAdapter.diff(baseTar, editedTar);
            } catch (IOException e) {
                throw new PatchGenerationException("Could not compute the patch: " + e.getMessage(), null, e);
            }

            log.accept("Checking that the new patch reproduces the edited pack");
            byte[] reproduced;
            try {
                reproduced = BsdiffAdapter.patch(baseTar, payload);
            } catch (IOException e) {
                throw new PatchGenerationException("Self-check failed: the new patch cannot be applied: " + e.getMessage(),
                        null, e);
            }
            if (!Arrays.equals(reproduced, editedTar)) {
                throw new PatchGenerationException("Self-check failed: applying the new patch to the base does not"
                        + " reproduce the edited pack", null, null);
            }

            PatchMetadata metadata = new PatchMetadata(DeterministicTar.FORMAT, spec.getPatchName(), spec.getPatchVersion(),
                    spec.getBaseName(), spec.getBaseVersion(), baseFingerprint, editedFingerprint, LibraryInfo.generatorId());
            ByteArrayOutputStream file = new ByteArrayOutputStream(payload.length + 1024);
            PatchFileFormat.write(file, metadata, payload);
            byte[] fileBytes = file.toByteArray();

            List<Path> written = new ArrayList<>();
            for (Path dir : request.outputDirs()) {
                written.add(writeAtomically(dir, spec.getPatchFileName(), fileBytes));
            }
            return new GeneratedPatch(metadata, written);
        } catch (IOException e) {
            throw new PackIoException("Failed while generating the patch: " + e.getMessage(), null, e);
        } finally {
            TempDirs.deleteQuietly(work);
        }
    }

    private static Path writeAtomically(Path dir, String fileName, byte[] bytes) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        Path temp = Files.createTempFile(dir, ".shaderpatcher-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }
}
