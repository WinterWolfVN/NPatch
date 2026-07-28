-keep class org.lsposed.npatch.metaloader.LSPAppComponentFactoryStub {
    public static byte[] dex;
    <init>();
}
-keep class * extends androidx.room.Entity {
    <fields>;
}
-keep interface * extends androidx.room.Dao {
    <methods>;
}
-keep class android.** { *; 
}
-keep class com.android.** { *; 
}
-keep class org.lsposed.npatch.metaloader.AppComponentFactoryStub { *; }

-dontwarn androidx.annotation.NonNull
-dontwarn androidx.annotation.Nullable
-dontwarn androidx.annotation.VisibleForTesting
