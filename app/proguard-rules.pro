# BetterStreamflix ProGuard/R8 Rules

# === General ===
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# === Kotlin ===
-keep class kotlin.Metadata { *; }
-keepclassmembers class **.WhenMappings { <fields>; }

# === kotlinx.serialization ===
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.betterstreamflix.**$$serializer { *; }
-keepclassmembers class com.betterstreamflix.** { *** Companion; }
-keepclasseswithmembers class com.betterstreamflix.** { kotlinx.serialization.KSerializer serializer(...); }
-keepclassmembers @kotlinx.serialization.Serializable class com.betterstreamflix.** { *** Companion; }
-keepclasseswithmembers @kotlinx.serialization.Serializable class com.betterstreamflix.** { kotlinx.serialization.KSerializer serializer(...); }

# === Retrofit ===
-keepattributes Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowshrinking,allowobfuscation interface retrofit2.CallAdapter
-keep,allowshrinking,allowobfuscation interface retrofit2.Converter
-keep,allowshrinking,allowobfuscation interface retrofit2.Converter$Factory
-keep,allowshrinking,allowobfuscation interface retrofit2.CallAdapter$Factory
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keep,allowobfuscation interface * { @retrofit2.http.* <methods>; }

# === OkHttp / OkIo ===
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.PublicClass
-keepnames class okio.PublicClass

# === Gson ===
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }
-keep class com.betterstreamflix.models.** { *; }
-keep class com.betterstreamflix.providers.** { *; }
-keep class com.betterstreamflix.providers.**$* { *; }
-keepclassmembers class com.betterstreamflix.providers.** { *; }
-keep class com.betterstreamflix.utils.TMDb3** { *; }
-keep class com.betterstreamflix.utils.TMDb3$* { *; }
-keepclassmembers class com.betterstreamflix.utils.TMDb3** { *; }
-keep class com.betterstreamflix.utils.TmdbUtils { *; }
-keep class com.betterstreamflix.extractors.** { *; }
-keep class com.betterstreamflix.utils.** { *; }
-keepclassmembers class com.betterstreamflix.utils.** { *; }

# === Supabase ===
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-dontwarn io.ktor.**

# === Ktor ===
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# === Glide ===
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** rewind(); }
-dontwarn com.bumptech.glide.**

# === ExoPlayer / Media3 ===
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# === Room ===
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# === Navigation ===
-keep class * extends androidx.navigation.fragment.NavHostFragment
-keep class androidx.navigation.** { *; }
-keep class com.betterstreamflix.fragments.**.*FragmentDirections { *; }
-keep class com.betterstreamflix.fragments.**.*FragmentDirections$* { *; }
-keep class com.betterstreamflix.fragments.**.*FragmentArgs { *; }
-keep class com.betterstreamflix.fragments.**.*FragmentArgs$* { *; }
-keep class * implements androidx.navigation.NavDirections { *; }
-keepclassmembers class com.betterstreamflix.R$id { *; }
-keepclassmembers class com.betterstreamflix.R$navigation { *; }

# === Parcelize ===
-keepclassmembers class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator CREATOR; }

# === Jsoup ===
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# === Mozilla Rhino ===
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# === NanoHTTPD ===
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# === ZXing ===
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# === Java-WebSocket ===
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# === Conscrypt ===
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# === DNSJava ===
-keep class org.xbill.DNS.** { *; }
-dontwarn org.xbill.DNS.**

# === AndroidSVG ===
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# === BuildConfig ===
-keep class com.betterstreamflix.BuildConfig { *; }

# === WebView JS Interfaces ===
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# === Native Methods ===
-keepclasseswithmembernames class * { native <methods>; }

# === Enum ===
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }