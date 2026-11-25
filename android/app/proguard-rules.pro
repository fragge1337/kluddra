# Keep Kotlinx Serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.kluddra.android.data.remote.**$$serializer { *; }
-keepclassmembers class app.kluddra.android.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class app.kluddra.android.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
