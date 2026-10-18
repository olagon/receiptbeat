package org.kealohalabs.receiptbeat.extraction

object CategoryGuess {
    val KEYS = listOf(
        "meals", "travel", "lodging", "car", "office", "software", "phone", "equipment",
        "professional", "education", "home_office", "medical", "charity", "gifts", "other"
    )

    // Checked in order, so specific brands come before generic words.
    private val merchantWords = listOf(
        "lodging" to "hotel|motel|inn|resort|suites|hilton|marriott|hyatt|sheraton|westin|airbnb|vrbo|holiday inn|best western|" +
            "hampton|courtyard|outrigger|aston|four seasons|ritz",
        "travel" to "airlines?|airways|air lines|delta|united|alaska air|southwest|jetblue|american air|hawaiian air|" +
            "mokulele|southern airways|expedia|uber|lyft|amtrak|greyhound|taxi|cab|airport|enterprise|hertz|avis|budget|alamo|turo",
        "car" to "shell|chevron|exxon|mobil|texaco|arco|valero|sinclair|aloha petroleum|minit stop|costco gas|gas|fuel|" +
            "petroleum|parking|jiffy lube|midas|pep boys|autozone|napa|o'reilly|car wash|tire",
        "medical" to "cvs|walgreens|rite aid|longs drugs|pharmacy|drug|clinic|medical|dental|dentist|hospital|urgent care|" +
            "optometr\\w*|kaiser|queen's|labcorp|quest diagnostics",
        "software" to "adobe|google|apple|microsoft|github|dropbox|zoom|slack|notion|atlassian|jetbrains|openai|anthropic|aws|" +
            "digitalocean|squarespace|godaddy|namecheap|netflix|spotify",
        "phone" to "verizon|t-mobile|tmobile|at&t|att|sprint|spectrum|hawaiian telcom|comcast|xfinity|cricket|mint mobile|" +
            "google fi|starlink",
        "office" to "staples|office depot|officemax|fedex office|ups store|usps|post office|kinko",
        "equipment" to "best buy|b&h|micro center|newegg|home depot|lowe's|lowes|ace hardware|city mill|harbor freight",
        "education" to "university|college|school|udemy|coursera|bookstore|books|barnes|tuition|seminar|conference",
        "charity" to "foundation|charity|church|donation|red cross|goodwill|salvation army|food bank",
        "gifts" to "florist|flowers|gift|hallmark|edible arrangements",
        "professional" to "law office|attorney|cpa|accounting|consulting|notary|legal",
        "meals" to "coffee|cafe|café|espresso|starbucks|restaurant|grill|diner|bistro|kitchen|pizza|sushi|ramen|bakery|" +
            "bar|pub|tavern|taqueria|burger|mcdonald'?s?|subway|chipotle|zippy|l&l|poke|shave ice|malasada|brewing|eatery|bbq|" +
            "noodle|deli|doordash|grubhub|ubereats|uber eats"
    )

    private val textWords = listOf(
        "lodging" to "folio|check-in|check-out|checkout|room rate|nights?|arrival|departure",
        "travel" to "flight|boarding|fare|itinerary|passenger|trip|ride|driver|baggage",
        "car" to "gallons?|unleaded|diesel|regular|premium|pump|fuel|parking|vehicle|odometer",
        "medical" to "rx|prescription|pharmacist|copay|patient",
        "software" to "subscription|license|plan renewal|monthly plan|annual plan",
        "phone" to "wireless|data plan|mobile service|internet service",
        "charity" to "donation|tax-deductible|contribution",
        "meals" to "server|table|gratuity|tip|guests?|dine in|take out|to go|entree|appetizer|latte|cappuccino|burger",
        "office" to "paper|toner|ink|pens|stapler|envelopes|postage"
    )

    private val merchantRegex = merchantWords.map { (k, w) -> k to Regex("""\b(?:$w)\b""") }
    private val textRegex = textWords.map { (k, w) -> k to Regex("""\b(?:$w)\b""") }

    /** Returns one of [KEYS] or null, from the merchant name first and then words in the receipt text. */
    fun guess(merchant: String?, text: String): String? {
        val m = merchant?.lowercase().orEmpty()
        if (m.isNotBlank()) merchantRegex.firstOrNull { it.second.containsMatchIn(m) }?.let { return it.first }
        val t = text.lowercase()
        return textRegex.maxByOrNull { (_, r) -> r.findAll(t).count() }?.takeIf { it.second.containsMatchIn(t) }?.first
    }
}
