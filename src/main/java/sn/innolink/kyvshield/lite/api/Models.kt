package sn.innolink.kyvshield.lite.api

import org.json.JSONArray
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
// IdentifyOptions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Options for the [KyvshieldLite.identify] call.
 *
 * @param topK     Maximum number of matches to return (default: server-side default, usually 3).
 * @param minScore Minimum similarity score threshold (0.0–1.0). Matches below this are excluded.
 */
data class IdentifyOptions(
    val topK: Int? = null,
    val minScore: Double? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// IdentifyMatch
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single identity match returned by the identify endpoint.
 *
 * Mirrors the backend `IdentifyMatch` JSON structure.
 */
data class IdentifyMatch(
    /** Unique identity ID in the registry. */
    val identityId: String,

    /** Similarity score (0.0–1.0). Higher = more similar. */
    val score: Double,

    /** Full name of the matched identity (if available). */
    val fullName: String? = null,

    /** The identifier key used for this identity (e.g. "nin", "document_id"). */
    val identifierKey: String = "",

    /** The identifier value (e.g. the NIN number). */
    val identifierValue: String = "",

    /** Document type (e.g. "SN-CIN"). */
    val documentType: String = "",

    /** Country code (e.g. "SN"). */
    val country: String? = null,

    /** Estimated age of the person (0 if unavailable). */
    val estimatedAge: Int = 0,

    /** Predicted gender (e.g. "M", "F"). */
    val predictedGender: String? = null,

    /** Extracted fields from the document. */
    val extraction: List<IdentityField> = emptyList(),

    /** When this identity was created. */
    val createdAt: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): IdentifyMatch {
            val extractionArray = json.optJSONArray("extraction")
            val fields = if (extractionArray != null) {
                (0 until extractionArray.length()).map {
                    IdentityField.fromJson(extractionArray.getJSONObject(it))
                }
            } else emptyList()

            return IdentifyMatch(
                identityId      = json.optString("identity_id", ""),
                score           = json.optDouble("score", 0.0),
                fullName        = json.optString("full_name").takeIf { it.isNotEmpty() },
                identifierKey   = json.optString("identifier_key", ""),
                identifierValue = json.optString("identifier_value", ""),
                documentType    = json.optString("document_type", ""),
                country         = json.optString("country").takeIf { it.isNotEmpty() },
                estimatedAge    = json.optInt("estimated_age", 0),
                predictedGender = json.optString("predicted_gender").takeIf { it.isNotEmpty() },
                extraction      = fields,
                createdAt       = json.optString("created_at", "")
            )
        }
    }

    /** Get an extracted field value by key. */
    fun getFieldValue(key: String): String? =
        extraction.firstOrNull { it.key == key || it.documentKey == key }?.value
}

// ─────────────────────────────────────────────────────────────────────────────
// IdentityField
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single extracted field from an identity document.
 *
 * Mirrors the backend `IdentityField` JSON structure.
 */
