package com.euphoriapatches.shaderpatcher;

import com.euphoriapatches.shaderpatcher.generate.GenerateRequest;
import com.euphoriapatches.shaderpatcher.generate.PatchGenerator;
import com.euphoriapatches.shaderpatcher.internal.PackArchiver;
import com.euphoriapatches.shaderpatcher.internal.PackExtractor;
import io.sigpipe.jbsdiff.Patch;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves that the archiver is a faithful port of the one EuphoriaPatcher has used so far, using its real files:
 * {@code ComplementaryReimagined_r5.9.3.zip} must fingerprint to the constants EuphoriaPatcher hardcoded in
 * {@code PatchInfo}. Skipped when the fixture files are not available (see build.gradle).
 */
class GoldenCompatibilityTest {
    private static final long LEGACY_BASE_TAR_SIZE = 1651200L;
    private static final String LEGACY_BASE_TAR_SHA256 = "17e51062476455065a58d8e5da49d60e9a5af38e8e6d67a135989cf71f103a19";

    private static Path fixture(String name) {
        String dir = System.getProperty("shaderpatcher.fixtures", "");
        assumeTrue(!dir.isEmpty(), "no fixtures folder configured");
        Path file = Paths.get(dir).resolve(name);
        assumeTrue(Files.isRegularFile(file), "fixture missing: " + file);
        return file;
    }

    @Test
    void realBaseZipFingerprintsToTheLegacyConstants() throws Exception {
        Path zip = fixture("ComplementaryReimagined_r5.9.3.zip");

        BaseFingerprint fp = ShaderPatcher.fingerprint(zip);

        assertEquals(LEGACY_BASE_TAR_SIZE, fp.getSize());
        assertEquals(LEGACY_BASE_TAR_SHA256, fp.getSha256());
    }

    @Test
    void realBaseFolderFingerprintsLikeTheZip(@TempDir Path temp) throws Exception {
        Path zip = fixture("ComplementaryReimagined_r5.9.3.zip");
        Path folder = temp.resolve("unpacked");
        ShaderPatcher.extract(zip, folder);

        assertEquals(ShaderPatcher.fingerprint(zip), ShaderPatcher.fingerprint(folder));
    }

    @Test
    @Tag("needs-deps")
    void oldRawPatchCanBeRebuiltAndReplacedByANewFormatPatch(@TempDir Path temp) throws Exception {
        Path zip = fixture("ComplementaryReimagined_r5.9.3.zip");
        Path oldPatch = fixture("EuphoriaPatches_1.10.5.patch");

        // Rebuild the edited pack the old way: raw bspatch on the base archive
        Path work = Files.createDirectories(temp.resolve("work"));
        Path baseTar = PackArchiver.toTar(zip, work, "base", null, PackArchiver.NO_LOG, Long.MAX_VALUE);
        ByteArrayOutputStream patchedTar = new ByteArrayOutputStream();
        Patch.patch(Files.readAllBytes(baseTar), Files.readAllBytes(oldPatch), patchedTar);
        Path edited = temp.resolve("edited");
        PackExtractor.extractTar(new ByteArrayInputStream(patchedTar.toByteArray()), edited, oldPatch, PackArchiver.NO_LOG);

        // Generate a new-format patch from base + edited, then apply it to the base
        PatchSpec spec = PatchSpec.builder().patchName("EuphoriaPatches").patchVersion("_1.10.5")
                .baseName("Complementary").baseVersion("_r5.9.3").build();
        Path patchDir = temp.resolve("patches");
        Path patchFile = PatchGenerator.generate(GenerateRequest.builder(spec)
                .base(zip).edited(edited).addOutputDir(patchDir).build()).getPatchFiles().get(0);

        ShaderPatcher patcher = ShaderPatcher.builder(PatchSource.path(patchFile)).expectPatchVersion("_1.10.5").build();
        assertEquals(LEGACY_BASE_TAR_SIZE, patcher.getMetadata().getBaseFingerprint().getSize());
        assertEquals(LEGACY_BASE_TAR_SHA256, patcher.getMetadata().getBaseFingerprint().getSha256());

        Path packs = temp.resolve("shaderpacks");
        PatchResult result = patcher.apply(zip, packs);

        assertEquals("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", result.getOutputDir().getFileName().toString());
        TestSupport.assertSameTree(edited, result.getOutputDir());
        List<String> lines = Files.readAllLines(result.getOutputDir().resolve("shaders/lib/misc/myFile.glsl"), StandardCharsets.UTF_8);
        assertEquals("// Euphoria Patches 1.10.5", lines.get(0).trim());
    }
}
