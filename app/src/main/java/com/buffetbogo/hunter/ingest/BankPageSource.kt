package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.PriceSources
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.data.VenueSocial
import com.buffetbogo.hunter.data.nameKey
import com.buffetbogo.hunter.data.stableOfferId
import com.buffetbogo.hunter.net.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.util.Locale

/** One bank's dining-offer page. */
data class BankPage(val bank: String, val shortName: String, val url: String)

/**
 * The Dhaka banks that publish their restaurant buffet lists as readable HTML.
 *
 * Only pages whose venue names and offer wording are server-rendered are listed: several banks
 * print their dining offers only inside images or PDFs, and those cannot be read without OCR, so
 * they are deliberately left out rather than half-parsed.
 */
object BankDirectory {

    val pages: List<BankPage> = listOf(
        BankPage(
            bank = "City Bank",
            shortName = "City Bank",
            url = "https://www.citybankplc.com/offers-and-rewards/discount/dining-offers",
        ),
        BankPage(
            bank = "Mutual Trust Bank",
            shortName = "MTB",
            url = "https://www.mutualtrustbank.com/cards/complimentary-companion-dining/",
        ),
        BankPage(
            bank = "Prime Bank",
            shortName = "Prime Bank",
            url = "https://www.primebank.com.bd/year-round-discount",
        ),
        BankPage(
            bank = "Trust Bank",
            shortName = "Trust Bank",
            url = "https://www.tblbd.com/partners/",
        ),
        BankPage(
            bank = "Shimanto Bank",
            shortName = "Shimanto",
            url = "https://www.shimantobank.com/card-offer/buffet",
        ),
        BankPage(
            bank = "NCC Bank",
            shortName = "NCC Bank",
            url = "https://www.nccbank.com.bd/NccbCards/cardOffers",
        ),
        BankPage(
            bank = "BRAC Bank",
            shortName = "BRAC Bank",
            url = "https://www.bracbank.com/en/page/buy-1-get-12-free-buffet-at-the-finest-restaurants-around-the-year",
        ),
        BankPage(
            bank = "United Commercial Bank",
            shortName = "UCB",
            url = "https://www.ucb.com.bd/cards/important-information?page=1",
        ),
        BankPage(
            bank = "Bank Asia",
            shortName = "Bank Asia",
            url = "https://www.bankasia-bd.com/Ramadan-26/",
        ),
        BankPage(
            bank = "Dhaka Bank",
            shortName = "Dhaka Bank",
            url = "https://dhakabank.com.bd/dining-offer/",
        ),
        BankPage(
            bank = "Premier Bank",
            shortName = "Premier Bank",
            url = "https://thepremierbankplc.com/discount-partners/",
        ),
        BankPage(
            bank = "Al-Arafah Islami Bank",
            shortName = "Al-Arafah",
            url = "https://www.aibl.com.bd/cardoffers/",
        ),
        BankPage(
            bank = "NRB Bank",
            shortName = "NRB Bank",
            url = "https://www.nrbbankbd.com/anniversary-bogo-offer/",
        ),
        BankPage(
            bank = "ONE Bank",
            shortName = "ONE Bank",
            url = "https://card.onebank.com.bd/bogo/",
        ),
        BankPage(
            bank = "Meghna Bank",
            shortName = "Meghna Bank",
            url = "https://www.meghnabank.com.bd/meghna-cards/BOGO",
        ),
    )
}

data class BankFetch(
    val offers: List<Offer>,
    val pagesRead: Int,
    val pagesFailed: Int,
    val venuesFound: Int,
)

/**
 * Reads every bank page in [BankDirectory] and turns the buffet deals it lists into offers.
 *
 * The pages are fetched concurrently (they are independent and each is large), then parsed by
 * [BankPageParser]. A page that changes shape simply contributes nothing; it never aborts the
 * sync or produces half a venue name.
 */
