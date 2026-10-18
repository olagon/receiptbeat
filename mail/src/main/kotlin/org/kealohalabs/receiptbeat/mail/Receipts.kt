package org.kealohalabs.receiptbeat.mail

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.kealohalabs.receiptbeat.core.model.Money

data class ScoreResult(val score: Int, val amountCents: Long?, val currency: String, val signals: List<String>)

object ReceiptScorer {
    const val KEEP = 70
    const val DROP = 25

    /** Editable starter list of merchant and payment sender domains. A sender matches its domain or any subdomain. */
    val knownSenders: Set<String> = setOf(
        "amazon.com", "amazon.ca", "amazon.co.uk", "uber.com", "lyft.com",
        "hawaiianairlines.com", "alaskaair.com", "united.com", "delta.com", "southwest.com", "aa.com",
        "squareup.com", "square.com", "stripe.com", "paypal.com", "venmo.com", "apple.com", "google.com",
        "doordash.com", "airbnb.com", "booking.com", "expedia.com", "costco.com", "target.com", "walmart.com", "homedepot.com"
    )

    private val subjectWords = Regex(
        "(?i)receipt|order confirm|invoice|your order|payment received|trip with|your payment|booking confirm|purchase"
    )
    private val marketingWords = Regex("(?i)\\b(sale|deals?|newsletter|clearance|limited time|shop now|promo|coupon)\\b|\\d+\\s*%\\s*off")

    fun score(msg: MailMessage, extraKnownSenders: Set<String> = emptySet()): ScoreResult {
        val signals = mutableListOf<String>()
        var score = 0
        val text = EmailExtractor.bodyOf(msg)
        val amount = Amounts.total(Amounts.find(text))
        if (Schema.present(msg.html.orEmpty())) {
            score += 60
            signals += "schema.org order"
        }
        if (isKnown(msg.header.from, extraKnownSenders)) {
            score += 30
            signals += "known sender"
        }
        if (amount != null) {
            score += 30
            signals += "amount near total"
        }
        if (msg.attachments.any { isPdf(it) && Regex("(?i)invoice|receipt").containsMatchIn(it.name) }) {
            score += 30
            signals += "invoice or receipt pdf"
        }
        if (subjectWords.containsMatchIn(msg.header.subject)) {
            score += 20
            signals += "receipt words in subject"
        }
        val unsubscribes = Regex("(?i)unsubscribe").findAll(text + msg.html.orEmpty()).count()
        if (amount == null && (marketingWords.containsMatchIn(msg.header.subject + "\n" + text) || unsubscribes >= 3)) {
            score -= 40
            signals += "marketing"
        }
        return ScoreResult(score.coerceIn(0, 100), amount?.cents, amount?.currency ?: "USD", signals)
    }

    /** Header-only pre-score for scheduled scans and borderline decisions. */
    fun headerScore(h: MailHeader): Int {
        var score = 10
        if (isKnown(h.from, emptySet())) score += 40
        if (subjectWords.containsMatchIn(h.subject)) score += 40
        if (marketingWords.containsMatchIn(h.subject)) score -= 30
        return score.coerceIn(0, 100)
    }

    private fun isKnown(from: String, extra: Set<String>): Boolean {
        val domain = from.substringAfter('@').lowercase()
        if (from in extra) return true
        return (knownSenders + extra.map { it.lowercase() }).any { domain == it || domain.endsWith(".$it") }
    }
}

internal fun isPdf(a: MailAttachment) = a.mime == "application/pdf" || a.name.endsWith(".pdf", ignoreCase = true)

data class EmailFields(
    val merchant: String?,
    val date: LocalDate?,
    val totalCents: Long?,
    val taxCents: Long?,
    val currency: String,
    val orderNumber: String?,
    val pdf: MailAttachment?,
    val bodyText: String,
    val fromSchema: Boolean
)

object EmailExtractor {
    /** Field pull in trust order: schema.org markup, then the first PDF attachment (the app OCRs it), then body text. */
    fun extract(msg: MailMessage): EmailFields {
        val body = bodyOf(msg)
        val schema = msg.html?.let { Schema.read(it) }
        val amounts = Amounts.find(body)
        val total = Amounts.total(amounts)
        return EmailFields(
            merchant = schema?.merchant ?: merchantOf(msg.header),
            date = schema?.date ?: bodyDate(body) ?: msg.header.date?.atZone(ZoneId.systemDefault())?.toLocalDate(),
            totalCents = schema?.total ?: total?.cents,
            taxCents = amounts.firstOrNull { it.isTax }?.cents,
            currency = schema?.currency ?: total?.currency ?: "USD",
            orderNumber =
            schema?.orderNumber ?: orderNumber.findAll(body).map { it.groupValues[1] }.firstOrNull { n -> n.any { it.isDigit() } },
            pdf = msg.attachments.firstOrNull { isPdf(it) },
            bodyText = body,
            fromSchema = schema != null
        )
    }

