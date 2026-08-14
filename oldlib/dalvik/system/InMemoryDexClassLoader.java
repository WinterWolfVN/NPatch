package oldlib.dalvik.system;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

public final class InMemoryDexClassLoader
        extends ClassLoader {

    private final DexClassLoader delegate;
    private final File dexFile;

    public InMemoryDexClassLoader(
            ByteBuffer buffer,
            ClassLoader parent) {

        this(new ByteBuffer[]{buffer}, parent);
    }

    public InMemoryDexClassLoader(
            ByteBuffer[] buffers,
            ClassLoader parent) {

        super(parent);

        if (buffers == null || buffers.length == 0) {
            throw new NullPointerException("buffers");
        }

        try {
            File root = getCacheRoot();
            cleanupOldFiles(root);

            File optimizedDir =
                    new File(root, "optimized");

            if (!optimizedDir.exists()
                    && !optimizedDir.mkdirs()
                    && !optimizedDir.isDirectory()) {
                throw new IOException(
                        "Unable to create optimized directory: "
                                + optimizedDir);
            }

            dexFile = new File(
                    root,
                    "dex_" + UUID.randomUUID().toString()
                            + ".dex");

            writeDex(buffers, dexFile);

            delegate = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    optimizedDir.getAbsolutePath(),
                    null,
                    parent
            );

        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to create DexClassLoader",
                    e
            );
        }
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {

        return delegate.loadClass(name);
    }

    private static File getCacheRoot()
            throws IOException {

        String cache =
                System.getProperty("java.io.tmpdir");

        if (cache == null || cache.length() == 0) {
            throw new IOException(
                    "Temporary directory unavailable");
        }

        File root =
                new File(cache, "oldlib-dex");

        if (!root.exists()
                && !root.mkdirs()
                && !root.isDirectory()) {
            throw new IOException(
                    "Unable to create cache directory: "
                            + root);
        }

        return root;
    }

    private static void writeDex(
            ByteBuffer[] buffers,
            File output)
            throws IOException {

        FileOutputStream out =
                new FileOutputStream(output);

        try {
            byte[] temp = new byte[8192];

            for (ByteBuffer source : buffers) {

                if (source == null) {
                    throw new NullPointerException(
                            "buffer");
                }

                ByteBuffer buffer =
                        source.duplicate();

                while (buffer.hasRemaining()) {

                    int count =
                            Math.min(
                                    buffer.remaining(),
                                    temp.length
                            );

                    buffer.get(
                            temp,
                            0,
                            count
                    );

                    out.write(
                            temp,
                            0,
                            count
                    );
                }
            }

            out.flush();

        } finally {
            out.close();
        }
    }

    private static void cleanupOldFiles(
            File root) {

        File[] files =
                root.listFiles();

        if (files == null) {
            return;
        }

        long now =
                System.currentTimeMillis();

        long maxAge =
                24L * 60L * 60L * 1000L;

        for (File file : files) {

            if (!file.isFile()) {
                continue;
            }

            String name =
                    file.getName();

            if (!name.startsWith("dex_")
                    || !name.endsWith(".dex")) {
                continue;
            }

            if (now - file.lastModified()
                    > maxAge) {
                // Best-effort cleanup.
                file.delete();
            }
        }
    }
            }
