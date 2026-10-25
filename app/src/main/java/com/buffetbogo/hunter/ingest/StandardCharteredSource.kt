package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.data.stableOfferId
import com.buffetbogo.hunter.net.Http
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads the buffet section of Standard Chartered Bangladesh's Buy One Get One page.
 *
 * The page is plain server-rendered HTML with a stable structure — one `<div class=box>` per
 * venue, an `<h3>` name, `<p class="offer-info">` deal lines, a "Valid till" date and an
 * `<img>` thumbnail — so it can be parsed with no API key and no JavaScript engine.
 * This is the app's most reliable live source: it covers most Dhaka five-star buffets.
 */
object StandardCharteredSource {

    const val PAGE_URL = "https://av.sc.com/bd/edm/b1g1-offers/"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/121.0.0.0 Mobile Safari/537.36"

    private val boxSplit = Regex("(?i)<div\\s+class=[\"']?box[\"']?\\s*>")
    private val h3 = Regex("(?is)<h3[^>]*>(.*?)</h3>")
    private val imgTag = Regex("(?is)<img[^>]*>")
    private val srcAttr = Regex("(?i)src=[\"']([^\"']+)[\"']")
    private val offerParagraph = Regex("(?is)<p\\s+class=[\"']offer-info[^\"']*[\"'][^>]*>(.*?)</p>")
    private val validTill = Regex("(?i)Valid\\s+till:?\\s*(\\d{1,2}-[A-Za-z]{3}-\\d{2,4})")
    private val address = Regex("(?i)Address:?\\s*(.+?)\\s*(?:Valid\\s+till|Contact|•|$)")
    private val bullets = Regex("•\\s*([^•]{3,200})")
    private val dateFormat = DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH)

    /** The bank page also lists Chattogram, Sylhet and Cox's Bazar hotels; this app is Dhaka-only. */
    private val outsideDhaka = Regex(
        "(?i)\\b(chattogram|chittagong|sylhet|co?x'?s?\\s*bazar|khulna|rajshahi|barishal|barisal|" +
            "rangpur|mymensingh|narayanganj|gazipur|savar)\\b",
    )

    suspend fun fetch(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Offer> {
        val html = Http.get(
            PAGE_URL,
            mapOf(
                "Accept" to "text/html,application/xhtml+xml",
                "User-Agent" to BROWSER_UA,
            ),
        )
        return parsePage(html, now, zone)
    }

    /** Pure parser so the extraction rules can be unit-tested without a network call. */
    fun parsePage(html: String, now: Long, zone: ZoneId): List<Offer> {
        if (html.isBlank()) return emptyList()
        val offers = ArrayList<Offer>()

        for (chunk in boxSplit.split(html)) {
            val name = h3.find(chunk)?.groupValues?.get(1)
                ?.let { OfferParser.cleanText(it) }
                ?.takeIf { it.length in 3..60 }
                ?: continue

            val lines = offerParagraph.findAll(chunk)
                .map { OfferParser.cleanText(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .toList()
            if (lines.isEmpty()) continue

            val primary = lines.first()
            val kind = OfferParser.detectKind(primary)
                ?: OfferParser.detectKind(lines.joinToString(". "))
                ?: continue

            val plain = OfferParser.cleanText(chunk)
            val statedAddress = address.find(plain)?.groupValues?.get(1)?.trim()
            if (outsideDhaka.containsMatchIn(name) ||
                (!statedAddress.isNullOrBlank() && outsideDhaka.containsMatchIn(statedAddress))
            ) {
                continue
            }

            val until = validTill.find(plain)?.groupValues?.get(1)?.let { parseValidity(it, zone) }
            if (until != null && until < now) continue

            val terms = bullets.findAll(plain)
                .map { it.groupValues[1].trim() }
                .take(4)
                .joinToString(" • ")
                .ifBlank { "Prior reservation is required." }

            offers += Offer(
                id = stableOfferId(PAGE_URL + "#" + name, kind.first),
                title = primary,
                restaurant = name,
                area = areaFor(plain, statedAddress),
                offerKind = kind.first,
                headline = kind.second,
                details = lines.joinToString(" • "),
                terms = terms.take(360),
                paymentNote = paymentNote(plain),
                priceBdt = OfferParser.extractPrice(plain),
                postedAt = now,
                validFrom = null,
                validUntil = until,
                source = Sources.BANK,
                sourceUrl = PAGE_URL,
                imageUrl = imageFor(chunk),
                rating = null,
                ratingCount = null,
                fetchedAt = now,
            )
        }
        return offers
    }

    private fun areaFor(plain: String, statedAddress: String?): String {
        if (!statedAddress.isNullOrBlank()) {
            val fromAddress = OfferParser.guessArea(statedAddress)
            if (fromAddress != "Dhaka") return fromAddress
        }
        return OfferParser.guessArea(plain)
    }

    private fun paymentNote(plain: String): String {
        val lower = plain.lowercase(Locale.ROOT)
        if (lower.contains("all standard chartered")) return "Standard Chartered cards"
        val parsed = OfferParser.paymentNote(plain)
        return if (parsed.startsWith("No card")) "Selected bank cards" else parsed
    }

    private fun imageFor(chunk: String): String? {
        val tag = imgTag.find(chunk)?.value ?: return null
        val raw = srcAttr.find(tag)?.groupValues?.get(1)?.trim().orEmpty()
        if (raw.isBlank() || raw.startsWith("data:")) return null
        return when {
            raw.startsWith("http") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "https://av.sc.com$raw"
            // Relative images live beside the page: .../b1g1-offers/ + images/LE1.jpg
            else -> PAGE_URL + raw.removePrefix("./")
        }
    }

    private fun parseValidity(raw: String, zone: ZoneId): Long? =
        runCatching { LocalDate.parse(raw, dateFormat) }
            .getOrNull()
            ?.atTime(LocalTime.MAX)
            ?.atZone(zone)
            ?.toInstant()
            ?.toEpochMilli()
}
