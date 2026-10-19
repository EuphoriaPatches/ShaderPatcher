package com.euphoriapatches.shaderpatcher;

import com.euphoriapatches.shaderpatcher.exception.OutputExistsException;
import com.euphoriapatches.shaderpatcher.generate.GenerateRequest;
import com.euphoriapatches.shaderpatcher.generate.GeneratedPatch;
import com.euphoriapatches.shaderpatcher.generate.PatchGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundTripTest {
    @TempDir
    Path temp;

    private Path patchFile;
    private Path expectedPack;
    private ShaderPatcher patcher;

    @BeforeEach
    void setUp() throws Exception {
        patchFile = TestSupport.generateSamplePatch(temp, temp.resolve("patches"));
        expectedPack = TestSupport.sampleEdited(temp.resolve("expected"));
        patcher = ShaderPatcher.builder(PatchSource.path(patchFile))
                .expectPatchName("SamplePatch").expectPatchVersion("_2.0").build();
    }

    @Test
    void appliesToAFolderBaseAndLeavesTheBaseUntouched() throws Exception {
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));
        List<String> before = snapshot(base);

        PatchResult result = patcher.apply(base, temp.resolve("shaderpacks"));

        assertEquals("SampleBase_r1 + SamplePatch_2.0", result.getOutputDir().getFileName().toString());
        TestSupport.assertSameTree(expectedPack, result.getOutputDir());
        assertEquals(before, snapshot(base), "the base pack must not be modified");
        assertEquals(patcher.getMetadata().getBaseFingerprint(), result.getBaseFingerprint());
    }

    @Test
    void appliesToAZipBase() throws Exception {
        Path folder = TestSupport.sampleBase(temp.resolve("src"));
        Path zip = temp.resolve("SampleBase_r1.zip");
        TestSupport.zip(folder, zip);

        PatchResult result = patcher.apply(zip, temp.resolve("shaderpacks"));

        assertEquals("SampleBase_r1 + SamplePatch_2.0", result.getOutputDir().getFileName().toString());
        TestSupport.assertSameTree(expectedPack, result.getOutputDir());
        assertTrue(Files.isRegularFile(zip));
    }

    @Test
    void leavesOnlyTheResultInTheTargetFolder() throws Exception {
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));
        Path target = temp.resolve("shaderpacks");

        patcher.apply(base, target);

        assertEquals(Collections.singletonList("SampleBase_r1 + SamplePatch_2.0/"), topLevel(target));
    }

    @Test
    void copySuffixesAreRemovedFromTheOutputName() throws Exception {
        Path folder = TestSupport.sampleBase(temp.resolve("src"));
        Path zip = temp.resolve("SampleBase_r1 (1).zip");
        TestSupport.zip(folder, zip);

        PatchResult result = patcher.apply(zip, temp.resolve("shaderpacks"));

        assertEquals("SampleBase_r1 + SamplePatch_2.0", result.getOutputDir().getFileName().toString());
    }

    @Test
    void patchOnlyNamingIgnoresTheBaseName() throws Exception {
        ShaderPatcher p = ShaderPatcher.builder(PatchSource.path(patchFile)).outputNaming(OutputNaming.PATCH_ONLY).build();
        Path base = TestSupport.sampleBase(temp.resolve("Whatever_Name"));

        PatchResult result = p.apply(base, temp.resolve("shaderpacks"));

        assertEquals("SamplePatch_2.0", result.getOutputDir().getFileName().toString());
        TestSupport.assertSameTree(expectedPack, result.getOutputDir());
    }

    @Test
    void customNamingReceivesTheContext() throws Exception {
        ShaderPatcher p = ShaderPatcher.builder(PatchSource.path(patchFile)).outputNaming(new OutputNaming() {
            @Override
            public String name(NamingContext context) {
                return "custom-" + context.getBaseFileName() + "-" + context.getMetadata().getPatchVersion();
            }
        }).build();
        Path base = TestSupport.sampleBase(temp.resolve("B"));

        PatchResult result = p.apply(base, temp.resolve("shaderpacks"));

        assertEquals("custom-B-_2.0", result.getOutputDir().getFileName().toString());
    }

    @Test
    void existingOutputFailsByDefaultAndChangesNothing() throws Exception {
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));
        Path target = temp.resolve("shaderpacks");
        Path existing = TestSupport.write(target, "SampleBase_r1 + SamplePatch_2.0/keep.txt", "keep");

        OutputExistsException e = assertThrows(OutputExistsException.class, () -> patcher.apply(base, target));

        assertEquals(existing.getParent(), e.getPath());
        assertEquals("keep", new String(Files.readAllBytes(existing)));
        assertEquals(Collections.singletonList("SampleBase_r1 + SamplePatch_2.0/"), topLevel(target));
    }

    @Test
    void existingOutputCanBeReplaced() throws Exception {
        ShaderPatcher p = ShaderPatcher.builder(PatchSource.path(patchFile)).existingOutput(ExistingOutput.REPLACE).build();
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));
        Path target = temp.resolve("shaderpacks");
        TestSupport.write(target, "SampleBase_r1 + SamplePatch_2.0/stale.txt", "stale");

        PatchResult result = p.apply(base, target);

        TestSupport.assertSameTree(expectedPack, result.getOutputDir());
        assertFalse(Files.exists(result.getOutputDir().resolve("stale.txt")));
    }

    @Test
    void verifyAndMatchBase() throws Exception {
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));
        Path other = TestSupport.sampleBase(temp.resolve("Other"));
        TestSupport.write(other, "extra.txt", "more");

        assertEquals(patcher.getMetadata().getBaseFingerprint(), patcher.verifyBase(base));
        assertTrue(patcher.matchesBase(base));
        assertFalse(patcher.matchesBase(other));
        assertFalse(patcher.matchesBase(temp.resolve("does-not-exist.zip")));
    }

    @Test
    void sizeOnlyMatchSkipsTheHash() throws Exception {
        Path sameSizeDifferentContent = TestSupport.sampleBase(temp.resolve("Variant"));
        TestSupport.write(sameSizeDifferentContent, "shaders/final.fsh", "void main() { /* BASE */ }\n");

        assertFalse(patcher.matchesBase(sameSizeDifferentContent, true));
        assertTrue(patcher.matchesBase(sameSizeDifferentContent, false));
    }

    @Test
    void metadataIsStoredInThePatch() {
        PatchMetadata m = patcher.getMetadata();

        assertEquals("SamplePatch", m.getPatchName());
        assertEquals("_2.0", m.getPatchVersion());
        assertEquals("SampleBase", m.getBaseName());
        assertEquals("_r1", m.getBaseVersion());
        assertEquals("SamplePatch_2.0", m.getPatchedName());
        assertEquals("SamplePatch_2.0.patch", m.getPatchFileName());
        assertEquals(1, m.getTarFormat());
        assertTrue(m.getGenerator().startsWith("shaderpatcher/"));
    }

    @Test
    void debugLogReceivesDiagnostics() throws Exception {
        final List<String> lines = new ArrayList<>();
        ShaderPatcher p = ShaderPatcher.builder(PatchSource.path(patchFile)).debugLog(lines::add).build();
        Path folder = TestSupport.sampleBase(temp.resolve("src"));
        Path zip = temp.resolve("SampleBase_r1.zip");
        TestSupport.zip(folder, zip);

        p.apply(zip, temp.resolve("shaderpacks"));

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Applying SamplePatch_2.0")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Extracted ")), lines.toString());
    }

    @Test
    void generatedPatchesAreReproducible() throws Exception {
        Path base = TestSupport.sampleBase(temp.resolve("again-base"));
        Path edited = TestSupport.sampleEdited(temp.resolve("again-edited"));

        GeneratedPatch second = PatchGenerator.generate(GenerateRequest.builder(TestSupport.spec())
                .base(base).edited(edited).addOutputDir(temp.resolve("patches2")).build());

        assertArrayEquals(Files.readAllBytes(patchFile), Files.readAllBytes(second.getPatchFiles().get(0)));
    }

    @Test
    void generatorWritesToEveryOutputFolderAndHonoursExcludes() throws Exception {
        Path base = TestSupport.sampleBase(temp.resolve("x-base"));
        Path edited = TestSupport.sampleEdited(temp.resolve("x-edited"));
        TestSupport.write(edited, "notes.txt", "dev notes");
        TestSupport.write(edited, "tools/build.py", "print()");
        TestSupport.write(edited, "shaders/helper.py", "print()");
        TestSupport.write(edited, ".git/config", "[core]");

        GeneratedPatch g = PatchGenerator.generate(GenerateRequest.builder(TestSupport.spec())
                .base(base).edited(edited)
                .editedExclude(name -> name.equals("notes.txt") || name.equals("tools") || name.endsWith(".py"))
                .addOutputDir(temp.resolve("out1")).addOutputDir(temp.resolve("out2")).build());

        assertEquals(2, g.getPatchFiles().size());
        assertArrayEquals(Files.readAllBytes(g.getPatchFiles().get(0)), Files.readAllBytes(g.getPatchFiles().get(1)));

        ShaderPatcher p = ShaderPatcher.builder(PatchSource.path(g.getPatchFiles().get(1))).build();
        PatchResult result = p.apply(base, temp.resolve("shaderpacks"));
        TestSupport.assertSameTree(expectedPack, result.getOutputDir());
    }

    @Test
    void extractCopiesFoldersAndUnpacksZips() throws Exception {
        Path folder = TestSupport.sampleBase(temp.resolve("src"));
        Path zip = temp.resolve("src.zip");
        TestSupport.zip(folder, zip);

        ShaderPatcher.extract(folder, temp.resolve("copy"));
        ShaderPatcher.extract(zip, temp.resolve("unpacked"));

        TestSupport.assertSameTree(folder, temp.resolve("copy"));
        TestSupport.assertSameTree(folder, temp.resolve("unpacked"));
    }

    private static List<String> snapshot(Path root) throws Exception {
        List<String> out = new ArrayList<>();
        for (String rel : TestSupport.listRelative(root)) {
            Path p = root.resolve(rel);
            out.add(rel + (Files.isDirectory(p) ? "" : ":" + new String(Files.readAllBytes(p))));
        }
        return out;
    }

    private static List<String> topLevel(Path dir) throws Exception {
        List<String> out = new ArrayList<>();
        try (java.util.stream.Stream<Path> s = Files.list(dir)) {
            s.forEach(p -> out.add(p.getFileName() + (Files.isDirectory(p) ? "/" : "")));
        }
        Collections.sort(out);
        return out;
    }
}