    /** HTML to readable plain text, scripts/styles removed, images dropped (remote images never loaded). */
    fun htmlToText(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, head, img, noscript").remove()
        doc.select("br").append("\\n")
        doc.select("p, div, tr, li, h1, h2, h3, h4, h5, h6, table").prepend("\\n")
        return doc.text().replace("\\n", "\n").lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** Sanitized HTML safe to render: no scripts, frames, objects, remote images, event handlers or external CSS. */
    fun safeHtml(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, iframe, frame, frameset, object, embed, applet, link, base, meta[http-equiv]").remove()
        doc.select("img").filter { REMOTE.containsMatchIn(it.attr("src")) || it.hasAttr("srcset") }.forEach { it.remove() }
        for (el in doc.getAllElements()) {
            el.attributes().map { it.key }.forEach { key ->
                val value = el.attr(key)
                val drop = key.startsWith("on", ignoreCase = true) ||
                    (key.equals("style", true) && value.contains("url(", true)) ||
                    (key.equals("background", true) && REMOTE.containsMatchIn(value)) ||
                    (key in URL_ATTRS && value.trim().startsWith("javascript:", true))
                if (drop) el.removeAttr(key)
            }
        }
        return doc.outerHtml()
    }

    internal fun bodyOf(msg: MailMessage): String = msg.html?.let { htmlToText(it) } ?: msg.text.orEmpty()

    private val REMOTE = Regex("^\\s*(https?:)?//", RegexOption.IGNORE_CASE)
    private val URL_ATTRS = setOf("href", "src", "action", "formaction")
    private val orderNumber = Regex(
        "(?i)\\b(?:order|confirmation|invoice|receipt|booking)\\s*(?:number|no\\.?|#|id|code)?\\s*[:#]?\\s*#?([A-Z0-9][A-Z0-9-]{4,})"
    )
    private val subjectMerchant = Regex("(?i)\\b(?:receipt|order|purchase|payment|trip)\\s+(?:from|at|with|to)\\s+([^\\n#|]{2,40})")
    private val fromNoise = Regex("(?i)\\b(receipts?|orders?|no-?reply|notifications?|team|billing|support|info|customer service)\\b")

    private fun merchantOf(h: MailHeader): String? {
        subjectMerchant.find(h.subject)?.groupValues?.get(1)?.trim()?.trimEnd('.', '!')?.takeIf { it.isNotBlank() }?.let { return it }
        h.fromName?.replace(fromNoise, "")?.replace(Regex("\\s+"), " ")?.trim(' ', '-', ',')?.takeIf { it.isNotBlank() }?.let { return it }
        val labels = h.from.substringAfter('@', "").split('.').filter { it.isNotBlank() }
        if (labels.size < 2) return null
        // "shop.co.uk" style: the name is before a short second-level label.
        val name = if (labels.size >= 3 && labels[labels.size - 2].length <= 3) labels[labels.size - 3] else labels[labels.size - 2]
        return name.replaceFirstChar { it.titlecase(Locale.US) }
    }

    private val dateNear = Regex(
        "(?i)(?:order(?:ed)?(?: date)?|date|placed on|paid on|issued|purchased)[^\\n\\d]{0,15}" +
            "([A-Z][a-z]{2,8}\\.? \\d{1,2},? \\d{4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4})"
    )
    private val dateFormats = listOf("MMMM d yyyy", "MMM d yyyy", "yyyy-MM-dd", "M/d/yyyy").map {
        DateTimeFormatter.ofPattern(it, Locale.US)
    }

    private fun bodyDate(body: String): LocalDate? = dateNear.findAll(body).firstNotNullOfOrNull { m ->
        val raw = m.groupValues[1].replace(",", "").replace(".", "")
        dateFormats.firstNotNullOfOrNull { runCatching { LocalDate.parse(raw, it) }.getOrNull() }
    }
}

internal data class Amount(val label: String, val cents: Long, val currency: String) {
    val isTax get() = Regex("tax|vat|gst|hst").containsMatchIn(label)
}

internal object Amounts {
    private val pattern = Regex(
        "(?i)\\b(grand total|order total|total charged|amount charged|amount paid|total paid|total due|amount due|balance due|" +
            "subtotal|sales tax|total|paid|charged|amount|tax|vat|gst|hst)\\b([^\\d$€£¥\\n]{0,25}?)" +
            "([$€£¥]|USD|EUR|GBP|CAD|AUD|JPY)?\\s?(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{2})?|\\d+\\.\\d{2})"
    )
    private val symbols = mapOf("$" to "USD", "€" to "EUR", "£" to "GBP", "¥" to "JPY")

