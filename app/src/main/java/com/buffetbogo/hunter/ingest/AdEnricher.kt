package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.PriceSources
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.data.VenueSocials
import com.buffetbogo.hunter.data.nameKey

/**
 * A promotion as the restaurant itself published it — either a Facebook post read through the
 * Graph API, or a public post found by search. Everything in here is taken from the restaurant's
 * own words, which is what the detail screen shows and links to.
 */
data class AdPost(
    val restaurant: String,
    val url: String,
    val text: String,
    val image: String? = null,
    val postedAt: Long? = null,
    /** Machine-readable kind such as BOGO_1_1, when the post states one. */
    val kind: String? = null,
    val priceBdt: Int? = null,
    val validUntil: Long? = null,
    val timing: String? = null,
)

/**
 * Folds the restaurants' own posts into the offers the app discovered elsewhere.
 *
 * Discovery (a bank's card-offer page, a news article) proves an offer exists but is not the ad:
 * it often omits the price, the serving hours and the restaurant's own conditions. When the
 * venue's own post is available it becomes the card's ad link, and any figure or date it states
 * replaces the discovery source's. A post that has no counterpart in the discovered set becomes
 * an offer in its own right, so nothing the restaurant published is lost.
 */
object AdEnricher {

    fun enrich(offers: List<Offer>, posts: List<AdPost>, now: Long): List<Offer> {
        if (posts.isEmpty()) return offers
        val byVenue = posts.groupBy { nameKey(it.restaurant) }

        val merged = offers.map { offer ->
            val candidate = pick(byVenue[nameKey(offer.restaurant)].orEmpty(), offer) ?: return@map offer
            apply(offer, candidate, now)
        }

        // One card per venue and deal: the enriched discovery row wins over a bare post row.
        return merged
            .groupBy { nameKey(it.restaurant) + "|" + it.offerKind }
            .flatMap { (_, group) ->
                if (group.size == 1) {
                    group
                } else {
                    group.filter { it.source != Sources.FACEBOOK }.ifEmpty { listOf(group.first()) }
                }
            }
    }

    /**
     * Prefers the post that states the same deal, then the one that carries a price or date. A
     * post describing a different deal is never grafted onto another offer.
     */
    private fun pick(candidates: List<AdPost>, offer: Offer): AdPost? {
        if (candidates.isEmpty()) return null
        val sameKind = candidates.filter { it.kind == offer.offerKind }
        val pool = sameKind.ifEmpty { candidates.filter { it.kind == null } }
        if (pool.isEmpty()) return null
        return pool.maxWithOrNull(
            compareBy<AdPost> { if (it.kind == offer.offerKind) 1 else 0 }
                .thenBy { if (it.priceBdt != null) 1 else 0 }
                .thenBy { if (it.validUntil != null) 1 else 0 }
                .thenBy { it.postedAt ?: 0L },
        )
    }

    private fun apply(offer: Offer, post: AdPost, now: Long): Offer {
        val richerText = post.text.length > offer.details.length + 40
        return offer.copy(
            adUrl = post.url,
            adLabel = VenueSocials.kindOf(post.url).label,
            timing = offer.timing ?: post.timing,
            adCheckedAt = now,
            imageUrl = post.image ?: offer.imageUrl,
            priceBdt = post.priceBdt ?: offer.priceBdt,
            priceUnit = if (post.priceBdt != null) {
                offer.priceUnit?.takeIf { it.isNotBlank() } ?: "per person"
            } else {
                offer.priceUnit
            },
            priceSource = if (post.priceBdt != null) PriceSources.AD else offer.priceSource,
            validUntil = post.validUntil ?: offer.validUntil,
            details = if (richerText) post.text.take(700) else offer.details,
            terms = if (richerText) {
                post.text.takeLast(360).trim() + " — full conditions are in the restaurant's post."
            } else {
                offer.terms
            },
        )
    }
}
