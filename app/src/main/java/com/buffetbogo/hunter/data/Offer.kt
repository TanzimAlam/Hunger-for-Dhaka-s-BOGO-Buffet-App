package com.buffetbogo.hunter.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** How a price was obtained, so an estimate is never mistaken for a quoted figure. */
object PriceSources {
    /** Quoted in the ad or the restaurant's own listing. */
    const val AD = "AD"
    /** Found by searching the restaurant on the web/social media. */
    const val SEARCH = "SEARCH"
    /** The restaurant's checked standing buffet price. */
    const val TYPICAL = "TYPICAL"
}

/** Where an offer came from. */
object Sources {
    /** Found through a web search hit (Brave, when the user supplies a key). */
    const val GOOGLE = "GOOGLE"
    /** A public Facebook page surfaced by a web search hit — never scraped. */
    const val FACEBOOK = "FACEBOOK"
    /** A dated promotion checked against the restaurant's or bank's own page. */
    const val CURATED = "CURATED"
    /** Parsed from a bank's live card-offer page. */
    const val BANK = "BANK"
    /** A dated article from the Google News feed. */
    const val NEWS = "NEWS"
    const val MANUAL = "MANUAL"
}

@Entity(
    tableName = "offers",
    indices = [Index("postedAt"), Index("validUntil")],
)
data class Offer(
    @PrimaryKey val id: String,
    val title: String,
    val restaurant: String,
    val area: String,
    /** Machine-readable kind such as BOGO_1_1 or EAT_4_PAY_1. */
    val offerKind: String,
    /** Short badge text such as "Buy 1 Get 1 Free". */
    val headline: String,
    val details: String,
    val terms: String,
    val paymentNote: String,
    val priceBdt: Int?,
    /** What the price covers, e.g. "per person" or "for 4 people (B1G3)". */
    val priceUnit: String? = null,
    /** One of [PriceSources]; null on rows written by older builds. */
    val priceSource: String? = null,
    /** Epoch millis the ad was published; the list is sorted by this, newest first. */
    val postedAt: Long,
    val validFrom: Long?,
    val validUntil: Long?,
    val source: String,
    /** Where the app discovered the promotion; not always where the ad itself lives. */
    val sourceUrl: String,
    val imageUrl: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val fetchedAt: Long,
    val hidden: Boolean = false,
    /**
     * The restaurant's own post or page carrying the ad (price, dates, conditions, timings).
     * This is what "Open the original ad" opens — never a bank's listing page.
     */
    val adUrl: String? = null,
    /** Human label for [adUrl]: "Facebook post", "Facebook page", "Restaurant website" … */
    val adLabel: String? = null,
    /** Serving window printed in the ad, e.g. "Lunch 12:30–3:30 pm · Dinner 6:30–10:30 pm". */
    val timing: String? = null,
    /** When [adUrl] was last resolved, so the lookup is not repeated on every sync. */
    val adCheckedAt: Long? = null,
)

@Entity(tableName = "restaurant_ratings")
data class RestaurantRating(
    @PrimaryKey val nameKey: String,
    val displayName: String,
    val rating: Double?,
    val ratingCount: Int?,
    val placeId: String?,
    val updatedAt: Long,
)

/** Offer plus the Google rating currently known for its restaurant. */
data class OfferUi(
    val offer: Offer,
    val rating: Double?,
    val ratingCount: Int?,
)

/**
 * Normalised key used to match a restaurant across offers and the rating cache.
 * Accents are folded ("Méridien" -> "meridien") before punctuation is dropped, so the same
 * hotel written with and without accents still shares one rating.
 */
fun nameKey(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

fun stableOfferId(sourceUrl: String, kind: String): String {
    val digest = MessageDigest.getInstance("SHA-1")
        .digest("$sourceUrl|$kind".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(20)
}

/** True when the card must admit the price was not printed in the ad itself. */
fun Offer.priceIsEstimate(): Boolean =
    priceSource == PriceSources.SEARCH || priceSource == PriceSources.TYPICAL

/**
 * The link the detail screen opens. Ad posts and restaurant pages win; a bank or news page is
 * used only when it is itself the ad (a curated entry pointing at the restaurant's own site),
 * which is decided when the offer is built — never here.
 */
fun Offer.adToOpen(): String? = adUrl?.takeIf { it.isNotBlank() }

/**
 * Last-resort link when no ad has been resolved yet: Facebook's own keyword search for this
 * restaurant's buffet posts. Always valid, and never a bank page.
 */
fun Offer.facebookSearchUrl(): String = VenueSocials.facebookSearchUrl(restaurant)

/** How directly a card's link points at the deal itself. */
enum class AdLinkState {
    /** The restaurant's own post, or its own dining/offer page: the best link the app can give. */
    DIRECT,
    /** Only the restaurant's Facebook page is known, not the post that carries this deal. */
    GENERIC_PAGE,
    /** Nothing from the restaurant at all; the button falls back to a Facebook search. */
    NONE,
}

/**
 * What the "Open the original ad" button can honestly promise.
 *
 * A bank page is never counted here: it proves the deal exists but is not the ad. A restaurant
 * website counts as direct because it is the venue's own page; a bare Facebook page does not,
 * because the deal may be buried anywhere in its feed.
 */
fun Offer.adLinkState(): AdLinkState = when {
    adUrl.isNullOrBlank() -> AdLinkState.NONE
    hasExactAdLink() || adLabel == AdKind.WEBSITE.label -> AdLinkState.DIRECT
    else -> AdLinkState.GENERIC_PAGE
}

/** True when the link is the restaurant's own post, not just its page or a search. */
fun Offer.hasExactAdLink(): Boolean {
    val url = adUrl ?: return false
    val isPost = DIFF_LINK_PATTERNS.any { url.contains(it, ignoreCase = true) }
    return isPost
}

private val DIFF_LINK_PATTERNS = listOf("/posts/", "/videos/", "/permalink/", "pfbid", "/reel/")


fun Offer.isExpired(now: Long = System.currentTimeMillis()): Boolean =
    validUntil != null && validUntil < now

fun Offer.isLive(now: Long = System.currentTimeMillis()): Boolean =
    !hidden && !isExpired(now)

/** Dhaka five-star hotels, used by the "5★ Hotels" filter. */
val FiveStarHotels: Set<String> = setOf(
    "Le Méridien Dhaka",
    "Sheraton Dhaka",
    "InterContinental Dhaka",
    "Renaissance Dhaka Gulshan",
    "Radisson Blu Dhaka Water Garden",
    "The Westin Dhaka",
    "Pan Pacific Sonargaon",
    "Amari Dhaka",
    "Six Seasons Hotel",
    "The Lakeshore Hotel",
    "Four Points by Sheraton Dhaka",
    "Crowne Plaza Dhaka Gulshan",
    "Crowne Plaza Dhaka Airport",
)

fun Offer.isFiveStar(): Boolean =
    FiveStarHotels.any { restaurant.contains(it, ignoreCase = true) }
