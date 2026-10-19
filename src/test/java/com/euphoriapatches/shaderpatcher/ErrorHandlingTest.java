package com.euphoriapatches.shaderpatcher;

import com.euphoriapatches.shaderpatcher.exception.BaseHashMismatchException;
import com.euphoriapatches.shaderpatcher.exception.BaseSizeMismatchException;
import com.euphoriapatches.shaderpatcher.exception.InvalidPackException;
import com.euphoriapatches.shaderpatcher.exception.PatchCorruptException;
import com.euphoriapatches.shaderpatcher.exception.PatchMetadataMismatchException;
import com.euphoriapatches.shaderpatcher.exception.PatchNotFoundException;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;
import com.euphoriapatches.shaderpatcher.internal.PatchFileFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every failure is a typed exception, leaves nothing behind, and nothing is ever printed. */
class ErrorHandlingTest {
    @TempDir
    Path temp;

    private Path patchFile;
    private ShaderPatcher patcher;
    private Path target;

    @BeforeEach
    void setUp() throws Exception {
        patchFile = TestSupport.generateSamplePatch(temp, temp.resolve("patches"));
        patcher = ShaderPatcher.builder(PatchSource.path(patchFile)).build();
        target = temp.resolve("shaderpacks");
    }

    @Test
    void wrongSizeIsReportedWithBothFingerprints() throws Exception {
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));
        TestSupport.write(base, "shaders/new.txt", "an extra file makes the archive bigger");

        BaseSizeMismatchException e = assertThrows(BaseSizeMismatchException.class, () -> patcher.apply(base, target));

        assertEquals(base, e.getPath());
        assertEquals(patcher.getMetadata().getBaseFingerprint(), e.getExpected());
        assertTrue(e.getActual().getSize() > e.getExpected().getSize());
        assertTrue(e.getMessage().contains("expected " + e.getExpected().getSize()), e.getMessage());
        assertNothingLeftBehind();
    }

    @Test
    void sameSizeButDifferentContentIsAHashMismatch() throws Exception {
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));
        TestSupport.write(base, "shaders/final.fsh", "void main() { /* BASE */ }\n");

        BaseHashMismatchException e = assertThrows(BaseHashMismatchException.class, () -> patcher.apply(base, target));

        assertEquals(e.getExpected().getSize(), e.getActual().getSize());
        assertFalse(e.getExpected().getSha256().equals(e.getActual().getSha256()));
        assertEquals(base, e.getPath());
        assertNothingLeftBehind();
    }

    @Test
    void aFileCalledZipThatIsNotAZipIsAnInvalidPack() throws Exception {
        Path fake = TestSupport.write(temp, "SampleBase_r1.zip", "this is not a zip file at all");

        InvalidPackException e = assertThrows(InvalidPackException.class, () -> patcher.apply(fake, target));

        assertEquals(fake, e.getPath());
        assertNothingLeftBehind();
    }

    @Test
    void anEmptyOrTruncatedZipIsAnInvalidPack() throws Exception {
        Path empty = Files.write(temp.resolve("empty.zip"), new byte[0]);
        Path folder = TestSupport.sampleBase(temp.resolve("src"));
        Path whole = temp.resolve("whole.zip");
        TestSupport.zip(folder, whole);
        byte[] bytes = Files.readAllBytes(whole);
        Path truncated = Files.write(temp.resolve("truncated.zip"), java.util.Arrays.copyOf(bytes, bytes.length / 2));

        assertThrows(InvalidPackException.class, () -> patcher.apply(empty, target));
        assertThrows(InvalidPackException.class, () -> patcher.apply(truncated, target));
        assertNothingLeftBehind();
    }

    @Test
    void aMissingBaseIsAnInvalidPack() {
        Path missing = temp.resolve("nothing.zip");

        assertThrows(InvalidPackException.class, () -> patcher.apply(missing, target));
        assertThrows(InvalidPackException.class, () -> patcher.verifyBase(missing));
        assertThrows(InvalidPackException.class, () -> ShaderPatcher.fingerprint(missing));
    }

    @Test
    void entriesEscapingTheTargetAreRejected() throws Exception {
        Path zip = temp.resolve("evil.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("shaders/ok.txt"));
            out.write(1);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("../../evil.txt"));
            out.write(1);
            out.closeEntry();
        }

        assertThrows(InvalidPackException.class, () -> patcher.apply(zip, target));

        assertFalse(Files.exists(temp.resolve("evil.txt")));
        assertFalse(Files.exists(temp.getParent().resolve("evil.txt")));
        assertNothingLeftBehind();
    }

    @Test
    void aMissingPatchFileIsPatchNotFound() {
        PatchNotFoundException e = assertThrows(PatchNotFoundException.class,
                () -> ShaderPatcher.builder(PatchSource.path(temp.resolve("nope.patch"))).build());

        assertTrue(e.getMessage().contains("nope.patch"), e.getMessage());
    }

    @Test
    void aMissingClasspathResourceIsPatchNotFound() {
        PatchNotFoundException e = assertThrows(PatchNotFoundException.class,
                () -> ShaderPatcher.builder(PatchSource.classpath(ErrorHandlingTest.class, "Missing_1.0.patch")).build());

        assertTrue(e.getMessage().contains("Missing_1.0.patch"), e.getMessage());
        assertNull(e.getPath());
    }

    @Test
    void aStreamSourceReturningNullIsPatchNotFound() {
        assertThrows(PatchNotFoundException.class, () -> ShaderPatcher.builder(
                PatchSource.stream("my resource", () -> (InputStream) null)).build());
    }

    @Test
    void aWrongExpectedPatchVersionIsAMetadataMismatch() {
        PatchMetadataMismatchException e = assertThrows(PatchMetadataMismatchException.class,
                () -> ShaderPatcher.builder(PatchSource.path(patchFile)).expectPatchVersion("_3.0").build());

        assertEquals("patchVersion", e.getField());
        assertEquals("_3.0", e.getExpected());
        assertEquals("_2.0", e.getActual());

        assertEquals("patchName", assertThrows(PatchMetadataMismatchException.class,
                () -> ShaderPatcher.builder(PatchSource.path(patchFile)).expectPatchName("Other").build()).getField());
    }

    @Test
    void aDamagedPayloadIsDetectedByItsChecksum() throws Exception {
        byte[] bytes = Files.readAllBytes(patchFile);
        bytes[bytes.length - 40] ^= 0x01; // inside the payload: the last 32 bytes are its SHA-256
        Path damaged = temp.resolve("damaged.patch");
        Files.write(damaged, bytes);
        ShaderPatcher p = ShaderPatcher.builder(PatchSource.path(damaged)).build(); // header is fine
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));

        PatchCorruptException e = assertThrows(PatchCorruptException.class, () -> p.apply(base, target));

        assertTrue(e.getMessage().contains("checksum"), e.getMessage());
        assertEquals(damaged, e.getPath());
        assertNothingLeftBehind();
    }

    @Test
    void aPayloadThatIsNotBsdiffIsReportedAsCorrupt() throws Exception {
        PatchFileFormat.Parsed real = readPatch(patchFile);
        Path bogus = temp.resolve("bogus.patch");
        writePatch(bogus, real.getMetadata(), "this is not a bsdiff stream".getBytes(StandardCharsets.UTF_8));
        ShaderPatcher p = ShaderPatcher.builder(PatchSource.path(bogus)).build();
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));

        PatchCorruptException e = assertThrows(PatchCorruptException.class, () -> p.apply(base, target));

        assertNotNull(e.getCause());
        assertNothingLeftBehind();
    }

    @Test
    void aPatchWhoseResultDoesNotMatchItsRecordedHashIsCorrupt() throws Exception {
        PatchFileFormat.Parsed real = readPatch(patchFile);
        PatchMetadata m = real.getMetadata();
        PatchMetadata lying = new PatchMetadata(m.getTarFormat(), m.getPatchName(), m.getPatchVersion(), m.getBaseName(),
                m.getBaseVersion(), m.getBaseFingerprint(), BaseFingerprint.of(m.getPatchedFingerprint().getSize(),
                "0000000000000000000000000000000000000000000000000000000000000000"), m.getGenerator());
        Path wrong = temp.resolve("wrong-result.patch");
        writePatch(wrong, lying, real.getPayload());
        ShaderPatcher p = ShaderPatcher.builder(PatchSource.path(wrong)).build();
        Path base = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));

        PatchCorruptException e = assertThrows(PatchCorruptException.class, () -> p.apply(base, target));

        assertTrue(e.getMessage().contains("recorded result"), e.getMessage());
        assertNothingLeftBehind();
    }

    @Test
    void nothingIsEverPrintedOrLogged() throws Exception {
        final Path good = TestSupport.sampleBase(temp.resolve("SampleBase_r1"));
        final Path bad = TestSupport.sampleBase(temp.resolve("Bad"));
        TestSupport.write(bad, "x.txt", "x");

        String printed = TestSupport.captureStandardStreams(() -> {
            patcher.apply(good, target);
            try {
                patcher.apply(bad, temp.resolve("other"));
            } catch (ShaderPatcherException expected) {
                // expected, reported only by throwing
            }
            patcher.matchesBase(bad);
            ShaderPatcher.fingerprint(good);
            return null;
        });

        assertEquals("", printed);
    }

    private void assertNothingLeftBehind() throws Exception {
        if (Files.exists(target)) {
            assertEquals(0, TestSupport.listRelative(target).size(), "target folder must stay empty: "
                    + TestSupport.listRelative(target));
        }
    }

    private static PatchFileFormat.Parsed readPatch(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return PatchFileFormat.read(in, file.toString(), file);
        }
    }

    private static void writePatch(Path file, PatchMetadata metadata, byte[] payload) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PatchFileFormat.write(out, metadata, payload);
        Files.write(file, out.toByteArray());
    }
}
