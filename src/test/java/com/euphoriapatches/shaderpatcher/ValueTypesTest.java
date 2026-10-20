package com.euphoriapatches.shaderpatcher;

import com.euphoriapatches.shaderpatcher.internal.PackNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueTypesTest {
    private static final String SHA = "17e51062476455065a58d8e5da49d60e9a5af38e8e6d67a135989cf71f103a19";

    @Test
    void fingerprintValidatesAndNormalizes() {
        BaseFingerprint fp = BaseFingerprint.of(1651200L, SHA.toUpperCase());

        assertEquals(SHA, fp.getSha256());
        assertEquals(BaseFingerprint.of(1651200L, SHA), fp);
        assertEquals(BaseFingerprint.of(1651200L, SHA).hashCode(), fp.hashCode());
        assertNotEquals(BaseFingerprint.of(1651201L, SHA), fp);
        assertThrows(IllegalArgumentException.class, () -> BaseFingerprint.of(-1, SHA));
        assertThrows(IllegalArgumentException.class, () -> BaseFingerprint.of(1, "abc"));
        assertThrows(IllegalArgumentException.class, () -> BaseFingerprint.of(1, null));
    }

    @Test
    void specBuildsNamesAndRejectsMisuse() {
        PatchSpec spec = PatchSpec.builder().patchName("EuphoriaPatches").patchVersion("_1.10.5").build();

        assertEquals("EuphoriaPatches_1.10.5.patch", spec.getPatchFileName());
        assertNull(spec.getBaseName());
        assertEquals("", PatchSpec.builder().patchName("X").build().getPatchVersion());
        assertThrows(IllegalArgumentException.class, () -> PatchSpec.builder().build());
        assertThrows(IllegalArgumentException.class, () -> PatchSpec.builder().patchName("").build());
        assertThrows(IllegalArgumentException.class, () -> PatchSpec.builder().patchName("A\nB").build());
        assertThrows(IllegalArgumentException.class, () -> PatchSpec.builder().patchName("A").baseVersion("1\r2").build());
    }

    @Test
    void copySuffixesAreCleaned() {
        assertEquals("Pack_r1", PackNames.cleanBaseName("Pack_r1 (1)"));
        assertEquals("Pack_r1", PackNames.cleanBaseName("Pack_r1 - Copy"));
        assertEquals("Pack_r1", PackNames.cleanBaseName("Pack_r1_copy (2)"));
        assertEquals("Pack_r1", PackNames.cleanBaseName("Pack_r1 copy 2"));
        assertEquals("Pack_r1", PackNames.cleanBaseName("Pack_r1 - Copy (2)"));
        assertEquals("Pack_r1", PackNames.cleanBaseName("Pack_r1 (copy)"));
        // a trailing number on its own is part of the name, not a copy counter
        assertEquals("Base-120", PackNames.cleanBaseName("Base-120"));
        assertEquals("Pack_2", PackNames.cleanBaseName("Pack_2"));
        assertEquals("Shader 3", PackNames.cleanBaseName("Shader 3"));
        assertEquals("Base-120", PackNames.cleanBaseName("Base-120 (1)"));
        assertEquals("Pack_r1", PackNames.cleanBaseName("Pack_r1"));
        assertEquals("Pack r1", PackNames.cleanBaseName("Pack   r1"));
        assertEquals("Pack_r1", PackNames.stripZipExtension("Pack_r1.ZIP"));
        assertEquals("Pack_r1.tar", PackNames.stripZipExtension("Pack_r1.tar"));
    }

    @Test
    void folderNamesAreChecked() {
        PackNames.requireFolderName("fine name + 1.0");
        assertThrows(IllegalStateException.class, () -> PackNames.requireFolderName(""));
        assertThrows(IllegalStateException.class, () -> PackNames.requireFolderName(".."));
        assertThrows(IllegalStateException.class, () -> PackNames.requireFolderName("a/b"));
        assertThrows(IllegalStateException.class, () -> PackNames.requireFolderName("a\\b"));
    }
}
