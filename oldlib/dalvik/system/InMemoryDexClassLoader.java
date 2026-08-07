package oldlib.dalvik.system;

import java.nio.ByteBuffer;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public final class InMemoryDexClassLoader {

public static ClassLoader InMemoryDexClassLoader(ByteBuffer[] buffers, ClassLoader parent) {
        try {
            int totalSize = 0;
            for (ByteBuffer buf : buffers) totalSize += buf.remaining();
            byte[] dexBytes = new byte[totalSize];
            int offset = 0;
            for (ByteBuffer buf : buffers) {
                int len = buf.remaining();
                buf.get(dexBytes, offset, len);
                offset += len;
            }

            Class<?> dexFileClass = DexFile.class;
            Method openDexFileMethod = dexFileClass.getDeclaredMethod("openDexFile", byte[].class);
            openDexFileMethod.setAccessible(true);
            Object cookie = openDexFileMethod.invoke(null, dexBytes);

            DexClassLoader InMemoryDexClassLoader = new DexClassLoader("", null, null, parent);
            Field pathListField = BaseDexClassLoader.class.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(InMemoryDexClassLoader);

            Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] dexElements = (Object[]) dexElementsField.get(pathList);

            Field dexFileField = dexElements[0].getClass().getDeclaredField("dexFile");
            dexFileField.setAccessible(true);
            Object dexFileObj = dexFileField.get(dexElements[0]);

            Field mCookieField = dexFileClass.getDeclaredField("mCookie");
            mCookieField.setAccessible(true);
            mCookieField.set(dexFileObj, cookie);

            return InMemoryDexClassLoader;
        } catch (Throwable e) {
            return parent;
        }
                  }
