package com.buffetbogo.hunter.net

import com.buffetbogo.hunter.data.nameKey
import java.util.Locale

/**
 * Finds a specific promotion post on a restaurant's own public page through web search.
 *
 * The Graph API is the clean route, but it needs Meta's approval; search engines already index
 * public Facebook posts, so a `site:facebook.com` query returns the permalink of the buffet post
 * itself. Results are accepted only when they point at a post (not a profile or a hashtag page)
 * and, when the venue's page handle is known, only from that page.
 */
class VenueAdFinder(private val braveApiKey: String) {

    suspend fun findPost(restaurant: String, expectedHandle: String? = null): SearchHit? {
        for (query in queries(restaurant)) {
            val hits = runCatching {
                if (braveApiKey.isNotBlank()) {
                    BraveSearchClient(braveApiKey).search(query, count = 10)
                } else {
                    DuckDuckGoSearch.search(query, count = 10)
                }
            }.getOrDefault(emptyList())
            pick(hits, restaurant, expectedHandle)?.let { return it }
        }
        return null
    }

    private fun queries(restaurant: String): List<String> = listOf(
        "site:facebook.com \"$restaurant\" buffet",
        "site:facebook.com $restaurant buy 1 get 1 buffet",
    )

    /** Pure selection rule, unit-tested with canned search results. */
    fun pick(hits: List<SearchHit>, restaurant: String, expectedHandle: String?): SearchHit? {
        val tokens = nameKey(restaurant)
            .split(" ")
            .filter { it.length >= 4 && it !in NOISE }
        return hits.firstOrNull { hit ->
            val url = hit.url.lowercase(Locale.ROOT)
            if (!url.contains("facebook.com")) return@firstOrNull false
            if (POST_MARKERS.none { url.contains(it) }) return@firstOrNull false
            if (expectedHandle != null) {
                url.contains(expectedHandle.lowercase(Locale.ROOT))
            } else {
                val text = (hit.title + " " + hit.snippet).lowercase(Locale.ROOT)
                tokens.isEmpty() || tokens.any { text.contains(it) }
            }
        }
    }

    private companion object {
        val POST_MARKERS = listOf("/posts/", "/videos/", "/reel/", "/permalink/", "pfbid")
        val NOISE = setOf("hotel", "dhaka", "restaurant", "the", "and", "buffet")
    }
}
