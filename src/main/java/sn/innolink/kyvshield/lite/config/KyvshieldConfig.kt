package sn.innolink.kyvshield.lite.config

/**
 * Theme configuration for customizing SDK colors.
 *
 * Pass to [KyvshieldConfig] to override default colors.
 * Colors are ARGB integers — e.g. `0xFFEF8352.toInt()` for orange.
 * Only set the colors you want to change; others use defaults.
 */
data class KyvshieldThemeConfig(
    /** Primary brand color (default: orange #EF8352). ARGB int. */
    val primaryColor: Int? = null,

    /** Success color (default: green #10B981). ARGB int. */
    val successColor: Int? = null,

    /** Warning color (default: yellow #F59E0B). ARGB int. */
    val warningColor: Int? = null,

    /** Error color (default: red #EF4444). ARGB int. */
    val errorColor: Int? = null,

    /**
     * Dark mode override.
     * - `true`  → force dark background
     * - `false` → force light background
     * - `null`  → follow system setting (default)
     */
    val darkMode: Boolean? = null
) {
    companion object {
        /** Blue theme preset — primary #3B82F6 */
        val blue = KyvshieldThemeConfig(primaryColor = 0xFF3B82F6.toInt())

        /** Green theme preset — primary #10B981 */
        val green = KyvshieldThemeConfig(primaryColor = 0xFF10B981.toInt())

        /** Purple theme preset — primary #8B5CF6 */
        val purple = KyvshieldThemeConfig(primaryColor = 0xFF8B5CF6.toInt())

        /** Kratos theme preset — corporate blue #00377D */
        val kratos = KyvshieldThemeConfig(primaryColor = 0xFF00377D.toInt())

        /** Luna theme preset — golden yellow #FFD100 */
        val luna = KyvshieldThemeConfig(primaryColor = 0xFFFFD100.toInt())
    }

    /**
     * Convert an ARGB [color] integer to a CSS hex string (e.g. `"#EF8352"`).
     * Strips the alpha channel — the JS SDK expects 6-digit hex.
     */
    internal fun colorToHex(color: Int): String =
        "#${(color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()}"

    /** CSS hex string for [primaryColor], or `null` if not set. */
    internal val primaryHex: String? get() = primaryColor?.let { colorToHex(it) }

    /** CSS hex string for [successColor], or `null` if not set. */
    internal val successHex: String? get() = successColor?.let { colorToHex(it) }

    /** CSS hex string for [warningColor], or `null` if not set. */
    internal val warningHex: String? get() = warningColor?.let { colorToHex(it) }

    /** CSS hex string for [errorColor], or `null` if not set. */
    internal val errorHex: String? get() = errorColor?.let { colorToHex(it) }
}

/**
 * Main SDK configuration — required to start a KYC session.
 *
 * ```kotlin
 * val config = KyvshieldConfig(
 *     baseUrl = "https://kyvshield-naruto.innolinkcloud.com",
 *     apiKey  = "your-api-key"
 * )
 * ```
 */
data class KyvshieldConfig(
    /** API base URL (required) — e.g. "https://kyvshield-naruto.innolinkcloud.com" */
    val baseUrl: String,

    /** API key for authentication (required) */
    val apiKey: String,

    /** API version (default: "v1") */
    val apiVersion: String = "v1",

    /** Enable detailed logging for debugging */
    val enableLog: Boolean = false,

    /** HTTP request timeout in seconds (default: 60) */
    val timeoutSeconds: Int = 60,

    /** Optional theme color overrides */
    val theme: KyvshieldThemeConfig? = null,

    /** Optional client ID for webhook correlation / tracking */
    val clientId: String? = null
) {
    /** Versioned API base path — e.g. "https://…/api/v1" */
    val apiBasePath: String get() = "$baseUrl/api/$apiVersion"
}
