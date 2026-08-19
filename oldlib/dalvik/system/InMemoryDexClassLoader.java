package oldlib.dalvik.system;

import android.os.Build;
import java.nio.ByteBuffer;

public final class InMemoryDexClassLoader
        extends BaseDexClassLoader {
                
    public static ClassLoader create(ByteBuffer buffer, ClassLoader parent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {                
                Class<?> systemClass = Class.forName("dalvik.system.InMemoryDexClassLoader");
                Constructor<?> constructor = systemClass.getConstructor(ByteBuffer.class, ClassLoader.class);
                return (ClassLoader) constructor.newInstance(buffer, parent);
            } catch (Exception e) {
                e.printStackTrace();                
            }
        }                
        return new InMemoryDexClassLoader(buffer, parent);
}
                
    public InMemoryDexClassLoader(
            ByteBuffer[] dexBuffers,
            ClassLoader parent) {
        super(dexBuffers, parent);
    }

    public InMemoryDexClassLoader(
            ByteBuffer dexBuffer,
            ClassLoader parent) {
        this(new ByteBuffer[]{dexBuffer}, parent);
    }
        }
