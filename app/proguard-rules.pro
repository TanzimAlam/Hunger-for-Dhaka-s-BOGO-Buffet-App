# Keep shrinking but do not strip Room/Compose metadata
-keep class androidx.room.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
