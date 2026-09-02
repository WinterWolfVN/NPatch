#include <jni.h>
#include <dlfcn.h>
#include <stdint.h>
#include <sys/mman.h>
#include <memory>
#include <string>
#include <vector>
#include <cstring>
#include <android/api-level.h>

namespace art {

class OatDexFile;

class MemMap {
public:
    uint8_t* begin_;
    size_t size_;
    uint8_t* Begin() const {
        return begin_;
    }
    size_t Size() const {
        return size_;
    }
};

class DexFile {
public:
    typedef std::unique_ptr<const DexFile> (*OpenMemoryFn)(const uint8_t*, size_t, const std::string&, uint32_t, std::unique_ptr<MemMap>, const OatDexFile*, std::string*);
};

}

using namespace art;

DexFile::OpenMemoryFn ResolveOpenMemory() {
    static DexFile::OpenMemoryFn fn = reinterpret_cast<DexFile::OpenMemoryFn>(dlsym(RTLD_DEFAULT, "_ZN3art7DexFile11OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjSt10unique_ptrINS_6MemMapESt14default_deleteISB_EEPKNS_10OatDexFileEPS8_"));
    return fn;
}

MemMap* ResolveMapAnonymous(const char* name, uint8_t* addr, size_t size, int prot, bool low4gb, bool reuse, std::string* error, bool useAshmem) {
    typedef MemMap* (*MapAnonymousFn)(const char*, uint8_t*, size_t, int, bool, bool, std::string*, bool);
    static MapAnonymousFn fn = reinterpret_cast<MapAnonymousFn>(dlsym(RTLD_DEFAULT, "_ZN3art6MemMap12MapAnonymousEPKcPhjibbPNSt3__112basic_stringIcNS4_11char_traitsIcEENS4_9allocatorIcEEEEb"));
    if (fn == nullptr) {
        if (error != nullptr) {
            *error = "MemMap::MapAnonymous not found";
        }
        return nullptr;
    }
    return fn(name, addr, size, prot, low4gb, reuse, error, useAshmem);
}

static void ThrowIOException(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/io/IOException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}

static void ThrowIllegalArgument(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/IllegalArgumentException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}

static void ThrowNullPointer(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/NullPointerException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}

std::unique_ptr<MemMap> MakeDexMemMap(JNIEnv* env, jobject buffer, jint start, jint end, std::string* error) {
    if (buffer == nullptr) {
        ThrowNullPointer(env, "buffer == null");
        return nullptr;
    }
    if (start < 0 || end < 0) {
        ThrowIllegalArgument(env, "Invalid ByteBuffer range");
        return nullptr;
    }
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    uint8_t* direct = reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    const bool isDirect = direct != nullptr && capacity >= 0;
    jclass bufferClass = env->GetObjectClass(buffer);
    if (bufferClass == nullptr) {
        return nullptr;
    }

    if (start < 0 || end <= start) {
        env->DeleteLocalRef(bufferClass);
        ThrowIllegalArgument(env, "Empty ByteBuffer");
        return nullptr;
    }
    const size_t size = static_cast<size_t>(end - start);
    std::string mapError;
    MemMap* raw = ResolveMapAnonymous("InMemoryDexClassLoader", nullptr, size, PROT_READ | PROT_WRITE, false, false, &mapError, true);
    if (raw == nullptr) {
        env->DeleteLocalRef(bufferClass);
        if (error != nullptr) {
            *error = mapError;
        }
        return nullptr;
    }
    std::unique_ptr<MemMap> map(raw);

    // DirectByteBuffer
    if (isDirect) {
        if (static_cast<jlong>(end) > capacity) {
            env->DeleteLocalRef(bufferClass);
            ThrowIllegalArgument(env, "ByteBuffer limit exceeds capacity");
            return nullptr;
        }
        std::memcpy(map->Begin(), direct + start, size);
        env->DeleteLocalRef(bufferClass);
        return map;
    }

    // Heap ByteBuffer
    jmethodID hasArrayMethod = env->GetMethodID(bufferClass, "hasArray", "()Z");
    jmethodID arrayMethod = env->GetMethodID(bufferClass, "array", "()Ljava/lang/Object");
    jmethodID arrayOffsetMethod = env->GetMethodID(bufferClass, "arrayOffset", "()I");
    if (hasArrayMethod == nullptr || arrayMethod == nullptr) {
        env->DeleteLocalRef(bufferClass);
        ThrowIllegalArgument(env, "Unable to access ByteBuffer backing array");
        return nullptr;
    }
    jboolean hasArray = env->CallBooleanMethod(buffer, hasArrayMethod);
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(bufferClass);
        return nullptr;
    }
    if (!hasArray) {
        env->DeleteLocalRef(bufferClass);
        ThrowIllegalArgument(env, "ByteBuffer has no backing array");
        return nullptr;
    }
    jobject arrayObject = env->CallObjectMethod(buffer, arrayMethod);
    if (env->ExceptionCheck() || arrayObject == nullptr) {
        env->DeleteLocalRef(bufferClass);
        return nullptr;
    }
    jint arrayOffset = env->CallIntMethod(buffer, arrayOffsetMethod);
    jbyteArray array = reinterpret_cast<jbyteArray>(arrayObject);
    jsize arrayLength = env->GetArrayLength(array);
    const jint sourceStart = arrayOffset + start;
    const jint sourceEnd = arrayOffset + end;
    if (sourceStart < 0 || sourceEnd > arrayLength || sourceEnd <= sourceStart) {
        env->DeleteLocalRef(array);
        env->DeleteLocalRef(bufferClass);
        ThrowIllegalArgument(env, "Invalid ByteBuffer backing array range");
        return nullptr;
    }
    jbyte* bytes = env->GetByteArrayElements(array, nullptr);
    if (bytes == nullptr) {
        env->DeleteLocalRef(array);
        env->DeleteLocalRef(bufferClass);
        return nullptr;
    }
    std::memcpy(map->Begin(), bytes + sourceStart, size);
    env->ReleaseByteArrayElements(array, bytes, JNI_ABORT);
    env->DeleteLocalRef(array);
    env->DeleteLocalRef(bufferClass);
    return map;
}

