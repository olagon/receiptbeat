package org.kealohalabs.receiptbeat.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Money is always whole cents in a Long. These helpers are the only way in and out of text. */
object Money {
    fun format(cents: Long, currency: String = "USD", locale: Locale = Locale.US): String {
        val f = NumberFormat.getCurrencyInstance(locale)
        f.currency = runCatching { Currency.getInstance(currency) }.getOrDefault(Currency.getInstance("USD"))
        return f.format(BigDecimal.valueOf(cents, 2))
    }

    /** "12.50", "1,234.5", "$8", "8,50" (comma decimal) to cents. Null when it is not a number. */
    fun parse(text: String): Long? {
        var s = text.trim().replace(Regex("[^0-9.,-]"), "")
        if (s.isEmpty() || s == "-" || s.none { it.isDigit() }) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            // "1.234,56" or "8,50": comma is the decimal mark
            lastComma > lastDot && s.length - lastComma - 1 == 2 -> s.replace(".", "").replace(',', '.')
            else -> s.replace(",", "")
        }
        return runCatching { BigDecimal(s).setScale(2, RoundingMode.HALF_UP).unscaledValue().toLong() }.getOrNull()
    }

    /** For TalkBack: "forty two dollars and fifty cents" style. */
    fun spoken(cents: Long, currency: String = "USD"): String {
        val unit = if (currency == "USD") "dollars" else currency
        val whole = cents / 100
        val part = kotlin.math.abs(cents % 100)
        return if (part == 0L) "$whole $unit" else "$whole $unit and $part cents"
    }
}
