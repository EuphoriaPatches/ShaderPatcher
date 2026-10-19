package com.euphoriapatches.shaderpatcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Helpers shared by the tests. */

// Tests have been partially AI generated, they have been manually reviewed tho
final class TestSupport {

    private TestSupport() {
    }

    static Path write(Path root, String relative, String content) throws IOException {
        return writeBytes(root, relative, content.getBytes(StandardCharsets.UTF_8));
    }

    static Path writeBytes(Path root, String relative, byte[] content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        return file;
    }

    /** A small pack that looks like a shader pack. */
    static Path sampleBase(Path dir) throws IOException {
        write(dir, "shaders/pack.json", "{\"version\": \"1.0\"}\n");
        write(dir, "shaders/lib/common.glsl", "#define SHADER_STYLE 1\n#define FOO 1\n");
        write(dir, "shaders/final.fsh", "void main() { /* base */ }\n");
        write(dir, "shaders/lang/en_US.lang", "option.A=A\noption.B=B\n");
        Files.createDirectories(dir.resolve("shaders/empty"));
        return dir;
    }

    /** The same pack after the patch author edited it: one file changed, one added, one removed. */
    static Path sampleEdited(Path dir) throws IOException {
        write(dir, "shaders/pack.json", "{\"version\": \"1.0\"}\n");
        write(dir, "shaders/lib/common.glsl", "#define SHADER_STYLE 1\n#define FOO 2\n#define BAR 3\n");
        write(dir, "shaders/final.fsh", "void main() { /* base */ }\n");
        write(dir, "shaders/lib/misc/myFile.glsl", "// Sample Patch 2.0\n");
        Files.createDirectories(dir.resolve("shaders/empty"));
        return dir;
    }

    static PatchSpec spec() {
        return PatchSpec.builder().patchName("SamplePatch").patchVersion("_2.0")
                .baseName("SampleBase").baseVersion("_r1").build();
    }

    static void zip(Path dir, Path zipFile) throws IOException {
        final List<Path> all = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                if (!d.equals(dir)) {
                    all.add(d);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                all.add(f);
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(all);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (Path p : all) {
                String name = dir.relativize(p).toString().replace('\\', '/');
                if (Files.isDirectory(p)) {
                    out.putNextEntry(new ZipEntry(name + "/"));
                    out.closeEntry();
                } else {
                    out.putNextEntry(new ZipEntry(name));
                    out.write(Files.readAllBytes(p));
                    out.closeEntry();
                }
            }
        }
    }

    static void assertSameTree(Path expected, Path actual) throws IOException {
        List<String> a = listRelative(expected);
        List<String> b = listRelative(actual);
        assertEquals(a, b, "same entries");
        for (String rel : a) {
            Path pa = expected.resolve(rel);
            Path pb = actual.resolve(rel);
            assertEquals(Files.isDirectory(pa), Files.isDirectory(pb), "type of " + rel);
            if (!Files.isDirectory(pa)) {
                assertArrayEquals(Files.readAllBytes(pa), Files.readAllBytes(pb), "content of " + rel);
            }
        }
    }

    static List<String> listRelative(Path root) throws IOException {
        final List<String> out = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                if (!d.equals(root)) {
                    out.add(root.relativize(d).toString().replace('\\', '/') + "/");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                out.add(root.relativize(f).toString().replace('\\', '/'));
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(out);
        return out;
    }

    /** Runs code while capturing everything written to System.out / System.err. */
    static String captureStandardStreams(Callable<?> code) throws Exception {
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(buffer, true, "UTF-8");
        System.setOut(capture);
        System.setErr(capture);
        try {
            code.call();
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Generates a patch for the sample packs into {@code out} and returns the file. */
    static Path generateSamplePatch(Path work, Path out) throws Exception {
        Path base = sampleBase(work.resolve("gen-base"));
        Path edited = sampleEdited(work.resolve("gen-edited"));
        return com.euphoriapatches.shaderpatcher.generate.PatchGenerator.generate(
                com.euphoriapatches.shaderpatcher.generate.GenerateRequest.builder(spec())
                        .base(base).edited(edited).addOutputDir(out).build()).getPatchFiles().get(0);
    }
}
