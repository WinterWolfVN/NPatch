package oldlib.dalvik.system;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.List;

public final class DexFile {
  private Object mCookie;
  private Object mInternalCookie;
  private String mFileName;

public DexFile(ByteBuffer buf) throws IOException {
    if (buf == null) {
        throw new NullPointerException("buf == null");
    }
    mCookie = openInMemoryDexFile(buf);
    mInternalCookie = mCookie;
    mFileName = null;
}

public DexFile(byte[] bytes) throws IOException {
    if (bytes == null) {
        throw new NullPointerException("bytes == null");
    }
    mCookie = createCookieWithArray(bytes, 0, bytes.length);
    if (mCookie == null) {
        throw new IOException("Unable to load dex from byte array");
    }
    mInternalCookie = mCookie;
    mFileName = null;
}

private DexFile(Object cookie) {
    this.mCookie = cookie;
    this.mInternalCookie = cookie;
    this.mFileName = null;
}
  
private static Object openInMemoryDexFile(ByteBuffer buf) throws IOException {
    int start = buf.position();
    int size = buf.remaining();
    int end = start + size;
    if (buf.isDirect()) {
        return createCookieWithDirectBuffer(buf, start, end);
    }
    if (buf.hasArray()) {
        int arrayStart = buf.arrayOffset() + start;
        int arrayEnd = arrayStart + size;
        return createCookieWithArray(buf.array(), arrayStart, arrayEnd);
    }
    byte[] tmp = new byte[size];
    ByteBuffer dup = buf.duplicate();
    dup.get(tmp);
    return createCookieWithArray(tmp, 0, size);
}                                         
 
private static native Object createCookieWithDirectBuffer(ByteBuffer buf, int start, int end) throws IOException;
private static native Object createCookieWithArray(byte[] buf, int start, int end) throws IOException;
}
      
