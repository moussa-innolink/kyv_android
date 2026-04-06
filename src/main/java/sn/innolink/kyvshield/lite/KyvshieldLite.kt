package sn.innolink.kyvshield.lite

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import sn.innolink.kyvshield.lite.config.KyvshieldConfig
import sn.innolink.kyvshield.lite.config.KyvshieldFlowConfig
import sn.innolink.kyvshield.lite.internal.KyvshieldWebViewActivity
import sn.innolink.kyvshield.lite.result.KYCResult
import kotlin.coroutines.resume

/**
 * Main entry point for the KyvShield Lite Android SDK.
 *
 * ## Quick-start
 *
 * ```kotlin
 * // From a traditional Activity (non-Compose):
 * KyvshieldLite.initKyc(
 *     activity = this,
 *     config   = KyvshieldConfig(
 *         baseUrl = "https://kyvshield-naruto.innolinkcloud.com",
 *         apiKey  = "your-api-key"
 *     ),
 *     flow = KyvshieldFlowConfig.standard()
 * ) { result ->
 *     if (result.success) {
 *         val name = result.getExtractedValue("first_name")
 *     }
 * }
 *
 * // From Compose (using ActivityResultContract):
 * val launcher = rememberLauncherForActivityResult(
 *     contract = KyvshieldLite.getResultContract()
 * ) { result -> handleResult(result) }
 *
 * Button(onClick = {
 *     launcher.launch(KyvshieldInput(config, flow))
 * }) { Text("Start KYC") }
 * ```
 */
object KyvshieldLite {

    // ── Core KYC flow ─────────────────────────────────────────────────────────

    /**
     * Launch the full-screen KYC WebView and deliver the result via [onResult].
     *
     * Internally uses [ActivityResultLauncher] registered against the calling
     * [Activity]'s result. This is the preferred entry-point for traditional
     * (non-Compose) Android UIs.
     *
     * @param activity  The calling [Activity] used to start the WebView.
     * @param config    SDK configuration including the API URL and API key.
     * @param flow      Flow configuration: steps, challenge mode, language, etc.
     * @param onResult  Callback invoked with the [KYCResult] once the flow ends.
     *                  Always called, even on error or cancellation.
     */
    fun initKyc(
        activity: Activity,
        config: KyvshieldConfig,
        flow: KyvshieldFlowConfig = KyvshieldFlowConfig(),
        onResult: (KYCResult) -> Unit
    ) {
        val intent = buildIntent(activity, config, flow)
        activity.startActivityForResult(intent, REQUEST_CODE)

        // Store the callback so onActivityResult can deliver it
        _pendingCallback = onResult
    }

    /**
     * Internal request code used with [Activity.startActivityForResult].
     * Exposed so host activities can forward `onActivityResult` calls.
     */
    const val REQUEST_CODE = 0x4B59  // "KY"

    /**
     * Pending callback — set by [initKyc], consumed by [handleActivityResult].
     * Only one KYC session runs at a time.
     */
    @Volatile
    private var _pendingCallback: ((KYCResult) -> Unit)? = null

