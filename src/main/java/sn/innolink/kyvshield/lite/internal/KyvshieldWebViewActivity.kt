package sn.innolink.kyvshield.lite.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import sn.innolink.kyvshield.lite.config.KyvshieldConfig
import sn.innolink.kyvshield.lite.config.KyvshieldFlowConfig
import sn.innolink.kyvshield.lite.result.KYCResult

/**
 * Internal full-screen WebView activity that bootstraps the KyvShield Web SDK.
 *
 * Lifecycle:
 *  1. [onCreate] — builds [WebView], registers [KyvShieldBridge] JS interface, loads HTML.
 *  2. HTML bootstrap injects the remote JS SDK via `<script src="…">`.
 *  3. JS SDK calls `window.KyvShield.initKyc(config, flow)` which runs the KYC flow.
 *  4. JS SDK posts the result to `KyvShieldBridge.onResult(jsonStr)`.
 *  5. Activity returns via `setResult(RESULT_OK)` with the result JSON in the intent.
 *
 * Never instantiate directly — use [sn.innolink.kyvshield.lite.KyvshieldLite.initKyc].
 */
/** In-memory result holder — avoids TransactionTooLargeException on large JSON results.
 *  Instant transfer (0ms), no disk I/O. Cleared after reading. */
internal object ResultHolder {
    @Volatile var json: String? = null
    fun take(): String? { val v = json; json = null; return v }
}

@SuppressLint("SetJavaScriptEnabled")
internal class KyvshieldWebViewActivity : AppCompatActivity() {

    companion object {
        internal const val EXTRA_CONFIG_JSON = "kyv_config_json"
        internal const val EXTRA_FLOW_JSON   = "kyv_flow_json"
        internal const val EXTRA_RESULT_JSON = "kyv_result_json"
        internal const val EXTRA_BASE_URL    = "kyv_base_url"
        internal const val EXTRA_ENABLE_LOG  = "kyv_enable_log"
        internal const val EXTRA_DARK_MODE   = "kyv_dark_mode"   // -1=system, 0=light, 1=dark

        private const val TAG = "KyvShieldLite"
        private const val SDK_VERSION = "0.0.4"
    }

    private lateinit var webView: WebView
    private var loadingOverlay: android.widget.FrameLayout? = null
    private var resultReceived = false

    // Parsed from intent
    private var configJson   = "{}"
    private var flowJson     = "{}"
    private var baseUrl      = ""
    private var enableLog    = false
    private var darkMode     = -1  // -1=system, 0=light, 1=dark

