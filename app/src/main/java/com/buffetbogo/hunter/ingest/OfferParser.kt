package com.buffetbogo.hunter.ingest

import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.util.Locale

/**
 * Turns an ad's title/snippet into structured offer data.
 *
 * This class is deliberately free of Android dependencies so the extraction rules can be
 * unit-tested on the JVM and reused by any future server-side crawler.
 */
object OfferParser {

    data class ParsedOffer(
        val kind: String,
        val headline: String,
        val validFrom: Long?,
        val validUntil: Long?,
        val priceBdt: Int?,
        val paymentNote: String,
    )

    private val wordNumbers = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
    )

    private val buyGet = Regex(
        "(?i)\\bbuy\\s*(\\d+|one|two|three|four|five|six)\\s*" +
            "(?:get|and\\s*get|&\\s*get|&)\\s*(\\d+|one|two|three|four|five|six)\\b",
    )
    private val plusOne = Regex("(?i)(?<![\\d.])(\\d)\\s*\\+\\s*(\\d)(?![\\d.])")
    // The bank marketing shorthand for the same thing: B1G1, B1G3, B2G1 …
    private val b1g = Regex("(?i)\\bb\\s*(\\d)\\s*g\\s*(\\d)\\b")
    // "Eat 4 Pay 1" and the equally common "Dine 4 Pay 1" / "Dine 3, Pay 1" wording.
    private val eatPay = Regex(
        "(?i)\\b(eat|dine)\\s*(\\d+|three|four|five|six|seven)\\s*(?:people\\s*)?[,;:\\-]?\\s*" +
            "pay\\s*(?:for\\s*)?(\\d+)\\b",
    )
    private val percentOff = Regex("(?i)(\\d{1,2})\\s*%\\s*off")
    // Some headlines reverse the wording: "Pay 1 Eat 3" means the same as "Eat 3 Pay 1".
    private val payEat = Regex("(?i)\\bpay\\s*(\\d+)\\s*(?:for\\s*)?(?:and\\s*)?eat\\s*(\\d+)\\b")

    private val buffetWords = listOf(
        "buffet", "all you can eat", "all-you-can-eat", "ayce", "iftar buffet", "eat all you can",
    )

    private val longDate = Regex(
        "(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+" +
            "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|" +
            "aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\.?" +
            "(?:\\s*,?\\s*(\\d{4}))?",
    )
    private val numericDate = Regex("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b")
    private val isoDate = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")

    // "BDT 8,950", "Tk. 2,519", "৳1,250" before the number; "8950 taka" after it.
    private val pricePatterns = listOf(
        Regex("(?i)(?:tk\\.?|bdt|৳|taka)\\s*([\\d,]{3,7})(?:\\.\\d{1,2})?"),
        Regex("(?i)([\\d,]{3,7})\\s*(?:tk\\.?|bdt|৳|taka)\\b"),
    )

    // "6:30 pm – 10:30 pm", "6.30pm to 10.30pm", "12:30-3:30 PM".
    private val timeWindow = Regex(
        "(?i)\\b(\\d{1,2}(?:[:.]\\d{2})?)\\s*(am|pm)?\\s*(?:-|–|—|to|till)\\s*" +
            "(\\d{1,2}(?:[:.]\\d{2})?)\\s*(am|pm)\\b",
    )
    private val mealBefore = Regex(
        "(?i)\\b(lunch|dinner|breakfast|brunch|iftar|high\\s*tea)\\b[^\\d]{0,24}$",
    )

    /**
     * Reads the serving window out of an ad, e.g. "Dinner 6:30 pm – 10:30 pm". Ads often print
     * the meridiem only on the closing time, so it is copied onto the opening one.
     */
    fun extractTiming(text: String): String? {
        val windows = LinkedHashSet<String>()
        for (match in timeWindow.findAll(text)) {
            val start = match.groupValues[1].replace('.', ':')
            val startMeridiem = match.groupValues[2].lowercase(Locale.ROOT)
            val end = match.groupValues[3].replace('.', ':')
            val endMeridiem = match.groupValues[4].lowercase(Locale.ROOT)
            val startTime = if (startMeridiem.isBlank()) "$start $endMeridiem" else "$start $startMeridiem"
            val label = mealBefore.find(text.substring(0, match.range.first))
                ?.groupValues?.get(1)
                ?.lowercase(Locale.ROOT)
                ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
            val window = (label?.let { "$it " } ?: "") + "$startTime – $end $endMeridiem"
            windows += window
            if (windows.size == 2) break
        }
        return windows.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private val monthNumbers = mapOf(
        "jan" to Month.JANUARY, "feb" to Month.FEBRUARY, "mar" to Month.MARCH,
        "apr" to Month.APRIL, "may" to Month.MAY, "jun" to Month.JUNE,
        "jul" to Month.JULY, "aug" to Month.AUGUST, "sep" to Month.SEPTEMBER,
        "oct" to Month.OCTOBER, "nov" to Month.NOVEMBER, "dec" to Month.DECEMBER,
    )

    private val paymentAliases = linkedMapOf(
        "visa signature" to "Visa Signature",
        "visa infinite" to "Visa Infinite",
        "mastercard" to "Mastercard",
        "american express" to "Amex",
        "amex" to "Amex",
        "city bank" to "City Bank",
        "citybank" to "City Bank",
        "brac bank" to "BRAC Bank",
        "brac" to "BRAC Bank",
        "dutch-bangla" to "Dutch-Bangla Bank",
        "dbbl" to "Dutch-Bangla Bank",
        "standard chartered" to "Standard Chartered",
        "prime bank" to "Prime Bank",
        "southeast bank" to "Southeast Bank",
        "hsbc" to "HSBC",
        "bkash" to "bKash",
        "nagad" to "Nagad",
        "rocket" to "Rocket",
        "upay" to "Upay",
        // Bank names before the generic card words, so the bank is reported first.
        "bank asia" to "Bank Asia",
        "midland bank" to "Midland Bank",
        "meghna bank" to "Meghna Bank",
        "shimanto" to "Shimanto Bank",
        "ific" to "IFIC Bank",
        "standard chartered" to "Standard Chartered",
        "commercial bank of ceylon" to "Commercial Bank of Ceylon",
        "credit card" to "Credit card",
        "debit card" to "Debit card",
        "visa" to "Visa",
    )

    fun isBuffetOffer(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return buffetWords.any { lower.contains(it) }
    }

    /** Extracts a structured offer, or null when the text describes no recognisable deal. */
    fun parse(text: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): ParsedOffer? {
        val plain = cleanText(text)
        if (plain.isBlank()) return null
        val kind = detectKind(plain) ?: return null
        val dates = extractDates(plain, now, zone)
        val payment = paymentNote(plain)
        return ParsedOffer(
            kind = kind.first,
            headline = kind.second,
            validFrom = dates.first,
            validUntil = dates.second,
            priceBdt = extractPrice(plain),
            paymentNote = payment,
        )
    }

    // "Minimum spend BDT 5,000" is a card condition, not the price of a buffet.
    private val priceNoise = Regex("(?i)\\bminimum\\s+(spend|purchase)|min\\.?\\s+spend|spend\\s+of|cashback")

    fun extractPrice(text: String): Int? {
        for (pattern in pricePatterns) {
            for (match in pattern.findAll(text)) {
                val value = match.groupValues[1]
                    .replace(",", "")
                    .toIntOrNull()
                    ?.takeIf { it in 100..1_000_000 }
                    ?: continue

                val from = maxOf(0, match.range.first - 40)
                val to = minOf(text.length, match.range.last + 41)
                val window = text.substring(from, to)
                if (!priceNoise.containsMatchIn(window)) return value
            }
        }
        return null
    }

    fun paymentNote(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val found = linkedSetOf<String>()
        for ((alias, label) in paymentAliases) {
            if (lower.contains(alias)) found += label
        }
        if (found.isEmpty()) {
            return if (lower.contains("selected card") || lower.contains("selected bank")) {
                "Selected bank cards"
            } else {
                "No card or payment condition mentioned"
            }
        }
        // "Visa Signature" already implies Visa; drop the generic label to keep the badge short.
        if ("Visa Signature" in found || "Visa Infinite" in found) found.remove("Visa")
        return found.take(3).joinToString(" · ")
    }

    /** Finds the offer as (kind, badge) e.g. ("BOGO_1_2", "Buy 1 Get 2"). */
    fun detectKind(text: String): Pair<String, String>? {
        eatPay.find(text)?.let { m ->
            val verb = m.groupValues[1].lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            val eat = toNumber(m.groupValues[2]) ?: return@let
            val pay = toNumber(m.groupValues[3]) ?: return@let
            if (eat > pay) return "EAT_${eat}_PAY_$pay" to "$verb $eat Pay $pay"
        }
        buyGet.find(text)?.let { m ->
            val buy = toNumber(m.groupValues[1]) ?: return@let
            val get = toNumber(m.groupValues[2]) ?: return@let
            if (get >= 1) return "BOGO_${buy}_$get" to "Buy $buy Get $get"
        }
        plusOne.find(text)?.let { m ->
            val buy = m.groupValues[1].toIntOrNull() ?: return@let
            val get = m.groupValues[2].toIntOrNull() ?: return@let
            if (buy in 1..4 && get in 1..5) return "BOGO_${buy}_$get" to "Buy $buy Get $get"
        }
        b1g.find(text)?.let { m ->
            val buy = m.groupValues[1].toIntOrNull() ?: return@let
            val get = m.groupValues[2].toIntOrNull() ?: return@let
            if (buy in 1..4 && get in 1..5) return "BOGO_${buy}_$get" to "Buy $buy Get $get"
        }
        payEat.find(text)?.let { m ->
            val pay = m.groupValues[1].toIntOrNull() ?: return@let
            val eat = m.groupValues[2].toIntOrNull() ?: return@let
            if (eat > pay && eat in 2..8) return "EAT_${eat}_PAY_$pay" to "Pay $pay Eat $eat"
        }
        percentOff.find(text)?.let { m ->
            val pct = m.groupValues[1].toIntOrNull() ?: return@let
            if (pct in 10..90) return "PCT_$pct" to "$pct% off buffet"
        }
        return null
    }

    /**
     * Returns validFrom to validUntil in epoch millis.
     * A single date is treated as the expiry. Missing years roll forward to the next occurrence.
     */
    fun extractDates(
        text: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long?, Long?> {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dates = sortedSetOf<LocalDate>()

        longDate.findAll(text).forEach { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = monthNumbers[m.groupValues[2].take(3).lowercase(Locale.ROOT)] ?: return@forEach
            val yearGroup = m.groupValues[3]
            var date = runCatching {
                if (yearGroup.isNotBlank()) {
                    LocalDate.of(yearGroup.toInt(), month, day)
                } else {
                    var candidate = LocalDate.of(today.year, month, day)
                    if (candidate < today.minusDays(45)) candidate = candidate.plusYears(1)
                    candidate
                }
            }.getOrNull() ?: return@forEach
            if (date.year in (today.year - 1)..(today.year + 2)) dates += date
        }

        isoDate.findAll(text).forEach { m ->
            runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()?.let { dates += it }
        }

        numericDate.findAll(text).forEach { m ->
            runCatching {
                LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
            }.getOrNull()?.let { dates += it }
        }

        if (dates.isEmpty()) return null to null
        val first = dates.first()
        val last = dates.last()
        val endOfDay = last.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        val startOfDay = first.atStartOfDay(zone).toInstant().toEpochMilli()
        return if (dates.size >= 2) startOfDay to endOfDay else null to endOfDay
    }

    /** Best-effort restaurant name, biased towards the well-known Dhaka venues. */
    fun guessRestaurant(text: String): String {
        val known = KnownVenues.match(text)
        if (known != null) return known

        val atPattern = Regex(
            "(?i)(?:buffet|brunch|dinner|lunch|iftar)\\s+(?:at|@)\\s+([\\p{L}0-9&'’.\\- ]{2,45})",
        )
        atPattern.find(text)?.groupValues?.get(1)?.let { raw ->
            val name = raw.split(" - ", " | ", ",", "(", "—", ".").first().trim()
            if (name.length in 3..45) return name
        }

        val title = text.lineSequence().firstOrNull().orEmpty()
        val firstChunk = title.split(" | ", " — ", " - ").first().trim()
        if (firstChunk.length in 4..60) {
            return firstChunk.take(60)
        }
        return "Dhaka restaurant"
    }

    fun guessArea(text: String): String {
        // A recognised venue beats a stray area mention elsewhere in the text.
        KnownVenueAreas.match(text)?.let { return it }
        val lower = text.lowercase(Locale.ROOT)
        return DhakaAreas.firstOrNull { lower.contains(it.lowercase(Locale.ROOT)) } ?: "Dhaka"
    }

    fun cleanText(raw: String): String =
        raw.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun toNumber(raw: String): Int? =
        raw.toIntOrNull() ?: wordNumbers[raw.lowercase(Locale.ROOT)]
}

/** Known Dhaka buffet venues, used to clean up messy search result titles. */
object KnownVenues {
    private val venues = linkedMapOf(
        "Le Méridien Dhaka" to listOf("le méridien", "le meridien", "lemeridien"),
        "Sheraton Dhaka" to listOf("sheraton"),
        "InterContinental Dhaka" to listOf("intercontinental", "inter continental"),
        "Renaissance Dhaka Gulshan" to listOf("renaissance"),
        "Radisson Blu Dhaka Water Garden" to listOf("radisson"),
        "The Westin Dhaka" to listOf("westin"),
        "Pan Pacific Sonargaon" to listOf("pan pacific", "sonargaon"),
        "Amari Dhaka" to listOf("amari"),
        "Six Seasons Hotel" to listOf("six seasons"),
        "The Lakeshore Hotel" to listOf("lakeshore"),
        "The Ascott Dhaka" to listOf("ascott"),
        "Hotel Sarina" to listOf("sarina"),
        "Four Points by Sheraton Dhaka" to listOf("four points"),
        "Holiday Inn Dhaka City Centre" to listOf("holiday inn"),
        "Dhaka Regency Hotel" to listOf("dhaka regency"),
        "Best Western Plus Maya" to listOf("best western"),
        "Golden Tulip The Grandmark" to listOf("golden tulip"),
        "Platinum Grand" to listOf("platinum grand"),
    )

    fun match(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return venues.entries.firstOrNull { (_, aliases) -> aliases.any { lower.contains(it) } }?.key
    }
}

/** Fixed neighbourhoods for the venues that appear most often, for pages that omit an address. */
object KnownVenueAreas {
    private val areas = linkedMapOf(
        "six seasons" to "Gulshan",
        "le méridien" to "Nikunja",
        "le meridien" to "Nikunja",
        "pan pacific" to "Kawran Bazar",
        "sonargaon" to "Kawran Bazar",
        "sheraton" to "Banani",
        "westin" to "Gulshan",
        "amari" to "Gulshan",
        "renaissance" to "Gulshan",
        "radisson" to "Airport Road",
        "intercontinental" to "Ramna",
        "sarina" to "Banani",
        "dhaka regency" to "Nikunja",
        "holiday inn" to "Tejgaon",
        "hyatt place" to "Uttara",
        "beyond buffet" to "Gulshan",
        "teriyaki" to "Gulshan",
        "crowne plaza dhaka gulshan" to "Gulshan",
        "crowne plaza gulshan" to "Gulshan",
        "crowne plaza dhaka airport" to "Airport Road",
    )

    fun match(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return areas.entries.firstOrNull { (alias, _) -> lower.contains(alias) }?.value
    }
}

val DhakaAreas = listOf(
    "Gulshan", "Banani", "Dhanmondi", "Uttara", "Motijheel", "Nikunja", "Baridhara",
    "Bashundhara", "Mirpur", "Mohammadpur", "Tejgaon", "Kawran Bazar", "Mohakhali",
    "Khilgaon", "Paltan", "Ramna", "Shahbagh", "Farmgate", "Badda", "Rampura",
    "Aftab Nagar", "Cantonment", "Panthapath", "Bailey Road", "Airport",
)
