package oldlib.dalvik.system;

import android.system.ErrnoException;
import dalvik.system.DexPathList.Element;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.List;
import libcore.io.Libcore;

public final class DexFile {
    public static final int DEX2OAT_NEEDED = 1;
    public static final int NO_DEXOPT_NEEDED = 0;
    public static final int PATCHOAT_NEEDED = 2;
    public static final int SELF_PATCHOAT_NEEDED = 3;
    private Object mCookie;
    private final String mFileName;
    private Object mInternalCookie;

    private DexFile(String str, String str2, int i, ClassLoader classLoader, Element[] elementArr) throws IOException {
        if (str2 != null) {
            try {
                String parent = new DexFile(str2).getParent();
                if (Libcore.os.getuid() != Libcore.os.stat(parent).st_uid) {
                    throw new DexFile("Optimized data directory " + parent + " is not owned by the current user. Shared storage cannot protect" + " your application from code injection attacks.");
                }
            } catch (ErrnoException e) {
            }
        }
        this.mCookie = openDexFile(str, str2, i, classLoader, elementArr);
        this.mFileName = str;
    }
   
    private static Class defineClass(String str, ClassLoader classLoader, Object obj, DexFile dexFile, List<Throwable> suppressed) {
        Class result = null;
        try {
            return defineClassNative(str, classLoader, obj, dexFile);
        } catch (NoClassDefFoundError e) {
            if (suppressed == null) {
                return result;
            }
            suppressed.add(e);
            return result;
        } catch (ClassNotFoundException e2) {
            if (suppressed == null) {
                return result;
            }
            suppressed.add(e2);
            return result;
        }
    }

    protected void finalize() throws Throwable {
        try {
            if (this.mInternalCookie == null || closeDexFile(this.mInternalCookie)) {
                this.mInternalCookie = null;
                this.mCookie = null;
                return;
            }
            throw new AssertionError("Failed to close dex file in finalizer.");
        } finally {
            super.finalize();
        }
    }

    DexFile(String str, ClassLoader classLoader, Element[] elementArr) throws IOException {
        this.mCookie = openDexFile(str, null, 0, classLoader, elementArr);
        this.mInternalCookie = this.mCookie;
        this.mFileName = str;
    }
    
    public DexFile(ByteBuffer buf) throws IOException {
        if (buf == null) {
             throw new NullPointerException("buf == null");
        }
        mCookie = openInMemoryDexFile(buf);
        mInternalCookie = mCookie;
        mFileName = null;
    }

    public void close() throws IOException {
        if (this.mInternalCookie != null) {
            if (closeDexFile(this.mInternalCookie)) {
                this.mInternalCookie = null;
            }
            this.mCookie = null;
        }
    }

    private static Object openDexFile(String str, String str2, int i, ClassLoader classLoader, Element[] elementArr) throws IOException {
        String str3 = null;
        String absolutePath = new DexFile(str).getAbsolutePath();
        if (str2 != null) {
            str3 = new DexFile(str2).getAbsolutePath();
        }
        return openDexFileNative(absolutePath, str3, i, classLoader, elementArr);
    }
    
    private static Object openInMemoryDexFile(ByteBuffer buf)
        throws IOException {
        if (buf == null) {
            throw new NullPointerException("buf == null");
        }
        if (buf.isDirect()) {
            return createCookieWithDirectBuffer(
                buf,
                buf.position(),
                buf.limit());
        } else {
        return createCookieWithArray(
                buf.array(),
                buf.position(),
                buf.limit());
        }
    }

    public DexFile(File file) throws IOException {
        this(file.getPath());
    }

    DexFile(File file, ClassLoader classLoader, Element[] elementArr) throws IOException {
        this(file.getPath(), classLoader, elementArr);
    }

    public DexFile(String str) throws IOException {
        this(str, null, null);
    }

    public Class loadClass(String str, ClassLoader classLoader) {
        return loadClassBinaryName(str.replace('.', '/'), classLoader, null);
    }

    public static DexFile loadDex(String str, String str2, int i) throws IOException {
        return loadDex(str, str2, i, null, null);
    }

    static DexFile loadDex(String str, String str2, int i, ClassLoader classLoader, Element[] elementArr) throws IOException {
        return new DexFile(str, str2, i, classLoader, elementArr);
    }

    public Enumeration<String> entries() {
        return new DFEnum(this, this);
    }

    public String getName() {
        return this.mFileName;
    }

    boolean isBackedByOatFile() {
        return isBackedByOatFile(this.mCookie);
    }

    public Class loadClassBinaryName(String str, ClassLoader classLoader, List<Throwable> suppressed) {
        return defineClass(str, classLoader, this.mCookie, this, suppressed);
    }

    public String toString() {
        return getName();
    }

    private static native Object createCookieWithDirectBuffer(ByteBuffer buf, int start, int end) throws IOException;
    private static native Object createCookieWithArray(byte[] buf, int start, int end) throws IOException;
    private static native boolean closeDexFile(Object obj);
    private static native Class defineClassNative(String str, ClassLoader classLoader, Object obj, DexFile dexFile) throws ClassNotFoundException, NoClassDefFoundError;
    private static native String[] getClassNameList(Object obj);
    public static native String getDexFileOutputPath(String str, String str2) throws FileNotFoundException;
    public static native String getDexFileStatus(String str, String str2) throws FileNotFoundException;
    public static native int getDexOptNeeded(String str, String str2, String str3, boolean z) throws FileNotFoundException, IOException;
    public static native String getNonProfileGuidedCompilerFilter(String str);
    private static native boolean isBackedByOatFile(Object obj);
    public static native boolean isDexOptNeeded(String str) throws FileNotFoundException, IOException;
    public static native boolean isProfileGuidedCompilerFilter(String str);
    public static native boolean isValidCompilerFilter(String str);
    private static native Object openDexFileNative(String str, String str2, int i, ClassLoader classLoader, Element[] elementArr);
}
                                   
