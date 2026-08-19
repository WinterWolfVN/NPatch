package oldlib.dalvik.system;

import java.nio.ByteBuffer;
import java.io.IOException;

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

private static native Object createCookieWithDirectBuffer(ByteBuffer buf, int start, int end) throws IOException;
private static native Object createCookieWithArray(byte[] buf, int start, int end) throws IOException;
} 
