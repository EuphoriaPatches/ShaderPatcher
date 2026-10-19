package com.euphoriapatches.shaderpatcher.internal;

import com.euphoriapatches.shaderpatcher.exception.InvalidPackException;
import com.euphoriapatches.shaderpatcher.exception.PackIoException;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.util.function.Consumer;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;

/**
 * Not public API. Unpacks zip packs and the patched TAR into folders, and copies folders.
 *
 * @author SpacEagle17
 */
public final class PackExtractor {

    private PackExtractor() {
    }

    /**
     * Unpacks a zip file. Extraction stops with an {@link InvalidPackException} once more than {@code maxBytes} would be
     * written (each entry counts 512 bytes for its TAR header plus its content), which defuses zip bombs.
     * Top-level {@code .git}, {@code .svn} and {@code .hg} are not unpacked, the archive ignores them anyway.
     */
    @SuppressWarnings("deprecation") // the builder-based ZipFile factory only exists in newer commons-compress versions
    public static void extractZip(Path zip, Path target, Consumer<String> log, long maxBytes) throws ShaderPatcherException {
        ZipFile opened;
        try {
            opened = new ZipFile(zip.toFile());
        } catch (IOException e) {
            // not a zip at all, damaged, or unreadable
            throw new InvalidPackException("Not a readable zip file: " + zip + " (" + e.getMessage() + ")", zip, e);
        }

        Path root = target.toAbsolutePath().normalize();
        Budget budget = new Budget(maxBytes);
        try (ZipFile zipFile = opened) {
            Files.createDirectories(root);
            int written = 0;
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                try (InputStream in = entry.isDirectory() ? null : zipFile.getInputStream(entry)) {
                    written += writeEntry(root, entry, in, zip, log, budget);
                }
            }
            log.accept("Extracted " + written + " files from " + zip.getFileName());
        } catch (IOException e) {
            throw new PackIoException("Failed to extract " + zip + " to " + root + ": " + e.getMessage(), zip, e);
        }
    }

    /** Unpacks a TAR stream (the patched archive). */
    public static void extractTar(InputStream tarBytes, Path target, Path source, Consumer<String> log)
            throws ShaderPatcherException {
        Path root = target.toAbsolutePath().normalize();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new BufferedInputStream(tarBytes))) {
            Files.createDirectories(root);
            Budget budget = new Budget(Long.MAX_VALUE); // the patched archive is verified against its hash beforehand
            int written = 0;
            ArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                written += writeEntry(root, entry, tar, source, log, budget);
            }
            log.accept("Extracted " + written + " files from the patched archive");
        } catch (IOException e) {
            throw new PackIoException("Failed to extract the patched archive to " + root + ": " + e.getMessage(), root, e);
        }
    }

    /** Writes one archive entry below root. Returns 1 if a file was written, otherwise 0. */
    private static int writeEntry(Path root, ArchiveEntry entry, InputStream content, Path source, Consumer<String> log,
                                  Budget budget) throws IOException, InvalidPackException {
        Path out = resolveEntry(root, entry.getName(), source, log);
        if (out == null) {
            return 0;
        }
        budget.charge(512, source); // the TAR header of this entry
        if (entry.isDirectory()) {
            Files.createDirectories(out);
            return 0;
        }
        if (out.equals(root)) {
            throw new InvalidPackException("Archive entry would replace the target folder itself: " + entry.getName(),
                    source, null);
        }
        Files.createDirectories(out.getParent());
        try (OutputStream file = Files.newOutputStream(out)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = content.read(buffer)) != -1) {
                budget.charge(read, source);
                file.write(buffer, 0, read);
            }
        }
        return 1;
    }

    /** Counts what has been written, so that an archive claiming to be tiny but unpacking to gigabytes is stopped. */
    private static final class Budget {
        private long remaining;

        Budget(long limit) {
            this.remaining = limit;
        }

        void charge(long bytes, Path source) throws InvalidPackException {
            remaining -= bytes;
            if (remaining < 0) {
                throw new InvalidPackException("The archive unpacks to more data than the base pack it should be: " + source,
                        source, null);
            }
        }
    }

    /** Copies a folder tree. */
    public static void copyDirectory(final Path folder, final Path target) throws PackIoException {
        try {
            final Path source = folder.toRealPath(); // a symbolic link to a folder is followed
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new PackIoException("Failed to copy " + folder + " to " + target + ": " + e.getMessage(), folder, e);
        }
    }

    private static Path resolveEntry(Path root, String rawName, Path source, Consumer<String> log)
            throws InvalidPackException {
        String name = rawName.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return root; // the archive's own root entry
        }
        if (DeterministicTar.isVcsEntry(name)) {
            return null; // never part of the archive, so never unpacked
        }
        Path resolved;
        try {
            resolved = root.resolve(name).normalize();
        } catch (InvalidPathException e) {
            // a name this operating system cannot represent, for example a ':' on Windows
            log.accept("Skipping entry with an invalid path: " + rawName + " (" + e.getReason() + ")");
            return null;
        }
        if (!resolved.startsWith(root)) {
            throw new InvalidPackException("Archive entry would be written outside the target folder: " + rawName,
                    source, null);
        }
        return resolved;
    }
}
