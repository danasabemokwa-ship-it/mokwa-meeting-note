# Rules used only if you enable minification in app/build.gradle.kts.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,includedescriptorclasses class com.meetnotes.app.**$$serializer { *; }
-keepclassmembers class com.meetnotes.app.** { *** Companion; }
-keepclasseswithmembers class com.meetnotes.app.** { kotlinx.serialization.KSerializer serializer(...); }
# JNI bridge for whisper.cpp
-keep class com.meetnotes.app.ai.transcription.WhisperLib { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
