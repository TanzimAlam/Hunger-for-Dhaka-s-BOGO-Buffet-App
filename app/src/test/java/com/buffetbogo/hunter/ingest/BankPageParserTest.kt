package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.VenueSocials
import com.buffetbogo.hunter.data.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Snapshot tests over the real bank dining pages (trimmed to the venue list, fetched
 * 27 October 2026). They fail loudly when a bank reshapes its page, and they pin the rule that
 * only restaurant-side data becomes an offer.
 */
class BankPageParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")

    // A date inside the campaign windows printed on the pages, so expired offers are not the
    // reason a venue goes missing here.
    private val now: Long = LocalDate.of(2026, 10, 20)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private val directory = VenueSocials.parse(
        File("src/main/assets/venue_socials.json").readText(),
    )

    private fun parse(fixture: String, bank: String): List<com.buffetbogo.hunter.data.Offer> =
        BankPageParser.parse(
            javaClass.classLoader!!.getResource(fixture)!!.readText(),
            BankPage(bank = bank, shortName = bank, url = "https://example.com/$fixture"),
            now,
            zone,
            directory,
        )

    private fun dump(label: String, offers: List<com.buffetbogo.hunter.data.Offer>) {
        println("=== $label (${offers.size})")
        offers.forEach { offer ->
            val until = offer.validUntil?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString()
            }
            println(
                listOf(
                    offer.restaurant,
                    offer.headline,
                    offer.area,
                    offer.priceBdt ?: "-",
                    "until=$until",
                    offer.paymentNote,
                ).joinToString(" | "),
            )
        }
    }

    @Test
    fun `mutual trust bank table`() {
        val offers = parse("bank-mtb.html", "Mutual Trust Bank")
        dump("MTB", offers)
        assertTrue("too few venues: ${offers.size}", offers.size >= 10)
        val names = offers.map { it.restaurant }
        assertTrue("missing Amari Dhaka: $names", "Amari Dhaka" in names)
        assertTrue("missing Six Seasons Hotel: $names", "Six Seasons Hotel" in names)
        assertTrue(offers.any { it.restaurant == "Amari Dhaka" && it.headline == "Buy 1 Get 1" })
        assertTrue(offers.all { it.source == Sources.BANK })
        // A venue may carry a number in its name (Grace 21), but never be one.
        assertTrue(offers.none { offer -> offer.restaurant.firstOrNull()?.isDigit() == true })
    }

    @Test
    fun `prime bank table`() {
        val offers = parse("bank-prime.html", "Prime Bank")
        dump("Prime", offers)
        assertTrue("too few venues: ${offers.size}", offers.size >= 8)
        val names = offers.map { it.restaurant }
        listOf("Sheraton Dhaka", "InterContinental Dhaka", "Six Seasons Hotel").forEach {
            assertTrue("missing $it in $names", it in names)
        }
        assertTrue(offers.any { it.paymentNote.contains("Prime Bank") })
    }

    @Test
    fun `trust bank buffet partners`() {
        val offers = parse("bank-trust.html", "Trust Bank")
        dump("Trust", offers)
        assertTrue("no Buffet B1G1 found", offers.isNotEmpty())
        assertTrue(offers.any { it.restaurant == "Amari Dhaka" })
        assertTrue(offers.any { it.headline.startsWith("Buy 1 Get") })
        assertTrue(offers.none { it.restaurant.contains("hospital", ignoreCase = true) })
    }

    @Test
    fun `shimanto bank buffet page`() {
        val offers = parse("bank-shimanto.html", "Shimanto Bank")
        dump("Shimanto", offers)
        assertTrue("too few venues: ${offers.size}", offers.size >= 5)
        val names = offers.map { it.restaurant }
        assertTrue("missing The Westin Dhaka in $names", "The Westin Dhaka" in names)
        assertTrue(offers.any { it.priceBdt != null })
        // The page prints campaign windows on some cards and not others.
        assertTrue("no end dates read at all", offers.count { it.validUntil != null } >= 2)
    }

    @Test
    fun `ncc bank BOGO partners`() {
        val offers = parse("bank-ncc.html", "NCC Bank")
        dump("NCC", offers)
        assertTrue("too few venues: ${offers.size}", offers.size >= 6)
        val names = offers.map { it.restaurant }
        assertTrue("missing Amari Dhaka in $names", "Amari Dhaka" in names)
        assertTrue(offers.none { it.restaurant.contains("Chattogram", ignoreCase = true) })
    }

    @Test
    fun `city bank dining page keeps only buffet entries`() {
        val offers = parse("bank-citybank.html", "City Bank")
        dump("City Bank", offers)
        assertTrue("no buffet entry found", offers.isNotEmpty())
        assertTrue(offers.all { it.details.contains("buffet", ignoreCase = true) })
        // The page also advertises "10% off all food except buffet" — that must not count.
        assertFalse(offers.any { it.headline.contains("10%") })
        assertEquals(1, offers.map { it.restaurant }.distinct().size)
    }

    @Test
    fun `a bank spelling becomes the hotel's own name`() {
        val offers = parse("bank-ncc.html", "NCC Bank")
        val names = offers.map { it.restaurant }
        assertTrue(names.contains("Hyatt Place Dhaka Uttara") || names.contains("Crowne Plaza Dhaka Airport"))
        assertTrue(offers.none { it.restaurant == "HYATT PLACE" })
        assertTrue(offers.none { it.restaurant == "CROWNE PLAZA AIRPORT" })
    }
}
