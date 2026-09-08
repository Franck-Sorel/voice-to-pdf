# Keep the JNI bridge class: its methods are called from native code via
# reflection/name-matching, so they must never be renamed or stripped.
-keep class com.sttapp.data.recognition.WhisperNative { *; }

# Room
-keep class * extends androidx.room.RoomDatabase

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
