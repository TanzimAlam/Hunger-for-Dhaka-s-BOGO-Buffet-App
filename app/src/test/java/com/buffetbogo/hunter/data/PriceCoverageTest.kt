package com.buffetbogo.hunter.data

import com.buffetbogo.hunter.ingest.StandardCharteredSource
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * The bank page states deals but no prices. This test proves the bundled price book can fill
 * most of those cards, so the feed does not silently lose the five-star offers.
 */
class PriceCoverageTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val now: Long = LocalDate.of(2026, 10, 24)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private val book = VenuePriceBook.parse(
        File("src/main/assets/venue_prices.json").readText(),
    )

    private val offers = StandardCharteredSource.parsePage(
        javaClass.classLoader!!.getResource("sc-offers-page.html")!!.readText(),
        now,
        zone,
    )

    @Test
    fun `the book can price nearly every bank listing`() {
        val priced = offers.filter { offer ->
            offer.priceBdt != null ||
                VenuePriceBook.find(
                    book,
                    offer.restaurant,
                    "${offer.title} ${offer.headline} ${offer.details}",
                ) != null
        }
        val missing = offers.filterNot { it in priced }.map { it.restaurant }
        println("priced ${priced.size}/${offers.size}; missing=$missing")

        assertTrue("the parsed page should not be empty", offers.isNotEmpty())
        assertTrue(
            "unpriced bank venues: $missing",
            priced.size >= offers.size - 2,
        )
    }
}
