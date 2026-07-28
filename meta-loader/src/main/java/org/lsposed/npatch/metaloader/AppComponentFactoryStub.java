package org.lsposed.npatch.metaloader;

import android.app.Application;
import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class AppComponentFactoryStub extends Application {

    private static final String TAG = "NPatch-AppStub";
    private Application originalApplication;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            Class<?> lspatchClass = Class.forName("org.lsposed.npatch.metaloader.LSPatchAppComponentFactoryStub");
            Object lspatchInstance = lspatchClass.getDeclaredConstructor().newInstance();
            Method initMethod = lspatchClass.getDeclaredMethod("bootstrap");
            initMethod.setAccessible(true);
            initMethod.invoke(lspatchInstance);

            originalApplication = createOriginalApplication();

            Method attachMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            attachMethod.setAccessible(true);
            attachMethod.invoke(originalApplication, base);

            hookInstrumentation(base.getClassLoader());

            Object activityThread = currentActivityThread();
            replaceApplication(activityThread, originalApplication);

        } catch (Throwable e) {
            Log.e(TAG, "Unable to bootstrap", e);
            throw new IllegalStateException("Unable to bootstrap", e);
        }
    }

    @Override
    public void onCreate() {
        if (originalApplication == null) {
            throw new IllegalStateException("Original application was not created");
        }
        try {
            originalApplication.onCreate();
        } catch (Throwable e) {
            Log.e(TAG, "Unable to start original application", e);
            throw new IllegalStateException("Unable to start original application", e);
        }
    }

    @Override
    public Context getApplicationContext() {
        return originalApplication != null ? originalApplication.getApplicationContext() : super.getApplicationContext();
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

    private static Application createOriginalApplication() throws Exception {
        Object activityThread = currentActivityThread();
        Object boundApplication = findField(activityThread.getClass(), "mBoundApplication").get(activityThread);
        Object loadedApk = findField(boundApplication.getClass(), "info").get(boundApplication);
        Method makeApplication = findMethod(loadedApk.getClass(), "makeApplication", boolean.class, Instrumentation.class);
        return (Application) makeApplication.invoke(loadedApk, false, null);
    }

    private void replaceApplication(Object activityThread, Application original) throws Exception {
        Field initialApplication = findField(activityThread.getClass(), "mInitialApplication");
        initialApplication.set(activityThread, original);

        Field allApplications = findField(activityThread.getClass(), "mAllApplications");
        @SuppressWarnings("unchecked")
        List<Application> applications = (List<Application>) allApplications.get(activityThread);
        applications.remove(this);
        if (!applications.contains(original)) applications.add(original);
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

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
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
