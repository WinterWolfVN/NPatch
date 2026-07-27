package android.app;

import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Field;

public class AppComponentFactoryStub {
    public static AppComponentFactory sInstance = new AppComponentFactory();

    public static void initEnv(ClassLoader cl, Context c) {
        AppEnvironment.init(cl, c);
    }

    public static ClassLoader getCl(ClassLoader original) {
        return AppEnvironment.cl(original);
    }

    public Application instantiateApplication(ClassLoader cl, String className) {
        try { return (Application) getCl(cl).loadClass(className).newInstance(); } 
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public Activity instantiateActivity(ClassLoader cl, String className, Intent intent) {
        try { return (Activity) getCl(cl).loadClass(className).newInstance(); } 
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static final class AppEnvironment {
        private static ClassLoader loader;

        static void init(ClassLoader cl, Context c) {
            loader = cl;
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
                return AppComponentFactory.sInstance.instantiateActivity(AppComponentFactory.getCl(cl != null ? cl : base.getContext().getClassLoader()), className, intent);
            }

            @Override
            public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
                return AppComponentFactory.sInstance.instantiateApplication(AppComponentFactory.getCl(cl != null ? cl : context.getClassLoader()), className);
            }
        }
    }
}
