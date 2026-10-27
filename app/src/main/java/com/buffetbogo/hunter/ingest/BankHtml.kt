package com.buffetbogo.hunter.ingest

import java.util.Locale

/**
 * Turns a bank's offer page into plain text lines.
 *
 * Bank dining pages are not feeds: each one is hand-written HTML with its own markup. Stripping
 * the tags down to one visible line per block element leaves the venue list readable no matter
 * which grid, card or table the page uses, and keeps the parser independent of CSS classes that
 * change without warning.
 */
object BankHtml {

    private val ignored = Regex("(?is)<(script|style|noscript|svg|template)[^>]*>.*?</\\1\\s*>")
    private val comments = Regex("(?s)<!--.*?-->")
    private val breakTag = Regex(
        "(?i)<(?:br|hr|/p|/div|/td|/th|/tr|/li|/ul|/ol|/h[1-6]|/section|/article|/table|" +
            "/thead|/tbody|/header|/footer|/nav|/main|/aside|/form|/fieldset|/figure|" +
            "/figcaption|/dd|/dt|/dl|/blockquote|/option|/label)(?:\\s[^>]*)?/?>",
    )
    private val anyTag = Regex("<[^>]+>")
    private val entity = Regex("&(#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")
    private val spaces = Regex("[\\u00a0\\s]+")

    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ndash" to "–", "mdash" to "—", "times" to "×", "hellip" to "…",
        "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”", "bull" to "•",
        "middot" to "·", "raquo" to "»", "laquo" to "«", "copy" to "©", "reg" to "®",
        "trade" to "™", "deg" to "°", "euro" to "€", "pound" to "£", "yen" to "¥",
    )

    /** Visible lines, in document order, with scripts and markup removed. */
    fun lines(html: String, limit: Int = 8_000): List<String> {
        if (html.isBlank()) return emptyList()
        val stripped = html
            .replace(ignored, " ")
            .replace(comments, " ")
            .replace(breakTag, "\n")
            .replace(anyTag, " ")
        return stripped.lineSequence()
            .map { decode(it).replace(spaces, " ").trim() }
            .filter { it.isNotEmpty() }
            .take(limit)
            .toList()
    }

    fun decode(raw: String): String = entity.replace(raw) { match ->
        val token = match.groupValues[1]
        when {
            token.startsWith("#x", ignoreCase = true) ->
                token.drop(2).toIntOrNull(16)?.let { codePoint(it) } ?: match.value
            token.startsWith("#") ->
                token.drop(1).toIntOrNull()?.let { codePoint(it) } ?: match.value
            else -> namedEntities[token.lowercase(Locale.ROOT)] ?: match.value
        }
    }

    private fun codePoint(value: Int): String? =
        if (value in 1..0x10ffff) String(Character.toChars(value)) else null
}
