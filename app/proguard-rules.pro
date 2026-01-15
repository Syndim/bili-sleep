# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Retrofit interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Gson models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.bilisleep.data.model.** { *; }
