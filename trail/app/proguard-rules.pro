# kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature,EnclosingMethod

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class dev.kevin.trail.core.payload.** { *; }

# SQLCipher
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.**

# osmdroid is loaded reflectively for tile providers
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Drop verbose logs from release builds. Trail never logs the webhook or token.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
