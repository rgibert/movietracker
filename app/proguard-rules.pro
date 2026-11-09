# Keep attributes needed by Retrofit's generic signatures.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Keep generic signatures for Retrofit service interfaces.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
