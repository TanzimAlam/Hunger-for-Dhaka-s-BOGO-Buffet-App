package com.buffetbogo.hunter.data

import android.content.Context
import com.buffetbogo.hunter.ingest.OfferParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Loads the bundled catalogue of checked offers and their checked Google ratings.
 *
 * These are real promotions verified against the restaurant's or bank's own page, each keeping
 * its source link and the validity dates stated there. They exist so the app shows live Dhaka
 * deals from the first launch, before any network call completes.
 */
object SeedLoader {

    /** Bump whenever seed_offers.json or venue_ratings.json changes. */
    const val VERSION = 6

    suspend fun load(context: Context, now: Long = System.currentTimeMillis()): List<Offer> =
        withContext(Dispatchers.IO) {
            val raw = context.assets.open("seed_offers.json").bufferedReader().use { it.readText() }
            parse(raw, now, ZoneId.systemDefault())
        }

    /** Pure parser, kept free of Android types so it can be unit-tested on the JVM. */
    fun parse(raw: String, now: Long, zone: ZoneId): List<Offer> {
        val array = JSONArray(raw)
        val offers = ArrayList<Offer>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val kind = item.getString("offerKind")
            val sourceUrl = item.optString("sourceUrl")
            val idExtra = if (item.has("idExtra")) item.optString("idExtra") else ""
            val id = if (idExtra.isNotBlank()) {
                stableOfferId("$sourceUrl#$idExtra", kind)
            } else {
                stableOfferId(sourceUrl, kind)
            }
            val details = item.optString("details")
            val terms = item.optString("terms")
            val source = item.optString("source", Sources.CURATED)
            // Curated rows link to the restaurant's own ad; a bank row never does.
            val adUrl = item.optString("adUrl").takeIf { it.isNotBlank() }
                ?: sourceUrl.takeIf { source != Sources.BANK }
            offers += Offer(
                id = id,
                title = item.getString("title"),
                restaurant = item.getString("restaurant"),
                area = item.optString("area", "Dhaka"),
                offerKind = kind,
                headline = item.getString("headline"),
                details = details,
                terms = terms,
                paymentNote = item.optString("paymentNote", "No card or payment condition mentioned"),
                priceBdt = if (item.has("priceBdt") && !item.isNull("priceBdt")) {
                    item.optInt("priceBdt")
                } else {
                    // Prices are often only quoted in the prose; recover them so the card shows one.
                    OfferParser.extractPrice("$details $terms ${item.getString("title")}")
                },
                priceUnit = item.optString("priceUnit").takeIf { it.isNotBlank() },
                priceSource = item.optString("priceSource")
                    .takeIf { it.isNotBlank() }
                    ?: if (item.has("priceBdt")) PriceSources.AD else null,
                postedAt = date(item, "postedAt", now, zone, endOfDay = false) ?: now,
                validFrom = date(item, "validFrom", now, zone, endOfDay = false),
                validUntil = date(item, "validUntil", now, zone, endOfDay = true),
                source = source,
                sourceUrl = sourceUrl,
                imageUrl = if (item.has("imageUrl")) item.optString("imageUrl") else null,
                rating = if (item.has("rating")) item.optDouble("rating") else null,
                ratingCount = if (item.has("ratingCount")) item.optInt("ratingCount") else null,
                fetchedAt = now,
                adUrl = adUrl,
                adLabel = item.optString("adLabel")
                    .takeIf { it.isNotBlank() }
                    ?: adUrl?.let { VenueSocials.kindOf(it).label },
                timing = item.optString("timing").takeIf { it.isNotBlank() },
                adCheckedAt = if (adUrl != null) now else null,
            )
        }
        return offers
    }

    suspend fun loadVenueRatings(context: Context): List<RestaurantRating> =
        withContext(Dispatchers.IO) {
            val raw = context.assets.open("venue_ratings.json").bufferedReader().use { it.readText() }
            parseVenueRatings(raw, ZoneId.systemDefault())
        }

    /**
     * Reads the checked Google ratings. Each venue may list [aliases] so the rating still
     * matches when a bank page spells the hotel differently from the hotel itself.
     */
    fun parseVenueRatings(raw: String, zone: ZoneId): List<RestaurantRating> {
        val array = JSONArray(raw)
        val rows = ArrayList<RestaurantRating>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val displayName = item.getString("restaurant")
            val rating = if (item.has("rating")) item.optDouble("rating") else null
            val count = if (item.has("ratingCount")) item.optInt("ratingCount") else null
            val checkedAt = date(item, "checkedAt", System.currentTimeMillis(), zone, endOfDay = true)
                ?: System.currentTimeMillis()

            val names = LinkedHashSet<String>()
            names += displayName
            item.optJSONArray("aliases")?.let { aliases ->
                for (index in 0 until aliases.length()) {
                    names += aliases.optString(index)
                }
            }

            names.filter { it.isNotBlank() }.forEach { name ->
                rows += RestaurantRating(
                    nameKey = nameKey(name),
                    displayName = displayName,
                    rating = rating,
                    ratingCount = count,
                    placeId = null,
                    updatedAt = checkedAt,
                )
            }
        }
        return rows
    }

    private fun date(
        item: JSONObject,
        key: String,
        now: Long,
        zone: ZoneId,
        endOfDay: Boolean,
    ): Long? {
        if (!item.has(key) || item.isNull(key)) return null
        val parsed = runCatching { LocalDate.parse(item.getString(key)) }.getOrNull() ?: return null
        val time = if (endOfDay) LocalTime.MAX else LocalTime.MIN
        return parsed.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}
