package com.euphoriapatches.shaderpatcher;

import com.euphoriapatches.shaderpatcher.exception.InvalidPackException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hostile archives: path traversal, entries that replace the target folder, zip bombs. */
class SecurityTest {
    @TempDir
    Path temp;

    private ShaderPatcher patcher;
    private Path target;

    @BeforeEach
    void setUp() throws Exception {
        Path patch = TestSupport.generateSamplePatch(temp, temp.resolve("patches"));
        patcher = ShaderPatcher.builder(PatchSource.path(patch)).build();
        target = temp.resolve("sandbox/shaderpacks");
    }

    private Path zipWith(String name, byte[] content) throws IOException {
        Path zip = temp.resolve("hostile.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(name));
            out.write(content);
            out.closeEntry();
        }
        return zip;
    }

    @Test
    void traversalNamesNeverWriteOutsideTheTarget() throws Exception {
        for (String name : Arrays.asList("../evil.txt", "../../evil.txt", "a/../../evil.txt", "..\\evil.txt",
                "..\\..\\evil.txt", "a\\..\\..\\evil.txt", "shaders/../../evil.txt")) {
            Path zip = zipWith(name, new byte[]{1});

            assertThrows(InvalidPackException.class, () -> ShaderPatcher.extract(zip, temp.resolve("out")), name);
            assertThrows(InvalidPackException.class, () -> patcher.apply(zip, target), name);
        }
        assertFalse(Files.exists(temp.resolve("evil.txt")));
        assertFalse(Files.exists(temp.getParent().resolve("evil.txt")));
        assertFalse(Files.exists(temp.resolve("sandbox/evil.txt")));
    }

    @Test
    void absoluteNamesStayInsideTheTarget() throws Exception {
        for (String name : Arrays.asList("/etc/evil.txt", "\\windows\\evil.txt", "C:/evil.txt", "C:\\evil.txt", "//server/share/evil.txt")) {
            Path out = temp.resolve("abs-" + Math.abs(name.hashCode()));
            Path zip = zipWith(name, new byte[]{1});
            try {
                ShaderPatcher.extract(zip, out);
            } catch (InvalidPackException rejected) {
                // refusing it is fine too
            }
            // whatever happened, nothing outside the target folder was written
            Path path = Paths.get("C:/evil.txt");
            assertFalse(Files.exists(path) && Files.size(path) == 1, name);
            assertFalse(Files.exists(temp.resolve("evil.txt")), name);
        }
    }

    @Test
    void anEntryThatWouldReplaceTheTargetFolderIsRejected() throws Exception {
        for (String name : Arrays.asList(".", "shaders/..")) {
            Path zip = zipWith(name, new byte[]{1});

            assertThrows(InvalidPackException.class, () -> ShaderPatcher.extract(zip, temp.resolve("out2")), name);
        }
    }

    @Test
    void aZipBombIsStoppedWithoutUnpackingEverything() throws Exception {
        // 200 MB of zeros compresses to about 200 KB; the sample base is only a few KB
        Path zip = temp.resolve("bomb.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("shaders/huge.bin"));
            byte[] zeros = new byte[1024 * 1024];
            for (int i = 0; i < 200; i++) {
                out.write(zeros);
            }
            out.closeEntry();
        }
        assertTrue(Files.size(zip) < 1024 * 1024);

        InvalidPackException e = assertThrows(InvalidPackException.class, () -> patcher.apply(zip, target));

        assertTrue(e.getMessage().contains("more data"), e.getMessage());
        assertFalse(patcher.matchesBase(zip));
    }

    @Test
    void manyEmptyEntriesCountTowardsTheLimitToo() throws Exception {
        Path zip = temp.resolve("many.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int i = 0; i < 2000; i++) {
                out.putNextEntry(new ZipEntry("shaders/f" + i + ".txt"));
                out.closeEntry();
            }
        }

        assertThrows(InvalidPackException.class, () -> patcher.apply(zip, target));
    }

    @Test
    void versionControlFoldersInAZipAreIgnoredLikeInFolders() throws Exception {
        Path folder = TestSupport.sampleBase(temp.resolve("folder"));
        Path withGit = TestSupport.sampleBase(temp.resolve("withGit"));
        TestSupport.write(withGit, ".git/objects/pack", "lots of history");
        TestSupport.write(withGit, ".svn/entries", "x");
        Path zip = temp.resolve("withGit.zip");
        TestSupport.zip(withGit, zip);

        assertEquals(ShaderPatcher.fingerprint(folder), ShaderPatcher.fingerprint(zip));
        assertTrue(patcher.matchesBase(zip));
    }

    @Test
    void aValidPackIsNeverStoppedByTheLimit() throws Exception {
        Path folder = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));
        Path zip = temp.resolve("SampleBase_r1.zip");
        TestSupport.zip(folder, zip);

        assertEquals(patcher.getMetadata().getBaseFingerprint(), patcher.verifyBase(zip));
    }
}
