package org.lsposed.npatch.metaloader;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.content.pm.ApplicationInfo;
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
            Class.forName("org.lsposed.npatch.metaloader.LSPAppComponentFactoryStub");
            originalApplication = createOriginalApplication(base);
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
        originalApplication.onCreate();
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
            int nRead;
            byte[] data = new byte[1024];
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
                try { is.close(); } catch (Exception ignored) {}
            }
        }
        Log.w(TAG, "Original application name not found, falling back to default");
        return "android.app.Application";
        }

        private static Application createOriginalApplication(Context base) throws Exception {
            Object activityThread = currentActivityThread();
            Object boundApplication = findField(activityThread.getClass(), "mBoundApplication").get(activityThread);
            Object loadedApk = findField(boundApplication.getClass(), "info").get(boundApplication);
            Field appInfoField = findField(loadedApk.getClass(), "mApplicationInfo");
            ApplicationInfo appInfo = (ApplicationInfo) appInfoField.get(loadedApk);
            appInfo.className = getRealApplicationNameFromJson(base);
            Field mApplicationField = findField(loadedApk.getClass(), "mApplication");
            mApplicationField.set(loadedApk, null);
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
        public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            // Try the framework-provided classloader first, then fallback to the stored classLoader, then to context's loader
            try {
                return base.newApplication(cl, className, context);
            } catch (ClassNotFoundException e1) {
                Log.w(TAG, "newApplication: not found with provided classloader, trying fallback", e1);
                ClassLoader fallback = classLoader != null ? classLoader : (context != null ? context.getClassLoader() : null);
                if (fallback != null && fallback != cl) {
                    try {
                        return base.newApplication(fallback, className, context);
                    } catch (ClassNotFoundException e2) {
                        Log.w(TAG, "newApplication: not found with fallback classloader, rethrow", e2);
                        throw e2;
                    }
                }
                throw e1;
            }
        }

        @Override
        public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            // Try the framework-provided classloader first, then fallback to the stored classLoader, then to thread context loader
            try {
                return base.newActivity(cl, className, intent);
            } catch (ClassNotFoundException e1) {
                Log.w(TAG, "newActivity: not found with provided classloader, trying fallback", e1);
                ClassLoader fallback = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
                if (fallback != null && fallback != cl) {
                    try {
                        return base.newActivity(fallback, className, intent);
                    } catch (ClassNotFoundException e2) {
                        Log.w(TAG, "newActivity: not found with fallback classloader, rethrow", e2);
                        throw e2;
                    }
                }
                throw e1;
            }
        }
    }
}
