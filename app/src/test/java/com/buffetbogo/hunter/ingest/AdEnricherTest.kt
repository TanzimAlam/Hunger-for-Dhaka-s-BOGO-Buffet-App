package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.PriceSources
import com.buffetbogo.hunter.data.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AdEnricherTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private fun day(d: Int): Long =
        LocalDate.of(2026, 10, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun bankOffer(
        restaurant: String = "Sheraton Dhaka",
        kind: String = "BOGO_1_1",
        price: Int? = null,
    ) = Offer(
        id = "bank-$restaurant-$kind",
        title = "Buy 1 Get 1 buffet dinner",
        restaurant = restaurant,
        area = "Banani",
        offerKind = kind,
        headline = "Buy 1 Get 1",
        details = "The bank lists a buffet deal.",
        terms = "Bank terms apply.",
        paymentNote = "Standard Chartered cards",
        priceBdt = price,
        postedAt = day(1),
        validFrom = null,
        validUntil = null,
        source = Sources.BANK,
        sourceUrl = "https://av.sc.com/bd/edm/b1g1-offers/",
        imageUrl = "https://av.sc.com/bank.jpg",
        rating = null,
        ratingCount = null,
        fetchedAt = day(1),
    )

    private val post = AdPost(
        restaurant = "Sheraton Dhaka",
        url = "https://www.facebook.com/SheratonDhaka/posts/1639808158163219/",
        text = "Buy 1 Get 1 on our buffet dinner at BDT 9,950 net per person. " +
            "Valid till 30 November 2026. Dinner 6:30 pm – 10:30 pm. Prior reservation required.",
        image = "https://scontent.xx.fbcdn.net/buffet.jpg",
        postedAt = day(2),
        kind = "BOGO_1_1",
        priceBdt = 9950,
        validUntil = day(30),
        timing = "Dinner 6:30 pm – 10:30 pm",
    )

    @Test
    fun `the restaurant's own post supplies the ad link, price, date and timing`() {
        val enriched = AdEnricher.enrich(listOf(bankOffer()), listOf(post), day(26)).single()

        assertEquals(post.url, enriched.adUrl)
        assertEquals("Facebook post", enriched.adLabel)
        assertEquals(9950, enriched.priceBdt)
        assertEquals("per person", enriched.priceUnit)
        assertEquals(PriceSources.AD, enriched.priceSource)
        assertEquals(day(30), enriched.validUntil)
        assertEquals("Dinner 6:30 pm – 10:30 pm", enriched.timing)
        assertEquals(post.image, enriched.imageUrl)
        assertTrue(enriched.details.contains("9,950"))
        // Discovery provenance is kept, but the ad is the restaurant's post.
        assertEquals(Sources.BANK, enriched.source)
    }

    @Test
    fun `a post that matches a discovered offer replaces the duplicate card`() {
        val standalone = bankOffer().copy(
            id = "fb-1",
            source = Sources.FACEBOOK,
            sourceUrl = post.url,
            adUrl = post.url,
            adLabel = "Facebook post",
        )
        val enriched = AdEnricher.enrich(listOf(bankOffer(), standalone), listOf(post), day(26))

        assertEquals(1, enriched.size)
        assertEquals(Sources.BANK, enriched.single().source)
        assertEquals(post.url, enriched.single().adUrl)
    }

    @Test
    fun `a post for a different deal becomes its own offer`() {
        val eatPay = bankOffer(kind = "EAT_4_PAY_1")
        val enriched = AdEnricher.enrich(listOf(eatPay), listOf(post), day(26))

        assertEquals(1, enriched.size)
        // Same venue but a different deal: the BOGO post is not applied to the Eat-4-Pay-1 row.
        assertNull(enriched.single().adUrl)
    }

    @Test
    fun `offers without a matching post are untouched`() {
        val other = bankOffer(restaurant = "Amari Dhaka")
        assertEquals(
            other,
            AdEnricher.enrich(listOf(other), listOf(post), day(26)).single(),
        )
    }

    @Test
    fun `no posts means no rewriting`() {
        val offers = listOf(bankOffer())
        assertEquals(offers, AdEnricher.enrich(offers, emptyList(), day(26)))
    }
}
