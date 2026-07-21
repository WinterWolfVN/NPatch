package android.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import dalvik.system.PathClassLoader;

public final class AppEnvironment {
    private static ClassLoader loader;
    private static Context ctx;
    private static final AppComponentFactory factory = new AppComponentFactory();

    public static void init(ClassLoader cl, Context c) {
        loader = new AppClassLoader(cl, c);
        ctx = c.getApplicationContext();
        hook();
    }

    public static ClassLoader cl(ClassLoader original) {
        return loader != null ? loader : original;
    }

    public static Context ctx() {
        return ctx;
    }

    public static AppComponentFactory factory() {
        return factory;
    }

    private static void hook() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object at = atClass.getDeclaredMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Field f = atClass.getDeclaredField("mInstrumentation");
            f.setAccessible(true);
            f.set(at, new Proxy((Instrumentation) f.get(at)));
        } catch (Throwable ignored) {}
    }

    public static final class AppClassLoader extends PathClassLoader {
        public AppClassLoader(ClassLoader parent, Context c) {
            super(c != null && c.getApplicationInfo() != null ? c.getApplicationInfo().sourceDir : "", parent);
        }
    }

    public static class AppComponentFactory {
        public Application instantiateApplication(ClassLoader cl, String className) {
            try {
                return (Application) AppEnvironment.cl(cl).loadClass(className).newInstance();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        public Activity instantiateActivity(ClassLoader cl, String className, Intent intent) {
            try {
                return (Activity) AppEnvironment.cl(cl).loadClass(className).newInstance();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    public static final class Proxy extends Instrumentation {
        private final Instrumentation base;

        public Proxy(Instrumentation base) {
            this.base = base;
        }

        @Override
        public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            return AppEnvironment.factory().instantiateActivity(cl != null ? cl : base.getContext().getClassLoader(), className, intent);
        }

        @Override
        public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            return AppEnvironment.factory().instantiateApplication(cl != null ? cl : context.getClassLoader(), className);
        }
    }

    public static final class AppInitializer extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context c = getContext();
            if (c != null) {
                AppEnvironment.init(c.getClassLoader(), c);
            }
            return true;
        }

        @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
        @Override public String getType(Uri u) { return null; }
        @Override public Uri insert(Uri u, ContentValues v) { return null; }
        @Override public int delete(Uri u, String s, String[] a) { return 0; }
        @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
    }
  }
