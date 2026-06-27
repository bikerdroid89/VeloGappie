# Strip verbose and debug log calls from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# MapLibre
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# Health Connect
-keep class androidx.health.connect.** { *; }

# Compose
-dontwarn androidx.compose.**

# Coroutines
-dontwarn kotlinx.coroutines.**
