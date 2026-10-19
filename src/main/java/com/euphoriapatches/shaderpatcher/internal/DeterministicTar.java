package com.euphoriapatches.shaderpatcher.internal;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Not public API. Builds the deterministic TAR archive that fingerprints and patches are based on.
 *
 * <p>Do not change how the archive is written (modes, times...). Every patch ever generated depends on these exact bytes.
 * A change needs a new {@link #FORMAT} number.
 *
 * @author SpacEagle17
 */
public final class DeterministicTar {
    /** Version number of the archive rules, stored in every patch as {@code tarFormat}. */
    public static final int FORMAT = 1;

    private DeterministicTar() {
    }

    /** Archives the contents of a folder. */
    public static void write(Path folder, Path tarFile, final Predicate<String> exclude) throws IOException {
        // A folder given as a symbolic link would otherwise be archived as just the link
        final Path sourceDir = folder.toRealPath();
        final List<Path> entries = new ArrayList<>();
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(sourceDir)) {
                    entries.add(dir);
                    return FileVisitResult.CONTINUE;
                }
                if (isSkipped(entryName(sourceDir, dir), exclude)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                entries.add(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!isSkipped(entryName(sourceDir, file), exclude)) {
                    entries.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        entries.sort(Comparator.comparing(Path::toUri));

        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new BufferedOutputStream(Files.newOutputStream(tarFile)))) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_ERROR);
            for (Path path : entries) {
                addEntry(tar, sourceDir, path);
            }
            tar.finish();
        }
    }

    private static boolean isSkipped(String name, Predicate<String> exclude) {
        return isVcsEntry(name) || (exclude != null && exclude.test(name));
    }

    /** Version control folders are only skipped at the top level, exactly as the original archiver did. */
    public static boolean isVcsEntry(String name) {
        for (String vcs : new String[]{".git", ".svn", ".hg"}) {
            if (name.equals(vcs) || name.startsWith(vcs + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String entryName(Path sourceDir, Path path) {
        // '/' on every platform, which also fixes archives built on Windows
        return sourceDir.relativize(path).toString().replace(File.separatorChar, '/');
    }

    private static void addEntry(TarArchiveOutputStream tar, Path sourceDir, Path path) throws IOException {
        String name = entryName(sourceDir, path);
        boolean regularFile = Files.isRegularFile(path);

        TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), name);
        entry.setModTime(0);
        entry.setSize(regularFile ? Files.size(path) : 0);
        entry.setIds(0, 0);
        entry.setNames("", "");
        entry.setMode(TarArchiveEntry.DEFAULT_FILE_MODE);

        tar.putArchiveEntry(entry);
        if (regularFile) {
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    tar.write(buffer, 0, read);
                }
            }
        }
        tar.closeArchiveEntry();
    }
}
