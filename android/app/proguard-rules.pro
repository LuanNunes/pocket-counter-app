# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.resolveprogramming.pocketcounter.**$$serializer { *; }
-keepclassmembers class com.resolveprogramming.pocketcounter.** { *** Companion; }
-keepclasseswithmembers class com.resolveprogramming.pocketcounter.** { kotlinx.serialization.KSerializer serializer(...); }