    // Colors derived from darkMode
    private val bgColor: Int get() = if (isDark) Color.parseColor("#0F172A") else Color.WHITE
    private val isDark: Boolean get() = darkMode == 1
    private val primaryColorHex: String get() {
        return try {
            val cfg = org.json.JSONObject(configJson)
            cfg.optJSONObject("theme")?.optString("primaryColor", "#EF8352") ?: "#EF8352"
        } catch (_: Exception) { "#EF8352" }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse extras
        configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: "{}"
        flowJson   = intent.getStringExtra(EXTRA_FLOW_JSON)   ?: "{}"
        baseUrl    = intent.getStringExtra(EXTRA_BASE_URL)    ?: ""
        enableLog  = intent.getBooleanExtra(EXTRA_ENABLE_LOG, false)
        darkMode   = intent.getIntExtra(EXTRA_DARK_MODE, -1)

        // Status bar styling
        applyStatusBarStyle()

        // Build layout: FrameLayout with WebView + loading overlay
        // SafeArea: fitsSystemWindows respects status bar
        val root = android.widget.FrameLayout(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(bgColor)
        }

        webView = WebView(this)
        webView.setBackgroundColor(bgColor)
        root.addView(webView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Native loading overlay (matching Flutter's _KyvshieldLoadingOverlay)
        loadingOverlay = android.widget.FrameLayout(this).apply {
            setBackgroundColor(bgColor)
            val textView = android.widget.TextView(this@KyvshieldWebViewActivity).apply {
                text = "Initialisation\u2026"
                setTextColor(android.graphics.Color.parseColor(primaryColorHex))
                textSize = 15f
                gravity = android.view.Gravity.CENTER
            }
            addView(textView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ))
        }
        root.addView(loadingOverlay, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        // Configure WebView
        configureWebView()

        // Register JS bridge
        webView.addJavascriptInterface(KyvShieldBridge(), "KyvShieldBridge")

        // Load HTML
        val html = buildHtml()
        log("HTML template generated (${html.length} chars)")
        webView.loadDataWithBaseURL(
            baseUrl.trimEnd('/'),
            html,
            "text/html",
            "UTF-8",
            null
        )

        // Back-press → cancel
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                returnCancel()
            }
        })
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("KyvShieldBridge")
        webView.destroy()
        super.onDestroy()
    }

    // ── WebView configuration ─────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings: WebSettings = webView.settings

        // JavaScript (required by the SDK)
        settings.javaScriptEnabled = true

        // DOM storage + database (IndexedDB, localStorage)
        settings.domStorageEnabled = true
        settings.databaseEnabled   = true

        // Media — allow camera/video without user gesture
        settings.mediaPlaybackRequiresUserGesture = false

        // Allow mixed content (JS SDK may load assets from same origin)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Zoom off
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        // User-Agent — append KyvShield identifier
        val defaultUA = settings.userAgentString ?: ""
        settings.userAgentString = "$defaultUA KyvShield/1.0"
        log("User-Agent: ${settings.userAgentString}")

        // Enable WebView debugging in dev builds
        if (enableLog) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Navigation delegate
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                log("Page finished: $url")
                // Hide loading overlay after 500ms (matching Flutter behavior)
                view?.postDelayed({
                    loadingOverlay?.animate()?.alpha(0f)?.setDuration(300)?.withEndAction {
                        loadingOverlay?.visibility = android.view.View.GONE
                    }?.start()
                }, 500)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    log("WebResource error [${error?.errorCode}]: ${error?.description} (${request?.url})")
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Let the WebView handle all navigation (SDK may use pushState)
                val url = request?.url?.toString()
                log("Navigation: $url")
                return false
            }
        }

        // Chrome client — camera permission + console logging
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                log("Permission request: ${request.resources.joinToString()}")
                request.grant(request.resources)
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                log("JS [${consoleMessage.messageLevel()}]: ${consoleMessage.message()}")
                return true
            }
        }
    }

    // ── Status bar ────────────────────────────────────────────────────────────

    private fun applyStatusBarStyle() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = bgColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decorView = window.decorView
            var flags = decorView.systemUiVisibility
            flags = if (isDark) {
                // Dark background → light icons
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                // Light background → dark icons
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            decorView.systemUiVisibility = flags
        }
    }

    // ── Result handling ───────────────────────────────────────────────────────

    private fun returnResult(jsonString: String) {
        if (resultReceived) return
        resultReceived = true
        log("returnResult: ${jsonString.length} chars")

        // Store result in memory singleton to avoid TransactionTooLargeException.
        // Android Intent extras are limited to ~1MB, but KYC results with base64
        // images can be 200KB-2MB. Memory transfer is instant (0ms).
        ResultHolder.json = jsonString
        setResult(Activity.RESULT_OK, Intent())
        finish()
    }

    private fun returnCancel() {
        if (resultReceived) return
        resultReceived = true
        val cancelJson = KYCResult.error("User cancelled")
        val cancelStr  = JSONObject().apply {
            put("success", false)
            put("overall_status", "ERROR")
            put("error_message", "User cancelled")
        }.toString()
        val intent = Intent().apply { putExtra(EXTRA_RESULT_JSON, cancelStr) }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private fun log(msg: String) {
        if (enableLog) Log.d(TAG, msg)
    }

    // ── JS Bridge ─────────────────────────────────────────────────────────────

    /**
     * JavaScript interface exposed as `window.KyvShieldBridge`.
     *
     * The JS SDK calls `KyvShieldBridge.postMessage(jsonStr)` with the result.
     */
    inner class KyvShieldBridge {
        @JavascriptInterface
        fun postMessage(jsonStr: String) {
            log("Bridge received (${jsonStr.length} chars)")
            log("Bridge preview: ${jsonStr.take(800)}")

            val result = KYCResult.fromJsonString(jsonStr)
            log("Parsed: success=${result.success}, status=${result.overallStatus}, error=${result.errorMessage}")
            log("Selfie: ${result.selfieResult != null}, Recto: ${result.rectoResult != null}, Verso: ${result.versoResult != null}")

            result.selfieResult?.let {
                log("  selfie: live=${it.isLive}, capturedImage=${it.capturedImage?.size ?: 0} bytes")
            }
            result.rectoResult?.let {
                log("  recto: status=${it.status}, alignedDoc=${it.alignedDocument?.size ?: 0} bytes, " +
                    "fields=${it.extraction?.fields?.size ?: 0}, photos=${it.extractedPhotos.size}, " +
                    "faceMatch=${it.faceVerification?.isMatch}")
            }
            result.versoResult?.let {
                log("  verso: status=${it.status}, alignedDoc=${it.alignedDocument?.size ?: 0} bytes, " +
                    "fields=${it.extraction?.fields?.size ?: 0}, photos=${it.extractedPhotos.size}")
            }

            // Return result on main thread
            runOnUiThread { returnResult(jsonStr) }
        }
    }

    // ── HTML generation ───────────────────────────────────────────────────────

    /**
     * Build the HTML bootstrap page.
     *
     * The generated HTML is identical in structure to the Flutter Lite SDK's
     * `_buildHtml()` method — same CSS, same JS bridge pattern, same SDK
     * bootstrap sequence.
     */
    private fun buildHtml(): String {
        val sdkUrl = "${baseUrl.trimEnd('/')}/static/sdk/kyvshield.min.js"
        val htmlBg = if (isDark) "#0F172A" else "#FFFFFF"
        val spinnerTrack = if (isDark) "#334155" else "#F1F5F9"
        val primaryHex = try {
            val cfg = JSONObject(configJson)
            val theme = cfg.optJSONObject("theme")
            theme?.optString("primaryColor", "#EF8352") ?: "#EF8352"
        } catch (_: Exception) { "#EF8352" }

        // Determine language from flow JSON
        val language = try {
            JSONObject(flowJson).optString("language", "fr")
        } catch (e: Exception) { "fr" }

        return """<!DOCTYPE html>
<html lang="$language">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport"
        content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <title>KyvShield KYC</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    html, body { width: 100%; height: 100%; background: $htmlBg; overflow: hidden; }
    #kyc-root { width: 100%; height: 100%; }
    /* HTML loading — pulsing rings animation */
    #html-loading {
      position: fixed;
      inset: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      background: $htmlBg;
      z-index: 9999;
      gap: 24px;
    }
    .pulse-container {
      position: relative;
      width: 100px;
      height: 100px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .pulse-ring {
      position: absolute;
      border: 2px solid $primaryHex;
      border-radius: 50%;
      animation: pulse-expand 2.4s ease-out infinite;
    }
    .pulse-ring:nth-child(1) { width: 90px; height: 90px; animation-delay: 0s; }
    .pulse-ring:nth-child(2) { width: 70px; height: 70px; animation-delay: 0.4s; }
    .pulse-ring:nth-child(3) { width: 50px;  height: 50px;  animation-delay: 0.8s; }
    @keyframes pulse-expand {
      0%   { transform: scale(0.5); opacity: 0.6; }
      100% { transform: scale(1.4); opacity: 0; }
    }
    .loading-dots {
      display: flex;
      gap: 8px;
      align-items: center;
    }
    .loading-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: $primaryHex;
      animation: dot-bounce 1.4s ease-in-out infinite;
    }
    .loading-dot:nth-child(1) { animation-delay: 0s; }
    .loading-dot:nth-child(2) { animation-delay: 0.2s; }
    .loading-dot:nth-child(3) { animation-delay: 0.4s; }
    @keyframes dot-bounce {
      0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
      40% { opacity: 1; transform: scale(1.2); }
    }
    .html-loading-text {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 14px;
      color: $primaryHex;
      letter-spacing: 0.5px;
    }
    #error-overlay {
      display: none;
      position: fixed;
      inset: 0;
      background: #0D0D0D;
      color: #fff;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      align-items: center;
      justify-content: center;
      flex-direction: column;
      gap: 16px;
      padding: 32px;
      text-align: center;
      z-index: 9998;
    }
    #error-overlay.visible { display: flex; }
    #error-msg { color: #EF4444; font-size: 14px; word-break: break-word; }
    #retry-btn {
      padding: 12px 24px;
      background: #EF8352;
      color: #fff;
      border: none;
      border-radius: 8px;
      font-size: 16px;
      cursor: pointer;
    }
  </style>
</head>
<body>
  <div id="html-loading">
    <div class="pulse-container">
      <div class="pulse-ring"></div>
      <div class="pulse-ring"></div>
      <div class="pulse-ring"></div>
    </div>
    <div class="loading-dots">
      <div class="loading-dot"></div>
      <div class="loading-dot"></div>
      <div class="loading-dot"></div>
    </div>
    <span class="html-loading-text">Chargement\u2026</span>
  </div>

  <div id="error-overlay">
    <p>Une erreur est survenue.</p>
    <p id="error-msg"></p>
    <button id="retry-btn" onclick="location.reload()">R\u00e9essayer</button>
  </div>

  <div id="kyc-root"></div>

  <script src="$sdkUrl" onerror="onSdkLoadError()"></script>

  <script>
    // ── Uint8Array → base64 conversion ──────────────────────────────────────
    // JSON.stringify(Uint8Array) produces {"0":255,"1":216,...} which is ~10x
    // larger than base64. This helper recursively converts all Uint8Array and
    // ArrayBuffer values to base64 strings before serialisation, so the Android
    // side can use the fast base64Decode path in parseBytes().
    function _u8ToB64(u8) {
      var bin = '';
      var len = u8.length;
      for (var i = 0; i < len; i++) bin += String.fromCharCode(u8[i]);
      return btoa(bin);
    }
    function _prepareForBridge(obj) {
      if (obj === null || obj === undefined) return obj;
      if (obj instanceof Uint8Array) return _u8ToB64(obj);
      if (obj instanceof ArrayBuffer) return _u8ToB64(new Uint8Array(obj));
      if (Array.isArray(obj)) return obj.map(_prepareForBridge);
      if (typeof obj === 'object' && obj.constructor === Object) {
        var out = {};
        for (var k in obj) {
          if (obj.hasOwnProperty(k)) out[k] = _prepareForBridge(obj[k]);
        }
        return out;
      }
      return obj;
    }

    // ── Bridge setup ─────────────────────────────────────────────────────────
    // Android: KyvShieldBridge is a Java object injected via addJavascriptInterface.
    // CRITICAL: Do NOT overwrite window.KyvShieldBridge — it would destroy the Java binding.
    // Instead, create a wrapper that calls the Java postMessage directly.
    (function() {
      // Store reference to the Java-injected bridge
      var _javaBridge = window.KyvShieldBridge;

      // Create the onResult callback that the JS SDK will call
      window._kyvBridgeOnResult = function(jsonStr) {
        var msg = typeof jsonStr === 'string' ? jsonStr : JSON.stringify(jsonStr);
        console.log('[KyvShieldBridge] onResult called, length=' + msg.length);
        try {
          if (_javaBridge && typeof _javaBridge.postMessage === 'function') {
            _javaBridge.postMessage(msg);
          } else if (window.KyvShieldBridge && typeof window.KyvShieldBridge.postMessage === 'function') {
            window.KyvShieldBridge.postMessage(msg);
          } else {
            console.error('[KyvShieldBridge] postMessage not available on bridge object');
          }
        } catch(e) {
          console.error('[KyvShieldBridge] postMessage error: ' + e);
        }
      };

      // Also expose as KyvShieldBridge.onResult for the SDK to find
      // But do NOT reassign window.KyvShieldBridge itself
      try {
        window.KyvShieldBridge.onResult = window._kyvBridgeOnResult;
      } catch(e) {
        // Java interface may not allow property assignment — that's OK
        console.warn('[KyvShieldBridge] Could not set onResult on Java bridge: ' + e);
      }
    })();

    function showError(msg) {
      var hl = document.getElementById('html-loading');
      if (hl) hl.style.display = 'none';
      var overlay = document.getElementById('error-overlay');
      overlay.classList.add('visible');
      document.getElementById('error-msg').textContent = msg || '';
      var errPayload = JSON.stringify({
        success: false,
        overall_status: 'ERROR',
        error_message: msg || 'Unknown SDK error'
      });
      try {
        if (window._kyvBridgeOnResult) { window._kyvBridgeOnResult(errPayload); }
        else if (window.KyvShieldBridge && window.KyvShieldBridge.postMessage) { window.KyvShieldBridge.postMessage(errPayload); }
      } catch(e) { console.error('[KyvShieldBridge] Error sending error: ' + e); }
    }

    function onSdkLoadError() {
      showError('Impossible de charger le SDK depuis le serveur ($sdkUrl).');
    }

    // ── Preload model + audio while user reads intro/instructions ──────────
    function preloadResources() {
      try {
        if (typeof KyvShield === 'undefined') return;
        if (KyvShield.FaceLandmarkerService && KyvShield.FaceLandmarkerService.preloadModel) {
          KyvShield.FaceLandmarkerService.preloadModel();
          console.log('[KyvShield] FaceLandmarker model preload initiated');
        }
        if (KyvShield.KyvSoundHelper) {
          var baseUrl = '$baseUrl';
          KyvShield.KyvSoundHelper.setBasePath(baseUrl + '/static/sdk/assets/sounds');
          KyvShield.KyvSoundHelper.setChallengeBasePath(baseUrl + '/static/sdk/assets/challenges');
          KyvShield.KyvSoundHelper.setEnabled(true);
          KyvShield.KyvSoundHelper.preloadChallengeAudio('$language');
          console.log('[KyvShield] Audio preload initiated ($language)');
        }
      } catch(e) { console.warn('[KyvShield] Preload error:', e); }
    }
    // Start preload immediately after SDK script loads
    preloadResources();

    async function bootstrapKyc() {
      try {
        if (typeof KyvShield === 'undefined') {
          showError('KyvShield SDK introuvable apr\u00e8s chargement du script.');
          return;
        }
        // Hide HTML loading spinner — SDK is about to mount
        var htmlLoading = document.getElementById('html-loading');
        if (htmlLoading) htmlLoading.style.display = 'none';

        var config = $configJson;
        var flow   = $flowJson;
        var result = await KyvShield.initKyc(config, flow);

        // Convert Uint8Arrays to base64 before JSON.stringify
        // This reduces payload ~10x (130K-key objects → compact base64 strings)
        if (typeof result === 'object' && result !== null) {
          result = _prepareForBridge(result);
        }

        // Use the wrapper function (not the Java bridge directly, which may not support onResult)
        var resultStr = typeof result === 'string' ? result : JSON.stringify(result);
        console.log('[KyvShieldBridge] Sending result (' + resultStr.length + ' chars)');
        if (window._kyvBridgeOnResult) {
          window._kyvBridgeOnResult(resultStr);
        } else if (window.KyvShieldBridge && window.KyvShieldBridge.postMessage) {
          window.KyvShieldBridge.postMessage(resultStr);
        } else {
          console.error('[KyvShieldBridge] No bridge available to send result');
        }
      } catch (err) {
        showError(err && err.message ? err.message : String(err));
      }
    }

    window.addEventListener('load', function() {
      bootstrapKyc();
    });
  </script>
</body>
</html>"""
    }
}
