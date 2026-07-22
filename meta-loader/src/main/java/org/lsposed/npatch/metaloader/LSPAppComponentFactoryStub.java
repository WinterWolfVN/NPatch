package org.lsposed.npatch.metaloader;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.app.AppComponentFactory;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.os.Build;
import android.os.Process;
import android.os.ServiceManager;
import android.util.JsonReader;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;
import org.lsposed.npatch.share.Constants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipFile;

@SuppressLint("UnsafeDynamicallyLoadedCode")
public class LSPAppComponentFactoryStub extends AppComponentFactory {

    private static final String TAG = "NPatch-MetaLoader";
    private static final Map<String, String> archToLib = new HashMap<String, String>(4);

    public static byte[] dex;

    static {
    final boolean appZygote = ActivityThread.currentActivityThread() == null;
    if (appZygote) {
        Log.i(TAG, "Skip loading libnpatch.so for appZygote");
    } else {
        bootstrap();
        AppComponentFactory.sInstance = new LSPAppComponentFactoryStub();
    }
}

    public static final class ProviderStub extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context c = getContext();
            if (c != null) {
                AppComponentFactory.AppEnvironment.init(c.getClassLoader(), c);
                try { Class.forName("org.lsposed.npatch.metaloader.LSPAppComponentFactoryStub", true, c.getClassLoader()); } catch (Throwable ignored) {}
            }
            return true;
        }
        @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
        @Override public String getType(Uri u) { return null; }
        @Override public Uri insert(Uri u, ContentValues v) { return null; }
        @Override public int delete(Uri u, String s, String[] a) { return 0; }
        @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
        }   
    
    private static void bootstrap() {
        try {
            archToLib.put("arm64", "arm64-v8a");
            archToLib.put("x86_64", "x86_64");

            var cl = Objects.requireNonNull(LSPAppComponentFactoryStub.class.getClassLoader());
            Class<?> VMRuntime = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = VMRuntime.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Method vmInstructionSet = VMRuntime.getDeclaredMethod("vmInstructionSet");
            vmInstructionSet.setAccessible(true);
            String arch = (String) vmInstructionSet.invoke(getRuntime.invoke(null));
            String libName = archToLib.get(arch);

            boolean useManager = false;
            String soPath;

            try (var is = cl.getResourceAsStream(Constants.CONFIG_ASSET_PATH);
                 var reader = new JsonReader(new InputStreamReader(is))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    var name = reader.nextName();
                    if (name.equals("useManager")) {
                        useManager = reader.nextBoolean();
                        break;
                    } else {
                        reader.skipValue();
                    }
                }
            }

            int currentUserId = Process.myUid() / 100000;

            if (useManager) {
                Log.i(TAG, "Bootstrap loader from manager");
                var ipm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
                ApplicationInfo manager;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manager = (ApplicationInfo) HiddenApiBypass.invoke(IPackageManager.class, ipm, "getApplicationInfo", Constants.MANAGER_PACKAGE_NAME, 0L, currentUserId);
                } else {
                    manager = ipm.getApplicationInfo(Constants.MANAGER_PACKAGE_NAME, 0, currentUserId);
                }
                try (var zip = new ZipFile(new File(manager.sourceDir));
                     var is = zip.getInputStream(zip.getEntry(Constants.LOADER_DEX_ASSET_PATH));
                     var os = new ByteArrayOutputStream()) {
                    transfer(is, os);
                    dex = os.toByteArray();
                }
                soPath = manager.sourceDir + "!/assets/npatch/so/" + libName + "/libnpatch.so";
            } else {
                Log.i(TAG, "Bootstrap loader from embedment");
                try (var is = cl.getResourceAsStream(Constants.LOADER_DEX_ASSET_PATH);
                     var os = new ByteArrayOutputStream()) {
                    transfer(is, os);
                    dex = os.toByteArray();
                }
                java.net.URL url = cl.getResource("assets/npatch/so/" + libName + "/libnpatch.so");
                if (url == null) {
                    throw new RuntimeException("Should not happen: libnpatch.so not found in assets");
                }
                String rawPath = url.getPath();
                if (rawPath.startsWith("file:")) {
                    soPath = rawPath.substring(5);
                } else if (rawPath.startsWith("jar:file:")) {
                    soPath = rawPath.substring(9);
                } else {
                    soPath = rawPath;
                }
                try {
                    soPath = java.net.URLDecoder.decode(soPath, "UTF-8");
                } catch (java.io.UnsupportedEncodingException e) {
                }
                Log.i(TAG, "Loading native lib from: " + soPath);
            }

            System.load(soPath);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void transfer(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while (-1 != (n = is.read(buffer))) {
            os.write(buffer, 0, n);
        }
    }
    }
