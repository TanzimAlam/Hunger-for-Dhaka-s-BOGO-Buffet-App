package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Guards the bundled catalogue: real links, real dates, no duplicates, nothing already dead. */
class SeedLoaderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val now: Long = LocalDate.of(2026, 10, 25)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private val json: String = File("src/main/assets/seed_offers.json").readText()

    private val offers: List<Offer> by lazy { SeedLoader.parse(json, now, zone) }

    @Test
    fun `every bundled offer is a real sourced listing`() {
        assertTrue(json.isNotBlank())
        assertTrue("catalogue should not be empty", offers.size >= 5)
        offers.forEach { offer ->
            assertTrue("${offer.restaurant} needs a link", offer.sourceUrl.startsWith("https://"))
            assertTrue("${offer.restaurant} needs a real title", offer.title.length > 10)
            assertEquals(Sources.CURATED, offer.source)
            assertTrue("${offer.restaurant} needs a payment note", offer.paymentNote.isNotBlank())
        }
    }

    @Test
    fun `every bundled offer carries a price`() {
        offers.forEach { offer ->
            assertTrue("${offer.restaurant} has no price", offer.priceBdt != null)
            assertTrue("${offer.restaurant} has an implausible price", offer.priceBdt!! > 0)
            assertTrue("${offer.restaurant} has no price unit", !offer.priceUnit.isNullOrBlank())
            assertEquals(PriceSources.AD, offer.priceSource)
        }
    }

    @Test
    fun `ids are unique`() {
        val ids = offers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `validity dates are absolute and still open`() {
        offers.forEach { offer ->
            if (offer.validUntil != null) {
                assertTrue(
                    "${offer.restaurant} ended already",
                    offer.validUntil!! >= now,
                )
            }
            assertTrue("${offer.restaurant} is posted in the future", offer.postedAt <= now)
        }
    }

    @Test
    fun `parsing is deterministic`() {
        val again = SeedLoader.parse(json, now, zone)
        assertEquals(offers.map { it.id }, again.map { it.id })
        assertEquals(offers.map { it.validUntil }, again.map { it.validUntil })
    }

    @Test
    fun `bundled offers are live as of their check date`() {
        val live = offers.filter { it.isLive(now) }
        assertEquals(offers.size, live.size)
    }

    @Test
    fun `prices are stated for the offers whose ads quote one`() {
        val priced = offers.filter { it.priceBdt != null }
        assertTrue("most of the catalogue should carry a price", priced.size >= 4)
        priced.forEach { offer ->
            assertTrue("${offer.restaurant} has an implausible price", offer.priceBdt!! in 100..50_000)
        }
        assertEquals(
            3999,
            offers.single { it.restaurant == "Amari Dhaka" && it.offerKind == "EAT_3_PAY_1" }.priceBdt,
        )
    }
}
