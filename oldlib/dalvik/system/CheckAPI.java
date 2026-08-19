package oldlib.dalvik.system;

import android.os.Build;
import java.nio.ByteBuffer;
import java.lang.reflect.Constructor;

public class CheckAPI {
    
    public static boolean isAndroidO() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }
    
    public static ClassLoader createClassLoader(ByteBuffer buffer, ClassLoader parent) {
        if (isAndroidO()) {
            try {              
                Class<?> systemClass = Class.forName("dalvik.system.InMemoryDexClassLoader");
                Constructor<?> constructor = systemClass.getConstructor(ByteBuffer.class, ClassLoader.class);
                return (ClassLoader) constructor.newInstance(buffer, parent);
                // Use this if you don't have the new InMemoryDexClassLoader 
                // return new dalvik.system.InMemoryDexClassLoader(buffer, parent);            
            } catch (Exception e) {
                e.printStackTrace();                
            }
        }               
        return new InMemoryDexClassLoader(buffer, parent);
    }    
}
