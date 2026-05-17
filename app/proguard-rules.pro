# Homecam-TE ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.homecam.te.model.** { *; }
-keep class com.homecam.te.network.EventResponseItem { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
