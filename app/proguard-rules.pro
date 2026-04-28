# ──────────────────────────────────────────────────────────────────────────────
# WorldMates Messenger — ProGuard / R8 rules
#
# Rule philosophy:
#   • Keep only what reflection/serialization actually needs (data models, enums,
#     network interfaces, native/JNI entry points).
#   • Let R8 remove everything else — that's where the size savings come from.
#   • Adding -keep class com.worldmates.** { *; }  defeats R8 entirely.
# ──────────────────────────────────────────────────────────────────────────────

# ── Debug info (stack traces stay readable in crash reports) ──────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,EnclosingMethod

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    ** CREATOR;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Enums (values()/valueOf() called by name at runtime) ─────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Data models — Gson/Retrofit deserialize fields by name ───────────────────
-keep class com.worldmates.messenger.data.model.** { *; }
-keepclassmembers class com.worldmates.messenger.data.model.** {
    <init>(...);
    <fields>;
}
# Strapi / network response models
-keep class com.worldmates.messenger.data.** { *; }

# ── Retrofit / OkHttp ────────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
# Keep all network classes (interfaces + data classes used as Retrofit return types).
# Without this R8 renames/merges response data classes, breaking the coroutine
# state-machine cast at invokeSuspend (ClassCastException in release builds).
-keep class com.worldmates.messenger.network.** { *; }
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# ── Gson ──────────────────────────────────────────────────────────────────────
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
# Preserve generic signatures of TypeToken and all subclasses (including anonymous
# object : TypeToken<List<X>>() {} created in ChatOrganizationManager, etc.).
# Without these two rules R8 strips the type argument and Gson throws
# "TypeToken must be created with a type argument" at runtime.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ── Socket.IO ─────────────────────────────────────────────────────────────────
-dontwarn io.socket.**
-keep class io.socket.** { *; }

# ── Firebase / FCM ────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }

# ── WebRTC (Stream / native JNI) ─────────────────────────────────────────────
-keep class org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
-dontwarn org.webrtc.**

# ── Lottie animations ─────────────────────────────────────────────────────────
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ── Coil image loading ────────────────────────────────────────────────────────
-dontwarn coil.**

# ── ML Kit (selfie segmentation for virtual backgrounds) ────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Bouncycastle (encryption) ────────────────────────────────────────────────
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── libphonenumber ───────────────────────────────────────────────────────────
-keep class com.google.i18n.phonenumbers.** { *; }

# ── ZXing QR scanning ─────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# ── Room database ────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Compose (R8 handles Compose well, just keep lambdas) ─────────────────────
-keep class androidx.compose.** { *; }

# ── Google Maps / Location ───────────────────────────────────────────────────
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.gms.location.** { *; }

# ── Dropbox SDK ──────────────────────────────────────────────────────────────
-dontwarn com.dropbox.**
-keep class com.dropbox.** { *; }

# ── Google Drive / API Client ─────────────────────────────────────────────────
-keep class com.google.api.** { *; }
-dontwarn com.google.api.**

# Игнорируем отсутствующие классы Apache HTTP и Java Naming
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**

# Если R8 всё равно ругается на "Missing classes", можно добавить общие правила
-keep class javax.naming.** { *; }
-keep class org.ietf.jgss.** { *; }