    /**
     * Forward `onActivityResult` from the host [Activity] to the SDK.
     *
     * Call this from your `Activity.onActivityResult` override:
     *
     * ```kotlin
     * override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
     *     super.onActivityResult(requestCode, resultCode, data)
     *     KyvshieldLite.handleActivityResult(requestCode, resultCode, data)
     * }
     * ```
     *
     * @return `true` if the result was consumed by the SDK.
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val callback = _pendingCallback ?: return false
        _pendingCallback = null

        val result = extractResult(resultCode, data)
        callback(result)
        return true
    }

    // ── ActivityResultContract (Compose / modern API) ─────────────────────────

    /**
     * Get an [ActivityResultContract] for use with the Jetpack
     * `registerForActivityResult` API or Compose's `rememberLauncherForActivityResult`.
     *
     * ```kotlin
     * val launcher = rememberLauncherForActivityResult(
     *     contract = KyvshieldLite.getResultContract()
     * ) { result: KYCResult -> /* handle */ }
     *
     * launcher.launch(KyvshieldInput(config, flow))
     * ```
     */
    fun getResultContract(): KyvshieldResultContract = KyvshieldResultContract()

    // ── Permission helpers ────────────────────────────────────────────────────

    /**
     * Check whether the camera permission is currently granted.
     *
     * This is a fast synchronous check — no dialog is shown.
     */
    suspend fun checkCameraPermission(context: Context): Boolean =
        withContext(Dispatchers.Main) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        }

    /**
     * Request the camera permission from the given [Activity].
     *
     * Suspends until the user responds to the system permission dialog.
     * Returns `true` if granted.
     *
     * Note: The WebView itself also requests camera permission via
     * [android.webkit.WebChromeClient.onPermissionRequest], so calling this
     * before [initKyc] is optional but recommended for a smoother UX.
     */
    suspend fun requestCameraPermission(activity: Activity): Boolean =
        suspendCancellableCoroutine { cont ->
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
                cont.resume(true)
                return@suspendCancellableCoroutine
            }
            // Register a one-shot result handler
            _pendingPermissionContinuation = cont
            activity.requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }

    private const val CAMERA_PERMISSION_REQUEST_CODE = 0x4B5A  // "KZ"

    @Volatile
    private var _pendingPermissionContinuation:
        kotlinx.coroutines.CancellableContinuation<Boolean>? = null

    /**
     * Forward `onRequestPermissionsResult` to the SDK.
     *
     * ```kotlin
     * override fun onRequestPermissionsResult(
     *     requestCode: Int,
     *     permissions: Array<out String>,
     *     grantResults: IntArray
     * ) {
     *     super.onRequestPermissionsResult(requestCode, permissions, grantResults)
     *     KyvshieldLite.handlePermissionsResult(requestCode, permissions, grantResults)
     * }
     * ```
     *
     * @return `true` if consumed by the SDK.
     */
    fun handlePermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != CAMERA_PERMISSION_REQUEST_CODE) return false
        val cont = _pendingPermissionContinuation ?: return false
        _pendingPermissionContinuation = null
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        cont.resume(granted)
        return true
    }

    /**
     * Whether the camera permission is permanently denied
     * (user selected "Don't ask again").
     *
     * When this returns `true`, call [openSettings] so the user can manually
     * re-enable the permission.
     */
    fun isCameraPermissionPermanentlyDenied(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    }

    /**
     * Open the app's system settings page.
     *
     * Useful when the camera permission is permanently denied and the user
     * must grant it manually.
     */
    fun openSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Build the [Intent] that starts [KyvshieldWebViewActivity].
     */
    internal fun buildIntent(
        context: Context,
        config: KyvshieldConfig,
        flow: KyvshieldFlowConfig
    ): Intent {
        return Intent(context, KyvshieldWebViewActivity::class.java).apply {
            putExtra(KyvshieldWebViewActivity.EXTRA_CONFIG_JSON, buildConfigJson(config))
            putExtra(KyvshieldWebViewActivity.EXTRA_FLOW_JSON,   buildFlowJson(flow))
            putExtra(KyvshieldWebViewActivity.EXTRA_BASE_URL,    config.baseUrl)
            putExtra(KyvshieldWebViewActivity.EXTRA_ENABLE_LOG,  config.enableLog)
            putExtra(KyvshieldWebViewActivity.EXTRA_DARK_MODE,   config.theme?.darkMode?.let {
                if (it) 1 else 0
            } ?: -1)
        }
    }

    /**
     * Extract the [KYCResult] from an activity result.
     */
    internal fun extractResult(resultCode: Int, data: Intent?): KYCResult {
        if (resultCode != Activity.RESULT_OK) {
            return KYCResult.error("User cancelled")
        }
        // Read result from in-memory singleton (instant, no disk I/O, no size limit)
        val jsonStr = sn.innolink.kyvshield.lite.internal.ResultHolder.take()
            ?: return KYCResult.error("User cancelled")
        return KYCResult.fromJsonString(jsonStr)
    }

    // ── JSON serialisation ────────────────────────────────────────────────────

    /**
     * Serialise [KyvshieldConfig] to a JSON string compatible with the
     * JS SDK's `KyvShield.initKyc(config, flow)` call.
     */
    internal fun buildConfigJson(config: KyvshieldConfig): String {
        val obj = JSONObject().apply {
            put("baseUrl",        config.baseUrl)
            put("apiKey",         config.apiKey)
            put("apiVersion",     config.apiVersion)
            put("enableLog",      config.enableLog)
            put("timeoutSeconds", config.timeoutSeconds)
            config.clientId?.let { put("clientId", it) }

            // Theme
            config.theme?.let { theme ->
                val themeObj = JSONObject()
                theme.primaryHex?.let { themeObj.put("primaryColor", it) }
                theme.successHex?.let { themeObj.put("successColor", it) }
                theme.warningHex?.let { themeObj.put("warningColor", it) }
                theme.errorHex?.let   { themeObj.put("errorColor",   it) }
                theme.darkMode?.let   { themeObj.put("themeMode", if (it) "dark" else "light") }
                if (themeObj.length() > 0) put("theme", themeObj)
            }
        }
        return obj.toString()
    }

    /**
     * Serialise [KyvshieldFlowConfig] to a JSON string compatible with the
     * JS SDK's `KyvShield.initKyc(config, flow)` call.
     *
     * Uses the exact same key names as the Flutter Lite SDK's `_buildHtml()`.
     */
    internal fun buildFlowJson(flow: KyvshieldFlowConfig): String {
        val obj = JSONObject().apply {
            put("steps",               JSONArray(flow.stepsAsStrings))
            put("challengeMode",       flow.challengeMode.name)
            put("language",            flow.language)
            put("showIntroPage",       flow.showIntroPage)
            put("showInstructionPages",flow.showInstructionPages)
            put("showResultPage",      flow.showResultPage)
            put("showSuccessPerStep",  flow.showSuccessPerStep)
            put("requireFaceMatch",    flow.requireFaceMatch)
            put("playChallengeAudio",  flow.playChallengeAudio)
            put("maxChallengeAudioPlay", flow.maxChallengeAudioPlay.count)
            put("pauseBetweenAudioPlay", flow.pauseBetweenAudioPlayMs)
            put("selfieDisplayMode",   flow.selfieDisplayMode.name)
            put("documentDisplayMode", flow.documentDisplayMode.name)

            // Target document (snake_case — JS SDK expects kyvshieldDocumentFromJson)
            flow.target?.let { doc ->
                val sides = doc.supportedSides
                val targetObj = JSONObject().apply {
                    put("doc_type",                doc.docType)
                    put("document_category",       doc.category)
                    put("document_category_label", doc.categoryLabel)
                    put("name",                    doc.name)
                    put("country",                 doc.country)
                    put("country_name",            doc.countryName)
                    put("enabled",                 doc.enabled)
                    put("supported_sides", JSONObject().apply {
                        put("has_recto",               sides.hasRecto)
                        put("has_verso",               sides.hasVerso)
                        put("recto_min_char_ocr",      sides.rectoMinCharOcr)
                        put("recto_min_block_ocr",     sides.rectoMinBlockOcr)
                        put("recto_min_char_ocr_web",  sides.rectoMinCharOcrWeb)
                        put("recto_min_block_ocr_web", sides.rectoMinBlockOcrWeb)
                        put("verso_min_char_ocr",      sides.versoMinCharOcr)
                        put("verso_min_block_ocr",     sides.versoMinBlockOcr)
                        put("verso_min_char_ocr_web",  sides.versoMinCharOcrWeb)
                        put("verso_min_block_ocr_web", sides.versoMinBlockOcrWeb)
                    })
                }
                put("target", targetObj)
            }

            // kycIdentifier
            flow.kycIdentifier?.let { put("kycIdentifier", it) }

            // Per-step challenge modes
            if (flow.stepChallengeModes.isNotEmpty()) {
                val stepModes = JSONObject()
                flow.stepChallengeModes.forEach { (step, mode) ->
                    stepModes.put(step.name, mode.name)
                }
                put("stepChallengeModes", stepModes)
            }
        }
        return obj.toString()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ActivityResultContract
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Input data for [KyvshieldResultContract].
 */
data class KyvshieldInput(
    val config: KyvshieldConfig,
    val flow: KyvshieldFlowConfig = KyvshieldFlowConfig()
)

/**
 * [ActivityResultContract] that wraps a [KyvshieldWebViewActivity] session.
 *
 * Use with Jetpack's `registerForActivityResult` API or Compose's
 * `rememberLauncherForActivityResult`:
 *
 * ```kotlin
 * // Compose
 * val launcher = rememberLauncherForActivityResult(
 *     KyvshieldLite.getResultContract()
 * ) { result: KYCResult ->
 *     if (result.success) { /* handle */ }
 * }
 * launcher.launch(KyvshieldInput(config, flow))
 *
 * // Fragment / ComponentActivity
 * val launcher = registerForActivityResult(KyvshieldResultContract()) { result ->
 *     handleResult(result)
 * }
 * launcher.launch(KyvshieldInput(config, flow))
 * ```
 */
class KyvshieldResultContract : ActivityResultContract<KyvshieldInput, KYCResult>() {

    override fun createIntent(context: Context, input: KyvshieldInput): Intent =
        KyvshieldLite.buildIntent(context, input.config, input.flow)

    override fun parseResult(resultCode: Int, intent: Intent?): KYCResult =
        KyvshieldLite.extractResult(resultCode, intent)
}
