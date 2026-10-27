package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The home feed shows one card per restaurant, with every deal inside it. */
class RestaurantFeedTest {

    private fun offer(
        restaurant: String,
        kind: String,
        headline: String,
        price: Int? = 5000,
        posted: Long = 1_000,
        until: Long? = null,
        rating: Double? = null,
    ) = OfferUi(
        offer = Offer(
            id = "$restaurant-$kind",
            title = "$headline at $restaurant",
            restaurant = restaurant,
            area = "Gulshan, Dhaka",
            offerKind = kind,
            headline = headline,
            details = "",
            terms = "",
            paymentNote = "Any payment method",
            priceBdt = price,
            postedAt = posted,
            validFrom = null,
            validUntil = until,
            source = Sources.BANK,
            sourceUrl = "https://example.com",
            imageUrl = null,
            rating = rating,
            ratingCount = null,
            fetchedAt = 1_000,
        ),
        rating = rating,
        ratingCount = null,
    )

    @Test
    fun `one card per restaurant with every deal kept`() {
        val groups = FeedGrouper.group(
            listOf(
                offer("Amari Dhaka", "BOGO_1_1", "Buy 1 Get 1"),
                offer("Amari Dhaka", "EAT_3_PAY_1", "Eat 3 Pay 1"),
                offer("Sheraton Dhaka", "BOGO_1_2", "Buy 1 Get 2"),
            ),
            SortOrder.ENDS_SOONEST,
        )

        assertEquals(2, groups.size)
        val amari = groups.first { it.restaurant == "Amari Dhaka" }
        assertEquals(2, amari.offerCount)
        assertEquals(listOf("B1G1", "Eat 3 Pay 1"), amari.dealLabels)
    }

    @Test
    fun `the same venue spelled differently is one card`() {
        val groups = FeedGrouper.group(
            listOf(
                offer("Le Meridien Dhaka", "BOGO_1_1", "Buy 1 Get 1"),
                offer("le méridien dhaka", "BOGO_1_3", "Buy 1 Get 3"),
            ),
            SortOrder.NEWEST,
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().offerCount)
    }

    @Test
    fun `the default order puts the soonest ending restaurant first`() {
        val groups = FeedGrouper.group(
            listOf(
                offer("Sheraton Dhaka", "BOGO_1_1", "Buy 1 Get 1", until = 9_000),
                offer("Amari Dhaka", "BOGO_1_1", "Buy 1 Get 1", until = 3_000),
                offer("Six Seasons Hotel", "BOGO_1_3", "Buy 1 Get 3", until = null),
            ),
            SortOrder.ENDS_SOONEST,
        )
        assertEquals(
            listOf("Amari Dhaka", "Sheraton Dhaka", "Six Seasons Hotel"),
            groups.map { it.restaurant },
        )
    }

    @Test
    fun `price and rating orders use the restaurant's best deal`() {
        val offers = listOf(
            offer("Sheraton Dhaka", "BOGO_1_1", "Buy 1 Get 1", price = 8_000, rating = 4.2),
            offer("Amari Dhaka", "EAT_3_PAY_1", "Eat 3 Pay 1", price = 8_500, rating = 4.8),
            offer("Amari Dhaka", "BOGO_1_1", "Buy 1 Get 1", price = 4_000, rating = 4.8),
        )

        val byPrice = FeedGrouper.group(offers, SortOrder.PRICE_LOW_HIGH)
        assertEquals(listOf("Amari Dhaka", "Sheraton Dhaka"), byPrice.map { it.restaurant })
        assertEquals(4_000, byPrice.first().lowestPrice)

        val byRating = FeedGrouper.group(offers, SortOrder.RATING_HIGH_LOW)
        assertEquals(listOf("Amari Dhaka", "Sheraton Dhaka"), byRating.map { it.restaurant })
    }

    @Test
    fun `newest order uses the restaurant's latest deal`() {
        val groups = FeedGrouper.group(
            listOf(
                offer("Sheraton Dhaka", "BOGO_1_1", "Buy 1 Get 1", posted = 5_000),
                offer("Amari Dhaka", "EAT_5_PAY_1", "Eat 5 Pay 1", posted = 2_000),
                offer("Amari Dhaka", "BOGO_1_1", "Buy 1 Get 1", posted = 9_000),
            ),
            SortOrder.NEWEST,
        )
        assertEquals("Amari Dhaka", groups.first().restaurant)
        assertEquals(9_000, groups.first().newest)
    }

    @Test
    fun `a restaurant with no end date reports none`() {
        val group = FeedGrouper.group(
            listOf(offer("Six Seasons Hotel", "BUFFET", "Daily buffet", until = null)),
            SortOrder.ENDS_SOONEST,
        ).single()
        assertNull(group.soonestEnd)
    }

    @Test
    fun `badges are short enough for a thumbnail`() {
        assertEquals("B1G3", offer("A", "BOGO_1_3", "Buy 1 Get 3").offer.shortDealLabel())
        assertEquals("Eat 4 Pay 1", offer("A", "EAT_4_PAY_1", "Eat 4 Pay 1").offer.shortDealLabel())
        assertEquals("25% off", offer("A", "PCT_25", "25% off").offer.shortDealLabel())
        assertTrue(offer("A", "BUFFET", "Daily buffet").offer.shortDealLabel().isNotBlank())
    }
}
