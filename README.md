# KyvShield Android SDK

**KyvShield** — Android SDK for identity verification (KYC). Selfie liveness, document OCR, face matching.

[![](https://jitpack.io/v/moussa-innolink/kyv_android.svg)](https://jitpack.io/#moussa-innolink/kyv_android)

## Installation

Add JitPack repository and the dependency:

```kotlin
// settings.gradle.kts
repositories {
    maven { url = uri("https://jitpack.io") }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.moussa-innolink:kyv_android:0.0.3")
}
```

## Full Example

All possible enum values listed. Code is copy-paste ready.

```kotlin
import sn.innolink.kyvshield.lite.KyvshieldLite
import sn.innolink.kyvshield.lite.KyvshieldInput
import sn.innolink.kyvshield.lite.config.*
import sn.innolink.kyvshield.lite.result.*

// ── Possible values ───────────────────────────────────────────────────
// CaptureStep        : selfie, recto, verso
// ChallengeMode      : minimal, standard, strict
// SelfieDisplayMode  : standard, compact, immersive, neonHud
// DocumentDisplayMode: standard, compact, immersive, neonHud
// ChallengeAudioRepeat: once, twice, thrice

// ── Fetch documents from API ──────────────────────────────────────────
// GET https://kyvshield-naruto.innolinkcloud.com/api/v1/documents
// Header: X-API-Key: YOUR_API_KEY
// Parse response → List<KyvshieldDocument> via KyvshieldDocument.fromJson()

// ── With Jetpack Compose (ActivityResultContract) ─────────────────────

val kycLauncher = rememberLauncherForActivityResult(
    KyvshieldLite.getResultContract()
) { result: KYCResult ->
    // Handle result
    println("Success: ${result.success}")
    println("Status: ${result.overallStatus}")
}

// Build config
val config = KyvshieldConfig(
    baseUrl = "https://kyvshield-naruto.innolinkcloud.com",
    apiKey = "YOUR_API_KEY",
    enableLog = true,
    theme = KyvshieldThemeConfig(
        primaryColor = 0xFFEF8352.toInt(),
        darkMode = false,
    ),
)

// Build flow
val flow = KyvshieldFlowConfig(
    steps = listOf(CaptureStep.selfie, CaptureStep.recto, CaptureStep.verso),
    language = "fr",
    showIntroPage = true,
    showInstructionPages = true,
    showResultPage = true,
    showSuccessPerStep = true,
    selfieDisplayMode = SelfieDisplayMode.standard,
    documentDisplayMode = DocumentDisplayMode.standard,
    challengeMode = ChallengeMode.minimal,
    requireFaceMatch = true,
    playChallengeAudio = true,
    maxChallengeAudioPlay = ChallengeAudioRepeat.once,
    pauseBetweenAudioPlayMs = 1000L,
    stepChallengeModes = mapOf(
        CaptureStep.selfie to ChallengeMode.minimal,
        CaptureStep.recto to ChallengeMode.standard,
        CaptureStep.verso to ChallengeMode.minimal,
    ),
    target = selectedDoc,
    kycIdentifier = "user-12345",
)

// Launch KYC
kycLauncher.launch(KyvshieldInput(config, flow))

// ── Handle result ─────────────────────────────────────────────────────

println("Success: ${result.success}")
println("Status: ${result.overallStatus}")   // pass, reject, error
println("Session: ${result.sessionId}")

// Selfie
result.selfieResult?.let {
    println("Live: ${it.isLive}")
    println("Image: ${it.capturedImage?.size ?: 0} bytes")
}

// Recto
result.rectoResult?.let {
    println("Recto score: ${it.score}")
    println("Aligned doc: ${it.alignedDocument?.size ?: 0} bytes")
    println("Photos: ${it.extractedPhotos.size}")
    println("Fields: ${it.extraction?.fields?.size ?: 0}")
    it.faceVerification?.let { fv ->
        println("Face match: ${fv.isMatch}")
        println("Similarity: ${fv.similarityScore}")
    }
}

// Verso
result.versoResult?.let {
    println("Verso score: ${it.score}")
    println("Fields: ${it.extraction?.fields?.size ?: 0}")
}

// Extracted data (searches recto + verso)
println("Nom: ${result.getExtractedValue("nom")}")
println("NIN: ${result.getExtractedValue("nin")}")

// Loop all fields sorted by priority
result.rectoResult?.extraction?.sortedFields?.forEach { field ->
    println("${field.label}: ${field.stringValue}")
}
```

## Display Modes

| Mode | Description |
|------|-------------|
| `standard` | Classic layout with header, camera, and instructions below |
| `compact` | Camera fills screen, instructions overlay at bottom |
| `immersive` | Full-screen camera with glass-effect overlays |
| `neonHud` | Futuristic dark theme with glow effects and monospace font |

## Permission Helpers

```kotlin
KyvshieldLite.checkCameraPermission(context)
KyvshieldLite.requestCameraPermission(activity)
KyvshieldLite.isCameraPermissionPermanentlyDenied(activity)
KyvshieldLite.openSettings(context)
```

## Platform Setup

`AndroidManifest.xml` (SDK declares these automatically via manifest merger):

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

Min SDK: **21** (Android 5.0 Lollipop)

## API Documentation

Full documentation: **[https://kyvshield-naruto.innolinkcloud.com/developer](https://kyvshield-naruto.innolinkcloud.com/developer)**

## License

BSD 3-Clause License. See [LICENSE](LICENSE).
