# Add project specific ProGuard rules here.
# Compose and Hilt ship their own consumer rules; Retrofit/OkHttp rely on
# generic signatures. Keep the model/DTO packages so kotlinx.serialization
# and Retrofit reflection keep working after minification.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keepclassmembers class app.cuentasclaras.**.dto.** { *; }
-keepclassmembers class app.cuentasclaras.**.model.** { *; }
