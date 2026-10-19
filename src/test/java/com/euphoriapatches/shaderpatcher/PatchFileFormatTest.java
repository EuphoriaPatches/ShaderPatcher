package com.euphoriapatches.shaderpatcher;

import com.euphoriapatches.shaderpatcher.exception.PatchCorruptException;
import com.euphoriapatches.shaderpatcher.internal.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The patch file container: layout, validation and the messages for each kind of damage. */
class PatchFileFormatTest {
    private static final String SHA_A = sha('a');
    private static final String SHA_B = sha('b');

    private static String sha(char first) {
        StringBuilder sb = new StringBuilder().append(first);
        while (sb.length() < 64) {
            sb.append('0');
        }
        return sb.toString();
    }

    @TempDir
    Path temp;

    private byte[] valid;

    @BeforeEach
    void setUp() throws Exception {
        valid = Files.readAllBytes(TestSupport.generateSamplePatch(temp, temp.resolve("patches")));
    }

    @Test
    void startsWithTheDocumentedMagic() {
        assertEquals("SPTCHEAGLE", new String(valid, 0, 10, StandardCharsets.US_ASCII));
        assertEquals(0, valid[10]);
        assertEquals(1, valid[11]); // format version 1, big-endian u16
    }

    @Test
    void wrongMagicIsRejected() {
        byte[] bytes = valid.clone();
        bytes[0] = 'X';

        PatchCorruptException e = assertThrows(PatchCorruptException.class, () -> open(bytes));

        assertTrue(e.getMessage().contains("wrong magic"), e.getMessage());
    }

    @Test
    void lineEndingConversionIsDetected() {
        // what git autocrlf or an ASCII-mode FTP transfer does: every LF becomes CR LF
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte b : valid) {
            if (b == 0x0A) {
                out.write(0x0D);
            }
            out.write(b);
        }

        PatchCorruptException e = assertThrows(PatchCorruptException.class, () -> open(out.toByteArray()));

        assertTrue(e.getMessage().contains("checksum"), e.getMessage());
    }

    @Test
    void aRawBsdiffFileIsRecognisedAsSuch() {
        byte[] raw = "BSDIFF40 and then some bytes".getBytes(StandardCharsets.US_ASCII);

        PatchCorruptException e = assertThrows(PatchCorruptException.class, () -> open(raw));

        assertTrue(e.getMessage().contains("raw bsdiff"), e.getMessage());
    }

    @Test
    void everyTruncationIsDetected() throws Exception {
        int headerLength = ((valid[12] & 0xff) << 24) | ((valid[13] & 0xff) << 16) | ((valid[14] & 0xff) << 8) | (valid[15] & 0xff);
        int headerEnd = 10 + 2 + 4 + headerLength + 4;
        Path base = TestSupport.sampleBase(temp.resolve("base"));
        Path file = temp.resolve("truncated.patch");

        for (int length = 0; length < valid.length; length++) {
            Files.write(file, Arrays.copyOf(valid, length));
            if (length < headerEnd) {
                // the header is cut: building the patcher already fails
                assertThrows(PatchCorruptException.class, () -> ShaderPatcher.builder(PatchSource.path(file)).build(),
                        "cut at " + length);
            } else {
                // only the payload or its checksum is cut: the header is fine, applying fails
                ShaderPatcher p = ShaderPatcher.builder(PatchSource.path(file)).build();
                assertThrows(PatchCorruptException.class, () -> p.apply(base, temp.resolve("target")), "cut at " + length);
            }
        }
        assertFalse(Files.exists(temp.resolve("target")));
    }

    @Test
    void aFlippedHeaderBitFailsTheHeaderChecksum() {
        byte[] bytes = valid.clone();
        bytes[30] ^= 0x01; // inside the header text

        PatchCorruptException e = assertThrows(PatchCorruptException.class, () -> open(bytes));

        assertTrue(e.getMessage().contains("header checksum"), e.getMessage());
    }

    @Test
    void unknownHeaderKeysAreIgnored() throws Exception {
        byte[] bytes = container(1, baseHeader() + "futureKey=whatever\n# a comment\n\n", "payload");

        PatchMetadata m = open(bytes);

        assertEquals("P", m.getPatchName());
        assertEquals("_1", m.getPatchVersion());
        assertEquals(SHA_A, m.getBaseFingerprint().getSha256());
        assertEquals(20, m.getPatchedFingerprint().getSize());
        assertNull(m.getBaseName());
        assertNull(m.getGenerator());
    }

    @Test
    void aNewerContainerVersionIsRefusedWithAClearMessage() {
        byte[] bytes = container(2, baseHeader(), "payload");

        PatchCorruptException e = assertThrows(PatchCorruptException.class, () -> open(bytes));

        assertTrue(e.getMessage().contains("Unsupported patch file format version 2"), e.getMessage());
    }

    @Test
    void aDifferentArchiveFormatIsRefused() {
        byte[] bytes = container(1, baseHeader().replace("tarFormat=1", "tarFormat=2"), "payload");

        PatchCorruptException e = assertThrows(PatchCorruptException.class, () -> open(bytes));

        assertTrue(e.getMessage().contains("archive format 2"), e.getMessage());
    }

    @Test
    void missingAndMalformedHeaderValuesAreRejected() {
        assertTrue(assertThrows(PatchCorruptException.class,
                () -> open(container(1, baseHeader().replace("patchName=P\n", ""), "x"))).getMessage().contains("patchName"));
        assertThrows(PatchCorruptException.class,
                () -> open(container(1, baseHeader().replace("baseTarSize=10", "baseTarSize=ten"), "x")));
        assertThrows(PatchCorruptException.class,
                () -> open(container(1, baseHeader().replace(SHA_A, "nothex"), "x")));
        assertThrows(PatchCorruptException.class, () -> open(container(1, baseHeader() + "no equals sign here\n", "x")));
    }

    @Test
    void aValidHandBuiltFileIsAccepted() throws Exception {
        open(container(1, baseHeader(), "payload"));
    }

    private static String baseHeader() {
        return "tarFormat=1\npatchName=P\npatchVersion=_1\nbaseTarSize=10\nbaseTarSha256=" + SHA_A
                + "\npatchedTarSize=20\npatchedTarSha256=" + SHA_B + "\n";
    }

    /** Builds a container by hand, independently of the library's writer. */
    private static byte[] container(int version, String header, String payload) {
        try {
            byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            crc.update(headerBytes, 0, headerBytes.length);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write("SPTCHEAGLE".getBytes(StandardCharsets.US_ASCII));
            out.writeShort(version);
            out.writeInt(headerBytes.length);
            out.write(headerBytes);
            out.writeInt((int) crc.getValue());
            out.writeLong(payloadBytes.length);
            out.write(payloadBytes);
            out.write(HashUtils.sha256().digest(payloadBytes));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PatchMetadata open(byte[] bytes) throws Exception {
        return ShaderPatcher.builder(PatchSource.stream("test bytes", () -> new java.io.ByteArrayInputStream(bytes)))
                .build().getMetadata();
    }
}
