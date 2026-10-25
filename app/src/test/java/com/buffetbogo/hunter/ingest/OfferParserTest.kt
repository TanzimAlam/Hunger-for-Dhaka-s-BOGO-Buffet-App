package com.buffetbogo.hunter.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class OfferParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val now: Long = LocalDate.of(2026, 10, 19)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private fun endOfDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

    private fun startOfDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `parses buy one get one with hotel name`() {
        val text = "Buy 1 Get 1 Free buffet every Friday at Le Méridien Dhaka"
        val parsed = OfferParser.parse(text, now, zone)
        assertNotNull(parsed)
        assertEquals("BOGO_1_1", parsed!!.kind)
        assertEquals("Buy 1 Get 1", parsed.headline)
        assertEquals("Le Méridien Dhaka", OfferParser.guessRestaurant(text))
        assertTrue(OfferParser.isBuffetOffer(text))
    }

    @Test
    fun `parses eat four pay one`() {
        val parsed = OfferParser.parse("Eat 4 Pay 1 on the dinner buffet — Sheraton Dhaka", now, zone)
        assertEquals("EAT_4_PAY_1", parsed?.kind)
        assertEquals("Eat 4 Pay 1", parsed?.headline)
    }

    @Test
    fun `parses spelled out numbers`() {
        val parsed = OfferParser.parse("Buy one get two on the Sunday brunch buffet", now, zone)
        assertEquals("BOGO_1_2", parsed?.kind)
    }

    @Test
    fun `parses 2 plus 1 style`() {
        val parsed = OfferParser.parse("2+1 buffet offer this weekend", now, zone)
        assertEquals("BOGO_2_1", parsed?.kind)
    }

    @Test
    fun `rejects text without a deal`() {
        assertNull(OfferParser.parse("New a la carte menu launched at our restaurant", now, zone))
        assertFalse(OfferParser.isBuffetOffer("New a la carte menu launched at our restaurant"))
    }

    @Test
    fun `extracts expiry with explicit year`() {
        val parsed = OfferParser.parse("Buy 1 Get 1 buffet. Valid till 30 November 2026", now, zone)
        assertEquals(endOfDay(2026, 11, 30), parsed?.validUntil)
    }

    @Test
    fun `rolls missing year into next year`() {
        val parsed = OfferParser.parse("Buy 1 Get 1 buffet until 5 January", now, zone)
        assertEquals(endOfDay(2027, 1, 5), parsed?.validUntil)
    }

    @Test
    fun `extracts a date range`() {
        val parsed = OfferParser.parse(
            "Eat 4 Pay 1 buffet from 1 November 2026 to 15 November 2026",
            now,
            zone,
        )
        assertEquals(startOfDay(2026, 11, 1), parsed?.validFrom)
        assertEquals(endOfDay(2026, 11, 15), parsed?.validUntil)
    }

    @Test
    fun `expired single date is still reported so the list can hide it`() {
        val parsed = OfferParser.parse("Buy 1 Get 1 buffet, offer ends 1 October 2026", now, zone)
        assertTrue(parsed!!.validUntil!! < now)
    }

    @Test
    fun `extracts price in taka`() {
        assertEquals(4500, OfferParser.extractPrice("Buffet at BDT 4,500 per person"))
        assertEquals(3200, OfferParser.extractPrice("Only Tk. 3200 for two"))
        assertNull(OfferParser.extractPrice("No price mentioned"))
    }

    @Test
    fun `reads the price formats the venues actually use`() {
        assertEquals(8950, OfferParser.extractPrice("Dinner is BDT 8,950 net per person"))
        assertEquals(1250, OfferParser.extractPrice("Gulshan lunch ৳1,250, regular ৳1,450"))
        assertEquals(2519, OfferParser.extractPrice("Unlimited steak buffet BDT 2,519"))
        assertEquals(1750, OfferParser.extractPrice("Everyday buffet lunch 1750 taka net"))
        assertEquals(5555, OfferParser.extractPrice("Grandiose dinner BDT 5555 NET"))
    }

    @Test
    fun `does not mistake phone numbers or years for prices`() {
        assertNull(OfferParser.extractPrice("Call 01712345678 to reserve a table"))
        assertNull(OfferParser.extractPrice("The festival runs in October 2026"))
    }

    @Test
    fun `a minimum spend is not the buffet price`() {
        assertNull(
            OfferParser.extractPrice("Minimum spend BDT 5,000 to avail the buffet offer"),
        )
        assertEquals(
            1250,
            OfferParser.extractPrice("Lunch discount: BDT 1,250 (regular 1,450)"),
        )
    }

    @Test
    fun `detects card conditions`() {
        val note = OfferParser.paymentNote("Offer valid with Visa Signature and City Bank credit cards only")
        assertTrue(note.contains("Visa Signature"))
        assertTrue(note.contains("City Bank"))
    }

    @Test
    fun `detects mobile wallet conditions`() {
        assertEquals("bKash", OfferParser.paymentNote("Pay with bKash at the counter"))
    }

    @Test
    fun `defaults when no condition is mentioned`() {
        assertEquals(
            "No card or payment condition mentioned",
            OfferParser.paymentNote("Walk in and enjoy the buffet"),
        )
    }

    @Test
    fun `guesses dhaka area`() {
        assertEquals("Gulshan", OfferParser.guessArea("Weekend buffet in Gulshan 2"))
        assertEquals("Dhaka", OfferParser.guessArea("Weekend buffet somewhere"))
    }

    @Test
    fun `cleans html and entities`() {
        assertEquals(
            "Buy 1 Get 1 & free dessert",
            OfferParser.cleanText("<b>Buy 1 Get 1</b> &amp; free dessert"),
        )
    }

    @Test
    fun `parses dine pay wording used by bank card offers`() {
        val parsed = OfferParser.detectKind("Dine 4 Pay 1 on buffet dinner")
        assertNotNull(parsed)
        assertEquals("EAT_4_PAY_1", parsed!!.first)
        assertEquals("Dine 4 Pay 1", parsed.second)

        val comma = OfferParser.detectKind("Dine 3, Pay 1 on buffet brunch")
        assertEquals("EAT_3_PAY_1", comma!!.first)

        val eat = OfferParser.detectKind("Eat 5 Pay 1 dinner")
        assertEquals("Eat 5 Pay 1", eat!!.second)
    }

    @Test
    fun `recognises the banks that publish buffet campaigns`() {
        assertTrue(
            OfferParser.paymentNote("Midland Bank credit cardholders to get a free buffet")
                .startsWith("Midland Bank"),
        )
        assertTrue(
            OfferParser.paymentNote("For Shimanto Bank Platinum and Gold credit cards")
                .startsWith("Shimanto Bank"),
        )
    }

    @Test
    fun `understands the b1g shorthand used by banks`() {
        val oneOne = OfferParser.detectKind("B1G1 via bKash payment at five-star iftar buffets")
        assertEquals("BOGO_1_1", oneOne!!.first)
        assertEquals("BOGO_1_3", OfferParser.detectKind("B1G3 buffet dinner")!!.first)
        assertEquals("BOGO_1_2", OfferParser.detectKind("Our B1G2 weekend brunch")!!.first)
    }

    @Test
    fun `understands pay first wording`() {
        val parsed = OfferParser.detectKind("Pay 1 Eat 3 buffet at Renaissance Dhaka")
        assertEquals("EAT_3_PAY_1", parsed!!.first)
        assertEquals("Pay 1 Eat 3", parsed.second)
    }

    @Test
    fun `visa tier replaces the generic visa label`() {
        assertEquals(
            "Visa Signature · Credit card",
            OfferParser.paymentNote("Applicable for VISA Signature and Platinum credit cards"),
        )
    }
}
