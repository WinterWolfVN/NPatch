package oldlib.dalvik.system;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.List;

public final class DexFile {
  private Object mCookie;
  private Object mInternalCookie;
  private String mFileName;
    
DexFile(ByteBuffer buf) throws IOException {
    if (buf == null) {
        throw new NullPointerException("buf == null");
    }
    mCookie = openInMemoryDexFile(buf);
    mInternalCookie = mCookie;
    mFileName = null;
}

public static DexFile loadDex(String path, String optimizedDirectory, int flags) throws IOException {
    Object cookie = openDexFile(path, optimizedDirectory, flags);
    return new DexFile(cookie);
}

private DexFile(Object cookie) {
    this.mCookie = cookie;
    this.mInternalCookie = cookie;
    this.mFileName = null;
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

public Class<?> loadClass(String name, ClassLoader loader) {
    return loadClassBinaryName(name, loader, null);
  }

public Class<?> loadClassBinaryName(String str, ClassLoader classLoader, List<Throwable> suppressed) {
    return defineClass(str, classLoader, this.mCookie, this, suppressed);
  }
  
private static native Object createCookieWithDirectBuffer(ByteBuffer buf, int start, int end) throws IOException;
private static native Object createCookieWithArray(byte[] buf, int start, int end) throws IOException;
private static native Object openDexFile(String path, String optimizedDirectory, int flags) throws IOException;
private static native Class<?> defineClass(String name, ClassLoader loader, Object cookie, DexFile dexFile, List<Throwable> suppressed);
}
