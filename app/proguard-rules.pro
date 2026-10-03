# ProGuard / R8 Rules for Kisan Kendra Release Build

# 1. Keep Data Models & Value Classes (Prevents R8 from obfuscating fields used in Gson / Firestore serialization)
-keep class shop.sabjiwala.kisankendra.model.** { *; }
-keepclassmembers class shop.sabjiwala.kisankendra.model.** { *; }

# 2. Keep Classes Annotated with @Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# 3. Retrofit 2 & OkHttp & Gson
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers enum * { *; }
-keep interface shop.sabjiwala.kisankendra.api.** { *; }
-dontwarn retrofit2.**
-keep class com.google.gson.** { *; }

# 4. Enable Class Repackaging for DEX size optimization
-repackageclasses ''

# 5. Retain Line Numbers & Source File attributes for crash logging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
