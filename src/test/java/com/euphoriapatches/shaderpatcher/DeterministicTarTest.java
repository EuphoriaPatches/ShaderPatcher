package com.euphoriapatches.shaderpatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The fingerprint depends on the pack's content only. */
class DeterministicTarTest {
    @TempDir
    Path temp;

    @Test
    void zipAndFolderGiveTheSameFingerprint() throws Exception {
        Path folder = TestSupport.sampleBase(temp.resolve("folder"));
        Path zip = temp.resolve("pack.zip");
        TestSupport.zip(folder, zip);

        assertEquals(ShaderPatcher.fingerprint(folder), ShaderPatcher.fingerprint(zip));
    }

    @Test
    void fileDatesDoNotMatter() throws Exception {
        Path a = TestSupport.sampleBase(temp.resolve("a"));
        Path b = TestSupport.sampleBase(temp.resolve("b"));
        Files.setLastModifiedTime(b.resolve("shaders/final.fsh"), FileTime.fromMillis(86_400_000L * 365));
        Files.setLastModifiedTime(b.resolve("shaders"), FileTime.fromMillis(86_400_000L * 900));

        assertEquals(ShaderPatcher.fingerprint(a), ShaderPatcher.fingerprint(b));
    }

    @Test
    void creationOrderDoesNotMatter() throws Exception {
        Path a = temp.resolve("a");
        TestSupport.write(a, "one.txt", "1");
        TestSupport.write(a, "two/x.txt", "2");
        TestSupport.write(a, "three.txt", "3");
        Path b = temp.resolve("b");
        TestSupport.write(b, "three.txt", "3");
        TestSupport.write(b, "two/x.txt", "2");
        TestSupport.write(b, "one.txt", "1");

        assertEquals(ShaderPatcher.fingerprint(a), ShaderPatcher.fingerprint(b));
    }

    @Test
    void contentAndNamesMatter() throws Exception {
        Path base = TestSupport.sampleBase(temp.resolve("base"));
        BaseFingerprint original = ShaderPatcher.fingerprint(base);

        Path changed = TestSupport.sampleBase(temp.resolve("changed"));
        TestSupport.write(changed, "shaders/final.fsh", "void main() { /* BASE */ }\n");
        assertNotEquals(original, ShaderPatcher.fingerprint(changed));

        Path renamed = TestSupport.sampleBase(temp.resolve("renamed"));
        // a different name, not just different case: a case-only move is a no-op on Windows and macOS
        Files.move(renamed.resolve("shaders/final.fsh"), renamed.resolve("shaders/final2.fsh"));
        assertNotEquals(original, ShaderPatcher.fingerprint(renamed));
    }

    @Test
    void versionControlFoldersAtTheTopLevelAreIgnored() throws Exception {
        Path plain = TestSupport.sampleBase(temp.resolve("plain"));
        Path withGit = TestSupport.sampleBase(temp.resolve("withGit"));
        TestSupport.write(withGit, ".git/config", "[core]");
        TestSupport.write(withGit, ".svn/entries", "x");
        TestSupport.write(withGit, ".hg/store", "x");

        assertEquals(ShaderPatcher.fingerprint(plain), ShaderPatcher.fingerprint(withGit));

        // but a nested folder with that name is part of the pack
        Path nested = TestSupport.sampleBase(temp.resolve("nested"));
        TestSupport.write(nested, "shaders/.git/config", "[core]");
        assertNotEquals(ShaderPatcher.fingerprint(plain), ShaderPatcher.fingerprint(nested));
    }

    @Test
    void backslashesInZipEntryNamesBehaveLikeSlashesOnEveryPlatform() throws Exception {
        Path folder = temp.resolve("folder");
        TestSupport.write(folder, "shaders/final.fsh", "x");
        Path zip = temp.resolve("windows-tool.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("shaders\\final.fsh"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        assertEquals(ShaderPatcher.fingerprint(folder), ShaderPatcher.fingerprint(zip));
    }

    @Test
    void aSymbolicLinkToAPackFolderIsFollowed() throws Exception {
        Path real = TestSupport.sampleBase(temp.resolve("real"));
        Path link = temp.resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException e) {
            assumeTrue(false, "cannot create symbolic links here: " + e);
        }

        assertEquals(ShaderPatcher.fingerprint(real), ShaderPatcher.fingerprint(link));
        ShaderPatcher.extract(link, temp.resolve("copy"));
        TestSupport.assertSameTree(real, temp.resolve("copy"));
    }

    @Test
    void anEmptyFolderIsAValidPackWithAFixedFingerprint() throws Exception {
        Path a = Files.createDirectories(temp.resolve("a"));
        Path b = Files.createDirectories(temp.resolve("b"));

        assertEquals(ShaderPatcher.fingerprint(a), ShaderPatcher.fingerprint(b));
    }
}
