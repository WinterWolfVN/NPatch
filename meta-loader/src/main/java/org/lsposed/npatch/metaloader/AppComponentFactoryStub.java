package org.lsposed.npatch.metaloader;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Method;

public final class AppComponentFactoryStub extends ContentProvider {    
    private static final String TAG = "NPatch-Metaloader";
    private static volatile boolean sDone;

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        android.util.Log.e(TAG, "Provider has been activated");
        if (sDone) return;
        sDone = true;

        try {
            Class<?> cls = Class.forName(
                    "org.lsposed.npatch.metaloader.LSPAppComponentFactoryStub",
                    false,
                    context.getClassLoader()                    
            );
            android.util.Log.e(TAG, "LSPAppComponentFactoryStub has been activated");
            Method m = cls.getDeclaredMethod("bootstrap");
            m.setAccessible(true);
            m.invoke(null);
            android.util.Log.e(TAG, "Bootstrap has been activated");
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Bug"  + android.util.Log.getStackTraceString(t));
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
            }
