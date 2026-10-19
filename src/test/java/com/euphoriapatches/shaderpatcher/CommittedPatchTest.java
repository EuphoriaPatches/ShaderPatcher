package com.euphoriapatches.shaderpatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A patch file generated once and committed must keep applying. If this fails, the archive rules or the patch file
 * format changed, which breaks every patch ever generated.
 */
class CommittedPatchTest {
    private static final String RESOURCE = "samples/SamplePatch_2.0.patch";

    @TempDir
    Path temp;

    @Test
    void theCommittedPatchAppliesOnThisPlatform() throws Exception {
        ShaderPatcher patcher = ShaderPatcher.builder(PatchSource.classpath(CommittedPatchTest.class, RESOURCE))
                .expectPatchName("SamplePatch").expectPatchVersion("_2.0").build();
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));

        PatchResult result = patcher.apply(base, temp.resolve("shaderpacks"));

        TestSupport.assertSameTree(TestSupport.sampleEdited(temp.resolve("expected")), result.getOutputDir());
        assertEquals("SampleBase_r1 + SamplePatch_2.0", result.getOutputDir().getFileName().toString());
    }
}
