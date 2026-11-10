package com.rokutoru.pipeline

import com.rokutoru.model.MangaPage
import com.rokutoru.model.TextBlock
import com.rokutoru.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import java.nio.charset.StandardCharsets

class TranslatorPipeline(
    private val sourceLang: String,
    private val targetLang: String,
    private val provider: AiProvider,
    private val apiKey: String,
    private val model: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun processPage(page: MangaPage): List<TextBlock> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) {
            "${provider.label} API key belum diisi. Buka Settings untuk mengaturnya."
        }
        require(model.isNotBlank()) { "Nama model belum diisi." }
        require(page.file.isFile) { "File gambar tidak ditemukan: ${page.file.name}" }

        val encodedImage = Base64.getEncoder().encodeToString(page.file.readBytes())
        val mimeType = when (page.file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val prompt = """
            Detect all readable dialogue, narration, and sound effects in this manga page.
            Translate each detected text from $sourceLang to $targetLang.
            Return only a JSON object with a "blocks" array. Each item must have:
            "originalText", "translatedText", and "box" with normalized image coordinates
            "x", "y", "width", "height" from 0 to 1. Coordinates use the top-left origin.
            Make boxes tightly enclose each text region. Do not invent text. If nothing is
            readable, return {"blocks":[]}.
        """.trimIndent()

        val requestBody = when (provider) {
            AiProvider.OPENAI -> buildOpenAiRequest(prompt, mimeType, encodedImage)
            AiProvider.GEMINI -> buildGeminiRequest(prompt, mimeType, encodedImage)
        }
        val endpoint = when (provider) {
            AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
            AiProvider.GEMINI ->
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "${URLEncoder.encode(model, StandardCharsets.UTF_8)}:generateContent" +
                    "?key=${URLEncoder.encode(apiKey, StandardCharsets.UTF_8)}"
        }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 180_000
            connection.doOutput = true
            if (provider == AiProvider.OPENAI) {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(requestBody.toString().toByteArray(Charsets.UTF_8)) }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(statusCode in 200..299) {
                "API ${provider.label} gagal (HTTP $statusCode): ${errorMessage(responseText) ?: responseText.take(500)}"
            }

            val content = when (provider) {
                AiProvider.OPENAI -> openAiContent(responseText)
                AiProvider.GEMINI -> geminiContent(responseText)
            } ?: error("${provider.label} mengembalikan respons dengan format yang tidak dikenal.")

            val blockArray = json.parseToJsonElement(content)
                .let { it as? kotlinx.serialization.json.JsonObject }
                ?.get("blocks")
                ?.let { it as? kotlinx.serialization.json.JsonArray }
                ?: error("Respons model tidak memiliki array 'blocks'.")

            blockArray.mapNotNull { item ->
                val block = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val box = block["box"] as? kotlinx.serialization.json.JsonObject
                    ?: return@mapNotNull null
                val x = box.number("x") ?: return@mapNotNull null
                val y = box.number("y") ?: return@mapNotNull null
                val width = box.number("width") ?: return@mapNotNull null
                val height = box.number("height") ?: return@mapNotNull null
                TextBlock(
                    id = UUID.randomUUID().toString(),
                    originalText = block.string("originalText").orEmpty(),
                    translatedText = block.string("translatedText").orEmpty(),
                    x = x.coerceIn(0f, 1f),
                    y = y.coerceIn(0f, 1f),
                    width = width.coerceIn(0.01f, 1f - x.coerceIn(0f, 0.99f)),
                    height = height.coerceIn(0.01f, 1f - y.coerceIn(0f, 0.99f))
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildOpenAiRequest(
        prompt: String,
        mimeType: String,
        encodedImage: String
    ) = buildJsonObject {
        put("model", model)
        put("response_format", buildJsonObject { put("type", "json_object") })
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", prompt)
                    })
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", "data:$mimeType;base64,$encodedImage")
                            put("detail", "high")
                        })
                    })
                }
            })
        }
    }

    private fun buildGeminiRequest(
        prompt: String,
        mimeType: String,
        encodedImage: String
    ) = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", prompt) })
                    add(buildJsonObject {
                        put("inline_data", buildJsonObject {
                            put("mime_type", mimeType)
                            put("data", encodedImage)
                        })
                    })
                }
            })
        }
        put("generationConfig", buildJsonObject {
            put("responseMimeType", "application/json")
            put("temperature", 0.2)
        })
    }

    private fun openAiContent(responseText: String): String? =
        json.parseToJsonElement(responseText)
            .let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("choices")
            ?.let { it as? kotlinx.serialization.json.JsonArray }
            ?.firstOrNull()
            ?.let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("message")
            ?.let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("content")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull

    private fun geminiContent(responseText: String): String? =
        json.parseToJsonElement(responseText)
            .let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("candidates")
            ?.let { it as? kotlinx.serialization.json.JsonArray }
            ?.firstOrNull()
            ?.let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("content")
            ?.let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("parts")
            ?.let { it as? kotlinx.serialization.json.JsonArray }
            ?.mapNotNull { part ->
                (part as? kotlinx.serialization.json.JsonObject)
                    ?.get("text")
                    ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                    ?.contentOrNull
            }
            ?.joinToString("\n")
            ?.takeIf { it.isNotBlank() }

    private fun errorMessage(responseText: String): String? = runCatching {
        val error = json.parseToJsonElement(responseText)
            .let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("error")
            ?.let { it as? kotlinx.serialization.json.JsonObject }
        error?.get("message")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull
            ?: error?.get("status")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.contentOrNull
    }.getOrNull()

    suspend fun processAll(
        pages: List<MangaPage>,
        onProgress: (completed: Int, total: Int) -> Unit
    ) {
        pages.forEachIndexed { index, page ->
            val detected = processPage(page)
            page.textBlocks.clear()
            page.textBlocks.addAll(detected)
            page.isProcessed = true
            onProgress(index + 1, pages.size)
        }
    }

    private fun kotlinx.serialization.json.JsonObject.number(key: String): Float? =
        this[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.floatOrNull }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        this[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
}
