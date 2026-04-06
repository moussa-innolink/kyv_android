# KyvShield Lite SDK ProGuard rules

# Keep all public SDK classes and their members
-keep public class sn.innolink.kyvshield.lite.** { public *; }

# Keep the JS bridge — annotated with @JavascriptInterface; must not be renamed
-keepclassmembers class sn.innolink.kyvshield.lite.internal.KyvshieldWebViewActivity$KyvShieldBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the ActivityResultContract input/output data classes
-keep class sn.innolink.kyvshield.lite.KyvshieldInput { *; }
-keep class sn.innolink.kyvshield.lite.KyvshieldResultContract { *; }

# Keep all result and config data classes (needed for reflection-free JSON parsing)
-keep class sn.innolink.kyvshield.lite.result.** { *; }
-keep class sn.innolink.kyvshield.lite.config.** { *; }

# Preserve enum names (used in JS bridge serialisation)
-keepclassmembers enum sn.innolink.kyvshield.lite.** { *; }
