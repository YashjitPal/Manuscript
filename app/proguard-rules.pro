# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.manuscript.**$$serializer { *; }
-keepclassmembers class com.manuscript.** { *** Companion; }
-keepclasseswithmembers class com.manuscript.** { kotlinx.serialization.KSerializer serializer(...); }

# Navigation 3 keys are serialized for saved state
-keep class com.manuscript.ui.navigation.** { *; }

# JLaTeXMath loads fonts and resources reflectively
-keep class ru.noties.jlatexmath.** { *; }
-keep class org.scilab.forge.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**
-dontwarn ru.noties.jlatexmath.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
