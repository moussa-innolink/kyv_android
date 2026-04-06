package sn.innolink.kyvshield.lite.result

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import sn.innolink.kyvshield.lite.config.VerificationStatus

// ─────────────────────────────────────────────────────────────────────────────
// Byte-array helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parse image bytes that may arrive as:
 *  - Base64 [String] (from `_prepareForBridge` JS helper)
 *  - JSON array of ints `[255, 216, ...]`
 *  - Uint8Array serialised by JSON.stringify `{"0":255,"1":216,...}`
 *
 * Returns `null` if [data] is null or cannot be decoded.
 */
internal fun parseBytes(data: Any?): ByteArray? {
    return when {
        data == null -> null
        data is ByteArray -> data
        data is String && data.isNotEmpty() ->
            try { Base64.decode(data, Base64.DEFAULT) } catch (e: Exception) { null }
        data is JSONArray -> {
            val len = data.length()
            ByteArray(len) { i -> data.getInt(i).toByte() }
        }
        data is JSONObject -> {
            // Uint8Array serialised as {"0":255,"1":216,...}
            val sorted = (0 until data.length()).map { i ->
                Pair(i, data.getInt(i.toString()))
            }.sortedBy { it.first }
            ByteArray(sorted.size) { i -> sorted[i].second.toByte() }
        }
        else -> null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExtractedPhoto
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Extracted photo from a document (e.g. the face photo on an ID card).
 *
 * Mirrors `ExtractedPhoto` in the Flutter Lite SDK.
 */
data class ExtractedPhoto(
    val imageBytes: ByteArray,
    val confidence: Double,
    val width: Int,
    val height: Int,
    val bbox: List<Double> = emptyList(),
    val area: Double = 0.0
) {
    /** Base64-encoded image string (backwards compatibility getter). */
    val image: String get() = Base64.encodeToString(imageBytes, Base64.DEFAULT)

    /** Encode image bytes as a Base64 string. */
    fun toBase64(): String = Base64.encodeToString(imageBytes, Base64.DEFAULT)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtractedPhoto) return false
        return imageBytes.contentEquals(other.imageBytes) &&
               confidence == other.confidence &&
               width == other.width &&
               height == other.height &&
               bbox == other.bbox &&
               area == other.area
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + bbox.hashCode()
        result = 31 * result + area.hashCode()
        return result
    }

    companion object {
        /**
         * Parse from a JSON object.
         * Supports both camelCase (`imageBytes`) and snake_case (`image`) keys.
         */
        fun fromJson(json: JSONObject): ExtractedPhoto {
            // camelCase (JS SDK) then snake_case (backend)
            val rawImage = json.opt("imageBytes") ?: json.opt("image")
            val bytes = parseBytes(rawImage) ?: ByteArray(0)

            val bboxArray = json.optJSONArray("bbox")
            val bbox = if (bboxArray != null) {
                (0 until bboxArray.length()).map { bboxArray.getDouble(it) }
            } else emptyList()

            return ExtractedPhoto(
                imageBytes = bytes,
                confidence = json.optDouble("confidence", 0.0),
                width      = json.optInt("width", 0),
                height     = json.optInt("height", 0),
                bbox       = bbox,
                area       = json.optDouble("area", 0.0)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExtractedField
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single extracted field with generic key, document-specific key, label and value.
 *
 * Mirrors `ExtractedField` in the Flutter Lite SDK.
 */
data class ExtractedField(
    /** Generic key (e.g. "document_id", "first_name") */
    val key: String,

    /** Document-specific key (e.g. "numero_carte", "prenoms") */
    val documentKey: String,

    /** Localized display label (e.g. "N° de la carte d'identité") */
    val label: String,

    /** The extracted value — can be String, Number, List, Map, or null */
    val value: Any?,

    /** Display priority: lower = show first (default: 999) */
    val displayPriority: Int = 999,

    /** Icon name for display (e.g. "credit_card", "user") */
    val icon: String? = null
) {
    /**
     * Value as a display string.
     * - Lists → joined with ", "
     * - Maps  → formatted as "key: value" pairs
     * - null  → null
     */
    val stringValue: String? get() {
        return when (value) {
            null           -> null
            is String      -> value
            is JSONArray   -> (0 until value.length()).map { value.get(it)?.toString() ?: "" }.joinToString(", ")
            is JSONObject  -> value.keys().asSequence().map { k -> "$k: ${value.opt(k)}" }.joinToString(", ")
            is List<*>     -> value.joinToString(", ") { it?.toString() ?: "" }
            is Map<*, *>   -> value.entries.joinToString(", ") { (k, v) -> "$k: $v" }
            else           -> value.toString()
        }
    }

    /** Whether the value is a list/array. */
    val isArray: Boolean get() = value is List<*> || value is JSONArray

    /** Whether the value is a map/object. */
    val isMap: Boolean get() = value is Map<*, *> || value is JSONObject

    /** Value as a list (empty if not a list). */
    @Suppress("UNCHECKED_CAST")
    val listValue: List<Any?> get() = when (value) {
        is List<*>   -> value as List<Any?>
        is JSONArray -> (0 until value.length()).map { value.get(it) }
        else         -> emptyList()
    }

    /** Value as a map (empty if not a map). */
    val mapValue: Map<String, Any?> get() = when (value) {
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
        is JSONObject -> value.keys().asSequence().associate { k -> k to value.opt(k) }
        else         -> emptyMap()
    }

    override fun toString(): String = "ExtractedField($key: $value)"

    companion object {
        /**
         * Parse from a JSON object.
         * Supports camelCase (`documentKey`, `displayPriority`) and
         * snake_case (`document_key`, `display_priority`) keys.
         */
        fun fromJson(json: JSONObject): ExtractedField {
            val key = json.optString("key", "")
            return ExtractedField(
                key = key,
                documentKey = json.optString("documentKey",
                    json.optString("document_key", key)),
                label = json.optString("label", ""),
                value = json.opt("value"),
                displayPriority = run {
                    val v = json.opt("displayPriority") ?: json.opt("display_priority")
                    when (v) {
                        is Number -> v.toInt()
                        else      -> 999
                    }
                },
                icon = json.optString("icon").takeIf { it.isNotEmpty() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DocumentData
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Extracted document data (OCR result).
 *
 * Mirrors `DocumentData` in the Flutter Lite SDK.
 */
data class DocumentData(
    /** All extracted fields (unsorted). */
    val fields: List<ExtractedField> = emptyList(),

    /** Document type key (e.g. "SN-CIN"). */
    val documentType: String? = null,

    /** Document category (e.g. "identity_card"). */
    val documentCategory: String? = null,

    /** Localized document category label. */
    val documentCategoryLabel: String? = null
) {
    /** Fields sorted by [ExtractedField.displayPriority] (lower = first). */
    val sortedFields: List<ExtractedField>
        get() = fields.sortedBy { it.displayPriority }

    /** Get field by generic key or document-specific key. */
    fun getField(key: String): ExtractedField? =
        fields.firstOrNull { it.key == key || it.documentKey == key }

    /** Get field value as String by key. */
    fun getValue(key: String): String? = getField(key)?.stringValue

    companion object {
        fun fromFields(fields: List<ExtractedField>) = DocumentData(fields = fields)

        /**
         * Parse from a JSON object.
         *
         * Supports:
         * - JS SDK format: `{ fields: [...], documentType, documentCategory, ... }`
         * - Backend format: `{ extraction: [...], document_type, document_category, ... }`
         * - Legacy format: `{ extraction: { "key": "value", ... } }`
         */
        fun fromJson(json: JSONObject): DocumentData {
            // JS SDK sends "fields", backend may send "extraction"
            val fieldsList = json.opt("fields") ?: json.opt("extraction")

            val fields: List<ExtractedField> = when (fieldsList) {
                is JSONArray -> (0 until fieldsList.length()).map { i ->
                    ExtractedField.fromJson(fieldsList.getJSONObject(i))
                }
                is JSONObject -> {
                    // Legacy map format: {"key": "value", ...}
                    var priority = 1
                    fieldsList.keys().asSequence().mapNotNull { key ->
                        val value = fieldsList.opt(key)
                        if (value != null && value.toString().isNotEmpty()) {
                            ExtractedField(
                                key = key,
                                documentKey = key,
                                label = key,
                                value = value,
                                displayPriority = priority++
                            )
                        } else null
                    }.toList()
                }
                else -> emptyList()
            }

            return DocumentData(
                fields = fields,
                documentType = json.optString("documentType").takeIf { it.isNotEmpty() }
                    ?: json.optString("document_type").takeIf { it.isNotEmpty() },
                documentCategory = json.optString("documentCategory").takeIf { it.isNotEmpty() }
                    ?: json.optString("document_category").takeIf { it.isNotEmpty() },
                documentCategoryLabel = json.optString("documentCategoryLabel").takeIf { it.isNotEmpty() }
                    ?: json.optString("document_category_label").takeIf { it.isNotEmpty() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FaceMatch
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Individual face match result from comparison.
 *
 * Mirrors `FaceMatch` in the Flutter Lite SDK.
 */
data class FaceMatch(
    val faceIndex: Int,
    val boundingBox: List<Double>,
    val detectionConfidence: Double,
    val similarityScore: Double,
    val isMatch: Boolean,
    val confidenceLevel: String
) {
    companion object {
        /**
         * Parse from a JSON object.
         * Supports camelCase and snake_case keys.
         */
        fun fromJson(json: JSONObject): FaceMatch {
            val bboxArray = json.optJSONArray("boundingBox")
                ?: json.optJSONArray("bounding_box")
            val bbox = if (bboxArray != null) {
                (0 until bboxArray.length()).map { bboxArray.getDouble(it) }
            } else emptyList()

            return FaceMatch(
                faceIndex = run {
                    val v = json.opt("faceIndex") ?: json.opt("face_index")
                    (v as? Number)?.toInt() ?: 0
                },
                boundingBox = bbox,
                detectionConfidence = run {
                    val v = json.opt("detectionConfidence") ?: json.opt("detection_confidence")
                    (v as? Number)?.toDouble() ?: 0.0
                },
                similarityScore = run {
                    val v = json.opt("similarityScore") ?: json.opt("similarity_score")
                    (v as? Number)?.toDouble() ?: 0.0
                },
                isMatch = (json.opt("isMatch") ?: json.opt("is_match")) as? Boolean ?: false,
                confidenceLevel = ((json.opt("confidenceLevel") ?: json.opt("confidence_level")) as? String) ?: "LOW"
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FaceResult
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Face verification result (selfie vs document photo).
 *
 * Mirrors `FaceResult` in the Flutter Lite SDK.
 */
data class FaceResult(
    val success: Boolean = false,
    val isMatch: Boolean,
    val similarityScore: Double,
    val threshold: Double,
    val confidenceLevel: String = "LOW",

    // Target face (document/ID photo)
    val targetFaceDetected: Boolean = false,
    val targetFaceConfidence: Double = 0.0,

    // Source faces (selfie — may contain multiple)
    val sourceFacesCount: Int = 0,
    val matches: List<FaceMatch> = emptyList(),
    val bestMatch: FaceMatch? = null,

    // Model info
    val detectionModel: String = "scrfd_10g",
    val recognitionModel: String = "buffalo_l",
    val processingTimeMs: Int = 0,

    // Legacy backwards-compat fields
    val cinFaceDetected: Boolean = false,
    val selfieFaceDetected: Boolean = false
) {
    /** Backwards compatibility: CIN face confidence */
    val cinFaceConfidence: Double
        get() = if (targetFaceConfidence > 0) targetFaceConfidence else if (cinFaceDetected) 1.0 else 0.0

    /** Backwards compatibility: selfie face confidence */
    val selfieFaceConfidence: Double
        get() = bestMatch?.detectionConfidence ?: if (selfieFaceDetected) 1.0 else 0.0

    companion object {
        /**
         * Parse from a JSON object.
         * Supports camelCase (JS SDK) and snake_case (backend) keys.
         */
        fun fromJson(json: JSONObject): FaceResult {
            // Parse matches array
            val matchesArray = json.optJSONArray("matches")
            val matchesList: List<FaceMatch> = if (matchesArray != null) {
                (0 until matchesArray.length()).map { FaceMatch.fromJson(matchesArray.getJSONObject(it)) }
            } else emptyList()

            // Parse best match (camelCase first, snake_case fallback)
            val bestMatchData = json.optJSONObject("bestMatch") ?: json.optJSONObject("best_match")
            val bestMatch = bestMatchData?.let { FaceMatch.fromJson(it) }

            // Determine confidence level
            var confidenceLevel = ((json.opt("confidenceLevel") ?: json.opt("confidence_level")) as? String) ?: ""
            if (confidenceLevel.isEmpty() && bestMatch != null) {
                confidenceLevel = bestMatch.confidenceLevel
            }
            if (confidenceLevel.isEmpty()) {
                val score = ((json.opt("similarityScore") ?: json.opt("similarity_score")) as? Number)?.toDouble() ?: 0.0
                confidenceLevel = when {
                    score >= 90 -> "VERY_HIGH"
                    score >= 70 -> "HIGH"
                    score >= 50 -> "MEDIUM"
                    else        -> "LOW"
                }
            }

            val targetDetected = (json.opt("targetFaceDetected") ?: json.opt("target_face_detected")) as? Boolean ?: false
            val srcCount = ((json.opt("sourceFacesCount") ?: json.opt("source_faces_count")) as? Number)?.toInt() ?: 0

            return FaceResult(
                success = json.optBoolean("success", false),
                isMatch = (json.opt("isMatch") ?: json.opt("is_match")) as? Boolean
                    ?: bestMatch?.isMatch ?: false,
                similarityScore = ((json.opt("similarityScore") ?: json.opt("similarity_score")) as? Number)?.toDouble()
                    ?: bestMatch?.similarityScore ?: 0.0,
                threshold = (json.opt("threshold") as? Number)?.toDouble() ?: 0.5,
                confidenceLevel = confidenceLevel,
                targetFaceDetected = targetDetected,
                targetFaceConfidence = ((json.opt("targetFaceConfidence") ?: json.opt("target_face_confidence")) as? Number)?.toDouble() ?: 0.0,
                sourceFacesCount = srcCount,
                matches = matchesList,
                bestMatch = bestMatch,
                detectionModel = ((json.opt("detectionModel") ?: json.opt("detection_model")) as? String) ?: "scrfd_10g",
                recognitionModel = ((json.opt("recognitionModel") ?: json.opt("recognition_model")) as? String) ?: "buffalo_l",
                processingTimeMs = ((json.opt("processingTimeMs") ?: json.opt("processing_time_ms")) as? Number)?.toInt() ?: 0,
                cinFaceDetected = targetDetected || ((json.opt("cinFaceDetected") ?: json.opt("cin_face_detected")) as? Boolean ?: false),
                selfieFaceDetected = srcCount > 0 || ((json.opt("selfieFaceDetected") ?: json.opt("selfie_face_detected")) as? Boolean ?: false)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SelfieResult
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Selfie liveness verification result.
 *
 * Mirrors `SelfieResult` in the Flutter Lite SDK.
 */
data class SelfieResult(
    val success: Boolean,
    val isLive: Boolean,
    val confidence: Double,
    val status: VerificationStatus,
    val capturedImage: ByteArray? = null,
    val userMessages: List<String> = emptyList(),
    val observations: List<String> = emptyList(),
    val spoofingIndicators: List<String> = emptyList(),
    val challengesPassed: Int = 0,
    val challengesTotal: Int = 0,
    val processingTimeMs: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelfieResult) return false
        return success == other.success &&
               isLive == other.isLive &&
               confidence == other.confidence &&
               status == other.status &&
               (capturedImage?.contentEquals(other.capturedImage ?: ByteArray(0)) ?: (other.capturedImage == null))
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + isLive.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (capturedImage?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * Parse from a JSON object.
         * Supports camelCase (JS SDK) and snake_case (backend) keys.
         */
        fun fromJson(json: JSONObject): SelfieResult {
            fun jsonArrayToStringList(arr: JSONArray?): List<String> {
                if (arr == null) return emptyList()
                return (0 until arr.length()).mapNotNull { arr.optString(it) }
            }

            return SelfieResult(
                success    = json.optBoolean("success", false),
                isLive     = (json.opt("isLive") ?: json.opt("is_live")) as? Boolean ?: false,
                confidence = (json.opt("confidence") as? Number)?.toDouble() ?: 0.0,
                status     = VerificationStatus.fromString(json.optString("status")),
                capturedImage = parseBytes(json.opt("capturedImage") ?: json.opt("captured_image")),
                userMessages = jsonArrayToStringList(
                    (json.opt("userMessages") ?: json.opt("user_messages")) as? JSONArray
                ),
                observations = jsonArrayToStringList(json.optJSONArray("observations")),
                spoofingIndicators = jsonArrayToStringList(
                    (json.opt("spoofingIndicators") ?: json.opt("spoofing_indicators")) as? JSONArray
                ),
                challengesPassed = ((json.opt("challengesPassed") ?: json.opt("challenges_passed")) as? Number)?.toInt() ?: 0,
                challengesTotal  = ((json.opt("challengesTotal")  ?: json.opt("challenges_total"))  as? Number)?.toInt() ?: 0,
                processingTimeMs = ((json.opt("processingTimeMs") ?: json.opt("processing_time_ms")) as? Number)?.toInt() ?: 0
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FraudAnalysis
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fraud analysis summary — provided for backwards compatibility.
 *
 * Mirrors `FraudAnalysis` in the Flutter Lite SDK.
 */
data class FraudAnalysis(
    val score: Double,
    val status: String,
    val indicators: List<String>,
    val isLive: Boolean
) {
    /** Component scores (placeholder for backwards compatibility). */
    val componentScores: Map<String, Double>
        get() = mapOf(
            "overall"  to score,
            "liveness" to if (isLive) 1.0 else 0.0
        )
}

// ─────────────────────────────────────────────────────────────────────────────
// DocumentResult
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Document analysis result (recto or verso).
 *
 * Mirrors `DocumentResult` in the Flutter Lite SDK.
 */
data class DocumentResult(
    val success: Boolean,
    val isLive: Boolean,
    val score: Double,
    val confidenceLevel: String,
    val status: VerificationStatus,
    val alignedDocument: ByteArray? = null,
    val extraction: DocumentData? = null,
    val extractedPhotos: List<ExtractedPhoto> = emptyList(),
    val faceVerification: FaceResult? = null,
    val userMessages: List<String> = emptyList(),
    val fraudIndicators: List<String> = emptyList(),
    val processingTimeMs: Int = 0
) {
    val isPass: Boolean     get() = status == VerificationStatus.pass
    val isReview: Boolean   get() = status == VerificationStatus.review
    val isRejected: Boolean get() = status == VerificationStatus.reject

    /** First extracted photo from the document (face photo), or null. */
    val mainPhoto: ExtractedPhoto? get() = extractedPhotos.firstOrNull()

    /** Whether the face in the selfie matches the face on this document. */
    val hasFaceMatch: Boolean get() = faceVerification?.isMatch ?: false

    /** Fraud analysis summary (backwards compatibility). */
    val fraudAnalysis: FraudAnalysis
        get() = FraudAnalysis(
            score      = score,
            status     = status.label,
            indicators = fraudIndicators,
            isLive     = isLive
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentResult) return false
        return success == other.success &&
               isLive == other.isLive &&
               score == other.score &&
               status == other.status &&
               (alignedDocument?.contentEquals(other.alignedDocument ?: ByteArray(0)) ?: (other.alignedDocument == null))
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + isLive.hashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (alignedDocument?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * Parse from a JSON object.
         * Supports camelCase (JS SDK) and snake_case (backend) keys.
         */
        fun fromJson(json: JSONObject): DocumentResult {
            fun jsonArrayToStringList(arr: JSONArray?): List<String> {
                if (arr == null) return emptyList()
                return (0 until arr.length()).mapNotNull { arr.optString(it) }
            }

            val extractionData = json.optJSONObject("extraction")
            val photosArray = (json.opt("extractedPhotos") ?: json.opt("extracted_photos")) as? JSONArray
            val faceData = (json.optJSONObject("faceVerification") ?: json.optJSONObject("face_verification"))

            return DocumentResult(
                success = json.optBoolean("success", false),
                isLive  = (json.opt("isLive") ?: json.opt("is_live")) as? Boolean ?: false,
                score   = (json.opt("score") as? Number)?.toDouble() ?: 0.0,
                confidenceLevel = ((json.opt("confidenceLevel") ?: json.opt("confidence")) as? String) ?: "LOW",
                status  = VerificationStatus.fromString(json.optString("status")),
                alignedDocument = parseBytes(json.opt("alignedDocument") ?: json.opt("aligned_document")),
                extraction = extractionData?.let { DocumentData.fromJson(it) },
                extractedPhotos = if (photosArray != null) {
                    (0 until photosArray.length()).map { ExtractedPhoto.fromJson(photosArray.getJSONObject(it)) }
                } else emptyList(),
                faceVerification = faceData?.let { FaceResult.fromJson(it) },
                userMessages = jsonArrayToStringList(
                    (json.opt("userMessages") ?: json.opt("user_messages")) as? JSONArray
                ),
                fraudIndicators = jsonArrayToStringList(
                    (json.opt("fraudIndicators") ?: json.opt("fraud_indicators")) as? JSONArray
                ),
                processingTimeMs = ((json.opt("processingTimeMs") ?: json.opt("processing_time_ms")) as? Number)?.toInt() ?: 0
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KYCResult
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Complete KYC session result returned by the SDK.
 *
 * Mirrors `KYCResult` in the Flutter Lite SDK.
 *
 * ```kotlin
 * KyvshieldLite.initKyc(activity, config, flow) { result ->
 *     if (result.success) {
 *         val name = result.getExtractedValue("first_name")
 *         val photo = result.mainPhoto?.imageBytes
 *     }
 * }
 * ```
 */
data class KYCResult(
    val success: Boolean,
    val overallStatus: VerificationStatus,
    val sessionId: String? = null,
    val selfieResult: SelfieResult? = null,
    val rectoResult: DocumentResult? = null,
    val versoResult: DocumentResult? = null,
    val rejectionReason: String? = null,
    val rejectionMessage: String? = null,
    val fraudIndicators: List<String> = emptyList(),
    val totalProcessingTimeMs: Int = 0,
    val errorMessage: String? = null,
    val errorCode: String? = null
) {
    // ── Convenience getters matching Flutter Lite SDK ─────────────────────────

    /** Get any extracted field by key from recto or verso. */
    fun getExtractedValue(key: String): String? =
        rectoResult?.extraction?.getValue(key) ?: versoResult?.extraction?.getValue(key)

    /** Main extracted photo from recto (or verso as fallback). */
    val mainPhoto: ExtractedPhoto?
        get() = rectoResult?.mainPhoto ?: versoResult?.mainPhoto

    /** Whether the selfie face matches the document face. */
    val faceMatches: Boolean?
        get() = rectoResult?.faceVerification?.isMatch ?: versoResult?.faceVerification?.isMatch

    /** Face similarity score (0.0–100.0). */
    val faceSimilarityScore: Double?
        get() = rectoResult?.faceVerification?.similarityScore ?: versoResult?.faceVerification?.similarityScore

    /** Selfie captured image bytes. */
    val selfieImage: ByteArray? get() = selfieResult?.capturedImage

    /** Recto aligned document image bytes. */
    val rectoImage: ByteArray? get() = rectoResult?.alignedDocument

    /** Verso aligned document image bytes. */
    val versoImage: ByteArray? get() = versoResult?.alignedDocument

    // Backwards compatibility aliases
    val selfieImageBytes: ByteArray? get() = selfieImage
    val rectoImageBytes: ByteArray?  get() = rectoImage
    val versoImageBytes: ByteArray?  get() = versoImage

    /** Face verification result (from recto, or verso as fallback). */
    val faceResult: FaceResult?
        get() = rectoResult?.faceVerification ?: versoResult?.faceVerification

    /** Recto extraction (convenience alias). */
    val rectoExtraction: DocumentData? get() = rectoResult?.extraction

    /** Verso extraction (convenience alias). */
    val versoExtraction: DocumentData? get() = versoResult?.extraction

    /**
     * Overall authenticity score — average of available recto/verso scores.
     * Returns 0.0 if neither is available.
     */
    val authenticityScore: Double get() {
        val scores = listOfNotNull(
            rectoResult?.score,
            versoResult?.score
        )
        return if (scores.isEmpty()) 0.0 else scores.sum() / scores.size
    }

    /** Recto authenticity score, or null if no recto result. */
    val rectoAuthenticityScore: Double? get() = rectoResult?.score

    /** Verso authenticity score, or null if no verso result. */
    val versoAuthenticityScore: Double? get() = versoResult?.score

    /** Whether any extracted photos exist across recto and verso. */
    val hasExtractedPhotos: Boolean
        get() = (rectoResult?.extractedPhotos?.isNotEmpty() == true) ||
                (versoResult?.extractedPhotos?.isNotEmpty() == true)

    /** All extracted photos from both recto and verso combined. */
    val allExtractedPhotos: List<ExtractedPhoto>
        get() = (rectoResult?.extractedPhotos ?: emptyList()) +
                (versoResult?.extractedPhotos ?: emptyList())

    // ── Factory methods ───────────────────────────────────────────────────────

    companion object {
        /** Create an error result with the given message. */
        fun error(message: String, errorCode: String? = null) = KYCResult(
            success       = false,
            overallStatus = VerificationStatus.error,
            errorMessage  = message,
            errorCode     = errorCode
        )

        /**
         * Parse from a [JSONObject].
         * Supports camelCase (JS SDK) and snake_case (backend) keys.
         */
        fun fromJson(json: JSONObject): KYCResult {
            fun jsonArrayToStringList(arr: JSONArray?): List<String> {
                if (arr == null) return emptyList()
                return (0 until arr.length()).mapNotNull { arr.optString(it) }
            }

            val selfieData = json.optJSONObject("selfieResult") ?: json.optJSONObject("selfie_result")
            val rectoData  = json.optJSONObject("rectoResult")  ?: json.optJSONObject("recto_result")
            val versoData  = json.optJSONObject("versoResult")  ?: json.optJSONObject("verso_result")

            return KYCResult(
                success = json.optBoolean("success", false),
                overallStatus = VerificationStatus.fromString(
                    (json.opt("overallStatus") ?: json.opt("overall_status")) as? String
                ),
                sessionId = ((json.opt("sessionId") ?: json.opt("session_id")) as? String)
                    ?.takeIf { it.isNotEmpty() },
                selfieResult = selfieData?.let { SelfieResult.fromJson(it) },
                rectoResult  = rectoData?.let  { DocumentResult.fromJson(it) },
                versoResult  = versoData?.let  { DocumentResult.fromJson(it) },
                rejectionReason  = ((json.opt("rejectionReason")  ?: json.opt("rejection_reason"))  as? String)?.takeIf { it.isNotEmpty() },
                rejectionMessage = ((json.opt("rejectionMessage") ?: json.opt("rejection_message")) as? String)?.takeIf { it.isNotEmpty() },
                fraudIndicators  = jsonArrayToStringList(
                    (json.opt("fraudIndicators") ?: json.opt("fraud_indicators")) as? JSONArray
                ),
                totalProcessingTimeMs = ((json.opt("totalProcessingTimeMs") ?: json.opt("total_processing_time_ms") ?: json.opt("processing_time_ms")) as? Number)?.toInt() ?: 0,
                errorMessage = ((json.opt("errorMessage") ?: json.opt("error_message")) as? String)?.takeIf { it.isNotEmpty() },
                errorCode    = ((json.opt("errorCode")    ?: json.opt("error_code"))    as? String)?.takeIf { it.isNotEmpty() }
            )
        }

        /**
         * Parse from a raw JSON string (as received from the JS bridge).
         * Returns [error] result if parsing fails.
         */
        fun fromJsonString(jsonString: String): KYCResult {
            return try {
                fromJson(JSONObject(jsonString))
            } catch (e: Exception) {
                error("Failed to parse result: ${e.message}")
            }
        }
    }
}
