package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.data.stableOfferId
import com.buffetbogo.hunter.net.BraveSearchClient
import com.buffetbogo.hunter.net.SearchHit
import kotlinx.coroutines.delay

/** What one refresh pass managed to collect, and how many sources were unreachable. */
data class FetchOutcome(
    val offers: List<Offer>,
    val sourcesTried: Int,
    val sourcesFailed: Int,
) {
    val allSourcesFailed: Boolean get() = sourcesTried > 0 && sourcesFailed == sourcesTried
}

/**
 * Collects buffet offers from every configured source.
 *
 * The two keyless sources run on every refresh: Standard Chartered's buffet page (plain HTML,
 * several Dhaka five-star venues with validity dates) and Google News RSS (dated press releases
 * about new campaigns). A Brave API key is optional and only adds broader web coverage,
 * including public Facebook pages that search engines have indexed.
 */
class WebOfferSource(private val braveApiKey: String) {

    suspend fun fetch(now: Long = System.currentTimeMillis()): FetchOutcome {
        val found = LinkedHashMap<String, Offer>()
        var tried = 0
        var failed = 0

        suspend fun collect(block: suspend () -> List<Offer>) {
            tried++
            runCatching { block() }
                .onSuccess { list -> list.forEach { found.putIfAbsent(it.id, it) } }
                .onFailure { failed++ }
        }

        collect { StandardCharteredSource.fetch(now) }
        collect { NewsRssSource.fetch(now) }
        if (braveApiKey.isNotBlank()) {
            collect { searchWeb(now) }
        }

        return FetchOutcome(
            offers = found.values.toList(),
            sourcesTried = tried,
            sourcesFailed = failed,
        )
    }

    private suspend fun searchWeb(now: Long): List<Offer> {
        val client = BraveSearchClient(braveApiKey)
        val hits = LinkedHashMap<String, SearchHit>()
        QUERIES.forEachIndexed { index, query ->
            if (index > 0) delay(1_100) // stay inside the free tier's 1 request/second limit
            runCatching { client.search(query, count = 10) }
                .getOrDefault(emptyList())
                .forEach { hits.putIfAbsent(it.url, it) }
        }

        val seenIds = HashSet<String>()
        return hits.values.mapNotNull { hit ->
            val text = "${hit.title}. ${hit.snippet}"
            if (!OfferParser.isBuffetOffer(text)) return@mapNotNull null
            val parsed = OfferParser.parse(text, now) ?: return@mapNotNull null
            if (parsed.validUntil != null && parsed.validUntil < now) return@mapNotNull null

            val id = stableOfferId(hit.url, parsed.kind)
            if (!seenIds.add(id)) return@mapNotNull null

            val isFacebook = hit.url.contains("facebook.com") || hit.url.contains("fb.com")
            Offer(
                id = id,
                title = hit.title.take(160).ifBlank { parsed.headline },
                restaurant = OfferParser.guessRestaurant(text),
                area = OfferParser.guessArea(text),
                offerKind = parsed.kind,
                headline = parsed.headline,
                details = hit.snippet.take(700).ifBlank { hit.title },
                terms = "See the original ad for full terms and conditions.",
                paymentNote = parsed.paymentNote,
                priceBdt = parsed.priceBdt,
                postedAt = hit.publishedAt ?: now,
                validFrom = parsed.validFrom,
                validUntil = parsed.validUntil,
                source = if (isFacebook) Sources.FACEBOOK else Sources.GOOGLE,
                sourceUrl = hit.url,
                imageUrl = hit.thumbnail,
                rating = null,
                ratingCount = null,
                fetchedAt = now,
            )
        }
    }

    companion object {
        val QUERIES = listOf(
            "buy 1 get 1 buffet Dhaka",
            "\"eat 4 pay 1\" OR \"eat 3 pay 1\" OR \"dine 4 pay 1\" buffet Dhaka",
            "buffet offer Dhaka hotel buy one get one",
            "site:facebook.com buffet offer Dhaka",
            "site:facebook.com buy 1 get 1 buffet Dhaka",
            "iftar buffet offer Dhaka buy 1 get 1",
        )
    }
}
