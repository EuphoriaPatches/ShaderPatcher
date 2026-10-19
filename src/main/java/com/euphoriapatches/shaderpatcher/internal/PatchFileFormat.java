package com.euphoriapatches.shaderpatcher.internal;

import com.euphoriapatches.shaderpatcher.BaseFingerprint;
import com.euphoriapatches.shaderpatcher.PatchMetadata;
import com.euphoriapatches.shaderpatcher.exception.PatchCorruptException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Not public API. Reads and writes the patch file container (big-endian, text is UTF-8):
 * magic {@code "SPTCHEAGLE"}, u16 format version, u32 header length, header ({@code key=value} lines),
 * u32 CRC-32 of the header, u64 payload length, bsdiff40 payload, SHA-256 of the payload.
 *
 * @author SpacEagle17
 */
public final class PatchFileFormat {
    /** Version of this container layout. */
    public static final int FORMAT_VERSION = 1;

    private static final byte[] MAGIC = {'S', 'P', 'T', 'C', 'H', 'E', 'A', 'G', 'L', 'E'};
    // Protection against malformed files to avoid OutOfMemoryError.
    private static final int MAX_HEADER = 64 * 1024;
    private static final int MAX_PAYLOAD = Integer.MAX_VALUE - 64;

    private PatchFileFormat() {
    }

    /** A fully read and verified patch file. */
    public static final class Parsed {
        private final PatchMetadata metadata;
        private final byte[] payload;

        Parsed(PatchMetadata metadata, byte[] payload) {
            this.metadata = metadata;
            this.payload = payload;
        }

        public PatchMetadata getMetadata() {
            return metadata;
        }

        public byte[] getPayload() {
            return payload;
        }
    }

    /** Writes a patch file. */
    public static void write(OutputStream out, PatchMetadata metadata, byte[] payload) throws IOException {
        byte[] header = encodeHeader(metadata);
        CRC32 crc = new CRC32();
        crc.update(header, 0, header.length);

        DataOutputStream data = new DataOutputStream(out);
        data.write(MAGIC);
        data.writeShort(FORMAT_VERSION);
        data.writeInt(header.length);
        data.write(header);
        data.writeInt((int) crc.getValue());
        data.writeLong(payload.length);
        data.write(payload);
        data.write(HashUtils.sha256().digest(payload));
        data.flush();
    }

    /** Reads and validates only the header. */
    public static PatchMetadata readMetadata(InputStream in, String description, Path file) throws PatchCorruptException {
        try {
            DataInputStream data = new DataInputStream(in);
            return readHeader(data, description, file);
        } catch (EOFException e) {
            throw new PatchCorruptException("Patch file is truncated: " + description, file, e);
        } catch (IOException e) {
            throw new PatchCorruptException("Cannot read patch file " + description + ": " + e.getMessage(), file, e);
        }
    }

    /** Reads the whole file and verifies header CRC and payload checksum. */
    public static Parsed read(InputStream in, String description, Path file) throws PatchCorruptException {
        try {
            DataInputStream data = new DataInputStream(in);
            PatchMetadata metadata = readHeader(data, description, file);

            long length = data.readLong();
            if (length < 0 || length > MAX_PAYLOAD) {
                throw new PatchCorruptException("Patch payload length is invalid (" + length + "): " + description, file, null);
            }
            byte[] payload = new byte[(int) length];
            data.readFully(payload);
            byte[] storedHash = new byte[32];
            data.readFully(storedHash);

            MessageDigest digest = HashUtils.sha256();
            if (!Arrays.equals(storedHash, digest.digest(payload))) {
                throw new PatchCorruptException("Patch payload checksum mismatch, the file is damaged: " + description, file, null);
            }
            return new Parsed(metadata, payload);
        } catch (EOFException e) {
            throw new PatchCorruptException("Patch file is truncated: " + description, file, e);
        } catch (IOException e) {
            throw new PatchCorruptException("Cannot read patch file " + description + ": " + e.getMessage(), file, e);
        }
    }

