package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OfferSorterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")

    private fun at(dayOfMonth: Int): Long =
        LocalDate.of(2026, 10, dayOfMonth).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun offer(
        name: String,
        posted: Long,
        until: Long?,
        price: Int?,
        rating: Double?,
    ): OfferUi = OfferUi(
        offer = Offer(
            id = name,
            title = "$name buffet",
            restaurant = name,
            area = "Dhaka",
            offerKind = "BOGO_1_1",
            headline = "Buy 1 Get 1",
            details = "",
            terms = "",
            paymentNote = "",
            priceBdt = price,
            postedAt = posted,
            validFrom = null,
            validUntil = until,
            source = Sources.CURATED,
            sourceUrl = "https://example.com/$name",
            imageUrl = null,
            rating = null,
            ratingCount = null,
            fetchedAt = posted,
        ),
        rating = rating,
        ratingCount = null,
    )

    private val early = offer("Early", posted = at(1), until = at(26), price = 3_000, rating = 4.2)
    private val late = offer("Late", posted = at(20), until = at(30), price = 1_500, rating = 4.7)
    private val ongoing = offer("Ongoing", posted = at(10), until = null, price = null, rating = null)

    @Test
    fun `end date is the default order`() {
        assertEquals(SortOrder.ENDS_SOONEST, SortOrder.fromKey(null))
        assertEquals(SortOrder.ENDS_SOONEST, SortOrder.fromKey("nonsense"))

        val sorted = OfferSorter.sort(listOf(ongoing, late, early), SortOrder.ENDS_SOONEST)
        assertEquals(listOf("Early", "Late", "Ongoing"), sorted.map { it.offer.restaurant })
    }

    @Test
    fun `newest order uses the post date`() {
        val sorted = OfferSorter.sort(listOf(early, ongoing, late), SortOrder.NEWEST)
        assertEquals(listOf("Late", "Ongoing", "Early"), sorted.map { it.offer.restaurant })
    }

    @Test
    fun `price order is cheapest first and keeps unlisted prices last`() {
        val sorted = OfferSorter.sort(listOf(ongoing, early, late), SortOrder.PRICE_LOW_HIGH)
        assertEquals(listOf("Late", "Early", "Ongoing"), sorted.map { it.offer.restaurant })
    }

    @Test
    fun `rating order is highest first and keeps unrated offers last`() {
        val sorted = OfferSorter.sort(listOf(ongoing, early, late), SortOrder.RATING_HIGH_LOW)
        assertEquals(listOf("Late", "Early", "Ongoing"), sorted.map { it.offer.restaurant })
    }

    @Test
    fun `every order keeps all offers`() {
        SortOrder.entries.forEach { order ->
            assertEquals(3, OfferSorter.sort(listOf(early, late, ongoing), order).size)
        }
    }

    @Test
    fun `five star filter only matches the known five star hotels`() {
        val sheraton = offer("Sheraton Dhaka", posted = at(1), until = at(26), price = null, rating = null)
        val streetFood = offer("Kacchi Bhai", posted = at(1), until = at(26), price = null, rating = null)
        assertEquals(true, sheraton.offer.isFiveStar())
        assertEquals(false, streetFood.offer.isFiveStar())
    }
}
