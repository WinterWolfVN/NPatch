package oldlib.dalvik.system;

import java.nio.ByteBuffer;
import dalvik.system.DexFile;
import dalvik.system.DexPathList;

public final class InMemoryDexClassLoader extends ClassLoader {
    private final DexFile[] dexFile;

    public InMemoryDexClassLoader(ByteBuffer[] buffer, ClassLoader parent) {
        super(parent);
        this.dexFile = CreateDexFile(buffer);
        if (buffer == null) {
            throw new NullPointerException("buffer == null");
        }
    }

    public InMemoryDexClassLoader(DexFile dexFile, ClassLoader parent) {
        super(parent);
        this.dexFile = DexFile[] { dexFile };
    }

    public static ByteBuffer ConvertByteToByteBuffer(byte[] dexFile) {
        if (dexFile == null) {
            throw new NullPointerException("dex == null");
        }
        return ByteBuffer.wrap(dex);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String binaryName = name.replace('.', '/');
        for (DexFile dex : dexFile) {
            Class<?> result = dex.loadClassBinaryName(binaryName, this, null);
            if (result != null) {
                return result;
            }
        }
        throw new ClassNotFoundException(name);
    }
    
    private static native DexFile[] CreateDexFile (ByteBuffer[] buffer);
    private static native Class<?> NativeBridge(String name, ClassLoader loader);
}
