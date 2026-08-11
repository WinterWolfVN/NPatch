package oldlib.dalvik.system;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import dalvik.system.DexClassLoader;

public final class InMemoryDexClassLoader extends DexClassLoader {

    public InMemoryDexClassLoader(
            ByteBuffer[] buffers,
            ClassLoader parent) {
        this(createDexPath(buffers), parent);
    }

    public InMemoryDexClassLoader(
            ByteBuffer buffer,
            ClassLoader parent) {
        this(new ByteBuffer[]{buffer}, parent);
    }

    private InMemoryDexClassLoader(
            DexPath path,
            ClassLoader parent) {
        super(
                path.dex.getAbsolutePath(),
                path.optimized.getAbsolutePath(),
                null,
                parent
        );
    }

    private static DexPath createDexPath(
            ByteBuffer[] buffers) {

        if (buffers == null || buffers.length == 0) {
            throw new NullPointerException("buffers");
        }

        try {
            File root = new File(
                    System.getProperty("java.io.tmpdir"),
                    "dex"
            );

            if (!root.exists() && !root.mkdirs()) {
                throw new IOException(
                        "Cannot create optimized directory"
                );
            }

            File dex = File.createTempFile(
                    "memory-",
                    ".dex",
                    root
            );

            try (FileOutputStream out =
                         new FileOutputStream(dex)) {

                byte[] data = new byte[8192];

                for (ByteBuffer buffer : buffers) {
                    if (buffer == null) {
                        throw new NullPointerException(
                                "buffer"
                        );
                    }

                    ByteBuffer copy = buffer.slice();

                    while (copy.hasRemaining()) {
                        int length = Math.min(
                                copy.remaining(),
                                data.length
                        );

                        copy.get(data, 0, length);
                        out.write(data, 0, length);
                    }
                }
            }

            File optimized = new File(
                    root,
                    dex.getName() + ".odex"
            );

            return new DexPath(
                    dex,
                    optimized
            );

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static final class DexPath {
        final File dex;
        final File optimized;

        DexPath(
                File dex,
                File optimized) {
            this.dex = dex;
            this.optimized = optimized;
        }
    }
            }
