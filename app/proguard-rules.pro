# kotlinx.serialization: keep generated serializers for seed JSON + nav routes
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.mealprepper.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mealprepper.app.**$$serializer { *; }
