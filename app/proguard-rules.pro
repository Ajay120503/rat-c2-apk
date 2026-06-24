# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep our app classes
-keep class com.rat.client.** { *; }
-keep class com.rat.client.models.** { *; }

# Keep Config
-keep class com.rat.client.Config { *; }

