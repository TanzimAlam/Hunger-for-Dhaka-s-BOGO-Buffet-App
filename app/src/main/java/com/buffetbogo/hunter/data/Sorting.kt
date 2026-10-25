package com.buffetbogo.hunter.data

/** The ways the feed can be ordered. The default is by when each offer ends. */
enum class SortOrder(val key: String, val label: String) {
    ENDS_SOONEST("ends_soonest", "Ends soonest"),
    NEWEST("newest", "Newest first"),
    PRICE_LOW_HIGH("price_low", "Price: low to high"),
    RATING_HIGH_LOW("rating", "Top rated"),
    ;

    companion object {
        fun fromKey(key: String?): SortOrder = entries.firstOrNull { it.key == key } ?: ENDS_SOONEST
    }
}

/** Pure sorting rules, so the ordering can be unit-tested without a device. */
object OfferSorter {

    fun sort(offers: List<OfferUi>, order: SortOrder): List<OfferUi> = when (order) {
        // Offers with no stated end date sit at the bottom rather than outranking dated ones.
        SortOrder.ENDS_SOONEST -> offers.sortedWith(
            compareBy<OfferUi> { it.offer.validUntil ?: Long.MAX_VALUE }
                .thenByDescending { it.offer.postedAt },
        )

        SortOrder.NEWEST -> offers.sortedByDescending { it.offer.postedAt }

        SortOrder.PRICE_LOW_HIGH -> offers.sortedWith(
            compareBy<OfferUi> { it.offer.priceBdt ?: Int.MAX_VALUE }
                .thenByDescending { it.offer.postedAt },
        )

        SortOrder.RATING_HIGH_LOW -> offers.sortedWith(
            compareByDescending<OfferUi> { it.rating ?: -1.0 }
                .thenByDescending { it.offer.postedAt },
        )
    }
}
