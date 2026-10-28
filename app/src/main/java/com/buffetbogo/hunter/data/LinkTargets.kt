package com.buffetbogo.hunter.data

import java.util.Locale

/**
 * Turns a link collected from a bank page, a Facebook post or a search result into something a
 * phone can actually open.
 *
 * Scraped links arrive with every kind of damage: HTML entities that were never decoded, quotes
 * and angle brackets left around an href, protocol-relative `//host/path` addresses, a bare
 * `facebook.com/...` with no scheme at all, control characters, and plain spaces. Android's
 * `Uri.parse` accepts all of them silently, and then `startActivity` finds no app that can handle
 * the result — which used to close the whole app. Every link now passes through [clean] first.
 */
object LinkTargets {

    /** Schemes that are safe to hand to the system. Anything else (intent:, file:, javascript:) is dropped. */
    private val ALLOWED_SCHEMES = setOf("http", "https")

    /** RFC 3986 characters that may appear in a URL without escaping. */
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val RESERVED = ":/?#[]@!$&'()*+,;="

    private val HAS_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.\\-]*:")

    /**
     * A URL the browser can open, or null when the input cannot be repaired. Never throws.
     *
     * @return e.g. `https://www.facebook.com/HotelSarinaDhaka/posts/123`
     */
    fun clean(raw: String?): String? {
        if (raw == null) return null
        var text = raw.trim()
        if (text.isEmpty()) return null

        text = unescapeEntities(text)
        text = stripWrapping(text)
        text = removeInvisible(text)
        if (text.isEmpty()) return null

        // "//www.facebook.com/x" means https, not a path.
        if (text.startsWith("//")) text = "https:$text"

        // "www.facebook.com/x", "facebook.com/x" and "platinumhotels.com.bd/x" all mean https.
        if (!HAS_SCHEME.containsMatchIn(text)) text = "https://$text"

        val scheme = text.substringBefore(':').lowercase(Locale.ROOT)
        if (scheme !in ALLOWED_SCHEMES) return null

        text = percentEncodeIllegal(text)
        if (text.length > MAX_LENGTH) return null

        val host = text.substringAfter("://", "").substringBefore('/').substringBefore('?')
            .substringBefore('#')
            .substringAfter('@')
            .substringBefore(':')
            .trim()
        if (host.isBlank() || host.startsWith('.') || host.endsWith('.')) return null
        // A host has to look like a host: a dot, or an IP, or localhost during development.
        if (!host.contains('.') && host != "localhost") return null
        if (host.any { it.isWhitespace() }) return null

        return text
    }

    /** True when [raw] survives [clean]. */
    fun isOpenable(raw: String?): Boolean = clean(raw) != null

    private fun unescapeEntities(text: String): String {
        if (!text.contains('&')) return text
        return text
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#x26;", "&")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    /** hrefs are often copied with their quotes or the surrounding markup still attached. */
    private fun stripWrapping(text: String): String =
        text.trim('<', '>', '"', '\'', '`', ' ', '\u00a0', '\u200b')

    private fun removeInvisible(text: String): String = buildString(text.length) {
        for (char in text) {
            when {
                char.code < 0x20 || char.code == 0x7f -> Unit
                char.code in 0x200B..0x200F -> Unit
                char.code == 0xfeff -> Unit
                else -> append(char)
            }
        }
    }

    /**
     * Escapes spaces, quotes, braces and non-ASCII text exactly as a browser would, while leaving
     * existing `%XX` escapes and the reserved characters of a query string alone.
     */
    private fun percentEncodeIllegal(text: String): String {
        val out = StringBuilder(text.length + 16)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '%' && index + 2 < text.length &&
                text[index + 1].isHexDigit() && text[index + 2].isHexDigit()
            ) {
                out.append(text, index, index + 3)
                index += 3
                continue
            }
            when {
                UNRESERVED.indexOf(char) >= 0 || RESERVED.indexOf(char) >= 0 -> out.append(char)
                else -> {
                    // Includes spaces and anything outside ASCII: encode its UTF-8 bytes.
                    val bytes = char.toString().toByteArray(Charsets.UTF_8)
                    if (char.code > 0x7f) {
                        bytes.forEach { byte ->
                            out.append('%').append(HEX[(byte.toInt() shr 4) and 0x0f])
                                .append(HEX[byte.toInt() and 0x0f])
                        }
                    } else {
                        out.append('%').append(HEX[(char.code shr 4) and 0x0f])
                            .append(HEX[char.code and 0x0f])
                    }
                }
            }
            index++
        }
        return out.toString()
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private const val HEX = "0123456789ABCDEF"

    /** Browsers stop well before this; anything longer is not a real ad link. */
    private const val MAX_LENGTH = 2_000
}
