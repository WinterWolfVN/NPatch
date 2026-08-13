package oldlib.dalvik.system;

import java.nio.ByteBuffer;

public final class InMemoryDexClassLoader extends ClassLoader {

    private final long cookie;

    public InMemoryDexClassLoader(
            ByteBuffer buffer,
            ClassLoader parent) {
        this(new ByteBuffer[]{buffer}, parent);
    }

    public InMemoryDexClassLoader(
            ByteBuffer[] buffers,
            ClassLoader parent) {
        super(parent);

        if (buffers == null || buffers.length == 0)
            throw new NullPointerException("buffers");

        for (ByteBuffer b : buffers) {
            if (b == null)
                throw new NullPointerException("buffer");
            if (!b.isDirect())
                throw new IllegalArgumentException(
                        "DEX buffer must be direct");
            if (!b.hasRemaining())
                throw new IllegalArgumentException(
                        "DEX buffer is empty");
        }

        cookie = nativeOpen(buffers, parent);

        if (cookie == 0)
            throw new RuntimeException(
                    "Unable to open DEX from memory");
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {

        Class<?> result =
                nativeFindClass(
                        name,
                        cookie,
                        this);

        if (result == null)
            throw new ClassNotFoundException(name);

        return result;
    }

    private static native long nativeOpen(
            ByteBuffer[] buffers,
            ClassLoader parent);

    private static native Class<?> nativeFindClass(
            String name,
            long cookie,
            ClassLoader loader);
    }
