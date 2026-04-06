package sn.innolink.kyvshield.lite.config

// ─────────────────────────────────────────────────────────────────────────────
// Capture step types for KYC flow
// ─────────────────────────────────────────────────────────────────────────────

/** Represents a single step in the KYC capture flow. */
enum class CaptureStep {
    /** Face liveness verification (selfie) */
    selfie,

    /** Document front side */
    recto,

    /** Document back side */
    verso
}

// ─────────────────────────────────────────────────────────────────────────────
// Challenge mode for liveness verification (security level)
// ─────────────────────────────────────────────────────────────────────────────

/** Security level for the liveness challenge. */
enum class ChallengeMode {
    /** Single challenge (e.g., tilt_left only) — Fastest */
    minimal,

    /** 2–3 challenges — Balanced security/UX (default) */
    standard,

    /** 4–5 challenges — Maximum security */
    strict
}

// ─────────────────────────────────────────────────────────────────────────────
// Display modes
// ─────────────────────────────────────────────────────────────────────────────

/** UI layout for the selfie capture screen. */
enum class SelfieDisplayMode {
    /** Standard layout: challenge animation below camera (default) */
    standard,

    /** Compact layout: challenge overlaid on camera, progress bar at bottom */
    compact,

    /** Immersive layout: full-screen camera with all elements overlaid */
    immersive,

    /** Neon HUD: futuristic dark theme with neon glow borders */
    neonHud
}

/** UI layout for the document capture screen. */
enum class DocumentDisplayMode {
    /** Standard layout: document frame centered, instructions below (default) */
    standard,

    /** Compact layout: larger document frame, instructions overlaid */
    compact,

    /** Immersive layout: full-screen camera with blur around document frame */
    immersive,

    /** Neon HUD: futuristic dark theme with neon glow borders */
    neonHud
}

// ─────────────────────────────────────────────────────────────────────────────
// ChallengeAudioRepeat
// ─────────────────────────────────────────────────────────────────────────────

/** How many times to play the challenge audio instruction. */
enum class ChallengeAudioRepeat(val count: Int) {
    /** Play once */
    once(1),

    /** Play twice with pause between */
    twice(2),

    /** Play three times with pause between */
    thrice(3)
}

// ─────────────────────────────────────────────────────────────────────────────
// VerificationStatus
// ─────────────────────────────────────────────────────────────────────────────

/** Outcome of a single verification step or the overall KYC session. */
enum class VerificationStatus {
    pass,
    review,
    reject,
    error;

    companion object {
        /** Parse a status string from the API (case-insensitive). */
        fun fromString(s: String?): VerificationStatus = when (s?.uppercase()) {
            "PASS"   -> pass
            "REVIEW" -> review
            "REJECT" -> reject
            else     -> error
        }
    }

    /** Uppercase label for display (e.g. "PASS", "REVIEW"). */
    val label: String get() = name.uppercase()

    /** Whether the status represents a successful outcome. */
    val isSuccess: Boolean get() = this == pass

    /** Whether the status requires manual review. */
    val isReview: Boolean get() = this == review

    /** Whether the status is a rejection. */
    val isRejected: Boolean get() = this == reject
}
