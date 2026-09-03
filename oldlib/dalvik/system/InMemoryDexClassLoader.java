package oldlib.dalvik.system;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import dalvik.system.DexFile;

public final class InMemoryDexClassLoader extends ClassLoader {
    private final DexFile[] dexFile;

    public InMemoryDexClassLoader(ByteBuffer[] buffer, ClassLoader parent) {
        super(parent);
        if (buffer == null) {
           throw new NullPointerException("buffer == null");
        }
        int count = buffer.length;
        int[] positions = new int[count];
        int[] limits = new int[count];
        boolean[] hasArrays = new boolean[count];
        Object[] arrays = new Object[count];
        int[] arrayOffsets = new int[count];
        for (int i = 0; i < count; i++) {
            if (buffer[i] == null) {
               throw new NullPointerException("buffer[" + i + "] == null");
            }
            positions[i] = buffer[i].position();
            limits[i] = buffer[i].limit();
            hasArrays[i] = buffer[i].hasArray();
            if (hasArrays[i]) {
               arrays[i] = buffer[i].array();
               arrayOffsets[i] = buffer[i].arrayOffset();
            }
        }
        this.dexFile = CreateDexFile(buffer, positions, limits, hasArrays, arrays, arrayOffsets);
        setDexElements(dexFile);
    }
    
    public InMemoryDexClassLoader(ByteBuffer buffer, ClassLoader parent) {
        this(new ByteBuffer[]{buffer}, parent);
    }

    public InMemoryDexClassLoader(DexFile dexFile, ClassLoader parent) {
        super(parent);
        this.dexFile = new DexFile[] { dexFile };
    }

    public static ByteBuffer ConvertByteToByteBuffer(byte[] dexFile) {
        if (dexFile == null) {
           throw new NullPointerException("dexFile == null");
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(dexFile.length);
        buffer.put(dexFile);
        buffer.position(0);
        buffer.limit(dexFile.length);
        return buffer;
    }
    
   private void setDexElements(DexFile[] dexFile) {
    try {
        Class<?> baseClass = Class.forName("dalvik.system.BaseDexClassLoader");
        Field pathListField = baseClass.getDeclaredField("pathList");
        pathListField.setAccessible(true);
        Object pathList = pathListField.get(this);
        Class<?> pathListClass = Class.forName("dalvik.system.DexPathList");
        Field dexElementsField = pathListClass.getDeclaredField("dexElements");
        dexElementsField.setAccessible(true);
        Object oldElements = dexElementsField.get(pathList);
        Class<?> elementClass = Class.forName("dalvik.system.DexPathList$Element");
        Constructor<?> elementConstructor = elementClass.getDeclaredConstructor(File.class, boolean.class, File.class, DexFile.class);
        elementConstructor.setAccessible(true);
        int oldLength = Array.getLength(oldElements);
        int newLength = dexFile.length;
        Object mergedElements = Array.newInstance(elementClass, oldLength + newLength);
        System.arraycopy(oldElements, 0, mergedElements, 0, oldLength);
        for (int i = 0; i < newLength; i++) {
            DexFile dexFile = dexFile[i];
            if (dexFile == null) {
                throw new NullPointerException("dexFile[" + i + "] == null");
            }
            Object element = elementConstructor.newInstance(null, false, null, dexFile);
            Array.set(mergedElements, oldLength + i, element);
        }
        dexElementsField.set(pathList, mergedElements);
    } catch (Exception e) {
        throw new RuntimeException("Unable to merge in-memory dex elements", e);
    }
}

    private static native DexFile[] CreateDexFile(ByteBuffer[] buffers, int[] positions, int[] limits, boolean[] hasArrays, Object[] arrays, int[] arrayOffsets);
            }
