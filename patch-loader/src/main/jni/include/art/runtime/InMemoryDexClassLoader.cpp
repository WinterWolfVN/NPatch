#include <jni.h>
#include <cstdint>
#include <vector>
#include <android/api-level.h>

namespace InMemoryDexClassLoader {

void Log(JNIEnv* env, const char* message) {
    jclass logClass = env->FindClass("android/util/Log");
    if (logClass == nullptr) {
        env->ExceptionClear();
        return;
    }
    jmethodID d = env->GetStaticMethodID(logClass, "d", "(Ljava/lang/String;Ljava/lang/String;)I");
    if (d == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(logClass);
        return;
    }
    jstring tag = env->NewStringUTF("InMemoryDex");
    jstring msg = env->NewStringUTF(message);
    if (tag != nullptr && msg != nullptr) {
        env->CallStaticIntMethod(logClass, d, tag, msg);
    }
    env->ExceptionClear();
    if (tag != nullptr)
        env->DeleteLocalRef(tag);
    if (msg != nullptr)
        env->DeleteLocalRef(msg);
    env->DeleteLocalRef(logClass);
}

static jclass NativeLoadClass(JNIEnv* env, jclass, jstring name, jobject loader) {
    if (env == nullptr || name == nullptr || loader == nullptr) {
        return nullptr;
    }
    Log(env, "nativeLoadClass()");
    return nullptr;
}

jobject CreateDexFileObject(JNIEnv* env, void* oat_file, const std::vector<void*>& dex_files, const char* file_name) {
    if (env == nullptr || dex_files.empty()) {
        return nullptr;
    }
    Log(env, "CreateDexFileObject()");
    jclass dexFileClass = env->FindClass("dalvik/system/DexFile");
    if (dexFileClass == nullptr) {
        env->ExceptionClear();
        Log(env, "DexFile class not found");
        return nullptr;
    }

    /*
     * API 25 cookie:
     *
     * [0]   = OatFile*
     * [1..] = DexFile*
     */
    const jsize cookieSize = static_cast<jsize>(1 + dex_files.size());
    jlongArray cookie = env->NewLongArray(cookieSize);
    if (cookie == nullptr) {
        Log(env, "NewLongArray failed");
        env->DeleteLocalRef(dexFileClass);
        return nullptr;
    }
    std::vector<jlong> values(cookieSize);
    values[0] = static_cast<jlong>(reinterpret_cast<uintptr_t>(oat_file));
    for (size_t i = 0; i < dex_files.size(); ++i) {
        values[i + 1] = static_cast<jlong>(reinterpret_cast<uintptr_t>(dex_files[i]));
    }
    env->SetLongArrayRegion(cookie, 0, cookieSize, values.data());
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        Log(env, "SetLongArrayRegion failed");
        env->DeleteLocalRef(cookie);
        env->DeleteLocalRef(dexFileClass);
        return nullptr;
    }

    /*
     * Do NOT call a DexFile constructor.
     *
     * The constructors eventually call openDexFileNative(),
     * which would load the DEX again.
     */
    jobject dexFile = env->AllocObject(dexFileClass);
    if (dexFile == nullptr) {
        Log(env, "AllocObject failed");
        env->DeleteLocalRef(cookie);
        env->DeleteLocalRef(dexFileClass);
        return nullptr;
    }
    jfieldID cookieField = env->GetFieldID(dexFileClass, "mCookie", "Ljava/lang/Object;");
    jfieldID internalCookieField = env->GetFieldID(dexFileClass, "mInternalCookie", "Ljava/lang/Object;");
    jfieldID fileNameField = env->GetFieldID(dexFileClass, "mFileName", "Ljava/lang/String;");
    if (cookieField == nullptr ||
        internalCookieField == nullptr ||
        fileNameField == nullptr) {
        env->ExceptionClear();
        Log(env, "DexFile fields not found");
        env->DeleteLocalRef(cookie);
        env->DeleteLocalRef(dexFileClass);
        env->DeleteLocalRef(dexFile);
        return nullptr;
    }

    env->SetObjectField(dexFile, cookieField, cookie);
    env->SetObjectField(dexFile, internalCookieField, cookie);
    jstring name = env->NewStringUTF(file_name != nullptr ? file_name : "");
    if (name == nullptr) {
        env->ExceptionClear();
        Log(env, "NewStringUTF failed");
        env->DeleteLocalRef(cookie);
        env->DeleteLocalRef(dexFileClass);
        env->DeleteLocalRef(dexFile);
        return nullptr;
    }
    env->SetObjectField(dexFile, fileNameField, name);
    env->DeleteLocalRef(name);
    env->DeleteLocalRef(cookie);
    env->DeleteLocalRef(dexFileClass);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        Log(env, "CreateDexFileObject finished with exception");
        env->DeleteLocalRef(dexFile);
        return nullptr;
    }
    Log(env, "DexFile object created");
    return dexFile;
}

} // namespace InMemoryDexClassLoader

static const JNINativeMethod gMethods[] = {
    {"nativeLoadClass", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/Class;", reinterpret_cast<void*>(NativeLoadClass)}
};

bool RegisterInMemoryDexClassLoader(JNIEnv* env) {
    if (env == nullptr) {
        return false;
    }
    if (android_get_device_api_level() >= 26) {
        return true;
    }
    jclass clazz = env->FindClass("oldlib/dalvik/system/InMemoryDexClassLoader");
    if (clazz == nullptr) {
        return false;
    }
    const int result = env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(gMethods[0]));    
    return result == JNI_OK;
}