    fun find(text: String): List<Amount> = pattern.findAll(text).mapNotNull { m ->
        val (label, between, sym, number) = m.destructured
        val code = symbols[sym] ?: sym.uppercase().ifEmpty { null }
            ?: Regex("\\b(USD|EUR|GBP|CAD|AUD|JPY)\\b", RegexOption.IGNORE_CASE).find(between)?.value?.uppercase() ?: "USD"
        Money.parse(number)?.let { Amount(label.lowercase(), it, code) }
    }.toList()

    /** ponytail: biggest "total" wins (total >= subtotal), else the first paid/charged/amount. Refunds or "total savings" can fool it. */
    fun total(amounts: List<Amount>): Amount? {
        val candidates = amounts.filter { !it.isTax && it.label != "subtotal" }
        return candidates.filter { "total" in it.label }.maxByOrNull { it.cents } ?: candidates.firstOrNull()
    }
}

internal data class SchemaFields(
    val merchant: String?,
    val date: LocalDate?,
    val total: Long?,
    val currency: String?,
    val orderNumber: String?
)

/** schema.org Order and Invoice markup, as JSON-LD or microdata. */
internal object Schema {
    private val marker = Regex(
        "(?i)\"@type\"\\s*:\\s*\\[?\\s*\"(?:https?://schema\\.org/)?(Order|Invoice)\"|" +
            "itemtype\\s*=\\s*[\"']https?://schema\\.org/(Order|Invoice)[\"']"
    )

    fun present(html: String) = marker.containsMatchIn(html)

    fun read(html: String): SchemaFields? {
        if (!present(html)) return null
        val doc = Jsoup.parse(html)
        return jsonLd(doc) ?: microdata(doc)
    }

    private fun jsonLd(doc: Document): SchemaFields? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val root = runCatching { Json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            val order = findOrder(root) ?: continue
            val ref = order.obj("referencesOrder")
            val due = order.obj("totalPaymentDue") ?: order.obj("priceSpecification")
            return SchemaFields(
                merchant = listOf("seller", "merchant", "provider", "broker").firstNotNullOfOrNull { order.name(it) },
                date = (order.str("orderDate") ?: ref?.str("orderDate"))?.let { date(it) },
                total = (order.str("totalPrice") ?: order.str("price") ?: due?.str("price") ?: due?.str("value"))?.let { Money.parse(it) },
                currency = order.str("priceCurrency") ?: due?.str("priceCurrency") ?: order.obj("acceptedOffer")?.str("priceCurrency"),
                orderNumber =
                order.str("orderNumber") ?: order.str("confirmationNumber") ?: order.str("identifier") ?: ref?.str("orderNumber")
            )
        }
        return null
    }

    private fun findOrder(e: JsonElement): JsonObject? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull { findOrder(it) }
        is JsonObject -> {
            val type = e["@type"]
            val types =
                (type as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: listOfNotNull((type as? JsonPrimitive)?.content)
            if (types.any {
                    it.substringAfterLast('/') in setOf("Order", "Invoice")
                }
            ) {
                e
            } else {
                e.values.firstNotNullOfOrNull { findOrder(it) }
            }
        }
        else -> null
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: (this[key] as? JsonArray)?.firstOrNull() as? JsonObject
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.name(key: String) = obj(key)?.str("name") ?: str(key)

    private fun microdata(doc: Document): SchemaFields? {
        val scope = doc.select("[itemscope][itemtype]").firstOrNull {
            Regex("(?i)schema\\.org/(Order|Invoice)$").containsMatchIn(it.attr("itemtype").trim())
        } ?: return null
        fun value(el: Element?) =
            el?.let { it.attr("content").ifEmpty { it.attr("datetime") }.ifEmpty { it.text() } }?.trim()?.ifEmpty { null }
        fun prop(name: String) = value(scope.selectFirst("[itemprop=$name]"))
        return SchemaFields(
            merchant = value(scope.selectFirst("[itemprop=seller] [itemprop=name], [itemprop=merchant] [itemprop=name]")) ?: prop("seller"),
            date = prop("orderDate")?.let { date(it) },
            total = (prop("totalPrice") ?: prop("price"))?.let { Money.parse(it) },
            currency = prop("priceCurrency"),
            orderNumber = prop("orderNumber") ?: prop("confirmationNumber")
        )
    }

    private fun date(s: String) = runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()
}
