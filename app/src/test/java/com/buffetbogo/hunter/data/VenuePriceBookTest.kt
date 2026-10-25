package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VenuePriceBookTest {

    private val raw: String = File("src/main/assets/venue_prices.json").readText()
    private val book = VenuePriceBook.parse(raw)

    @Test
    fun `bundled price book parses and covers the catalogue venues`() {
        assertTrue(book.isNotEmpty())
        assertTrue(book.containsKey(nameKey("Hotel Sarina Dhaka")))
        assertTrue(book.containsKey(nameKey("Amari Dhaka")))
    }

    @Test
    fun `dinner is used unless the offer is a lunch one`() {
        val dinner = VenuePriceBook.find(book, "Hotel Sarina Dhaka", "Buffet dinner Buy 1 Get 3")
        assertEquals(2100, dinner?.amount)
        assertEquals(PriceSources.TYPICAL, dinner?.source)

        val lunch = VenuePriceBook.find(book, "Hotel Sarina Dhaka", "buffet lunch offer")
        assertEquals(1800, lunch?.amount)
    }

    @Test
    fun `accents and aliases match the same venue`() {
        val byAlias = VenuePriceBook.find(book, "Sarina", "dinner buffet")
        assertEquals(2100, byAlias?.amount)

        val accented = VenuePriceBook.parse(
            """[{"restaurant":"Le Méridien Dhaka","lunch":4200,"dinner":5200,"unit":"per person"}]""",
        )
        assertEquals(5200, VenuePriceBook.find(accented, "Le Meridien Dhaka", "dinner")?.amount)
    }

    @Test
    fun `breakfast offers use a breakfast rate or nothing at all`() {
        val platinum = VenuePriceBook.find(book, "Platinum Grand", "Dine 2 Pay 1 on buffet breakfast")
        assertEquals(1399, platinum?.amount)
        assertTrue(platinum!!.unit.contains("breakfast"))

        // Sheraton publishes no breakfast rate; a lunch/dinner figure must not stand in for it.
        assertNull(VenuePriceBook.find(book, "Sheraton Dhaka", "buffet breakfast"))
    }

    @Test
    fun `unknown venues return nothing rather than a guess`() {
        assertNull(VenuePriceBook.find(book, "Some New Cafe", "buffet dinner"))
    }
}
