# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**
-keep class okio.** { *; }

# Gson
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Socket.IO
-dontwarn io.socket.**
-keep class io.socket.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.firebase.** { *; }

# Kotlin
-keepclassmembers class ** {
    ** CREATOR;
}

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# WorldMates App
-keep class com.worldmates.messenger.** { *; }
-keepclassmembers class com.worldmates.messenger.data.model.** {
    <fields>;
}

# Keep model classes
-keep class com.worldmates.messenger.data.model.** { *; }
-keepclassmembers class com.worldmates.messenger.data.model.** {
    <init>(...);
}

# Keep interfaces
-keep interface com.worldmates.messenger.network.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Generic
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile