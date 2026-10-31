# Shizuku talks to its server through Binder/AIDL classes that must keep their names.
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-keep class moe.shizuku.** { *; }

# Keep line numbers for readable crash reports, but hide the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
