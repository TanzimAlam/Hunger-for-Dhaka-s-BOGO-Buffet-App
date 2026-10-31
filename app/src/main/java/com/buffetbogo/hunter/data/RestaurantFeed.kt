package com.buffetbogo.hunter.data

/**
 * Everything one restaurant is running, shown as a single card in the feed.
 *
 * A venue can publish several deals at once (B1G1 on some cards, Eat 3 Pay 1 on others). The
 * feed shows the restaurant once and lists each deal inside the card, so the same hotel never
 * fills the list three times over.
 */
data class RestaurantGroup(
    val restaurant: String,
    val offers: List<OfferUi>,
) {
    val offerCount: Int get() = offers.size

    val rating: Double? get() = offers.firstNotNullOfOrNull { it.rating }
    val ratingCount: Int? get() = offers.firstNotNullOfOrNull { it.ratingCount }
    val heroImage: String? get() = offers.firstNotNullOfOrNull { it.offer.imageUrl }
    val area: String get() = offers.first().offer.area
    val newest: Long get() = offers.maxOf { it.offer.postedAt }

    /** The earliest end date across the restaurant's deals; null when none states one. */
    val soonestEnd: Long? get() = offers.mapNotNull { it.offer.validUntil }.minOrNull()

    /** Cheapest price on offer, used by the "price: low to high" ordering. */
    val lowestPrice: Int? get() = offers.mapNotNull { it.offer.priceBdt }.minOrNull()

    /** One compact badge per deal, e.g. ["B1G1", "Eat 3 Pay 1"]. */
    val dealLabels: List<String> get() = offers.map { it.offer.shortDealLabel() }.distinct()
}

/** Folds the offer list into one entry per restaurant, then orders the restaurants. */
object FeedGrouper {

    fun group(offers: List<OfferUi>, order: SortOrder): List<RestaurantGroup> {
        val groups = offers
            .groupBy { nameKey(it.offer.restaurant) }
            .map { (_, items) ->
                // Two banks often list the same deal for the same hotel. Keep the copy that
                // opens the restaurant's own post, otherwise the one with a real price.
                val onePerDeal = items
                    .sortedWith(
                        compareByDescending<OfferUi> { it.offer.hasExactAdLink() }
                            .thenByDescending { it.offer.adUrl != null }
                            .thenByDescending { it.offer.priceBdt != null },
                    )
                    .distinctBy { it.offer.offerKind }
                RestaurantGroup(
                    restaurant = items.first().offer.restaurant,
                    // Deals inside a card follow the same rule as the feed itself.
                    offers = OfferSorter.sort(onePerDeal, order),
                )
            }
        return groups.sortedWith(comparatorFor(order))
    }

    private fun comparatorFor(order: SortOrder): Comparator<RestaurantGroup> = when (order) {
        // A restaurant sits where its first deal to end sits, so expiring offers stay on top.
        SortOrder.ENDS_SOONEST, SortOrder.MY_CARDS ->
            compareBy<RestaurantGroup> { it.soonestEnd ?: Long.MAX_VALUE }
                .thenByDescending { it.newest }

        SortOrder.NEWEST -> compareByDescending { it.newest }

        SortOrder.PRICE_LOW_HIGH -> compareBy<RestaurantGroup> { it.lowestPrice ?: Int.MAX_VALUE }
            .thenByDescending { it.newest }

        SortOrder.RATING_HIGH_LOW -> compareByDescending<RestaurantGroup> { it.rating ?: -1.0 }
            .thenByDescending { it.newest }
    }.thenBy { it.restaurant.lowercase() }
}

/** Compact badge text for a deal: "B1G1", "B1G3", "Eat 3 Pay 1", "25% off". */
fun Offer.shortDealLabel(): String {
    when {
        offerKind.startsWith("BOGO_") -> {
            val parts = offerKind.removePrefix("BOGO_").split("_")
            val buy = parts.getOrNull(0)?.toIntOrNull()
            val get = parts.getOrNull(1)?.toIntOrNull()
            if (buy != null && get != null) return "B${buy}G$get"
        }
        offerKind.startsWith("EAT_") -> {
            val parts = offerKind.removePrefix("EAT_").split("_")
            val eat = parts.getOrNull(0)?.toIntOrNull()
            val pay = parts.getOrNull(2)?.toIntOrNull()
            if (eat != null && pay != null) return "Eat $eat Pay $pay"
        }
        offerKind.startsWith("PCT_") -> {
            val pct = offerKind.removePrefix("PCT_").toIntOrNull()
            if (pct != null) return "$pct% off"
        }
        offerKind == "BUFFET" -> return "Buffet"
        offerKind == "DISCOUNT" -> return "Discount"
    }
    return headline.take(22).ifBlank { "Offer" }
}
