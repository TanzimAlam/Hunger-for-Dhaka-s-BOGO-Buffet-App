package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.data.stableOfferId
import com.buffetbogo.hunter.net.Http
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Finds dated buffet promotions in Dhaka news coverage through Google News' public RSS feed.
 *
 * Bangladeshi outlets (The Daily Star, TBS, Dhaka Tribune, The Bangladesh Monitor …) publish a
 * press release for nearly every hotel buffet campaign, complete with the offer wording and,
 * often, the exact campaign dates. RSS needs no key and no account, which is why it is the
 * app's default discovery source.
 *
 * The feed only carries the headline, so when an article states no end date the app keeps the
 * offer for [ASSUMED_WINDOW_DAYS] days and says so in the offer details.
 */
object NewsRssSource {

    private const val ENDPOINT = "https://news.google.com/rss/search"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/121.0.0.0 Mobile Safari/537.36"
    private const val MAX_AGE_DAYS = 60L
    private const val ASSUMED_WINDOW_DAYS = 21L

    val QUERIES = listOf(
        "buffet Dhaka buy 1 get 1",
        "buffet offer Dhaka hotel",
        "b1g1 buffet Dhaka",
        "dine 3 pay 1 buffet Dhaka",
        "buy 1 get 1 buffet Bangladesh hotel",
        "iftar buffet Dhaka offer",
    )

    private val item = Regex("(?is)<item>(.*?)</item>")
    private val dateFormat = DateTimeFormatter.ofPattern(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        Locale.ENGLISH,
    )
    private val publisherSuffix = Regex("\\s+[-–—]\\s+([^-–—]{2,40})$")

    suspend fun fetch(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Offer> {
        val found = LinkedHashMap<String, Offer>()
        QUERIES.forEachIndexed { index, query ->
            if (index > 0) delay(700)
            runCatching { Http.get(feedUrl(query), mapOf("User-Agent" to BROWSER_UA)) }
                .getOrNull()
                ?.let { parseFeed(it, now) }
                ?.forEach { offer -> found.putIfAbsent(offer.id, offer) }
        }
        return found.values.toList()
    }

    /** Pure parser for the RSS payload, so the feed handling is testable offline. */
    fun parseFeed(xml: String, now: Long): List<Offer> {
        if (xml.isBlank()) return emptyList()
        val offers = ArrayList<Offer>()

        for (match in item.findAll(xml)) {
            val block = match.groupValues[1]
            val rawTitle = tag("title", block) ?: continue
            val link = tag("link", block)?.trim().orEmpty()
            if (link.isBlank()) continue

            val headline = stripPublisher(OfferParser.cleanText(rawTitle))
            if (headline.length < 12) continue
            if (!OfferParser.isBuffetOffer(headline)) continue
            val kind = OfferParser.detectKind(headline) ?: continue

            val postedAt = tag("pubDate", block)
                ?.let { parseDate(OfferParser.cleanText(it)) }
                ?: continue
            val ageDays = TimeUnit.MILLISECONDS.toDays(now - postedAt)
            if (ageDays > MAX_AGE_DAYS || ageDays < -1) continue

            val publisher = tag("source", block)?.let { OfferParser.cleanText(it) }.orEmpty()
            val validUntil = postedAt + TimeUnit.DAYS.toMillis(ASSUMED_WINDOW_DAYS)
            if (validUntil < now) continue

            offers += Offer(
                id = stableOfferId(link, kind.first),
                title = headline.take(180),
                restaurant = restaurantFor(headline),
                area = OfferParser.guessArea(headline),
                offerKind = kind.first,
                headline = kind.second,
                details = buildString {
                    append(headline)
                    if (publisher.isNotBlank()) append(" — reported by ").append(publisher)
                    append(". The article states no end date, so this listing is kept visible for ")
                    append(ASSUMED_WINDOW_DAYS).append(" days.")
                },
                terms = "Open the article for the full offer wording, card conditions and dates.",
                paymentNote = OfferParser.paymentNote(headline),
                priceBdt = OfferParser.extractPrice(headline),
                postedAt = postedAt,
                validFrom = null,
                validUntil = validUntil,
                source = Sources.NEWS,
                sourceUrl = link,
                imageUrl = null,
                rating = null,
                ratingCount = null,
                fetchedAt = now,
            )
        }
        return offers
    }

    private fun restaurantFor(headline: String): String {
        val guessed = OfferParser.guessRestaurant(headline)
        val looksLikeHeadline = guessed.length > 45 ||
            guessed.contains(" to get ", ignoreCase = true) ||
            guessed.contains(" announce", ignoreCase = true)
        return if (looksLikeHeadline) "Dhaka restaurant" else guessed
    }

    private fun stripPublisher(title: String): String {
        val match = publisherSuffix.find(title) ?: return title.trim()
        val candidate = match.groupValues[1]
        if (candidate.any { it.isDigit() }) return title.trim()
        return title.removeRange(match.range).trim()
    }

    private fun tag(name: String, block: String): String? =
        Regex("(?is)<$name[^>]*>(.*?)</$name>")
            .find(block)
            ?.groupValues?.get(1)
            ?.removePrefix("<![CDATA[")
            ?.removeSuffix("]]>")

    private fun parseDate(raw: String): Long? =
        runCatching { ZonedDateTime.parse(raw.trim(), dateFormat).toInstant().toEpochMilli() }
            .getOrNull()

    private fun feedUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "$ENDPOINT?q=$encoded&hl=en-US&gl=US&ceid=US:en"
    }
}
