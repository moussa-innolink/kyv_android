package sn.innolink.kyvshield.lite.config

/**
 * Flow configuration — which capture steps to include and all UI options.
 *
 * Works for ALL countries and ALL document types.
 * Mirrors `KyvshieldFlowConfig` in the Flutter Lite SDK.
 *
 * ```kotlin
 * // Standard flow: selfie + recto
 * val flow = KyvshieldFlowConfig.standard()
 *
 * // Full flow with French UI
 * val flow = KyvshieldFlowConfig.full(language = "fr")
 *
 * // Custom flow
 * val flow = KyvshieldFlowConfig(
 *     steps = listOf(CaptureStep.selfie, CaptureStep.recto),
 *     challengeMode = ChallengeMode.strict,
 *     language = "fr"
 * )
 * ```
 */
data class KyvshieldFlowConfig(
    /**
     * Capture steps in order.
     * Default: [selfie, recto, verso]
     */
    val steps: List<CaptureStep> = listOf(CaptureStep.selfie, CaptureStep.recto, CaptureStep.verso),

    /** Language code for UI strings ('fr', 'en', 'wo'). Default: 'fr' */
    val language: String = "fr",

    /**
     * Default challenge mode (used if step not in [stepChallengeModes]).
     * - minimal: Single challenge (fastest)
     * - standard: 2-3 challenges (default, balanced)
     * - strict: 4-5 challenges (highest security)
     */
    val challengeMode: ChallengeMode = ChallengeMode.standard,

    /**
     * Per-step challenge mode overrides.
     * Example: `mapOf(CaptureStep.selfie to ChallengeMode.minimal)`
     */
    val stepChallengeModes: Map<CaptureStep, ChallengeMode> = emptyMap(),

    /** Display mode for the selfie screen (UI layout). Default: standard */
    val selfieDisplayMode: SelfieDisplayMode = SelfieDisplayMode.standard,

    /** Display mode for the document capture screen (UI layout). Default: standard */
    val documentDisplayMode: DocumentDisplayMode = DocumentDisplayMode.standard,

    /** Show intro page explaining the verification steps. Default: true */
    val showIntroPage: Boolean = true,

    /** Show instruction pages before each capture step. Default: true */
    val showInstructionPages: Boolean = true,

    /** Show result page at the end with verification summary. Default: true */
    val showResultPage: Boolean = true,

    /**
     * Show success animation after each step completes.
     * If `true`: displays success screen before moving to next step.
     * If `false`: immediately moves to next step after validation.
     */
    val showSuccessPerStep: Boolean = true,

    /**
     * Require face match (selfie vs document photo).
     * Only applies if steps contains both selfie and recto/verso.
     */
    val requireFaceMatch: Boolean = true,

    /** Require AML (Anti-Money Laundering) sanctions screening. Default: false */
    val requireAml: Boolean = false,

    /** Play audio instructions for each challenge. Default: false */
    val playChallengeAudio: Boolean = false,

    /** How many times to play the challenge audio instruction. Default: once */
    val maxChallengeAudioPlay: ChallengeAudioRepeat = ChallengeAudioRepeat.once,

    /**
     * Pause duration between audio repetitions in milliseconds.
     * Only used when [maxChallengeAudioPlay] > 1. Default: 1000 ms
     */
    val pauseBetweenAudioPlayMs: Long = 1000L,

    /**
     * Target document type.
     * If null → automatic detection by the backend.
     */
    val target: KyvshieldDocument? = null,

    /**
     * Pass-through identifier for webhook correlation.
     * Use to pass your user_id, transaction_id, or any reference.
     * Stored and returned in all webhooks exactly as provided.
     */
    val kycIdentifier: String? = null
) {
    // ─────────────────────────────────────────────────────────────────────────
    // Convenience getters
    // ─────────────────────────────────────────────────────────────────────────

    /** Whether the flow includes selfie capture. */
    val hasSelfie: Boolean get() = CaptureStep.selfie in steps

    /** Whether the flow includes recto capture. */
    val hasRecto: Boolean get() = CaptureStep.recto in steps

    /** Whether the flow includes verso capture. */
    val hasVerso: Boolean get() = CaptureStep.verso in steps

    /** Whether face match is possible (selfie + document). */
    val canMatchFace: Boolean get() = hasSelfie && (hasRecto || hasVerso)

    /** Steps as a list of strings for the JS SDK bridge. */
    val stepsAsStrings: List<String> get() = steps.map { it.name }

    /**
     * Get the challenge mode for a specific step.
     * Returns [stepChallengeModes][step] if set, otherwise [challengeMode].
     */
    fun getChallengeModeForStep(step: CaptureStep): ChallengeMode =
        stepChallengeModes[step] ?: challengeMode

    /**
     * Step challenge modes as a `Map<String, String>` for the JS SDK.
     * Returns `{"selfie": "minimal", "recto": "strict", ...}` for all steps.
     */
    val stepChallengeModesMap: Map<String, String>
        get() = steps.associate { step -> step.name to getChallengeModeForStep(step).name }

    // ─────────────────────────────────────────────────────────────────────────
    // Factory presets  (mirrors Flutter Lite SDK named constructors)
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        /** Selfie only — identity verification without document */
        fun selfieOnly(target: KyvshieldDocument? = null, language: String = "fr") =
            KyvshieldFlowConfig(
                steps = listOf(CaptureStep.selfie),
                requireFaceMatch = false,
                target = target,
                language = language
            )

        /** Recto only — no selfie or verso */
        fun rectoOnly(target: KyvshieldDocument? = null, language: String = "fr") =
            KyvshieldFlowConfig(
                steps = listOf(CaptureStep.recto),
                requireFaceMatch = false,
                target = target,
                language = language
            )

        /** Document only — recto + verso, no selfie */
        fun documentOnly(target: KyvshieldDocument? = null, language: String = "fr") =
            KyvshieldFlowConfig(
                steps = listOf(CaptureStep.recto, CaptureStep.verso),
                requireFaceMatch = false,
                target = target,
                language = language
            )

        /** Selfie + Recto (no verso) — recommended flow */
        fun standard(target: KyvshieldDocument? = null, language: String = "fr") =
            KyvshieldFlowConfig(
                steps = listOf(CaptureStep.selfie, CaptureStep.recto),
                requireFaceMatch = true,
                target = target,
                language = language
            )

        /** Full flow — selfie + recto + verso + face match */
        fun full(target: KyvshieldDocument? = null, language: String = "fr") =
            KyvshieldFlowConfig(
                steps = listOf(CaptureStep.selfie, CaptureStep.recto, CaptureStep.verso),
                requireFaceMatch = true,
                target = target,
                language = language
            )

        /**
         * Quick flow — selfie + recto, no UI pages, minimal challenges.
         * Ideal for embedding in your own UI.
         */
        fun quick(target: KyvshieldDocument? = null, language: String = "fr") =
            KyvshieldFlowConfig(
                steps = listOf(CaptureStep.selfie, CaptureStep.recto),
                challengeMode = ChallengeMode.minimal,
                selfieDisplayMode = SelfieDisplayMode.compact,
                documentDisplayMode = DocumentDisplayMode.compact,
                showIntroPage = false,
                showInstructionPages = false,
                showResultPage = false,
                showSuccessPerStep = false,
                requireFaceMatch = true,
                target = target,
                language = language
            )

        /**
         * Minimal flow — full capture, no UI pages, minimal challenges.
         * Fastest possible flow.
         */
        fun minimal(target: KyvshieldDocument? = null, language: String = "fr") =
            KyvshieldFlowConfig(
                steps = listOf(CaptureStep.selfie, CaptureStep.recto, CaptureStep.verso),
                challengeMode = ChallengeMode.minimal,
                selfieDisplayMode = SelfieDisplayMode.compact,
                documentDisplayMode = DocumentDisplayMode.compact,
                showIntroPage = false,
                showInstructionPages = false,
                showResultPage = false,
                showSuccessPerStep = false,
                requireFaceMatch = true,
                target = target,
                language = language
            )

        /** Strict flow — full capture, maximum security challenges */
        fun strict(target: KyvshieldDocument? = null, language: String = "fr") =
            KyvshieldFlowConfig(
                steps = listOf(CaptureStep.selfie, CaptureStep.recto, CaptureStep.verso),
                challengeMode = ChallengeMode.strict,
                requireFaceMatch = true,
                target = target,
                language = language
            )
    }
}
