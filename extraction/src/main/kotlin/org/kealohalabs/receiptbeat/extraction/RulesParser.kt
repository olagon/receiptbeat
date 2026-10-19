package org.kealohalabs.receiptbeat.extraction

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import org.kealohalabs.receiptbeat.core.model.Confidence
import org.kealohalabs.receiptbeat.core.model.Money

/** Regex and heuristics over OCR lines. Runs on every device and cross checks the AI. */
object RulesParser {
    fun parse(text: String, today: LocalDate = LocalDate.now()): Extracted = parse(text.lines().map { OcrLine(it) }, today)

    fun parse(lines: List<OcrLine>, today: LocalDate = LocalDate.now()): Extracted {
        val rows = lines.map { Row(it) }.filter { it.raw.isNotBlank() }
        val fullText = rows.joinToString("\n") { it.raw }
        val currency = currency(fullText)
        rows.forEach { it.amount = lastAmount(it.clean, currency.value == "JPY") }
        pairColumns(rows)

        val subtotal = rows.lastOrNull { it.isSubtotal() && it.amount != null }
        val tip = rows.lastOrNull { it.isTip() && (it.amount ?: 0) > 0 }
        val taxRows = rows.filter { it.isTax() && (it.amount ?: 0) > 0 }
        val (total, totalConfidence) = total(rows, subtotal?.amount, taxRows, tip?.amount)
        val tax = vatTable(rows)?.let { Triple(it.first, it.second, Confidence.HIGH) }
            ?: tax(taxRows, subtotal?.amount, tip?.amount, total?.amount)
        val merchant = merchant(rows)
        val merchantName = merchant?.let { MerchantNormalizer.normalize(it.second) }
        val merchantConfidence = if (merchant?.third == true) Confidence.HIGH else Confidence.MEDIUM
        val date = date(rows, today, dayFirst = currency.value in dayFirstCurrencies)
        val card = card(fullText)

        return Extracted(
            merchant = found(merchantName?.ifBlank { null }, merchantConfidence, merchant?.first?.box),
            merchantRaw = merchant?.first?.raw?.trim(),
            address = address(rows),
            phone = phone.find(fullText)?.value,
            date = found(date?.second, date?.third ?: Confidence.LOW, date?.first?.box),
            time = time(rows, date?.first),
            subtotal = found(subtotal?.amount, Confidence.HIGH, subtotal?.box),
            tax = found(tax?.second, tax?.third ?: Confidence.LOW, tax?.first?.box),
            tip = found(tip?.amount, Confidence.HIGH, tip?.box),
            total = found(total?.amount, totalConfidence, total?.box),
            currency = currency,
            paymentBrand = card.first,
            last4 = card.second,
            receiptNumber = receiptNumber.find(fullText)?.groupValues?.get(1),
            lineItems = lineItems(rows),
            categoryGuess = CategoryGuess.guess(merchantName, fullText)
        )
    }

    private class Row(line: OcrLine) {
        val raw = line.text
        val box = line.box

        /** OCR fixes for numbers: O read for 0 next to digits, stray space around a decimal point. */
        val clean = raw
            .replace(numberish) { m -> if (m.value.any { it.isDigit() }) m.value.replace('O', '0').replace('o', '0') else m.value }
            .replace(Regex("""(\d) ?\. ?(\d) ?(\d)(?!\d)"""), "$1.$2$3")
        val upper = raw.uppercase()

        /** Letters only, so "T OTAL" and "SUB TOTAL" still match. */
        val joined = upper.replace('0', 'O').replace('1', 'L').filter { it.isLetter() }
        val words = upper.split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }.toSet()
        var amount: Long? = null

        fun has(vararg w: String) = w.any { it in words }

        fun isSubtotal() = "SUBTOTAL" in joined ||
            "SUBTOT" in joined ||
            "ZWISCHENSUMME" in joined ||
            "SOUSTOTAL" in joined ||
            "小計" in joined ||
            has("NETTO") &&
            !has("BRUTTO")

        fun isTip() = has("TIP", "TIPS", "GRATUITY", "TRINKGELD", "POURBOIRE") && '%' !in raw && !has("SUGGESTED", "GUIDE")

        fun isTax(): Boolean {
            val taxWord = has("TAX", "TAXES", "VAT", "GST", "HST", "PST", "QST", "MWST", "UST", "TVA", "IVA", "GET", "STEUER") ||
                "SALESTAX" in joined ||
                "GETAX" in joined ||
                "消費税" in joined ||
                joined.endsWith("税")
            val notTax = has("TAXABLE", "EXEMPT", "REG", "NO", "NUMBER", "ID", "EXCL", "BEFORE", "PRETAX", "NONTAX") ||
                "PRETAX" in joined
            return taxWord && !notTax && !isTaxInclusiveTotal() && !isSubtotal()
        }

        /** "TOTAL INCL. VAT 12.00" is a total, but "GST INCLUDED IN TOTAL 2.64" is a tax. */
        fun isTaxInclusiveTotal(): Boolean {
            val incl = Regex("""\bINCL""").find(upper)?.range?.first ?: return false
            val taxAt = Regex("""\b(TAX|VAT|GST|HST|MWST|TVA|IVA)\b""").find(upper)?.range?.first ?: return false
            return "TOTAL" in joined && incl < taxAt
        }

        /** 1 for strong total words, 2 for plain TOTAL style words, null for anything else. */
        fun totalTier(): Int? {
            val skip = listOf("SAVING", "SAVED", "ITEMS", "DISCOUNT", "POINTS", "QTY", "COUPON", "TENDER", "CHANGE", "CASH", "BEFORE")
            if (skip.any { it in joined } || isSubtotal() || isTip() || '%' in raw) return null
            if (isTax()) return null
            val strong = listOf(
                "GRANDTOTAL", "TOTALDUE", "AMOUNTDUE", "BALANCEDUE", "ORDERTOTAL", "TOTALCHARGES", "AMOUNTPAID",
                "TOTALPAID", "TOTALAMOUNT", "TOTALCHARGED", "TOTALUSD", "TOTALEUR", "TOTALCAD", "TOTALGBP", "TOTALAUD"
            )
            val plain = listOf("TOTAL", "SUMME", "GESAMT", "BRUTTO", "TOTALE", "IMPORTE", "BETRAG", "MONTANT", "合計", "TOPAY", "BALANCE")
            return when {
                strong.any { it in joined } -> 1
                plain.any { it in joined } || has("AMOUNT", "CHARGED") -> 2
                else -> null
            }
        }
    }

    /** A run of digits, O/o and separators not touching letters, like "1O.5O" or "1O8". */
    private val numberish = Regex("""(?<![\p{L}])[\dOo][\dOo.,]*(?![\p{L}])""")

    private val dayFirstCurrencies = setOf("EUR", "GBP", "AUD", "NZD", "INR", "CHF")

    private val decimalAmount =
        Regex("""(?<![\d.,])-?\d{1,3}(?:([,.])\d{3})*[.,]\d{2}(?![\d%]|[.,]\d)|(?<![\d.,])-?\d+[.,]\d{2}(?![\d%]|[.,]\d)""")
    private val wholeAmount = Regex("""(?<![\d.,:/])\d{1,3}(?:,\d{3})+(?![\d.,])|(?<![\d.,:/])\d+(?![\d.,:/%])""")

    private fun lastAmount(text: String, wholeUnits: Boolean): Long? {
        val m = decimalAmount.findAll(text).lastOrNull() ?: if (wholeUnits) wholeAmount.findAll(text).lastOrNull() else null
        return m?.let { Money.parse(it.value) }
    }

    private fun total(rows: List<Row>, subtotal: Long?, taxRows: List<Row>, tip: Long?): Pair<Row?, Confidence> {
        val candidates = rows.filter { (it.amount ?: 0) > 0 && it.totalTier() != null }
        if (candidates.isNotEmpty()) {
            // A total that matches subtotal + tax (+ tip) wins; otherwise the largest keyword total.
            val taxes = (taxRows.mapNotNull { it.amount } + taxRows.sumOf { it.amount ?: 0 } + 0L).distinct()
            val tips = listOfNotNull(0L, tip)
            val checked = candidates.filter { c ->
                subtotal != null && taxes.any { t -> tips.any { abs(subtotal + t + it - c.amount!!) <= 1 } }
            }
            checked.maxByOrNull { it.amount!! }?.let { return it to Confidence.HIGH }
            val best = candidates.minOf { it.totalTier()!! }
            val pick = candidates.filter { it.totalTier() == best }.maxBy { it.amount!! }
            return pick to if (subtotal == null) Confidence.HIGH else Confidence.MEDIUM
        }
        val payWords = Regex("""\b(VISA|MASTERCARD|MC|AMEX|DISCOVER|CARD|CREDIT|DEBIT|PAID|CHARGE|TEND|APPROVED|PAYMENT|SALE)\b""")
        val near = rows.indices.filter { payWords.containsMatchIn(rows[it].upper) }
            .flatMap { listOf(it - 1, it, it + 1) }.filter { it in rows.indices }.map { rows[it] }
        near.filter { (it.amount ?: 0) > 0 }.maxByOrNull { it.amount!! }?.let { return it to Confidence.MEDIUM }
        return rows.filter { (it.amount ?: 0) > 0 }.maxByOrNull { it.amount!! } to Confidence.LOW
    }

    private fun tax(taxRows: List<Row>, subtotal: Long?, tip: Long?, total: Long?): Triple<Row, Long, Confidence>? {
        if (taxRows.isEmpty()) return null
        if (subtotal != null && total != null) {
            val rest = total - subtotal - (tip ?: 0)
            taxRows.lastOrNull { abs(it.amount!! - rest) <= 1 }?.let { return Triple(it, it.amount!!, Confidence.HIGH) }
            val sum = taxRows.sumOf { it.amount!! }
            if (taxRows.size > 1 && abs(sum - rest) <= 1) return Triple(taxRows.last(), sum, Confidence.HIGH)
        }
        // "TOTAL TAX" beats the individual lines; otherwise they add up (hotel nights, GST + PST).
        taxRows.lastOrNull { "TOTAL" in it.joined }?.let { return Triple(it, it.amount!!, Confidence.MEDIUM) }
        return Triple(taxRows.last(), taxRows.sumOf { it.amount!! }, Confidence.MEDIUM)
    }

    /** European VAT breakdown: a header like "TVA HT TVA TTC" over rows of numbers. Reads the tax column. */
    private fun vatTable(rows: List<Row>): Pair<Row, Long>? {
        val taxCols = setOf("TAX", "VAT", "MWST", "STEUER", "TVA", "IVA", "GST", "UST")
        val h = rows.indexOfFirst {
            it.amount == null && it.words.any(taxCols::contains) && it.has("NETTO", "BRUTTO", "HT", "TTC", "NET", "GROSS", "EXCL", "INCL")
        }
        if (h < 0) return null
        val labels = rows[h].upper.split(Regex("""\s+""")).filter { w -> w.any { it.isLetter() } }
        val fromRight = labels.size - 1 - labels.indexOfLast { it.filter(Char::isLetter) in taxCols }
        var sum = 0L
        for (r in rows.drop(h + 1)) {
            val amounts = decimalAmount.findAll(r.clean).mapNotNull { Money.parse(it.value) }.toList()
            if (amounts.size < 2 || amounts.size <= fromRight) break
            val value = amounts[amounts.size - 1 - fromRight]
            if (r.totalTier() != null) return r to value
            sum += value
        }
        return if (sum > 0) rows[h] to sum else null
    }

    /** Some POS layouts print labels in one column and amounts in the next, so OCR gives labels then values. */
    private fun pairColumns(rows: List<Row>) {
        var i = 0
        while (i < rows.size) {
            val labels = (i until rows.size).takeWhile { rows[it].amount == null && rows[it].raw.count(Char::isLetter) >= 2 }.size
            val start = i + labels
            val values = (start until rows.size).takeWhile { rows[it].amount != null && rows[it].raw.count(Char::isLetter) <= 2 }.size
            val n = minOf(labels, values)
            if (n >= 2) {
                for (k in 0 until n) {
                    rows[start - n + k].amount = rows[start + k].amount
                    rows[start + k].amount = null
                }
            }
            i = start + maxOf(values, if (labels == 0) 1 else 0)
        }
    }

    private fun currency(text: String): Field<String> {
        val code = Regex("""\b(USD|EUR|GBP|JPY|CAD|AUD|NZD|CHF|INR|MXN|SEK|NOK|DKK|CNY|HKD|SGD|KRW)\b""").find(text.uppercase())?.value
        val upper = text.uppercase()
        val c = when {
            Regex("""\bC\$|CA\$|CDN\$""").containsMatchIn(upper) -> "CAD"
            Regex("""\bA\$|AU\$""").containsMatchIn(upper) -> "AUD"
            Regex("""\bNZ\$""").containsMatchIn(upper) -> "NZD"
            '€' in text -> "EUR"
            '£' in text -> "GBP"
            '¥' in text || '円' in text || '￥' in text -> "JPY"
            '₹' in text -> "INR"
            code != null && code != "USD" -> code
            '$' in text || code == "USD" -> when {
                Regex("""\bABN\b""").containsMatchIn(upper) -> "AUD"
                Regex("""\b(HST|GST|PST|QST)\b""").containsMatchIn(upper) -> "CAD"
                else -> "USD"
            }
            else -> null
        }
        return if (c != null) Field(c, Confidence.HIGH) else Field("USD", Confidence.MEDIUM)
    }

    private val months = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6, "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10,
        "NOV" to 11, "DEC" to 12, "OKT" to 10, "DEZ" to 12, "MAI" to 5, "MÄR" to 3, "MRZ" to 3, "FÉV" to 2, "AVR" to 4, "DÉC" to 12
    )
    private const val MON = """(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC|OKT|DEZ|MAI|MÄR|MRZ|FÉV|AVR|DÉC)\p{L}*\.?"""
    private val isoDate = Regex("""(?<!\d)(20\d\d)[-/.年](\d{1,2})[-/.月](\d{1,2})(?!\d)""")
    private val numDate = Regex("""(?<![\d.])(\d{1,2})([/.\-])(\d{1,2})\2(\d{4}|\d{2})(?![\d])""")
    private val dayMonDate = Regex("""(?<!\d)(\d{1,2})(?:ST|ND|RD|TH)?[ \-.]*$MON[ \-,.]*(\d{4}|\d{2})(?!\d)""")
    private val monDayDate = Regex("""(?<![\p{L}])$MON[ \-]*(\d{1,2})(?:ST|ND|RD|TH)?,?[ \-]+(\d{4}|\d{2})(?!\d)""")

    // OCR often splits the year on receipt fonts: "10/05/2 026". Not a time: "10/05/20 07:42" stays as is.
    private val splitYear = Regex("""(\d{1,2}[/.\-]\d{1,2}[/.\-])(\d{1,3}) (\d{1,3})(?![\d:])""")

    private fun datesIn(text: String, dayFirst: Boolean): List<LocalDate> {
        val t = text.uppercase().replace(Regex("""(?<=\d)O|O(?=\d)"""), "0").replace(splitYear) { m ->
            val (head, a, b) = m.destructured
            if ((a + b).length == 4 && (a + b).startsWith("20")) head + a + b else m.value
        }
        val out = mutableListOf<Pair<Int, LocalDate>>()
        fun add(m: MatchResult, y: String, month: Int, d: Int) {
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            runCatching { LocalDate.of(year, month, d) }.getOrNull()?.let { out += m.range.first to it }
        }
        isoDate.findAll(t).forEach { add(it, it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        numDate.findAll(t).forEach {
            val (a, sep, b, y) = it.destructured
            val first = a.toInt()
            val second = b.toInt()
            val swap = first > 12 || (second <= 12 && (dayFirst || sep == "."))
            if (swap) add(it, y, second, first) else add(it, y, first, second)
        }
        dayMonDate.findAll(t).forEach { add(it, it.groupValues[3], months.getValue(it.groupValues[2]), it.groupValues[1].toInt()) }
        monDayDate.findAll(t).forEach { add(it, it.groupValues[3], months.getValue(it.groupValues[1]), it.groupValues[2].toInt()) }
        return out.sortedBy { it.first }.map { it.second }
    }

    private fun date(rows: List<Row>, today: LocalDate, dayFirst: Boolean): Triple<Row, LocalDate, Confidence>? {
        val all = rows.flatMap { r -> datesIn(r.raw, dayFirst).map { r to it } }
            .filter { (r, d) -> d.year >= 2000 && !d.isAfter(today.plusYears(1)) && !r.has("EXP", "EXPIRES", "EXPIRY", "VALID", "RETURN") }
        if (all.isEmpty()) return null
        val past = all.filter { !it.second.isAfter(today) }.ifEmpty { all }
        val checkout = Regex("""DEPART|CHECK.?OUT""")
        val purchase = Regex("""ORDER|PURCHASE|ISSUE|TRANSACTION|INVOICE|PAID|BILL|SALE|RECEIPT|FOLIO""")
        // Hotels: the check out date, the last one on that line.
        val pick = past.lastOrNull { checkout.containsMatchIn(it.first.upper) }
            ?: past.firstOrNull { purchase.containsMatchIn(it.first.upper) && "DATE" in it.first.joined }
            ?: past.firstOrNull {
                "DATE" in it.first.joined &&
                    !Regex("""ARRIV|CHECK.?IN|FLIGHT|TRAVEL|DUE""").containsMatchIn(it.first.upper)
            }
            ?: past.first()
        val confidence = if (pick.second.isAfter(today)) Confidence.LOW else Confidence.HIGH
        return Triple(pick.first, pick.second, confidence)
    }

    private val timeRegex =
        Regex("""(?<![\d:])(\d{1,2})[:.](\d{2})(?::\d{2})?\s*([AP])\.?M\b|(?<![\d:])(\d{1,2}):(\d{2})(?::\d{2})?(?![\d:])""")

    private fun time(rows: List<Row>, dateRow: Row?): LocalTime? {
        for (r in listOfNotNull(dateRow) + rows) {
            val m = timeRegex.find(r.upper) ?: continue
            val g = m.groupValues
            var h = (g[1].ifEmpty { g[4] }).toInt()
            val min = (g[2].ifEmpty { g[5] }).toInt()
            if (g[3] == "P" && h < 12) h += 12
            if (g[3] == "A" && h == 12) h = 0
            if (h in 0..23 && min in 0..59) return LocalTime.of(h, min)
        }
        return null
    }

    private val phone = Regex("""(?<!\d)(?:\+?1[ .-]?)?\(?\d{3}\)?[ .-]?\d{3}[ .-]\d{4}(?!\d)""")
    private val street = Regex(
        """^\d+[\s-]+.*\b(ST|STREET|AVE|AVENUE|BLVD|RD|ROAD|HWY|HIGHWAY|DR|DRIVE|LN|LANE|WAY|PL|PLACE|PKWY|CT|CIR|SUITE|STE|SQ|SQUARE|""" +
            """PLAZA|STR|STRASSE|STRAßE)\b"""
    )
    private val cityLine = Regex("""\b[A-Z]{2}\s+\d{5}(?:-\d{4})?\b|\b\d{5}\s+\p{L}+|\b[A-Z]\d[A-Z]\s?\d[A-Z]\d\b""")

    private fun address(rows: List<Row>): String? {
        val top = rows.take(10)
        val i = top.indexOfFirst { street.containsMatchIn(it.upper) }
        if (i < 0) return top.firstOrNull { cityLine.containsMatchIn(it.upper) }?.raw?.trim()
        val next = top.getOrNull(i + 1)?.takeIf { cityLine.containsMatchIn(it.upper) }
        return listOfNotNull(top[i].raw.trim(), next?.raw?.trim()).joinToString(", ")
    }

    private val intro = Regex(
        """(?:WELCOME TO|RECEIPT FROM|YOUR (?:ORDER|PURCHASE|RIDE|TRIP) (?:FROM|WITH)|THANKS? (?:YOU )?FOR (?:SHOPPING|DINING|VISITING|""" +
            """CHOOSING|FLYING|STAYING|ORDERING)(?: AT| WITH| FROM)?)\s+(.+)""",
        RegexOption.IGNORE_CASE
    )
    private val notMerchant = Regex(
        """^(WELCOME|RECEIPT|SALES RECEIPT|CUSTOMER COPY|MERCHANT COPY|ORIGINAL|DUPLICATE|COPY|INVOICE|TAX INVOICE|ORDER|THANK|""" +
            """TEL|PHONE|FAX|CASHIER|REGISTER|STORE|DATE|TIME|TABLE|SERVER|GUEST|CHECK|HOST|OPEN|HELLO|HI |DEAR|FROM:|TO:|SUBJECT|""" +
            """E-?RECEIPT|BILL|KASSENBON|RECHNUNG|QUITTUNG|FACTURE|TICKET|RECIBO)"""
    )

    /** First meaningful line near the top. Third value is true when an intro phrase named it. */
    private fun merchant(rows: List<Row>): Triple<Row, String, Boolean>? {
        val top = rows.take(12)
        top.firstNotNullOfOrNull { r -> intro.find(r.raw)?.groupValues?.get(1)?.trim()?.trimEnd('!', '.')?.let { r to it } }
            ?.takeIf { it.second.count(Char::isLetter) >= 2 }?.let { return Triple(it.first, it.second, true) }
        val r = top.firstOrNull { r ->
            val letters = r.raw.count { it.isLetter() }
            letters >= 2 &&
                letters >= r.raw.count { it.isDigit() } &&
                !notMerchant.containsMatchIn(r.upper.trim()) &&
                !street.containsMatchIn(r.upper) &&
                !cityLine.containsMatchIn(r.upper) &&
                !phone.containsMatchIn(r.raw) &&
                datesIn(r.raw, false).isEmpty() &&
                r.amount == null &&
                !r.raw.contains('@') &&
                !r.raw.trim().startsWith("http")
        } ?: return null
        // Names split over two lines: "COURTYARD" / "BY MARRIOTT", or a known chain like "UNITED STATES" / "POSTAL SERVICE".
        val next = top.getOrNull(top.indexOf(r) + 1)?.raw?.trim()
        if (next != null && next.none { it.isDigit() }) {
            val both = "${r.raw.trim()} $next"
            val connector = Regex("""^(BY|OF|AND|&|DE)\b""").containsMatchIn(next.uppercase())
            if (connector || MerchantNormalizer.isChain(both) && !MerchantNormalizer.isChain(r.raw)) return Triple(r, both, false)
        }
        return Triple(r, r.raw, false)
    }

    private val brands = listOf(
        "AMEX" to Regex("""\bAMEX\b|AMERICAN EXPRESS"""),
        "MASTERCARD" to Regex("""MASTER ?CARD|\bMC\b|\bM/C\b"""),
        "VISA" to Regex("""\bVISA\b"""),
        "DISCOVER" to Regex("""\bDISCOVER\b"""),
        "JCB" to Regex("""\bJCB\b""")
    )
    private val last4Patterns = listOf(
        Regex("""(?:[X*•#.]{2,}[\s-]*)+(\d{4})(?!\d)""", RegexOption.IGNORE_CASE),
        Regex("""ENDING(?: IN)?:?\s*(\d{4})(?!\d)""", RegexOption.IGNORE_CASE),
        Regex("""(?:CARD|ACCT|ACCOUNT)\s*(?:#|NO\.?|NUMBER)?:?\s*(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)
    )

    private fun card(text: String): Pair<String?, String?> {
        val upper = text.uppercase()
        val brand = brands.mapNotNull { (b, r) -> r.find(upper)?.let { b to it.range.first } }.minByOrNull { it.second }?.first
        val last4 = last4Patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
        return brand to last4
    }

    private val receiptNumber = Regex(
        """\b(?:RECEIPT|ORDER|INVOICE|INV|TRANSACTION|TRANS|TRAN|TXN|TICKET|CHECK|CHK|FOLIO|CONFIRMATION|CONF|REF|REFERENCE|BILL|""" +
            """RECHNUNG|BON|BELEG)\s*(?:#|NO\.?|NUM(?:BER)?|ID|NR\.?)?\s*[:#]?\s*(?=[A-Z0-9-]*\d)([A-Z0-9][A-Z0-9-]{2,})""",
        RegexOption.IGNORE_CASE
    )

    private fun lineItems(rows: List<Row>): List<ParsedLineItem> {
        val end = rows.indexOfFirst { it.isSubtotal() || it.totalTier() != null || it.isTax() }
        if (end <= 0) return emptyList()
        val qtyRegex = Regex("""^(\d{1,3})\s*[xX@]?\s+(?=\p{L})""")
        return rows.subList(0, end).mapNotNull { r ->
            val amount = r.amount ?: return@mapNotNull null
            if (amount <= 0 || phone.containsMatchIn(r.raw) || datesIn(r.raw, false).isNotEmpty()) return@mapNotNull null
            val namePart = r.clean.substringBeforeLast(decimalAmount.findAll(r.clean).lastOrNull()?.value ?: return@mapNotNull null)
            val qty = qtyRegex.find(namePart.trim())?.groupValues?.get(1)?.toDouble() ?: 1.0
            val name = namePart.trim().replace(qtyRegex, "").replace(Regex("""[$€£¥]\s*$"""), "").trim(' ', '.', '-', ':', '|', '*')
            if (name.count { it.isLetter() } < 2) null else ParsedLineItem(name, qty, amount)
        }
    }
}
