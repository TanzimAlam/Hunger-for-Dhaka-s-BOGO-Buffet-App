package com.buffetbogo.hunter.data

import org.json.JSONArray
import java.util.Locale

/** A price we can put on a card, with where it came from. */
data class PriceGuess(
    val amount: Int,
    /** One of [PriceSources]. */
    val source: String,
    val unit: String,
    val sourceUrl: String? = null,
)

/** One restaurant's checked standing buffet prices. */
data class VenuePrice(
    val displayName: String,
    val breakfast: Int?,
    val lunch: Int?,
    val dinner: Int?,
    val unit: String,
    val sourceUrl: String?,
)

/**
 * The bundled price book. Ads on Facebook frequently state the deal but not the price, so the
 * app keeps the restaurants' checked standing buffet prices and uses them rather than leave a
 * card blank. Anything marked [PriceSources.TYPICAL] is labelled "typical" in the UI.
 */
object VenuePriceBook {

    fun parse(raw: String): Map<String, VenuePrice> {
        val array = JSONArray(raw)
        val book = LinkedHashMap<String, VenuePrice>(array.length() * 2)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val name = item.getString("restaurant")
            val price = VenuePrice(
                displayName = name,
                breakfast = item.optInt("breakfast").takeIf { item.has("breakfast") },
                lunch = item.optInt("lunch").takeIf { item.has("lunch") },
                dinner = item.optInt("dinner").takeIf { item.has("dinner") },
                unit = item.optString("unit", "per person"),
                sourceUrl = item.optString("sourceUrl").takeIf { it.isNotBlank() },
            )
            book[nameKey(name)] = price
            item.optJSONArray("aliases")?.let { aliases ->
                for (index in 0 until aliases.length()) {
                    val alias = aliases.optString(index)
                    if (alias.isNotBlank()) book[nameKey(alias)] = price
                }
            }
        }
        return book
    }

    /**
     * Picks the meal the ad talks about. A breakfast offer is never answered with a dinner
     * rate; everything else falls back to dinner because most Dhaka campaigns are dinner ones.
     */
    fun find(
        book: Map<String, VenuePrice>,
        restaurant: String,
        context: String,
    ): PriceGuess? {
        val price = book[nameKey(restaurant)] ?: return null
        val lower = context.lowercase(Locale.ROOT)
        val wantsBreakfast = lower.contains("breakfast") &&
            !lower.contains("lunch") && !lower.contains("dinner")
        val wantsLunch = !wantsBreakfast && lower.contains("lunch") && !lower.contains("dinner")

        val meal: String
        val amount: Int?
        when {
            wantsBreakfast -> {
                meal = "breakfast"
                amount = price.breakfast
            }
            wantsLunch -> {
                meal = "lunch"
                amount = price.lunch ?: price.dinner
            }
            else -> {
                meal = "dinner"
                amount = price.dinner ?: price.lunch
            }
        }
        if (amount == null) return null

        val unit = if (price.unit.isBlank() || price.unit == "per person") {
            "per person · $meal"
        } else {
            price.unit
        }
        return PriceGuess(
            amount = amount,
            source = PriceSources.TYPICAL,
            unit = unit,
            sourceUrl = price.sourceUrl,
        )
    }
}
