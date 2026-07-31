package org.lsposed.npatch.metaloader;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class NewApplicationStub extends Application {

    private static final String TAG = "NPatch-Metaloader";
    private Application originalApplication;
    private static volatile boolean bootstrapComplete = false;
    private static final Object bootstrapLock = new Object();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            ClassLoader realClassLoader = base.getClassLoader();
            Thread.currentThread().setContextClassLoader(realClassLoader);
            tring nativeLibDir = base.getApplicationInfo().nativeLibraryDir;
            Class.forName("org.lsposed.npatch.metaloader.LSPAppComponentFactoryStub");
            originalApplication = createOriginalApplication(base);
            hookInstrumentation(base.getClassLoader());
            Object activityThread = currentActivityThread();
            replaceApplication(activityThread, originalApplication);
            synchronized (bootstrapLock) {
                bootstrapComplete = true;
                bootstrapLock.notifyAll();
            }
            Log.i(TAG, "Bootstrap complete");
        } catch (Throwable e) {
            synchronized (bootstrapLock) {
                bootstrapComplete = true;
                bootstrapLock.notifyAll();
            }
            Log.e(TAG, "Unable to bootstrap", e);
            throw new IllegalStateException("Unable to bootstrap", e);
        }
    }

    @Override
    public void onCreate() {
        if (originalApplication == null) {
            throw new IllegalStateException("Original application was not created");
        }
        originalApplication.onCreate();
    }

    public static void ensureBootstrapComplete() {
        if (bootstrapComplete) {
            return;
        }
        synchronized (bootstrapLock) {
            if (bootstrapComplete) {
                return;
            }
            try {
                Log.d(TAG, "Waiting for bootstrap to complete...");
                bootstrapLock.wait(5000);
                if (!bootstrapComplete) {
                    Log.w(TAG, "Bootstrap timeout reached!");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Bootstrap wait interrupted", e);
            }
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

    private static String getRealApplicationNameFromJson(Context base) {
        InputStream is = null;
        try {
            is = base.getAssets().open("npatch/config.json");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            String jsonString = new String(buffer.toByteArray(), "UTF-8");
            JSONObject jsonObject = new JSONObject(jsonString);
            String appName = jsonObject.optString("applicationName", "");
            if (!appName.isEmpty()) {
                Log.i(TAG, "Successfully extracted original application name: " + appName);
                return appName;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse npatch/config.json", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
            }
        }
        Log.w(TAG, "Original application name not found, falling back to default");
        return "android.app.Application";
    }

    private static Application createOriginalApplication(Context base) throws Exception {
        try {
            String appName = getRealApplicationNameFromJson(base);
            Log.d(TAG, "Original app name from config: " + appName);
            Object activityThread = currentActivityThread();
            Object boundApplication = findField(activityThread.getClass(), "mBoundApplication").get(activityThread);
            Object loadedApk = findField(boundApplication.getClass(), "info").get(boundApplication);
            Field appInfoField = findField(loadedApk.getClass(), "mApplicationInfo");
            ApplicationInfo appInfo = (ApplicationInfo) appInfoField.get(loadedApk);
            if ("android.app.Application".equals(appName) || appName == null || appName.isEmpty()) {
                Log.d(TAG, "Using default android.app.Application");
                appInfo.className = null;
            } else {
                Log.d(TAG, "Using original Application: " + appName);
                appInfo.className = appName;
            }
            Field mApplicationField = findField(loadedApk.getClass(), "mApplication");
            mApplicationField.set(loadedApk, null);
            Method makeApplication = findMethod(
                    loadedApk.getClass(),
                    "makeApplication",
                    boolean.class,
                    Instrumentation.class
            );
            return (Application) makeApplication.invoke(loadedApk, false, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create application", e);
            throw e;
        }
    }

    private void replaceApplication(Object activityThread, Application original) throws Exception {
        if (original == null) {
            throw new IllegalStateException("Original application is null");
        }
        Field initialApplicationField = findField(activityThread.getClass(), "mInitialApplication");
        initialApplicationField.set(activityThread, original);
        Field allApplicationsField = findField(activityThread.getClass(), "mAllApplications");
        @SuppressWarnings("unchecked")
        List<Application> applications = (List<Application>) allApplicationsField.get(activityThread);
        applications.remove(this);
        if (!applications.contains(original)) {
            applications.add(original);
        }
        Object boundApplication = findField(activityThread.getClass(), "mBoundApplication").get(activityThread);
        Object loadedApk = findField(boundApplication.getClass(), "info").get(boundApplication);
        Field applicationField = findField(loadedApk.getClass(), "mApplication");
        applicationField.set(loadedApk, original);
        Log.i(TAG, "Application replaced: " + original.getClass().getName());
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
    public Application newApplication(
            ClassLoader cl,
            String className,
            Context context
    ) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        ensureBootstrapComplete();

        try {
            return base.newApplication(cl, className, context);
        } catch (ClassNotFoundException e) {
            ClassLoader fallback = classLoader != null
                    ? classLoader
                    : context.getClassLoader();

            if (fallback != null && fallback != cl) {
                return base.newApplication(fallback, className, context);
            }

            throw e;
        }
    }

    @Override
    public Activity newActivity(
            ClassLoader cl,
            String className,
            Intent intent
    ) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        ensureBootstrapComplete();

        try {
            return base.newActivity(cl, className, intent);
        } catch (ClassNotFoundException e) {
            ClassLoader fallback = classLoader != null
                    ? classLoader
                    : Thread.currentThread().getContextClassLoader();

            if (fallback != null && fallback != cl) {
                return base.newActivity(fallback, className, intent);
            }

            throw e;
        }
    }
}
}
