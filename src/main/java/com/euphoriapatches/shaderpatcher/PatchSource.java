package com.euphoriapatches.shaderpatcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Where the patch file comes from. Use {@link #classpath(Class, String)} for a patch bundled in a jar.
 *
 * @author SpacEagle17
 */
public abstract class PatchSource {

    PatchSource() {
    }

    protected abstract InputStream open() throws IOException;

    public abstract String getLocation();

    public Path getFile() {
        return null;
    }

    /** Opens the patch (used by the library). */
    InputStream openStream() throws IOException {
        InputStream in = open();
        if (in == null) {
            throw new FileNotFoundException(getLocation());
        }
        return in;
    }

    /** A patch bundled as a resource, looked up with the class loader of {@code anchor}. */
    public static PatchSource classpath(final Class<?> anchor, final String resourceName) {
        if (anchor == null || resourceName == null) {
            throw new IllegalArgumentException("anchor and resourceName are required");
        }
        final String name = resourceName.startsWith("/") ? resourceName.substring(1) : resourceName;
        return new PatchSource() {
            @Override
            protected InputStream open() {
                ClassLoader loader = anchor.getClassLoader();
                return loader != null ? loader.getResourceAsStream(name) : ClassLoader.getSystemResourceAsStream(name);
            }

            @Override
            public String getLocation() {
                return "classpath resource " + name + " (via " + anchor.getName() + ")";
            }
        };
    }

    /** A patch stored as a file. */
    public static PatchSource path(final Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file is required");
        }
        return new PatchSource() {
            @Override
            protected InputStream open() throws IOException {
                return Files.newInputStream(file);
            }

            @Override
            public String getLocation() {
                return "file " + file;
            }

            @Override
            public Path getFile() {
                return file;
            }
        };
    }

    /** A patch from any stream factory, for example a resource opened by a mod loader's own API. */
    public static PatchSource stream(final String location, final Callable<? extends InputStream> opener) {
        if (location == null || opener == null) {
            throw new IllegalArgumentException("location and opener are required");
        }
        return new PatchSource() {
            @Override
            protected InputStream open() throws IOException {
                try {
                    return opener.call();
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }

            @Override
            public String getLocation() {
                return location;
            }
        };
    }
}
