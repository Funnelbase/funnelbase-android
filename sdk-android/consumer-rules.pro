# Rules applied automatically to any app that depends on this SDK.

# kotlinx.serialization generates a synthetic $$serializer per @Serializable class and looks it
# up reflectively. Without these, R8 strips them and events fail to serialize in release builds
# only — the worst possible time to find out.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.funnelbase.sdk.** {
    *** Companion;
}
-keepclasseswithmembers class com.funnelbase.sdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.funnelbase.sdk.**$$serializer { *; }
