package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.data.VenueSocial
import com.buffetbogo.hunter.data.VenueSocials
import com.buffetbogo.hunter.data.hasExactAdLink
import com.buffetbogo.hunter.data.nameKey
import com.buffetbogo.hunter.net.VenueAdFinder
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Gives every offer a link to the restaurant's own ad.
 *
 * Two steps, cheapest first: the bundled directory supplies each venue's official Facebook page
 * (and its own dining page); then, only for venues whose exact post is still unknown, a
 * `site:facebook.com` search looks for the promotion post itself. A bank or news URL is never
 * used as the ad link — those only ever prove that an offer exists.
 */
class AdLinkResolver(
    private val directory: Map<String, VenueSocial>,
    private val finder: VenueAdFinder,
) {

    suspend fun resolve(
        offers: List<Offer>,
        now: Long,
        maxSearches: Int = MAX_SEARCHES,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): List<Offer> {
        val linked = offers.map { withDirectoryLink(it, now) }

        val stale = linked.filter { !it.hasExactAdLink() && needsCheck(it, now) }
        if (stale.isEmpty() || maxSearches <= 0) return linked

        val byVenue = stale.groupBy { nameKey(it.restaurant) }.entries.take(maxSearches)
        val found = HashMap<String, String>()

        for ((key, group) in byVenue) {
            val restaurant = group.first().restaurant
            val handle = VenueSocials.find(directory, restaurant)
                ?.facebook
                ?.let { VenueSocials.facebookHandle(it) }
            val hit = runCatching { finder.findPost(restaurant, handle) }.getOrNull()
            if (hit != null) found[key] = hit.url
            delay(SEARCH_SPACING_MS)
        }

        return linked.map { offer ->
            val key = nameKey(offer.restaurant)
            val url = found[key]
            when {
                url != null && !offer.hasExactAdLink() -> offer.copy(
                    adUrl = url,
                    adLabel = VenueSocials.kindOf(url).label,
                    adCheckedAt = now,
                )
                // Record the attempt so an unchanged restaurant is not searched every sync.
                needsCheck(offer, now) -> offer.copy(adCheckedAt = now)
                else -> offer
            }
        }
    }

    /**
     * Instant, offline link assignment from the directory. Never points at a bank page. Also
     * settles on the venue's canonical name, so a bank's spelling and the hotel's own post are
     * recognised as the same restaurant.
     */
    fun withDirectoryLink(offer: Offer, now: Long): Offer {
        val social = VenueSocials.find(directory, offer.restaurant)
        val named = if (social != null && nameKey(social.displayName) != nameKey(offer.restaurant)) {
            offer.copy(restaurant = social.displayName)
        } else {
            offer
        }
        if (!named.adUrl.isNullOrBlank()) return named

        val ownLink = named.sourceUrl.takeIf {
            named.source != Sources.BANK &&
                named.source != Sources.NEWS &&
                named.source != Sources.GOOGLE
        }
        if (!ownLink.isNullOrBlank()) {
            return named.copy(
                adUrl = ownLink,
                adLabel = VenueSocials.kindOf(ownLink).label,
                adCheckedAt = stamp(ownLink, named.adCheckedAt, now),
            )
        }

        val page = social?.facebook ?: social?.website ?: return named
        return named.copy(
            adUrl = page,
            adLabel = VenueSocials.kindOf(page).label,
            adCheckedAt = stamp(page, named.adCheckedAt, now),
        )
    }

    /** Only a post counts as the ad; a page link keeps the door open for the post search. */
    private fun stamp(url: String, existing: Long?, now: Long): Long? =
        if (VenueSocials.kindOf(url) == com.buffetbogo.hunter.data.AdKind.FACEBOOK_POST) {
            existing ?: now
        } else {
            existing
        }

    private fun needsCheck(offer: Offer, now: Long): Boolean {
        val checked = offer.adCheckedAt ?: return true
        return now - checked > TimeUnit.DAYS.toMillis(AD_TTL_DAYS)
    }

    private companion object {
        /** Kept small: this only runs for venues the directory could not date precisely. */
        const val MAX_SEARCHES = 4
        const val AD_TTL_DAYS = 7L
        const val SEARCH_SPACING_MS = 1_100L
    }
}
