package org.lsposed.npatch.metaloader;

import android.app.LoadedApk;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Method;

public final class AppComponentFactoryStub {    
    private static final String TAG = "NPatch-Metaloader";    
    
    public void run(Context context) {            
        android.util.Log.e(TAG, "Provider has been activated");        

        try {
            ClassLoader cl = AppComponentFactoryStub.class.getClassLoader();
            Class<?> cls = Class.forName(
                    "org.lsposed.npatch.metaloader.LSPAppComponentFactoryStub",
                    true,
                    context.getClassLoader()                    
            );            
            android.util.Log.e(TAG, "LSPAppComponentFactoryStub has been activated");          
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Bug"  + android.util.Log.getStackTraceString(t));
        }
    }
}    
