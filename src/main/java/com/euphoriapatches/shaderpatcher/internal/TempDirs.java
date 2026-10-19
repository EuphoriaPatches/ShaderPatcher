package com.euphoriapatches.shaderpatcher.internal;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Not public API. Temporary folders and robust deletion.
 *
 * @author SpacEagle17
 */
public final class TempDirs {
    private static final int ATTEMPTS = 6;

    private TempDirs() {
    }

    public static Path create(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    /** Deletes a file or a folder tree. */
    public static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                deleteWithRetry(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                deleteWithRetry(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Like {@link #deleteRecursively(Path)} but never throws; for cleanup in {@code finally} blocks. */
    public static void deleteQuietly(Path path) {
        try {
            deleteRecursively(path);
        } catch (IOException ignored) {
            // best effort: a leftover temp folder must not hide the real result or error
        }
    }

    /** Moves a folder to its final name. */
    public static void moveWithRetry(Path from, Path to) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                try {
                    Files.move(from, to, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(from, to);
                }
                return;
            } catch (java.nio.file.FileAlreadyExistsException e) {
                throw e;
            } catch (IOException e) {
                last = e;
                backOff(attempt);
            }
        }
        throw last;
    }

    private static void deleteWithRetry(Path path) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (NoSuchFileException e) {
                return;
            } catch (IOException e) {
                last = e;
                clearReadOnly(path);
                backOff(attempt);
            }
        }
        throw last;
    }

    private static void clearReadOnly(Path path) {
        try {
            Files.setAttribute(path, "dos:readonly", false);
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException ignored) {
            // not a DOS file system, or already writable
        }
    }

    private static void backOff(int attempt) throws IOException {
        if (attempt >= ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(40L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while retrying a file operation");
        }
    }
}