class BankPageSource(
    private val now: Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val directory: Map<String, VenueSocial>,
    private val pages: List<BankPage> = BankDirectory.pages,
) {

    suspend fun fetchAll(): BankFetch = coroutineScope {
        val results = pages
            .map { page -> async { page to runCatching { fetch(page) } } }
            .awaitAll()

        var read = 0
        var failed = 0
        val offers = ArrayList<Offer>()
        for ((page, result) in results) {
            result
                .onSuccess { html ->
                    read++
                    runCatching {
                        BankPageParser.parse(html, page, now, zone, directory)
                    }.getOrDefault(emptyList()).forEach { offers += it }
                }
                .onFailure { failed++ }
        }
        BankFetch(
            offers = offers,
            pagesRead = read,
            pagesFailed = failed,
            venuesFound = offers.map { nameKey(it.restaurant) }.distinct().size,
        )
    }

    private suspend fun fetch(page: BankPage): String {
        // Be a well-behaved client: one page at a time per host, with a desktop-ish UA.
        val html = Http.get(
            page.url,
            mapOf(
                "Accept" to "text/html,application/xhtml+xml",
                "Accept-Language" to "en-US,en;q=0.9",
                "User-Agent" to BROWSER_UA,
            ),
        )
        delay(150)
        return html
    }

    private companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/121.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * Extracts buffet deals from a bank dining page.
 *
 * The pages have nothing in common structurally — one is a grid of cards, one a wide table, one
 * a list of partner names with the deal underneath — so the parser works from the visible text:
 * find a deal phrase ("Buy 1 Get 1", "B1G3", "BOGO", "25% off buffet"), then walk back for the
 * venue it belongs to and forward for the card condition. Venue names are matched against the
 * app's directory first, so a bank's spelling becomes the hotel's own name and the same hotel
 * never appears twice.
 */
object BankPageParser {

    private val outsideDhaka = Regex(
        "(?i)\\b(chattogram|chittagong|sylhet|co?x'?s?\\s*bazar|khulna|rajshahi|barishal|barisal|" +
            "rangpur|mymensingh|narayanganj|gazipur|savar)\\b",
    )
    private val buffetExcluded = Regex("(?i)(except|excluding|exclude)[^.]{0,24}buffet")
    private val phoneLike = Regex("^[+()\\d\\s-]{6,}$")
    private val nameLabel = Regex("(?i)^NAME\\s*:\\s*(.+)$")
    private val eligibility = Regex("(?i)ELIGIBILITY\\s*:\\s*([^\\n]{4,90})")
    private val eligibilityTail = listOf("REGION", "ADDRESS", "VALID", "OFFER", "READ MORE", "Read More")
    private val addressLike = Regex("(?i)(road|avenue|ave\\b|plot|house|sector|gulshan|banani|uttara|dhaka)")
    private val factLine = Regex(
        "(?i)(price|bdt|tk\\.?|৳|valid|duration|timing|breakfast|lunch|brunch|dinner|buffet|offer details)",
    )
    private val cardWords = Regex("(?i)\\b(cards?|eligib\\w*|applicable|debit|credit|holders?|only)\\b")
    private val labelPrefix = Regex("(?i)^(card|cards|eligible cards?|eligibility|card eligibility)\\s*:\\s*")

    private val junkLines = setOf(
        "view", "view details", "view detail", "close", "read more", "read less", "details",
        "book now", "reserve", "share", "learn more", "click here", "see more", "show more",
        "×", "x", "•", "-",
    )

    private val blockedWords = listOf(
        "offer", "card", "eligib", "valid", "address", "reservation", "price", "buffet",
        "discount", "savings", "partner", "region", "contact", "menu", "home", "login",
        "terms", "privacy", "career", "about", "branch", "atm", "search", "filter", "sort",
        "category", "select", "click", "phone", "email", "website", "facebook", "follow",
        "subscribe", "download", "apply", "notice", "tender", "investor", "statement",
    )

    private val venueHints = listOf(
        "hotel", "restaurant", "resort", "suites", "suite", "inn", "plaza", "palace", "lounge",
        "cafe", "café", "grill", "kitchen", "dining", "regency", "residence", "brasserie",
        "bistro", "steakhouse", "steak house", "eatery", "banquet", "marriott", "hyatt",
        "sheraton", "westin", "meridien", "méridien", "intercontinental", "pan pacific", "amari",
        "radisson", "holiday inn", "crowne plaza", "sarina", "seasons", "ascott", "best western",
        "golden tulip", "lakeshore", "bengal", "platinum", "omni", "grandmark", "concord",
        "doreen", "shinepukur", "maple leaf", "canary", "blueberry",
    )

    private const val LOOKBACK = 8
    private const val LOOKAHEAD = 4

    fun parse(
        html: String,
        page: BankPage,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        directory: Map<String, VenueSocial> = emptyMap(),
    ): List<Offer> {
        val lines = BankHtml.lines(html)
        if (lines.isEmpty()) return emptyList()

        val byDeal = LinkedHashMap<String, Offer>()
        for ((index, line) in lines.withIndex()) {
            val kind = OfferParser.detectKind(line) ?: continue
            val from = maxOf(0, index - LOOKBACK)
            val to = minOf(lines.size, index + LOOKAHEAD + 1)
            val window = lines.subList(from, to)
            val windowText = window.joinToString(" ")

            if (!isBuffetDeal(kind.first, line)) continue
            // Only a line that excludes buffet from its own discount disqualifies the deal;
            // a neighbouring venue's "all food except buffet" must not silence this one.
            if (buffetExcluded.containsMatchIn(line)) continue
            if (outsideDhaka.containsMatchIn(line)) continue

            val venue = findVenue(lines, index, directory) ?: continue
            if (outsideDhaka.containsMatchIn(venue)) continue
            if (venue.lowercase(Locale.ROOT).contains("bank")) continue

            val dates = OfferParser.extractDates(windowText, now, zone)
            val price = OfferParser.extractPrice(windowText)
            val offer = Offer(
                id = stableOfferId("${page.url}#${nameKey(venue)}", kind.first),
                title = "${kind.second} at $venue",
                restaurant = venue,
                area = areaFor(venue, window),
                offerKind = kind.first,
                headline = kind.second,
                details = describe(lines, index, venue, kind.second),
                terms = "Listed on ${page.bank}'s card-offer page. Confirm the conditions with the restaurant.",
                paymentNote = cardNote(lines, index, windowText, page),
                priceBdt = price,
                priceUnit = if (price != null) "per person" else null,
                priceSource = if (price != null) PriceSources.AD else null,
                postedAt = now,
                validFrom = dates.first,
                validUntil = dates.second,
                source = Sources.BANK,
                sourceUrl = page.url,
                imageUrl = null,
                rating = null,
                ratingCount = null,
                fetchedAt = now,
            )

            // The same venue is often printed twice on a page (a summary card and a detail
            // panel). Keep one deal and let the richer copy fill in the missing fields.
            val key = nameKey(venue) + "|" + kind.first
            byDeal[key] = byDeal[key]?.let { merge(it, offer) } ?: offer
        }

        return byDeal.values.filter { it.validUntil == null || it.validUntil >= now }
    }

    /**
     * The venue's neighbourhood: its own row's address when the page prints one, otherwise the
     * fixed area the app already knows for that hotel. Never taken from a neighbouring row.
     */
    private fun areaFor(venue: String, window: List<String>): String {
        val address = window.firstOrNull { line ->
            line.any { it.isDigit() } && addressLike.containsMatchIn(line) &&
                line.length in 8..120
        }
        return OfferParser.guessArea(listOfNotNull(venue, address).joinToString(" "))
    }

    /**
     * BOGO/Eat-N-Pay deals always count; a percentage only when that very line is about a
     * buffet, so "15% off all food" next to a buffet entry is not mistaken for a buffet deal.
     */
    private fun isBuffetDeal(kind: String, line: String): Boolean = when {
        kind.startsWith("BOGO_") || kind.startsWith("EAT_") -> true
        kind.startsWith("PCT_") -> line.contains("buffet", ignoreCase = true)
        else -> false
    }

    /**
     * The venue a deal belongs to: an explicit `NAME:` label when the page provides one (City
     * Bank), otherwise the nearest venue-looking line above the deal, preferring a hotel the app
     * already knows so spellings collapse to one restaurant.
     */
    private fun findVenue(
        lines: List<String>,
        marker: Int,
        directory: Map<String, VenueSocial>,
    ): String? {
        for (index in marker downTo maxOf(0, marker - 3)) {
            val label = nameLabel.find(lines[index])?.groupValues?.get(1)
                ?.let { clean(it) }
                ?.takeIf { it.length in 3..70 }
            if (label != null) return canonical(label, directory) ?: tidy(label)
        }

        var loose: String? = null
        for (index in marker - 1 downTo maxOf(0, marker - LOOKBACK)) {
            val candidate = clean(lines[index])
            if (candidate.length < 4) continue
            if (OfferParser.detectKind(candidate) != null) continue
            if (candidate.endsWith(":")) continue
            canonical(candidate, directory)?.let { return it }
            if (loose == null && looksLikeVenue(candidate)) loose = tidy(candidate)
        }
        return loose
    }

    private fun canonical(line: String, directory: Map<String, VenueSocial>): String? {
        val key = nameKey(line)
        if (key.length < 4) return null
        directory[key]?.let { return it.displayName }
        return directory.entries
            .asSequence()
            .filter { (alias, _) -> alias.length >= 6 }
            .firstOrNull { (alias, _) ->
                key == alias || key.startsWith("$alias ") || alias.startsWith("$key ")
            }
            ?.value
            ?.displayName
    }

    private fun looksLikeVenue(line: String): Boolean {
        if (line.length !in 4..70) return false
        if (phoneLike.matches(line)) return false
        if (line.any { it.isDigit() } && line.none { it.isLetter() }) return false
        if (line.contains("@") || line.contains("http") || line.contains("|")) return false
        val lower = line.lowercase(Locale.ROOT)
        if (blockedWords.any { lower.contains(it) }) return false
        if (line.count { it == ' ' } > 7) return false
        return venueHints.any { lower.contains(it) }
    }

    /** ALL-CAPS bank markup reads badly in a card; settle it into normal title case. */
    private fun tidy(raw: String): String {
        val trimmed = raw.trim().trim('-', '•', '·', ';').trim()
        if (trimmed.length > 5 && trimmed == trimmed.uppercase(Locale.ROOT)) {
            return trimmed.lowercase(Locale.ROOT)
                .split(' ')
                .joinToString(" ") { word ->
                    word.replaceFirstChar { it.titlecase(Locale.ROOT) }
                }
        }
        return trimmed
    }

    private fun clean(raw: String): String =
        raw.replace(Regex("\\s+"), " ").trim()

    private fun cardNote(
        lines: List<String>,
        marker: Int,
        windowText: String,
        page: BankPage,
    ): String {
        eligibility.find(windowText)?.groupValues?.get(1)
            ?.let { trimEligibility(it) }
            ?.takeIf { it.length in 4..70 }
            ?.let { return labelPrefix.replace(it, "").trim() }

        for (index in marker..minOf(lines.size - 1, marker + LOOKAHEAD)) {
            val line = clean(lines[index])
            if (line.length < 4 || !cardWords.containsMatchIn(line)) continue
            val stripped = labelPrefix.replace(line, "").trim()
            if (stripped.length in 4..70) return stripped.trimEnd('.', ',', ';')
            val parsed = OfferParser.paymentNote(line)
            if (!parsed.startsWith("No card")) return parsed
            return stripped.take(70).trimEnd('.', ',', ';')
        }
        return "${page.shortName} cards"
    }

    /** "Signature and Signature Plus Credit CardsREGION: Dhaka" → "Signature and Signature Plus Credit Cards". */
    private fun trimEligibility(raw: String): String {
        var text = clean(raw)
        for (tail in eligibilityTail) {
            val index = text.indexOf(tail, ignoreCase = true)
            if (index >= 4) text = text.substring(0, index)
        }
        text = text.trim().trimEnd('…', '.', ',', ';', ':', ' ')
        if (text.length > 70) text = text.take(70).trimEnd('…', '.', ',', ';', ':', ' ')
        return text
    }

    /**
     * A short, honest excerpt of what the bank printed for this deal, taken from after the deal
     * phrase so a neighbouring restaurant's blurb is never attached to this one.
     */
    private fun describe(
        lines: List<String>,
        marker: Int,
        venue: String,
        headline: String,
    ): String {
        val facts = lines.subList(marker, minOf(lines.size, marker + LOOKAHEAD + 1))
            .map { clean(it) }
            .filter { it.length >= 4 }
            .filter { it.lowercase(Locale.ROOT) !in junkLines }
            .filterNot { phoneLike.matches(it) }
            .filterNot { nameKey(it) == nameKey(venue) }
            // Keep the deal's own facts, not the next partner's name or blurb.
            .filter { factLine.containsMatchIn(it) }
            .distinct()
            .take(3)
        return (listOf("$headline buffet at $venue") + facts)
            .distinct()
            .joinToString(" · ")
            .take(420)
    }

    private fun merge(first: Offer, second: Offer): Offer = first.copy(
        priceBdt = first.priceBdt ?: second.priceBdt,
        priceUnit = first.priceUnit ?: second.priceUnit,
        priceSource = first.priceSource ?: second.priceSource,
        validFrom = first.validFrom ?: second.validFrom,
        validUntil = first.validUntil ?: second.validUntil,
        paymentNote = if (first.paymentNote.endsWith("cards")) second.paymentNote else first.paymentNote,
        details = if (second.details.length > first.details.length) second.details else first.details,
    )
}
