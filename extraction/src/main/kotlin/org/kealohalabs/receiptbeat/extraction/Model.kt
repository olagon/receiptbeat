package org.kealohalabs.receiptbeat.extraction

import java.time.LocalDate
import java.time.LocalTime
import org.kealohalabs.receiptbeat.core.model.Confidence

/** Where a line sits on the display image, in pixels. */
data class Box(val x: Int, val y: Int, val w: Int, val h: Int)

data class OcrLine(val text: String, val box: Box? = null)

/** One extracted value, how sure we are, and the line it came from. */
data class Field<T>(val value: T?, val confidence: Confidence, val box: Box? = null)

data class ParsedLineItem(val name: String, val qty: Double, val totalCents: Long)

data class Extracted(
    val merchant: Field<String>,
    val merchantRaw: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val date: Field<LocalDate>,
    val time: LocalTime? = null,
    val subtotal: Field<Long>,
    val tax: Field<Long>,
    val tip: Field<Long>,
    val total: Field<Long>,
    val currency: Field<String>,
    val paymentBrand: String? = null,
    val last4: String? = null,
    val receiptNumber: String? = null,
    val lineItems: List<ParsedLineItem> = emptyList(),
    /** Other plausible totals, shown when AI and rules disagree. */
    val totalAlternatives: List<Long> = emptyList(),
    val categoryGuess: String? = null
)

internal fun <T> missing(): Field<T> = Field(null, Confidence.LOW)

internal fun <T> found(value: T?, confidence: Confidence, box: Box? = null): Field<T> =
    if (value == null) missing() else Field(value, confidence, box)
