package com.buffetbogo.hunter.net

import com.buffetbogo.hunter.data.PriceGuess
import com.buffetbogo.hunter.data.PriceSources
import com.buffetbogo.hunter.ingest.OfferParser
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Finds the buffet price for a restaurant when the ad itself does not state one.
 *
 * Facebook's Graph API cannot search public posts by keyword, so this uses Brave's index of
 * public pages (including `site:facebook.com` results) and reads the price out of the matching
 * snippets. Only figures next to buffet wording are accepted, and absurd values are dropped.
 */
class BuffetPriceSearch(private val apiKey: String) {

    suspend fun lookup(restaurant: String, meal: String = "dinner"): PriceGuess? {
        if (restaurant.isBlank()) return null
        val candidates = ArrayList<Candidate>()

        queries(restaurant, meal).forEachIndexed { index, query ->
            if (index > 0) delay(1_500)
            val hits = search(query)
            hits.forEach { hit ->
                val text = "${hit.title}. ${hit.snippet}"
                val lower = text.lowercase(Locale.ROOT)
                if (!OfferParser.isBuffetOffer(text)) return@forEach
                val amount = OfferParser.extractPrice(text) ?: return@forEach
                if (amount !in MIN_PRICE..MAX_PRICE) return@forEach
                val perPerson = lower.contains("per person") || lower.contains("/person") ||
                    lower.contains("per pax") || lower.contains("pp")
                candidates += Candidate(amount, hit.url, perPerson, text)
            }
        }

        if (candidates.isEmpty()) return null
        // The same figure usually repeats across the restaurant's page and aggregator sites.
        val best = candidates
            .groupBy { it.amount }
            .maxByOrNull { (amount, rows) ->
                val perPersonBonus = if (rows.any { it.perPerson }) 3 else 0
                val plausibleBonus = if (amount in 1_000..6_000) 1 else 0
                rows.size * 2 + perPersonBonus + plausibleBonus
            }
            ?.value
            ?.first()
            ?: return null

        return PriceGuess(
            amount = best.amount,
            source = PriceSources.SEARCH,
            unit = if (best.perPerson) "per person · $meal" else "$meal price · as posted online",
            sourceUrl = best.sourceUrl,
        )
    }

    /** Brave can afford two queries per venue; the keyless fallback makes do with one. */
    private fun queries(restaurant: String, meal: String): List<String> = if (apiKey.isNotBlank()) {
        listOf(
            "\"$restaurant\" Dhaka $meal buffet price BDT",
            "site:facebook.com \"$restaurant\" buffet price",
        )
    } else {
        listOf("\"$restaurant\" Dhaka $meal buffet price BDT")
    }

    private suspend fun search(query: String): List<com.buffetbogo.hunter.net.SearchHit> =
        runCatching {
            if (apiKey.isNotBlank()) {
                BraveSearchClient(apiKey).search(query, count = 8)
            } else {
                DuckDuckGoSearch.search(query, count = 8)
            }
        }.getOrDefault(emptyList())

    private data class Candidate(
        val amount: Int,
        val sourceUrl: String,
        val perPerson: Boolean,
        val text: String,
    )

    private companion object {
        const val MIN_PRICE = 500
        const val MAX_PRICE = 20_000
    }
}