jobject AllocateDexFileObject(JNIEnv* env, jclass dexFileClass, jfieldID cookieField, jfieldID internalCookieField, DexFile* nativeDexFile) {
    jobject object = env->AllocObject(dexFileClass);
    if (object == nullptr) {
        return nullptr;
    }
    jlongArray cookie = env->NewLongArray(2);
    jlong values[2];
    values[0] = 0;
    values[1] = static_cast<jlong>(reinterpret_cast<uintptr_t>(nativeDexFile));
    env->SetLongArrayRegion(cookie, 0, 2, values);
    env->SetObjectField(object, cookieField, cookie);
    env->SetObjectField(object, internalCookieField, cookie);
    env->DeleteLocalRef(cookie);
    return object;
}

static jobjectArray CreateDexFile(JNIEnv* env, jclass, jobjectArray buffers, jintArray positions, jintArray limits) {
    if (buffers == nullptr) {
        ThrowNullPointer(env, "buffer == null");
        return nullptr;
    }
    if (positions == nullptr || limits == nullptr) {
        ThrowNullPointer(env, "positions/limits == null");
        return nullptr;
    }
    const jsize count = env->GetArrayLength(buffers);
    DexFile::OpenMemoryFn OpenMemory = ResolveOpenMemory();
    jclass dexFileClass = env->FindClass("dalvik/system/DexFile");
    if (dexFileClass == nullptr) {
        return nullptr;
    }
    jfieldID cookieField = env->GetFieldID(dexFileClass, "mCookie", "Ljava/lang/Object;");
    jfieldID internalCookieField = env->GetFieldID(dexFileClass, "mInternalCookie", "Ljava/lang/Object;");
    if (cookieField == nullptr || internalCookieField == nullptr) {
        env->DeleteLocalRef(dexFileClass);
        return nullptr;
    }
    jobjectArray result =env->NewObjectArray(count, dexFileClass, nullptr);
    jint* positionPtr = env->GetIntArrayElements(positions, nullptr);
    jint* limitPtr = env->GetIntArrayElements(limits, nullptr);
    for (jsize i = 0; i < count; ++i) {
        jobject buffer = env->GetObjectArrayElement(buffers, i);
        const jint position = positionPtr[i];
        const jint limit = limitPtr[i];
        if (position < 0 || limit < position) {
            env->DeleteLocalRef(buffer);
            env->ReleaseIntArrayElements(positions, positionPtr, JNI_ABORT);
            env->ReleaseIntArrayElements(limits, limitPtr, JNI_ABORT);
            env->DeleteLocalRef(result);
            env->DeleteLocalRef(dexFileClass);
            ThrowIOException(env, "Invalid ByteBuffer position/limit");
            return nullptr;
        }
        std::string mapError;
        std::unique_ptr<MemMap> map = MakeDexMemMap(env, buffer, position, limit, &mapError);
        env->DeleteLocalRef(buffer);
        const uint8_t* base = map->Begin();
        const size_t size = map->Size();
        if (base == nullptr || size == 0) {
            env->ReleaseIntArrayElements(positions, positionPtr, JNI_ABORT);
            env->ReleaseIntArrayElements(limits, limitPtr, JNI_ABORT);
            env->DeleteLocalRef(result);
            env->DeleteLocalRef(dexFileClass);
            ThrowIOException(env, "Invalid MemMap");
            return nullptr;
        }
        std::string location ="InMemoryDexClassLoader-" + std::to_string(static_cast<int>(i));
        std::string error;
        std::unique_ptr<const DexFile> dex =OpenMemory(base, size, location, 0, std::move(map), nullptr, &error);
        DexFile* nativeDexFile = const_cast<DexFile*>(dex.release());
        jobject javaDexFile = AllocateDexFileObject(env, dexFileClass, cookieField, internalCookieField, nativeDexFile);
        env->SetObjectArrayElement(result, i, javaDexFile);
        env->DeleteLocalRef(javaDexFile);
    }
    env->ReleaseIntArrayElements(positions, positionPtr, JNI_ABORT);
    env->ReleaseIntArrayElements(limits, limitPtr, JNI_ABORT);
    env->DeleteLocalRef(dexFileClass);
    return result;
}

static jobject NativeBridge(JNIEnv* env, jclass, jstring name, jobject loader) {
    if (name == nullptr) {
        return nullptr;
    }
    if (loader == nullptr) {
        return nullptr;
    }
    return nullptr;
}

static const JNINativeMethod gMethods[] = {
    {
        "CreateDexFile",
        "([Ljava/nio/ByteBuffer;)[Ldalvik/system/DexFile;",
        reinterpret_cast<void*>(CreateDexFile)
    },
    {
        "NativeBridge",
        "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/Class;",
        reinterpret_cast<void*>(NativeBridge)
    }
};

extern "C" jint RegisterInMemoryDexClassLoader(JNIEnv* env) {
    if (env == nullptr) {
        return JNI_ERR;
    }
    if (android_get_device_api_level() >= 26) {
        return JNI_OK;
    }
    jclass clazz = env->FindClass("oldlib/dalvik/system/InMemoryDexClassLoader");
    if (clazz == nullptr) {
        return JNI_ERR;
    }
    const int result = env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
    env->DeleteLocalRef(clazz);
    return result == JNI_OK ? JNI_OK : JNI_ERR;
}
