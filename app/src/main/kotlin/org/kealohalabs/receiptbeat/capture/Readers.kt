package org.kealohalabs.receiptbeat.capture

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.kealohalabs.receiptbeat.core.data.AiProvider
import org.kealohalabs.receiptbeat.core.data.SecretStore
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.extraction.Box
import org.kealohalabs.receiptbeat.extraction.Extracted
import org.kealohalabs.receiptbeat.extraction.NanoPrompt
import org.kealohalabs.receiptbeat.extraction.OcrLine

/** ML Kit Text Recognition v2, on device. Keeps every line and where it sits on the image. */
@Singleton
class Ocr @Inject constructor() {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Lines in reading order. [yOffset] shifts boxes when several pages are read as one. */
    suspend fun read(bmp: Bitmap, yOffset: Int = 0): List<OcrLine> {
        val result = client.process(InputImage.fromBitmap(bmp, 0)).await()
        return result.textBlocks.flatMap { it.lines }
            .map { l ->
                val r = l.boundingBox
                OcrLine(l.text, r?.let { Box(it.left, it.top + yOffset, it.width(), it.height()) })
            }
            .sortedWith(compareBy({ (it.box?.y ?: 0) / ROW_PX }, { it.box?.x ?: 0 }))
    }

    private companion object {
        const val ROW_PX = 12
    }
}

/**
 * Gemini Nano through the ML Kit GenAI Prompt API. Android only allows it while the app is on screen,
 * so background work skips it and the rules parser carries the load.
 */
@Singleton
class NanoReader @Inject constructor() {
    private val model by lazy { Generation.getClient() }

    val inForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    suspend fun status(): Int = runCatching { model.checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE)

    suspend fun ask(prompt: String): String? {
        if (!inForeground || status() != FeatureStatus.AVAILABLE) return null
        return runCatching {
            val req = generateContentRequest(TextPart(prompt)) {
                temperature = 0.1f
                topK = 8
                maxOutputTokens = 400
            }
            model.generateContent(req).candidates.firstOrNull()?.text
        }.onFailure { Log.w("ReceiptBeat", "Nano failed", it) }.getOrNull()
    }

    suspend fun extract(ocrText: String): Extracted? = ask(NanoPrompt.build(ocrText))?.let(NanoPrompt::parse)
}

/**
 * Optional cloud AI with the user's own key. Off by default. Sends only the OCR text.
 * Uses plain HttpURLConnection so no extra network library is needed.
 */
@Singleton
class CloudReader @Inject constructor(private val settings: SettingsRepository, private val secrets: SecretStore) {
    suspend fun extract(ocrText: String): Extracted? = withContext(Dispatchers.IO) {
        val provider = settings.current().aiProvider
        val key = secrets.get(SecretStore.AI_KEY)
        if (provider == AiProvider.NONE || key.isNullOrBlank()) return@withContext null
        runCatching { ask(provider, key, NanoPrompt.build(ocrText))?.let(NanoPrompt::parse) }
            .onFailure { Log.w("ReceiptBeat", "Cloud AI failed", it) }
            .getOrNull()
    }

    /** Also used by the Settings extraction test screen. */
    fun ask(provider: AiProvider, key: String, prompt: String): String? = when (provider) {
        AiProvider.NONE -> null
        AiProvider.GEMINI -> post(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            mapOf("x-goog-api-key" to key),
            JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        ).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        AiProvider.CLAUDE -> post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
            JSONObject().put("model", "claude-haiku-5-5").put("max_tokens", 600)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        ).getJSONArray("content").getJSONObject(0).getString("text")
        AiProvider.OPENAI -> post(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer $key"),
            JSONObject().put("model", "gpt-4.1-mini")
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        ).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 45_000
        c.doOutput = true
        c.setRequestProperty("content-type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        check(code in 200..299) { "HTTP $code" }
        return JSONObject(text)
    }
}
