package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.data.VenueSocials
import com.buffetbogo.hunter.net.VenueAdFinder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class AdLinkResolverTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val now: Long =
        LocalDate.of(2026, 10, 26).atStartOfDay(zone).toInstant().toEpochMilli()

    private val directory = VenueSocials.parse(
        File("src/main/assets/venue_socials.json").readText(),
    )
    private val resolver = AdLinkResolver(directory, VenueAdFinder(""))

    private fun offer(
        restaurant: String,
        source: String,
        sourceUrl: String,
    ) = Offer(
        id = "id-$restaurant",
        title = "Buy 1 Get 1 buffet",
        restaurant = restaurant,
        area = "Dhaka",
        offerKind = "BOGO_1_1",
        headline = "Buy 1 Get 1",
        details = "",
        terms = "",
        paymentNote = "",
        priceBdt = 5_000,
        postedAt = now,
        validFrom = null,
        validUntil = null,
        source = source,
        sourceUrl = sourceUrl,
        imageUrl = null,
        rating = null,
        ratingCount = null,
        fetchedAt = now,
    )

    @Test
    fun `a bank listing links to the restaurant's page, never the bank`() = runBlocking {
        val resolved = resolver.resolve(
            listOf(offer("Sheraton Dhaka", Sources.BANK, "https://av.sc.com/bd/edm/b1g1-offers/")),
            now,
            maxSearches = 0,
        ).single()

        assertEquals("https://www.facebook.com/SheratonDhaka/", resolved.adUrl)
        assertEquals("Facebook page", resolved.adLabel)
        assertFalse(resolved.adUrl!!.contains("sc.com"))
    }

    @Test
    fun `a curated ad post is kept as the ad`() = runBlocking {
        val post = "https://www.facebook.com/AmariDhaka/posts/1447875447366872/"
        val resolved = resolver.resolve(
            listOf(offer("Amari Dhaka", Sources.CURATED, post)),
            now,
            maxSearches = 0,
        ).single()

        assertEquals(post, resolved.adUrl)
        assertEquals("Facebook post", resolved.adLabel)
    }

    @Test
    fun `a restaurant website is used when it has no facebook page`() = runBlocking {
        val resolved = resolver.resolve(
            listOf(offer("Platinum Grand", Sources.BANK, "https://av.sc.com/bd/edm/b1g1-offers/")),
            now,
            maxSearches = 0,
        ).single()

        assertEquals("https://platinumhotels.com.bd/restaurant/terrabistro-banani", resolved.adUrl)
        assertEquals("Restaurant website", resolved.adLabel)
    }

    @Test
    fun `a bank spelling is normalised to the venue's own name`() = runBlocking {
        val resolved = resolver.resolve(
            listOf(
                offer(
                    "Pan Pacific Sonargaon Dhaka",
                    Sources.BANK,
                    "https://av.sc.com/bd/edm/b1g1-offers/",
                ),
            ),
            now,
            maxSearches = 0,
        ).single()

        assertEquals("Pan Pacific Sonargaon", resolved.restaurant)
        assertEquals("https://www.facebook.com/PanPacificSonargaonDhaka/", resolved.adUrl)
    }

    @Test
    fun `an unknown venue keeps no ad link so the UI can offer a search`() = runBlocking {
        val resolved = resolver.resolve(
            listOf(offer("Some New Dhaka Buffet", Sources.BANK, "https://av.sc.com/x")),
            now,
            maxSearches = 0,
        ).single()

        assertNull(resolved.adUrl)
    }

    @Test
    fun `a post link already known is not searched again`() = runBlocking {
        val post = "https://www.facebook.com/SheratonDhaka/posts/1639808158163219/"
        val known = offer("Sheraton Dhaka", Sources.BANK, "https://av.sc.com/x")
            .copy(adUrl = post, adLabel = "Facebook post", adCheckedAt = now)
        val resolved = resolver.resolve(listOf(known), now, maxSearches = 4).single()
        assertEquals(post, resolved.adUrl)
    }
}
