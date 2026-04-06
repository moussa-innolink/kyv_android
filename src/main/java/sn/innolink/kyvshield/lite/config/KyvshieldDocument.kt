package sn.innolink.kyvshield.lite.config

import org.json.JSONObject

/**
 * Supported sides for a document + OCR detection thresholds.
 *
 * Mirrors `KyvshieldDocumentSides` in the Flutter Lite SDK.
 */
data class KyvshieldDocumentSides(
    val hasRecto: Boolean,
    val hasVerso: Boolean,
    val rectoMinCharOcr: Int = 0,
    val rectoMinBlockOcr: Int = 0,
    val rectoMinCharOcrWeb: Int = 0,
    val rectoMinBlockOcrWeb: Int = 0,
    val versoMinCharOcr: Int = 0,
    val versoMinBlockOcr: Int = 0,
    val versoMinCharOcrWeb: Int = 0,
    val versoMinBlockOcrWeb: Int = 0
) {
    companion object {
        fun fromJson(json: JSONObject) = KyvshieldDocumentSides(
            hasRecto             = json.optBoolean("has_recto", false),
            hasVerso             = json.optBoolean("has_verso", false),
            rectoMinCharOcr      = json.optInt("recto_min_char_ocr", 0),
            rectoMinBlockOcr     = json.optInt("recto_min_block_ocr", 0),
            rectoMinCharOcrWeb   = json.optInt("recto_min_char_ocr_web", 0),
            rectoMinBlockOcrWeb  = json.optInt("recto_min_block_ocr_web", 0),
            versoMinCharOcr      = json.optInt("verso_min_char_ocr", 0),
            versoMinBlockOcr     = json.optInt("verso_min_block_ocr", 0),
            versoMinCharOcrWeb   = json.optInt("verso_min_char_ocr_web", 0),
            versoMinBlockOcrWeb  = json.optInt("verso_min_block_ocr_web", 0)
        )
    }

    /** Get minimum OCR character count for a side (native). */
    fun minCharOcr(side: String): Int = if (side == "verso") versoMinCharOcr else rectoMinCharOcr

    /** Get minimum OCR block count for a side (native). */
    fun minBlockOcr(side: String): Int = if (side == "verso") versoMinBlockOcr else rectoMinBlockOcr

    /** Convert to a JSON-compatible map for the JS bridge. */
    internal fun toSnakeCaseMap(): Map<String, Any> = mapOf(
        "has_recto"               to hasRecto,
        "has_verso"               to hasVerso,
        "recto_min_char_ocr"      to rectoMinCharOcr,
        "recto_min_block_ocr"     to rectoMinBlockOcr,
        "recto_min_char_ocr_web"  to rectoMinCharOcrWeb,
        "recto_min_block_ocr_web" to rectoMinBlockOcrWeb,
        "verso_min_char_ocr"      to versoMinCharOcr,
        "verso_min_block_ocr"     to versoMinBlockOcr,
        "verso_min_char_ocr_web"  to versoMinCharOcrWeb,
        "verso_min_block_ocr_web" to versoMinBlockOcrWeb
    )
}

/**
 * Document type model from `/api/v1/documents`.
 *
 * Contains basic info about a supported document type.
 * Mirrors `KyvshieldDocument` in the Flutter Lite SDK.
 */
data class KyvshieldDocument(
    /** Unique document type key (e.g. "SN-CIN", "SN-PASSPORT") */
    val docType: String,

    /** Document category (e.g. "identity_card", "passport") */
    val category: String = "",

    /** Localized document category label */
    val categoryLabel: String = "",

    /** Human-readable document name */
    val name: String = "",

    /** ISO country code (e.g. "SN") */
    val country: String = "",

    /** Full country name (e.g. "Sénégal") */
    val countryName: String = "",

    /** Whether this document type is currently enabled */
    val enabled: Boolean = true,

    /** Supported sides + OCR thresholds */
    val supportedSides: KyvshieldDocumentSides = KyvshieldDocumentSides(hasRecto = true, hasVerso = false)
) {
    /** Whether the document has a recto side */
    val hasRecto: Boolean get() = supportedSides.hasRecto

    /** Whether the document has a verso side */
    val hasVerso: Boolean get() = supportedSides.hasVerso

    companion object {
        /**
         * Parse from a JSON object using snake_case keys
         * (as returned by the `/api/v1/documents` endpoint).
         */
        fun fromJson(json: JSONObject): KyvshieldDocument {
            val sidesJson = json.optJSONObject("supported_sides")
            return KyvshieldDocument(
                docType       = json.optString("doc_type", ""),
                category      = json.optString("document_category", ""),
                categoryLabel = json.optString("document_category_label", ""),
                name          = json.optString("name", ""),
                country       = json.optString("country", ""),
                countryName   = json.optString("country_name", ""),
                enabled       = json.optBoolean("enabled", true),
                supportedSides = if (sidesJson != null)
                    KyvshieldDocumentSides.fromJson(sidesJson)
                else
                    KyvshieldDocumentSides(hasRecto = true, hasVerso = false)
            )
        }
    }

    /**
     * Convert to the snake_case map expected by the JS SDK's
     * `kyvshieldDocumentFromJson` function.
     */
    internal fun toSnakeCaseMap(): Map<String, Any?> = mapOf(
        "doc_type"                to docType,
        "document_category"       to category,
        "document_category_label" to categoryLabel,
        "name"                    to name,
        "country"                 to country,
        "country_name"            to countryName,
        "enabled"                 to enabled,
        "supported_sides"         to supportedSides.toSnakeCaseMap()
    )

    override fun toString(): String = "$docType ($name)"
}
