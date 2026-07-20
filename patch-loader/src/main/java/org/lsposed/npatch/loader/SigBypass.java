package org.lsposed.npatch.loader;

import static org.lsposed.npatch.share.Constants.ORIGINAL_APK_ASSET_PATH;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.Signature;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonSyntaxException;

import org.json.JSONException;
import org.json.JSONObject;
import org.lsposed.lspd.nativebridge.SvcBypass;
import org.lsposed.npatch.loader.util.XLog;
import org.lsposed.npatch.share.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SigBypass {

    private static final String TAG = "NPatch-SigBypass";
    private static final Map<String, String> signatures = new HashMap<>();
    private static String cachedOriginalApkPath;
        
    private static String cachedPatchedApkPath;
    private static int activeSigBypassLevel;
    private static boolean packageInfoConstructorHooked;
    private static boolean packageInfoCreatorProxied;
    private static boolean applicationInfoHooked;
    private static boolean packageArchiveInfoHooked;
    private static boolean hasSigningCertificateHooked;
    private static boolean javaIoHooked;
    private static boolean nativeOpenatEnabled;
    private static boolean seccompRedirectEnabled;
    
    static {
        moduleCallerPrefixes.add("top.nkbe.npatch.");
        moduleCallerPrefixes.add("org.matrix.vector.");
        moduleCallerPrefixes.add("de.robv.android.xposed.");
        moduleCallerPrefixes.add("io.github.libxposed.");
        moduleCallerPrefixes.add("org.lsposed.");
    }

    public static void registerModuleCallerPrefix(String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            moduleCallerPrefixes.add(prefix);
        }
    }

    static boolean isModuleCallerForCompat() {
        return isModuleCaller();
    }
    
    private static Signature getOriginalSignature(String packageName) {
        String replacement = signatures.get(packageName);
        if (replacement == null || replacement.isEmpty()) return null;
        try {
            return new Signature(replacement);
        } catch (Throwable e) {
            Log.w(TAG, "fail to construct original signature for " + packageName, e);
            return null;
        }
    }
    
    private static boolean matchesOriginalCertificate(Signature signature, byte[] certificate, int type) {
        if (signature == null || certificate == null) return false;
        try {
            byte[] raw = signature.toByteArray();
            if (type == CERT_INPUT_RAW_X509) {
                return MessageDigest.isEqual(raw, certificate);
            }
            if (type == CERT_INPUT_SHA256) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
                return MessageDigest.isEqual(digest, certificate);
            }
        } catch (Throwable e) {
            Log.w(TAG, "fail to compare signature certificate", e);
        }
        return false;
    }
    
    private static boolean isSignatureSensitiveCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.startsWith("android.content.pm.PackageParser")
                    || className.startsWith("android.content.pm.parsing.")
                    || className.startsWith("android.util.apk.")
                    || className.startsWith("java.util.jar.")
                    || className.startsWith("sun.security.pkcs.")
                    || className.startsWith("sun.security.util.")
                    || className.startsWith("org.apache.harmony.security.")) {
                return true;
            }
        }
        return false;
    }

    private static String visibleApkPathForCaller() {
        if (isModuleCaller() && cachedPatchedApkPath != null) {
            return cachedPatchedApkPath;
        }
        return cachedOriginalApkPath != null ? cachedOriginalApkPath : cachedPatchedApkPath;
    }

    private static void replaceSignature(Context context, PackageInfo packageInfo) {
        boolean hasSignature = (packageInfo.signatures != null && packageInfo.signatures.length != 0) || packageInfo.signingInfo != null;
        if (hasSignature) {
            String packageName = packageInfo.packageName;
            String replacement = signatures.get(packageName);
            if (replacement == null && !signatures.containsKey(packageName)) {
                try {
                    var metaData = context.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData;
                    String encoded = null;
                    if (metaData != null) encoded = metaData.getString("npatch");
                    if (encoded != null) {
                        var json = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
                        try {
                            var patchConfig = new JSONObject(json);
                            replacement = patchConfig.getString("originalSignature");
                        } catch (JSONException e) {
                            Log.w(TAG, "fail to get originalSignature", e);
                        }
                    }
                } catch (PackageManager.NameNotFoundException | JsonSyntaxException ignored) {
                }
                signatures.put(packageName, replacement);
            }
            if (replacement != null) {
                if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                    XLog.d(TAG, "Replace signature info for `" + packageName + "` (method 1)");
                    packageInfo.signatures[0] = new Signature(replacement);
                }
                if (packageInfo.signingInfo != null) {
                    XLog.d(TAG, "Replace signature info for `" + packageName + "` (method 2)");
                    Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
                    if (signaturesArray != null && signaturesArray.length > 0) {
                        signaturesArray[0] = new Signature(replacement);
                    }
                }
            }
        }
    }

    private static void hookPackageParser(Context context) {
        XposedBridge.hookAllMethods(PackageParser.class, "generatePackageInfo", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                var packageInfo = (PackageInfo) param.getResult();
                if (packageInfo == null) return;
                replaceSignature(context, packageInfo);
            }
        });
    }

    private static void proxyPackageInfoCreator(Context context) {
        Parcelable.Creator<PackageInfo> originalCreator = PackageInfo.CREATOR;
        Parcelable.Creator<PackageInfo> proxiedCreator = new Parcelable.Creator<>() {
            @Override
            public PackageInfo createFromParcel(Parcel source) {
                PackageInfo packageInfo = originalCreator.createFromParcel(source);
                replaceSignature(context, packageInfo);
                return packageInfo;
            }

            @Override
            public PackageInfo[] newArray(int size) {
                return originalCreator.newArray(size);
            }
        };
        XposedHelpers.setStaticObjectField(PackageInfo.class, "CREATOR", proxiedCreator);
        try {
            Map<?, ?> mCreators = (Map<?, ?>) XposedHelpers.getStaticObjectField(Parcel.class, "mCreators");
            mCreators.clear();
        } catch (NoSuchFieldError ignore) {
        } catch (Throwable e) {
            Log.w(TAG, "fail to clear Parcel.mCreators", e);
        }
        try {
            Map<?, ?> sPairedCreators = (Map<?, ?>) XposedHelpers.getStaticObjectField(Parcel.class, "sPairedCreators");
            sPairedCreators.clear();
        } catch (NoSuchFieldError ignore) {
        } catch (Throwable e) {
            Log.w(TAG, "fail to clear Parcel.sPairedCreators", e);
        }
    }

    public static void replaceApplication(String packageName, String sourceDir, String resourcesDir) throws IOException {
        try {
            Log.i(TAG, "Start Replace application info for `" + packageName + "`");
            XposedBridge.hookAllMethods(Class.forName("android.app.ApplicationPackageManager"), "getApplicationInfo", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (packageName.equals(param.args[0])) {
                        ApplicationInfo info = (ApplicationInfo) param.getResult();
                        info.sourceDir = sourceDir;
                        info.publicSourceDir = sourceDir;
                    }
                }
            });
            XposedBridge.hookAllMethods(Class.forName("android.app.ApplicationPackageManager"), "getApplicationInfoAsUser", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (packageName.equals(param.args[0])) {
                        ApplicationInfo info = (ApplicationInfo) param.getResult();
                        info.sourceDir = sourceDir;
                        info.publicSourceDir = sourceDir;
                    }
                }
            });
        } catch (Throwable e) {
            Log.w(TAG, "fail to replace getApplicationInfo", e);
        }
    }

    private static String extractOriginalApk(Context context) {
        File cacheDir = new File(context.getCacheDir(), "npatch/origin");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        try (ZipFile sourceFile = new ZipFile(context.getPackageResourcePath())) {
            ZipEntry entry = sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH);
            if (entry == null) {
                Log.e(TAG, "Original APK not found in assets!");
                return null;
            }

            File targetFile = new File(cacheDir, entry.getCrc() + ".apk");
            if (targetFile.exists() && targetFile.length() == entry.getSize()) {
                return targetFile.getAbsolutePath();
            }

            try (InputStream is = sourceFile.getInputStream(entry);
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            }
            return targetFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract original APK", e);
            return null;
        }
    }
    
    private static void hookPackageInfoConstructor(Context context) {
        if (packageInfoConstructorHooked) return;
        try {
            XposedHelpers.findAndHookConstructor(PackageInfo.class, Parcel.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof PackageInfo packageInfo)) return;
                    replaceSignature(context, packageInfo);
                }
            });
            packageInfoConstructorHooked = true;
        } catch (Throwable e) {
            Log.w(TAG, "fail to hook PackageInfo(Parcel), fallback to CREATOR proxy", e);
            proxyPackageInfoCreator(context);
        }
    }
    
    private static void hookPackageArchiveInfo(Context context) {
        if (packageArchiveInfoHooked) return;
        try {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (cachedOriginalApkPath == null) return;
                    Object apkPath = param.args.length == 0 ? null : param.args[0];
                    if (!(apkPath instanceof String path) || !path.equals(cachedPatchedApkPath)) {
                        return;
                    }
                    if (isModuleCaller()) {
                        return;
                    }
                    param.args[0] = cachedOriginalApkPath;
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    PackageInfo packageInfo = (PackageInfo) param.getResult();
                    if (packageInfo == null) return;
                    replaceSignature(context, packageInfo);
                }
            };
            hookPackageArchiveInfoMethods(PackageManager.class, hook);
            try {
                hookPackageArchiveInfoMethods(Class.forName("android.app.ApplicationPackageManager"), hook);
            } catch (Throwable ignored) {
            }
            packageArchiveInfoHooked = true;
        } catch (Throwable e) {
            Log.w(TAG, "fail to replace getPackageArchiveInfo", e);
        }
    }
    
    private static void hookHasSigningCertificate(Context context) {
        if (hasSigningCertificateHooked) return;
        try {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (isModuleCaller()) return;
                    if (param.args.length < 3) return;
                    Object packageNameArg = param.args[0];
                    Object certificateArg = param.args[1];
                    Object typeArg = param.args[2];
                    if (!(certificateArg instanceof byte[] certificate)
                            || !(typeArg instanceof Integer type)) {
                        return;
                    }
                    String packageName = null;
                    if (packageNameArg instanceof String str) {
                        packageName = str;
                    } else if (packageNameArg instanceof Integer uid && uid == Process.myUid()) {
                        packageName = context.getPackageName();
                    }
                    if (packageName == null) return;
                    Signature originalSignature = getOriginalSignature(packageName);
                    if (originalSignature == null) return;
                    if (matchesOriginalCertificate(originalSignature, certificate, type)) {
                        param.setResult(true);
                    }
                }
            };
            XposedBridge.hookAllMethods(PackageManager.class, "hasSigningCertificate", hook);
            try {
                XposedBridge.hookAllMethods(Class.forName("android.app.ApplicationPackageManager"), "hasSigningCertificate", hook);
            } catch (Throwable ignored) {
            }
            hasSigningCertificateHooked = true;
        } catch (Throwable e) {
            Log.w(TAG, "fail to hook hasSigningCertificate", e);
        }
    }
 
    private static void hookPackageArchiveInfoMethods(Class<?> clazz, XC_MethodHook hook) {
        try {
            XposedBridge.hookAllMethods(clazz, "getPackageArchiveInfo", hook);
        } catch (NoSuchMethodError ignored) {
        }
    }

    private static void hookJavaIO(String currentApkPath, String originalApkPath) {
        XC_MethodHook redirectHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 0) {
                    if (param.args[0] instanceof String) {
                        String path = (String) param.args[0];
                        if (path.equals(currentApkPath)) {
                            param.args[0] = originalApkPath;
                        }
                    } else if (param.args[0] instanceof File) {
                        File file = (File) param.args[0];
                        if (file.getPath().equals(currentApkPath)) {
                            param.args[0] = new File(originalApkPath);
                        }
                    }
                }
            }
        };
        XposedBridge.hookAllConstructors(ZipFile.class, redirectHook);
        try {
            XposedBridge.hookAllConstructors(FileInputStream.class, redirectHook);
        } catch (Throwable ignored) {}
    }

    static void doSigBypass(Context context, int sigBypassLevel) throws IOException {
        activeSigBypassLevel = Math.max(activeSigBypassLevel, sigBypassLevel);
        String currentApkPath = cachedPatchedApkPath != null ? cachedPatchedApkPath : context.getPackageResourcePath();
        if (sigBypassLevel >= Constants.SIGBYPASS_BASIC && cachedOriginalApkPath == null) {
            cachedOriginalApkPath = extractOriginalApk(context);
        }

        if (sigBypassLevel >= Constants.SIGBYPASS_BASIC && cachedOriginalApkPath != null) {
            hookJavaIO(currentApkPath, cachedOriginalApkPath);
            org.lsposed.lspd.nativebridge.SigBypass.enableOpenatHook(
                    currentApkPath,
                    cachedOriginalApkPath,
                    context.getPackageName()
            );
            if (!nativeOpenatEnabled) {
                nativeOpenatEnabled = true;
            }
        }

        if (sigBypassLevel >= Constants.SIGBYPASS_HIGH) {
            proxyPackageInfoCreator(context);
            hookPackageArchiveInfo(context);
            hookHasSigningCertificate(context);
        }

        if (sigBypassLevel == Constants.SIGBYPASS_EXTREME) {
            hookPackageInfoConstructor(context);
        }

        if (sigBypassLevel >= Constants.SIGBYPASS_BASIC && cachedOriginalApkPath == null) {
            XLog.w(TAG, "Original APK unavailable, native signature bypass disabled");
        }
    }
}
