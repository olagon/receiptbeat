package org.kealohalabs.receiptbeat.extraction

import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.kealohalabs.receiptbeat.core.model.Confidence
import org.kealohalabs.receiptbeat.core.model.Money

object NanoPrompt {
    private const val MAX_OCR_CHARS = 3000

    /** Prompt for an on-device LLM. Keeps the OCR text short for a ~4k token context. */
    fun build(ocrText: String): String = """
        |Read this receipt text and reply with ONLY one JSON object, no other words:
        |{"merchant":"","date":"YYYY-MM-DD","time":"HH:MM","subtotal":"12.34","tax":"","tip":"","total":"","currency":"USD",
        |"card_brand":"","last4":"","receipt_number":"","category":""}
        |Use null for anything you cannot find. Amounts are plain numbers with a dot, no symbols.
        |total is the final amount paid, including tip. category is one of: ${CategoryGuess.KEYS.joinToString(",")}.
        |Receipt text:
        |${ocrText.take(MAX_OCR_CHARS)}
    """.trimMargin()

    /** Parse a model reply (tolerates ```json fences and extra text) into Extracted with MEDIUM confidence, or null. */
    fun parse(reply: String): Extracted? {
        val end = reply.lastIndexOf('}')
        // Try each "{" in turn so stray braces in chatter before the JSON do not break it.
        val obj = reply.indices.filter { reply[it] == '{' && it < end }.firstNotNullOfOrNull { start ->
            runCatching { Json.parseToJsonElement(reply.substring(start, end + 1)).jsonObject }.getOrNull()
        } ?: return null

        fun str(key: String): String? = (obj[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        fun money(key: String): Field<Long> = found(str(key)?.let { Money.parse(it) }, Confidence.MEDIUM)

        val raw = str("merchant")
        val currency = str("currency")?.uppercase()?.takeIf { Regex("[A-Z]{3}").matches(it) }
        return Extracted(
            merchant = found(raw?.let { MerchantNormalizer.normalize(it) }?.ifBlank { null }, Confidence.MEDIUM),
            merchantRaw = raw,
            date = found(str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, Confidence.MEDIUM),
            time = str("time")?.let { t -> runCatching { LocalTime.parse(t.padStart(5, '0').take(5)) }.getOrNull() },
            subtotal = money("subtotal"),
            tax = money("tax"),
            tip = money("tip"),
            total = money("total"),
            currency = found(currency, Confidence.MEDIUM),
            paymentBrand = str("card_brand")?.uppercase(),
            last4 = str("last4")?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 },
            receiptNumber = str("receipt_number"),
            categoryGuess = str("category")?.lowercase()?.takeIf { it in CategoryGuess.KEYS }
        )
    }
}
