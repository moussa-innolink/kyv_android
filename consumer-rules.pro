# Consumer ProGuard rules — applied to the host app when it depends on this library

# Keep all public SDK classes and their members
-keep public class sn.innolink.kyvshield.lite.** { public *; }

# Keep the JS bridge interface (required for @JavascriptInterface to work)
-keepclassmembers class sn.innolink.kyvshield.lite.internal.KyvshieldWebViewActivity$KyvShieldBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve enum names (used in JS bridge serialisation)
-keepclassmembers enum sn.innolink.kyvshield.lite.** { *; }
