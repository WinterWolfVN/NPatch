package oldlib.dalvik.system;

import dalvik.system.DexFile;
import java.lang.reflect.Method;

public final class InMemoryDexClassLoader extends ClassLoader {
    private final DexFile dexFile;

    public InMemoryDexClassLoader(ByteBuffer buffer, ClassLoader parent) {
        super(parent);
        this.dexFile = CreateDexFile(buffer);
    
    public InMemoryDexClassLoader(DexFile dexFile, ClassLoader parent) {
        super(parent);
        this.dexFile = dexFile;

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        try {
            Method method = DexFile.class.getDeclaredMethod("loadClassBinaryName", String.class, ClassLoader.class, java.util.List.class);
            method.setAccessible(true);
            Class<?> result = (Class<?>) method.invoke(dexFile, name.replace('.', '/'), this, null);
            if (result != null) {
                return result;
            }
        } catch (ReflectiveOperationException e) {
            throw new ClassNotFoundException(name, e);
        }
        throw new ClassNotFoundException(name);
    }

    private static native DexFile CreateDexFile(ByteBuffer buffer);
    private static native Class<?> NativeBridge(String name, ClassLoader loader);
}
