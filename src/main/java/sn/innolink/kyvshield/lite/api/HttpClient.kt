package sn.innolink.kyvshield.lite.api

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Minimal internal HTTP client for KyvShield REST API calls.
 *
 * Uses [HttpURLConnection] to avoid adding OkHttp or Retrofit as dependencies.
 * All methods are blocking — call from a coroutine dispatcher (e.g. [Dispatchers.IO]).
 */
internal class HttpClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val apiVersion: String = "v1",
    private val timeoutMs: Int = 60_000
) {
    private val apiBase: String get() = "${baseUrl.trimEnd('/')}/api/$apiVersion"

    // ── Identify (multipart) ────────────────────────────────────────────────

    /**
     * POST /api/v1/identify — send image as multipart form with optional top_k / min_score.
     */
    fun identify(imageBytes: ByteArray, options: IdentifyOptions?): IdentifyResponse {
        val boundary = "KyvShield-${UUID.randomUUID()}"
        val url = URL("$apiBase/identify")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("X-API-Key", apiKey)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            val body = buildMultipart(boundary) {
                addFile("image", "image.jpg", "image/jpeg", imageBytes)
                options?.topK?.let { addField("top_k", it.toString()) }
                options?.minScore?.let { addField("min_score", it.toString()) }
            }

            conn.outputStream.use { it.write(body) }

            val status = conn.responseCode
            val responseBody = readResponse(conn)
            val json = JSONObject(responseBody)
            return IdentifyResponse.fromJson(json, httpStatus = status)
        } catch (e: Exception) {
            return IdentifyResponse.error("Network error: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    // ── Verify Face (multipart) ─────────────────────────────────────────────

    /**
     * POST /api/v1/verify/face — send target_image + source_image as multipart form.
     */
    fun verifyFace(
        targetImageBytes: ByteArray,
        sourceImageBytes: ByteArray,
        options: FaceVerifyOptions?
    ): FaceVerifyResponse {
        val boundary = "KyvShield-${UUID.randomUUID()}"
        val url = URL("$apiBase/verify/face")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("X-API-Key", apiKey)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            val body = buildMultipart(boundary) {
                addFile("target_image", "target.jpg", "image/jpeg", targetImageBytes)
                addFile("source_image", "source.jpg", "image/jpeg", sourceImageBytes)
                options?.detectionModel?.let { addField("detection_model", it) }
                options?.recognitionModel?.let { addField("recognition_model", it) }
            }

            conn.outputStream.use { it.write(body) }

            val status = conn.responseCode
            val responseBody = readResponse(conn)
            val json = JSONObject(responseBody)
            return FaceVerifyResponse.fromJson(json, httpStatus = status)
        } catch (e: Exception) {
            return FaceVerifyResponse.error("Network error: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    // ── Multipart builder ───────────────────────────────────────────────────

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) {
            conn.inputStream
        } else {
            conn.errorStream ?: conn.inputStream
        }
        return stream.bufferedReader().use { it.readText() }
    }

    private fun buildMultipart(boundary: String, block: MultipartBuilder.() -> Unit): ByteArray {
        val builder = MultipartBuilder(boundary)
        builder.block()
        return builder.build()
    }

    private class MultipartBuilder(private val boundary: String) {
        private val baos = ByteArrayOutputStream()
        private val writer = DataOutputStream(baos)
        private val lineEnd = "\r\n"

        fun addField(name: String, value: String) {
            writer.writeBytes("--$boundary$lineEnd")
            writer.writeBytes("Content-Disposition: form-data; name=\"$name\"$lineEnd")
            writer.writeBytes(lineEnd)
            writer.writeBytes(value)
            writer.writeBytes(lineEnd)
        }

        fun addFile(name: String, filename: String, mimeType: String, data: ByteArray) {
            writer.writeBytes("--$boundary$lineEnd")
            writer.writeBytes("Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"$lineEnd")
            writer.writeBytes("Content-Type: $mimeType$lineEnd")
            writer.writeBytes(lineEnd)
            writer.write(data)
            writer.writeBytes(lineEnd)
        }

        fun build(): ByteArray {
            writer.writeBytes("--$boundary--$lineEnd")
            writer.flush()
            return baos.toByteArray()
        }
    }
}
