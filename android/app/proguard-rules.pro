# AANO ProGuard rules
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

# Keep Google API client classes
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.**
-dontwarn com.google.gson.**

# Strip debug logging in production release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Protect Note and Task data model serialization
-keepclassmembers class app.aa.aano.data.** {
    public <methods>;
    public <fields>;
}

# Security & Cryptographic Integrity Protection
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class androidx.biometric.** { *; }
-keep interface androidx.biometric.** { *; }

# Optimization & Obfuscation
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses