package org.kealohalabs.receiptbeat.extraction

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object MerchantNormalizer {
    private val prefix = Regex("""^(?:SQ|TST|PAYPAL|PP|SP|GGL|GOOGLE|APL|IC|DD|SQU)\s*\*\s*""", RegexOption.IGNORE_CASE)
    private val storeNumber = Regex("""(?:\bstore\s*(?:no\.?|#)?\s*\d+|#\s*\d+|\bno\.\s*\d+|\s\d{2,}$)""", RegexOption.IGNORE_CASE)
    private val suffix = Regex("""[,\s]+(?:LLC|L\.L\.C\.|INC|CORP|CORPORATION|LTD|GMBH|PLC)\.?(?=\s|$)""", RegexOption.IGNORE_CASE)
    private val web = Regex("""^www\.|\.(?:com|net|org)\b""", RegexOption.IGNORE_CASE)
    private val small = setOf("of", "and", "the", "at", "on", "de", "la")

    // Local merchant dictionary: chains whose printed names vary a lot. Key prefix to display name.
    private val chains = listOf(
        "wholefoods" to "Whole Foods Market", "cvs" to "CVS", "walgreens" to "Walgreens", "lowes" to "Lowe's",
        "unitedstatespostal" to "USPS", "usps" to "USPS", "thehomedepot" to "Home Depot", "homedepot" to "Home Depot",
        "walmart" to "Walmart", "costco" to "Costco", "safeway" to "Safeway", "starbucks" to "Starbucks", "mcdonald" to "McDonald's",
        "7eleven" to "7-Eleven", "circlek" to "Circle K", "longsdrugs" to "Longs Drugs", "amazon" to "Amazon",
        "traderjoe" to "Trader Joe's", "chevron" to "Chevron", "riteaid" to "Rite Aid", "bestbuy" to "Best Buy",
        "officedepot" to "Office Depot", "staples" to "Staples", "foodland" to "Foodland", "ktasuper" to "KTA Super Stores"
    )

    fun normalize(raw: String): String {
        var s = raw.trim().replace(prefix, "").replace(web, "").replace(Regex("""(?<=\p{L})0(?=\p{L})"""), "O")
        s = s.replace(suffix, "").replace(storeNumber, "")
        s = s.replace(Regex("""\s+"""), " ").trim(' ', '-', ',', '.', '*', '#', ':', '|')
        val k = s.lowercase().filter { it.isLetterOrDigit() }
        chains.firstOrNull { k.startsWith(it.first) }?.let { return it.second }
        return s.split(' ').mapIndexed { i, w -> word(w, i == 0) }.joinToString(" ")
    }

    internal fun isChain(raw: String): Boolean = normalize(raw) in chains.map { it.second }

    /** Lowercase letters and digits only, so "SQ *KONA COFFEE" and "Kona Coffee" share a key. */
    fun key(name: String): String = normalize(name).lowercase().filter { it.isLetterOrDigit() }

    private fun word(w: String, first: Boolean): String {
        val letters = w.filter { it.isLetter() }
        val mixed = letters.any { it.isUpperCase() } && letters.any { it.isLowerCase() }
        return when {
            mixed -> w
            // Acronyms like CVS, BBQ, H&M stay upper case.
            letters.length in 1..3 && (letters.none { it.lowercaseChar() in "aeiou" } || '&' in w) -> w.uppercase()
            !first && w.lowercase() in small -> w.lowercase()
            else -> w.lowercase().split('-').joinToString("-") { p -> p.replaceFirstChar { it.uppercase() } }
        }
    }
}

object CardMasker {
    private val candidate = Regex("""(?<!\d)\d(?:[ -]?\d){12,18}(?!\d)""")

    fun mask(text: String): String = candidate.replace(text) { m ->
        val digits = m.value.filter { it.isDigit() }
        if (luhn(digits)) "•••• " + digits.takeLast(4) else m.value
    }

    private fun luhn(digits: String): Boolean = digits.reversed().mapIndexed { i, c ->
        val d = c - '0'
        if (i % 2 == 1) (d * 2).let { if (it > 9) it - 9 else it } else d
    }.sum() % 10 == 0
}

object ImageHash {
    /** Each bit says whether a pixel is brighter than its right neighbour. */
    fun dHash(gray9x8: IntArray): Long {
        require(gray9x8.size == 72) { "need 9x8 = 72 values" }
        var hash = 0L
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val i = row * 9 + col
                hash = (hash shl 1) or if (gray9x8[i] > gray9x8[i + 1]) 1L else 0L
            }
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

object Duplicates {
    /**
     * Likely duplicate: same total and dates within one day (merchant keys equal or either null),
     * OR image hashes within distance 6 when the totals do not disagree. Printed receipts all look alike
     * to a small image hash, so two different totals always mean two different receipts.
     */
    fun isLikely(
        aTotal: Long?,
        aDate: LocalDate?,
        aMerchant: String?,
        aHash: Long?,
        bTotal: Long?,
        bDate: LocalDate?,
        bMerchant: String?,
        bHash: Long?
    ): Boolean {
        val totalsDiffer = aTotal != null && bTotal != null && aTotal != bTotal
        if (!totalsDiffer && aHash != null && bHash != null && ImageHash.distance(aHash, bHash) <= 6) return true
        if (aTotal == null || aTotal != bTotal || aDate == null || bDate == null) return false
        if (abs(ChronoUnit.DAYS.between(aDate, bDate)) > 1) return false
        return aMerchant == null || bMerchant == null || MerchantNormalizer.key(aMerchant) == MerchantNormalizer.key(bMerchant)
    }
}