    private static PatchMetadata readHeader(DataInputStream data, String description, Path file)
            throws IOException, PatchCorruptException {
        byte[] magic = new byte[MAGIC.length];
        int got = readUpTo(data, magic);
        if (got < MAGIC.length || !Arrays.equals(magic, MAGIC)) {
            throw badMagic(magic, got, description, file);
        }

        int version = data.readUnsignedShort();
        if (version != FORMAT_VERSION) {
            throw new PatchCorruptException("Unsupported patch file format version " + version + " (this library reads "
                    + FORMAT_VERSION + "), update the library: " + description, file, null);
        }

        int headerLength = data.readInt();
        if (headerLength < 0 || headerLength > MAX_HEADER) {
            throw new PatchCorruptException("Patch header length is invalid (" + headerLength + "): " + description, file, null);
        }
        byte[] header = new byte[headerLength];
        data.readFully(header);
        int storedCrc = data.readInt();
        CRC32 crc = new CRC32();
        crc.update(header, 0, header.length);
        if ((int) crc.getValue() != storedCrc) {
            throw new PatchCorruptException("Patch header checksum mismatch, the file is damaged: " + description, file, null);
        }
        return decodeHeader(header, description, file);
    }

    private static PatchCorruptException badMagic(byte[] magic, int got, String description, Path file) {
        if (got >= 8 && new String(magic, 0, 8, StandardCharsets.ISO_8859_1).equals("BSDIFF40")) {
            return new PatchCorruptException("This is a raw bsdiff file without ShaderPatcher metadata, regenerate it with"
                    + " ShaderPatcher: " + description, file, null);
        }
        return new PatchCorruptException("Not a ShaderPatcher patch file (wrong magic): " + description, file, null);
    }

    private static int readUpTo(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int n = in.read(buffer, total, buffer.length - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    private static byte[] encodeHeader(PatchMetadata m) {
        StringBuilder sb = new StringBuilder();
        line(sb, "tarFormat", Integer.toString(m.getTarFormat()));
        line(sb, "patchName", m.getPatchName());
        line(sb, "patchVersion", m.getPatchVersion());
        if (m.getBaseName() != null) {
            line(sb, "baseName", m.getBaseName());
        }
        if (m.getBaseVersion() != null) {
            line(sb, "baseVersion", m.getBaseVersion());
        }
        line(sb, "baseTarSize", Long.toString(m.getBaseFingerprint().getSize()));
        line(sb, "baseTarSha256", m.getBaseFingerprint().getSha256());
        line(sb, "patchedTarSize", Long.toString(m.getPatchedFingerprint().getSize()));
        line(sb, "patchedTarSha256", m.getPatchedFingerprint().getSha256());
        if (m.getGenerator() != null) {
            line(sb, "generator", m.getGenerator());
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder sb, String key, String value) {
        sb.append(key).append('=').append(value).append('\n');
    }

    private static PatchMetadata decodeHeader(byte[] header, String description, Path file) throws PatchCorruptException {
        Map<String, String> values = new HashMap<>();
        for (String line : new String(header, StandardCharsets.UTF_8).split("\n")) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new PatchCorruptException("Malformed patch header line \"" + line + "\": " + description, file, null);
            }
            values.put(line.substring(0, eq), line.substring(eq + 1));
        }
        try {
            int tarFormat = Integer.parseInt(required(values, "tarFormat", description, file));
            if (tarFormat != DeterministicTar.FORMAT) {
                throw new PatchCorruptException("Patch uses archive format " + tarFormat + " but this library builds format "
                        + DeterministicTar.FORMAT + ", update the library: " + description, file, null);
            }
            BaseFingerprint base = BaseFingerprint.of(
                    Long.parseLong(required(values, "baseTarSize", description, file)),
                    required(values, "baseTarSha256", description, file));
            BaseFingerprint patched = BaseFingerprint.of(
                    Long.parseLong(required(values, "patchedTarSize", description, file)),
                    required(values, "patchedTarSha256", description, file));
            return new PatchMetadata(tarFormat, required(values, "patchName", description, file),
                    values.getOrDefault("patchVersion", ""),
                    values.get("baseName"), values.get("baseVersion"), base, patched, values.get("generator"));
        } catch (IllegalArgumentException e) { // also covers NumberFormatException
            throw new PatchCorruptException("Invalid patch header value: " + e.getMessage() + ": " + description, file, e);
        }
    }

    private static String required(Map<String, String> values, String key, String description, Path file)
            throws PatchCorruptException {
        String value = values.get(key);
        if (value == null) {
            throw new PatchCorruptException("Patch header is missing \"" + key + "\": " + description, file, null);
        }
        return value;
    }
}