data class IdentityField(
    val key: String,
    val documentKey: String = "",
    val label: String = "",
    val value: String = "",
    val rawValue: String? = null,
    val displayPriority: Int = 999,
    val icon: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject): IdentityField = IdentityField(
            key             = json.optString("key", ""),
            documentKey     = json.optString("document_key", ""),
            label           = json.optString("label", ""),
            value           = json.optString("value", ""),
            rawValue        = json.optString("raw_value").takeIf { it.isNotEmpty() },
            displayPriority = json.optInt("display_priority", 999),
            icon            = json.optString("icon").takeIf { it.isNotEmpty() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IdentifyResponse
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Response from [KyvshieldLite.identify].
 *
 * Mirrors the backend `IdentifyResponse` JSON structure.
 */
data class IdentifyResponse(
    /** Whether the request was successful. */
    val success: Boolean,

    /** Unique search ID for this query. */
    val searchId: String = "",

    /** Number of matches returned. */
    val resultsCount: Int = 0,

    /** List of identity matches, sorted by score (highest first). */
    val matches: List<IdentifyMatch> = emptyList(),

    /** The top_k value used for this search. */
    val topK: Int = 0,

    /** The min_score threshold used for this search. */
    val minScore: Double = 0.0,

    /** Processing time in milliseconds. */
    val processingTimeMs: Long = 0,

    /** Error message (only set when [success] is false). */
    val error: String? = null,

    /** HTTP status code from the server. */
    val httpStatus: Int = 200
) {
    /** The best (highest score) match, or null if no matches. */
    val bestMatch: IdentifyMatch? get() = matches.firstOrNull()

    /** Whether any matches were found. */
    val hasMatches: Boolean get() = matches.isNotEmpty()

    companion object {
        fun fromJson(json: JSONObject, httpStatus: Int = 200): IdentifyResponse {
            val matchesArray = json.optJSONArray("matches")
            val matchesList = if (matchesArray != null) {
                (0 until matchesArray.length()).map {
                    IdentifyMatch.fromJson(matchesArray.getJSONObject(it))
                }
            } else emptyList()

            return IdentifyResponse(
                success          = json.optBoolean("success", false),
                searchId         = json.optString("search_id", ""),
                resultsCount     = json.optInt("results_count", 0),
                matches          = matchesList,
                topK             = json.optInt("top_k", 0),
                minScore         = json.optDouble("min_score", 0.0),
                processingTimeMs = json.optLong("processing_time_ms", 0),
                error            = json.optString("error").takeIf { it.isNotEmpty() },
                httpStatus       = httpStatus
            )
        }

        fun error(message: String, httpStatus: Int = 0) = IdentifyResponse(
            success    = false,
            error      = message,
            httpStatus = httpStatus
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FaceVerifyOptions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Options for the [KyvshieldLite.verifyFace] call.
 *
 * @param detectionModel   Face detection model (default: "scrfd_10g").
 * @param recognitionModel Face recognition model (default: "buffalo_l").
 */
data class FaceVerifyOptions(
    val detectionModel: String? = null,
    val recognitionModel: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// FaceVerifyMatch
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single face match from the face verification endpoint.
 *
 * Mirrors the backend `FaceMatch` JSON structure.
 */
data class FaceVerifyMatch(
    val faceIndex: Int,
    val boundingBox: List<Double> = emptyList(),
    val detectionConfidence: Double = 0.0,
    val similarityScore: Double = 0.0,
    val isMatch: Boolean = false,
    val confidenceLevel: String = "LOW"
) {
    companion object {
        fun fromJson(json: JSONObject): FaceVerifyMatch {
            val bboxArray = json.optJSONArray("bounding_box")
            val bbox = if (bboxArray != null) {
                (0 until bboxArray.length()).map { bboxArray.getDouble(it) }
            } else emptyList()

            return FaceVerifyMatch(
                faceIndex           = json.optInt("face_index", 0),
                boundingBox         = bbox,
                detectionConfidence = json.optDouble("detection_confidence", 0.0),
                similarityScore     = json.optDouble("similarity_score", 0.0),
                isMatch             = json.optBoolean("is_match", false),
                confidenceLevel     = json.optString("confidence_level", "LOW")
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FaceVerifyResponse
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Response from [KyvshieldLite.verifyFace].
 *
 * Mirrors the backend `FaceCompareResponse` JSON structure.
 */
data class FaceVerifyResponse(
    /** Whether the request was successful. */
    val success: Boolean,

    /** Whether a face was detected in the target (reference) image. */
    val targetFaceDetected: Boolean = false,

    /** Detection confidence of the target face. */
    val targetFaceConfidence: Double = 0.0,

    /** Number of faces detected in the source image. */
    val sourceFacesCount: Int = 0,

    /** All face matches found in the source image. */
    val matches: List<FaceVerifyMatch> = emptyList(),

    /** The best match (highest similarity) among all faces in the source image. */
    val bestMatch: FaceVerifyMatch? = null,

    /** Similarity threshold used for match determination. */
    val threshold: Double = 0.5,

    /** Detection model used. */
    val detectionModel: String = "scrfd_10g",

    /** Recognition model used. */
    val recognitionModel: String = "buffalo_l",

    /** Processing time in milliseconds. */
    val processingTimeMs: Long = 0,

    /** Error message (only set when [success] is false). */
    val error: String? = null,

    /** HTTP status code from the server. */
    val httpStatus: Int = 200
) {
    /** Whether the best match is a positive match. */
    val isMatch: Boolean get() = bestMatch?.isMatch ?: false

    /** Similarity score of the best match (0.0–1.0). */
    val similarityScore: Double get() = bestMatch?.similarityScore ?: 0.0

    /** Confidence level of the best match. */
    val confidenceLevel: String get() = bestMatch?.confidenceLevel ?: "LOW"

    companion object {
        fun fromJson(json: JSONObject, httpStatus: Int = 200): FaceVerifyResponse {
            val matchesArray = json.optJSONArray("matches")
            val matchesList = if (matchesArray != null) {
                (0 until matchesArray.length()).map {
                    FaceVerifyMatch.fromJson(matchesArray.getJSONObject(it))
                }
            } else emptyList()

            val bestMatchJson = json.optJSONObject("best_match")

            return FaceVerifyResponse(
                success              = json.optBoolean("success", false),
                targetFaceDetected   = json.optBoolean("target_face_detected", false),
                targetFaceConfidence = json.optDouble("target_face_confidence", 0.0),
                sourceFacesCount     = json.optInt("source_faces_count", 0),
                matches              = matchesList,
                bestMatch            = bestMatchJson?.let { FaceVerifyMatch.fromJson(it) },
                threshold            = json.optDouble("threshold", 0.5),
                detectionModel       = json.optString("detection_model", "scrfd_10g"),
                recognitionModel     = json.optString("recognition_model", "buffalo_l"),
                processingTimeMs     = json.optLong("processing_time_ms", 0),
                error                = json.optString("error").takeIf { it.isNotEmpty() },
                httpStatus           = httpStatus
            )
        }

        fun error(message: String, httpStatus: Int = 0) = FaceVerifyResponse(
            success    = false,
            error      = message,
            httpStatus = httpStatus
        )
    }
}
