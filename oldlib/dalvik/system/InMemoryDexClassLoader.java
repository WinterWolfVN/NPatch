package oldlib.dalvik.system;

import java.nio.ByteBuffer;

public final class InMemoryDexClassLoader extends ClassLoader {

    private final Object cookie;

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

        for (ByteBuffer b : buffers) {
            if (b == null) {
                throw new NullPointerException("buffer");
            }

            if (!b.isDirect()) {
                throw new IllegalArgumentException(
                        "buffer must be a direct ByteBuffer");
            }

            if (!b.hasRemaining()) {
                throw new IllegalArgumentException(
                        "buffer is empty");
            }
        }

        cookie = nativeOpen(buffers, parent);

        if (cookie == null) {
            throw new RuntimeException(
                    "Unable to create in-memory DEX cookie");
        }
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {

        Class<?> c = nativeFindClass(
                name.replace('.', '/'),
                cookie,
                this
        );

        if (c == null) {
            throw new ClassNotFoundException(name);
        }

        return c;
    }

    private static native Object nativeOpen(
            ByteBuffer[] buffers,
            ClassLoader parent);

    private static native Class<?> nativeFindClass(
            String name,
            Object cookie,
            ClassLoader loader);
            }
