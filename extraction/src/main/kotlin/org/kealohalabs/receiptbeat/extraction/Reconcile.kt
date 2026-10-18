package org.kealohalabs.receiptbeat.extraction

import java.time.LocalDate
import kotlin.math.abs
import org.kealohalabs.receiptbeat.core.model.Confidence

object Reconcile {
    /** Merge AI result (may be null) with rules result. Prefer AI per field when present, rules otherwise.
     *  Then run the math check: subtotal+tax+tip must equal total within 1 cent, else total confidence LOW.
     *  If AI and rules totals disagree, total confidence LOW and both values in totalAlternatives.
     *  Dates in the future (after today) get LOW. */
    fun merge(ai: Extracted?, rules: Extracted, today: LocalDate = LocalDate.now()): Extracted {
        fun <T> pick(a: Field<T>?, r: Field<T>) = if (a?.value != null) a else r
        val merged = Extracted(
            merchant = pick(ai?.merchant, rules.merchant),
            merchantRaw = rules.merchantRaw ?: ai?.merchantRaw,
            address = ai?.address ?: rules.address,
            phone = ai?.phone ?: rules.phone,
            date = pick(ai?.date, rules.date),
            time = ai?.time ?: rules.time,
            subtotal = pick(ai?.subtotal, rules.subtotal),
            tax = pick(ai?.tax, rules.tax),
            tip = pick(ai?.tip, rules.tip),
            total = pick(ai?.total, rules.total),
            currency = pick(ai?.currency, rules.currency),
            paymentBrand = ai?.paymentBrand ?: rules.paymentBrand,
            last4 = ai?.last4 ?: rules.last4,
            receiptNumber = ai?.receiptNumber ?: rules.receiptNumber,
            lineItems = ai?.lineItems?.ifEmpty { null } ?: rules.lineItems,
            categoryGuess = ai?.categoryGuess ?: rules.categoryGuess
        )
        var total = merged.total
        var alternatives = emptyList<Long>()
        val aiTotal = ai?.total?.value
        val rulesTotal = rules.total.value
        if (aiTotal != null && rulesTotal != null && abs(aiTotal - rulesTotal) > 1) {
            total = total.copy(confidence = Confidence.LOW)
            alternatives = listOf(aiTotal, rulesTotal)
        }
        val sub = merged.subtotal.value
        if (sub != null && total.value != null) {
            val sum = sub + (merged.tax.value ?: 0) + (merged.tip.value ?: 0)
            if (abs(sum - total.value!!) > 1) total = total.copy(confidence = Confidence.LOW)
        }
        val date = merged.date.let { if (it.value?.isAfter(today) == true) it.copy(confidence = Confidence.LOW) else it }
        return merged.copy(total = total, totalAlternatives = alternatives, date = date)
    }
}
