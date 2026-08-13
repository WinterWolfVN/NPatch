package oldlib.dalvik.system;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import dalvik.system.DexClassLoader;

public final class InMemoryDexClassLoader extends DexClassLoader {

    public InMemoryDexClassLoader(
            ByteBuffer buffer,
            ClassLoader parent) {
        this(new ByteBuffer[]{buffer}, parent);
    }

    public InMemoryDexClassLoader(
            ByteBuffer[] buffers,
            ClassLoader parent) {
        this(createDexPath(buffers), parent);
    }

    private InMemoryDexClassLoader(
            DexPath path,
            ClassLoader parent) {
        super(
                path.dexFile.getAbsolutePath(),
                path.optimizedDirectory.getAbsolutePath(),
                null,
                parent
        );
    }

    private static DexPath createDexPath(ByteBuffer[] buffers) {
        if (buffers == null || buffers.length == 0) {
            throw new NullPointerException("buffers");
        }

        try {
            File root = new File(
                    System.getProperty("java.io.tmpdir"),
                    "oldlib-dex"
            );

            if (!root.exists() && !root.mkdirs()) {
                throw new IOException(
                        "Unable to create dex directory: "
                                + root
                );
            }

            File dexFile = File.createTempFile(
                    "memory-",
                    ".dex",
                    root
            );

            try (FileOutputStream out =
                         new FileOutputStream(dexFile)) {

                byte[] tmp = new byte[8192];

                for (ByteBuffer buffer : buffers) {
                    if (buffer == null) {
                        throw new NullPointerException("buffer");
                    }
                    
                    ByteBuffer copy = buffer.duplicate();

                    while (copy.hasRemaining()) {
                        int count = Math.min(
                                copy.remaining(),
                                tmp.length
                        );

                        copy.get(tmp, 0, count);
                        out.write(tmp, 0, count);
                    }
                }

                out.flush();
            }

            return new DexPath(
                    dexFile,
                    root
            );

        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to create temporary dex",
                    e
            );
        }
    }

    private static final class DexPath {
        final File dexFile;
        final File optimizedDirectory;

        DexPath(
                File dexFile,
                File optimizedDirectory) {
            this.dexFile = dexFile;
            this.optimizedDirectory = optimizedDirectory;
        }
    }
        }
