package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AdLinkStateTest {

    private fun offer(url: String?, label: String?): Offer = Offer(
        id = url ?: "none",
        title = "Buy 1 Get 1 buffet",
        restaurant = "Test Kitchen",
        area = "Dhaka",
        offerKind = "BOGO_1_1",
        headline = "Buy 1 Get 1",
        details = "",
        terms = "",
        paymentNote = "Any card",
        priceBdt = 2_000,
        postedAt = 0L,
        validFrom = null,
        validUntil = null,
        source = Sources.BANK,
        sourceUrl = "https://example.com/bank-page",
        imageUrl = null,
        rating = null,
        ratingCount = null,
        fetchedAt = 0L,
        adUrl = url,
        adLabel = label,
    )

    @Test
    fun `no link is admitted rather than dressed up as the ad`() {
        assertEquals(AdLinkState.NONE, offer(null, null).adLinkState())
        assertEquals(AdLinkState.NONE, offer("", "Facebook page").adLinkState())
    }

    @Test
    fun `a venue's facebook page is only a generic page`() {
        val page = offer("https://www.facebook.com/TBASS.BD/", AdKind.FACEBOOK_PAGE.label)
        assertEquals(AdLinkState.GENERIC_PAGE, page.adLinkState())
    }

    @Test
    fun `an exact facebook post is a direct link`() {
        val post = offer(
            "https://www.facebook.com/AmariDhaka/posts/buffet/1447875447366872/",
            AdKind.FACEBOOK_POST.label,
        )
        assertEquals(AdLinkState.DIRECT, post.adLinkState())
    }

    @Test
    fun `the restaurant's own dining page counts as direct`() {
        val site = offer("https://www.sarinahotel.com/product/buffet-dinner-b1g3/", AdKind.WEBSITE.label)
        assertEquals(AdLinkState.DIRECT, site.adLinkState())
    }
}
