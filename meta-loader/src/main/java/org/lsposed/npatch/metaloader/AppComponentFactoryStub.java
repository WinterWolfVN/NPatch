package org.lsposed.npatch.metaloader;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AppComponentFactoryStub extends Application {

    private static final String TAG = "NPatch-AppStub";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Class<?> lspatchClass = Class.forName("org.lsposed.npatch.metaloader.LSPatchAppComponentFactoryStub");
            Object lspatchInstance = lspatchClass.newInstance();                      
            Method initMethod = lspatchClass.getDeclaredMethod("bootstrap", Context.class);
            initMethod.setAccessible(true);
            initMethod.invoke(lspatchInstance, base);
        try {
            hookInstrumentation(base.getClassLoader());
        } catch (Throwable e) {
            Log.e(TAG, "Unable to hook instrumentation", e);
        }
    }

    private void hookInstrumentation(ClassLoader classLoader) {
        try {
            Object activityThread = currentActivityThread();
            Field instrumentationField = findField(activityThread.getClass(), "mInstrumentation");
            Instrumentation original = (Instrumentation) instrumentationField.get(activityThread);
            Instrumentation proxy = new InstrumentationProxy(original, classLoader);
            instrumentationField.set(activityThread, proxy);
        } catch (Throwable e) {
            Log.e(TAG, "Unable to hook instrumentation", e);
        }
    }

    private static Object currentActivityThread() throws Exception {
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Method current = activityThread.getDeclaredMethod("currentActivityThread");
        current.setAccessible(true);
        return current.invoke(null);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static class InstrumentationProxy extends Instrumentation {
        private final Instrumentation base;
        private final ClassLoader classLoader;

        InstrumentationProxy(Instrumentation base, ClassLoader classLoader) {
            this.base = base;
            this.classLoader = classLoader;
        }

        @Override
        public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            return base.newActivity(classLoader, className, intent);
        }
    }
                }
