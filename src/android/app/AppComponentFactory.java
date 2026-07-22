package android.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import dalvik.system.PathClassLoader;
import java.lang.reflect.Field;

public class AppComponentFactory {
    public static AppComponentFactory sInstance = new AppComponentFactory();

    public Application instantiateApplication(ClassLoader cl, String className) {
        try { return (Application) cl.loadClass(className).newInstance(); } 
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public Activity instantiateActivity(ClassLoader cl, String className, Intent intent) {
        try { return (Activity) cl.loadClass(className).newInstance(); } 
        catch (Throwable t) { throw new RuntimeException(t); }        
    }                                                            
}

final class AppEnvironment {
    private static ClassLoader loader;

    static void init(ClassLoader cl, Context c) {
        loader = new PathClassLoader(c.getApplicationInfo().sourceDir, cl);
        hook();
    }

    static ClassLoader cl(ClassLoader original) { 
        return loader != null ? loader : original; 
    }

    private static void hook() {
        try {
            Object at = Class.forName("android.app.ActivityThread").getDeclaredMethod("currentActivityThread").invoke(null);
            Field f = at.getClass().getDeclaredField("mInstrumentation");
            f.setAccessible(true);
            f.set(at, new Proxy((Instrumentation) f.get(at)));
        } catch (Throwable ignored) {}
    }

    static final class Proxy extends Instrumentation {
        private final Instrumentation base;
        Proxy(Instrumentation base) { this.base = base; }

        @Override
        public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            return AppComponentFactory.sInstance.instantiateActivity(AppEnvironment.cl(cl != null ? cl : base.getContext().getClassLoader()), className, intent);
        }

        @Override
        public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            return AppComponentFactory.sInstance.instantiateApplication(AppEnvironment.cl(cl != null ? cl : context.getClassLoader()), className);
        }
    }
    }
