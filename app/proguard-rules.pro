# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.luke.workouttracker.**$$serializer { *; }
-keepclassmembers class com.luke.workouttracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.luke.workouttracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
