package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.PriceSources
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.data.VenueSocial
import com.buffetbogo.hunter.data.VenueSocials
import com.buffetbogo.hunter.data.stableOfferId
import com.buffetbogo.hunter.net.FacebookGraph
import com.buffetbogo.hunter.net.FbPost
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** What one pass over the venues' Facebook pages produced. */
data class FacebookResult(
    val offers: List<Offer>,
    val pagesRead: Int,
    val pagesFailed: Int,
    /** Meta's own explanation when the token was rejected or lacks the permission. */
    val error: String?,
    /** The Facebook account the token belongs to, when Meta accepted it. */
    val account: String? = null,
)

/**
 * Reads the official Facebook pages of the tracked venues and keeps their buffet posts.
 *
 * This is the source that gives an offer its real ad: the restaurant's own post, with its own
 * price, dates, serving hours and conditions. Meta requires **Page Public Content Access** for
 * an app to read pages it does not administer, so the source is opt-in and a rejected token is
 * reported verbatim rather than hidden — the app then falls back to the venue's public page.
 */
class FacebookPageSource(
    private val token: String,
    private val graph: FacebookGraph = FacebookGraph(token),
) {

    suspend fun fetch(
        directory: Map<String, VenueSocial>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        maxPages: Int = MAX_PAGES,
    ): FacebookResult {
        if (token.isBlank()) {
            return FacebookResult(emptyList(), 0, 0, "No Facebook Page token configured.")
        }

        val account = runCatching { graph.whoAmI() }.getOrElse { error ->
            return FacebookResult(emptyList(), 0, 1, error.message, null)
        }

        val venues = directory.values
            .distinctBy { it.displayName }
            .filter { !it.facebook.isNullOrBlank() }
            .take(maxPages)

        val offers = LinkedHashMap<String, Offer>()
        var read = 0
        var failed = 0
        var error: String? = null

        for (venue in venues) {
            val pageUrl = venue.facebook ?: continue
            val handle = VenueSocials.facebookHandle(pageUrl) ?: continue
            val posts = runCatching { graph.feed(handle, limit = 15) }
                .onFailure { if (error == null) error = it.message }
                .getOrNull()
            if (posts == null) {
                failed++
                continue
            }
            read++
            postsToOffers(venue, pageUrl, posts, now, zone)
                .forEach { offers.putIfAbsent(it.id, it) }
            delay(150)
        }

        return FacebookResult(offers.values.toList(), read, failed, error, account)
    }

    /**
     * Turns one venue's posts into offers. A post qualifies only when it is about a buffet
     * promotion (BOGO / eat-pay) and is either still dated in the future or recent enough to be
     * running with no stated end date.
     */
    fun postsToOffers(
        venue: VenueSocial,
        pageUrl: String,
        posts: List<FbPost>,
        now: Long,
        zone: ZoneId,
    ): List<Offer> {
        val offers = ArrayList<Offer>()
        for (post in posts) {
            val text = post.message
            if (text.length < 12) continue
            if (!OfferParser.isBuffetOffer(text)) continue
            val kind = OfferParser.detectKind(text) ?: continue

            val dates = OfferParser.extractDates(text, now, zone)
            val until = dates.second
            if (until != null && until < now) continue
            val stale = until == null &&
                now - post.createdAt > TimeUnit.DAYS.toMillis(MAX_AGE_WITHOUT_DATE_DAYS)
            if (stale) continue

            val link = post.permalink ?: pageUrl
            val price = OfferParser.extractPrice(text)
            offers += Offer(
                id = stableOfferId(post.permalink ?: "${pageUrl}#${post.id}", kind.first),
                title = firstLine(text, kind.second),
                restaurant = venue.displayName,
                area = OfferParser.guessArea(text),
                offerKind = kind.first,
                headline = kind.second,
                details = text.take(700),
                terms = termsFrom(text),
                paymentNote = OfferParser.paymentNote(text),
                priceBdt = price,
                priceUnit = "per person",
                priceSource = if (price != null) PriceSources.AD else null,
                postedAt = post.createdAt,
                validFrom = dates.first,
                validUntil = until,
                source = Sources.FACEBOOK,
                sourceUrl = link,
                imageUrl = post.image,
                rating = null,
                ratingCount = null,
                fetchedAt = now,
                adUrl = link,
                adLabel = VenueSocials.kindOf(link).label,
                timing = OfferParser.extractTiming(text),
                adCheckedAt = now,
            )
        }
        return offers
    }

    private fun firstLine(text: String, fallback: String): String {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.length >= 8 }
        return (line ?: fallback).take(150)
    }

    private fun termsFrom(text: String): String {
        val lower = text.lowercase()
        val marker = listOf("valid", "terms", "t&c", "condition", "reservation", "advance")
            .map { lower.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
        if (marker == null) return "See the restaurant's post for the full conditions."
        return text.substring(marker).take(360)
    }

    companion object {
        private const val MAX_PAGES = 20
        private const val MAX_AGE_WITHOUT_DATE_DAYS = 45L
    }
}
