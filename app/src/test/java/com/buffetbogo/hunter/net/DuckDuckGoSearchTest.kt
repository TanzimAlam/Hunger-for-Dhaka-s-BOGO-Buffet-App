package com.buffetbogo.hunter.net

import com.buffetbogo.hunter.ingest.OfferParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckDuckGoSearchTest {

    private val html: String =
        javaClass.classLoader!!.getResource("ddg-price-results.html")!!.readText()

    @Test
    fun `decodes redirect links and cleans snippets`() {
        val hits = DuckDuckGoSearch.parse(html)
        assertEquals(2, hits.size)
        assertEquals(
            "https://www.sarinahotel.com/product/buffet-lunch-saturday-thursday/",
            hits[0].url,
        )
        assertTrue(hits[0].snippet.contains("starting from BDT 1800 net per person"))
        // The lookup only works if the price parser can read a plain search snippet.
        assertEquals(1800, OfferParser.extractPrice(hits[0].snippet))
        assertEquals("https://www.facebook.com/HotelSarinaDhaka/videos/1139704848281255/", hits[1].url)
        assertTrue(hits[1].snippet.contains("Banani & free parking"))
    }

    @Test
    fun `empty or blocked pages return no hits`() {
        assertTrue(DuckDuckGoSearch.parse("").isEmpty())
        assertTrue(DuckDuckGoSearch.parse("<html><body>No results.</body></html>").isEmpty())
    }
}
