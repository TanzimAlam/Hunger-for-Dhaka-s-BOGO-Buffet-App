package com.buffetbogo.hunter.net

import com.buffetbogo.hunter.ingest.OfferParser
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val thumbnail: String?,
    val publishedAt: Long?,
)

/**
 * Brave Search API client (free tier available, no credit card).
 *
 * Brave indexes public Facebook pages, so `site:facebook.com` queries surface public offer
 * posts without scraping Facebook itself. Facebook's own Graph API cannot search public
 * posts by keyword, which is why the app does not call it directly.
 */
class BraveSearchClient(private val apiKey: String) {

    suspend fun search(query: String, count: Int = 10): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.search.brave.com/res/v1/web/search" +
            "?q=$encoded&count=$count&safesearch=off&freshness=pm&text_decorations=false"
        val body = Http.get(
            url,
            mapOf(
                "Accept" to "application/json",
                "X-Subscription-Token" to apiKey,
            ),
        )
        val results = JSONObject(body)
            .optJSONObject("web")
            ?.optJSONArray("results")
            ?: return emptyList()

        val hits = ArrayList<SearchHit>(results.length())
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val link = item.optString("url")
            if (link.isBlank()) continue
            hits += SearchHit(
                title = OfferParser.cleanText(item.optString("title")),
                url = link,
                snippet = OfferParser.cleanText(
                    item.optString("description").ifBlank { item.optString("extra_snippets") },
                ),
                thumbnail = item.optJSONObject("thumbnail")?.optString("src")?.takeIf { it.isNotBlank() },
                publishedAt = parseAge(item.optString("page_age")),
            )
        }
        return hits
    }

    private fun parseAge(raw: String): Long? {
        if (raw.isBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .recoverCatching {
                LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            .getOrNull()
    }
}
