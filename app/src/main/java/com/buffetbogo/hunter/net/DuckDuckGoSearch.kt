package com.buffetbogo.hunter.net

import com.buffetbogo.hunter.ingest.OfferParser
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Keyless search fallback for price lookups.
 *
 * Brave gives a proper API but needs a key. When none is set, the app falls back to DuckDuckGo's
 * lightweight HTML endpoint, reads the result titles and snippets, and leaves the actual price
 * extraction to [com.buffetbogo.hunter.net.BuffetPriceSearch]. Nothing is scraped beyond ordinary
 * search-result text, and only the restaurant name is ever sent as a query.
 */
object DuckDuckGoSearch {

    private const val ENDPOINT = "https://html.duckduckgo.com/html/"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/121.0.0.0 Mobile Safari/537.36"

    private val titleRegex = Regex(
        "(?is)<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
    )
    private val snippetRegex = Regex(
        "(?is)<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>",
    )
    private val redirectParam = Regex("[?&]uddg=([^&]+)")
    private val tag = Regex("<[^>]+>")

    suspend fun search(query: String, count: Int = 8): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = Http.get(
            "$ENDPOINT?q=$encoded",
            mapOf(
                "Accept" to "text/html,application/xhtml+xml",
                "User-Agent" to UA,
            ),
        )
        return parse(html, count)
    }

    /** Pure parser, unit-tested against a saved result page. */
    fun parse(html: String, count: Int = 8): List<SearchHit> {
        val titles = titleRegex.findAll(html)
            .map { match -> decodeLinks(match.groupValues[1]) to clean(match.groupValues[2]) }
            .toList()
        val snippets = snippetRegex.findAll(html)
            .map { match -> clean(match.groupValues[1]) }
            .toList()

        return titles.mapIndexed { index, (url, title) ->
            SearchHit(
                title = title,
                url = url,
                snippet = snippets.getOrNull(index).orEmpty(),
                thumbnail = null,
                publishedAt = null,
            )
        }
            .filter { it.url.startsWith("http") && it.title.isNotBlank() }
            .take(count)
    }

    private fun decodeLinks(href: String): String {
        val raw = redirectParam.find(href)?.groupValues?.get(1) ?: return href
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(href)
    }

    private fun clean(raw: String): String =
        OfferParser.cleanText(
            raw.replace(tag, " ")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&nbsp;", " "),
        )
}
