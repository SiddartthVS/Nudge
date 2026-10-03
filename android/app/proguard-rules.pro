# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# R8 is enabled to shrink the React Native / AndroidX / Kotlin libraries (see app/build.gradle).
# Nudge's own code is tiny, so it is kept exactly as written: the native modules JS calls by
# name (PermissionsModule, StatsModule, BlockModule, ...), the accessibility service and the
# Supabase classes must never be renamed or removed.
-keep class com.nudge.** { *; }

# Keep line numbers in crash logs readable.
-keepattributes SourceFile,LineNumberTable,*Annotation*
