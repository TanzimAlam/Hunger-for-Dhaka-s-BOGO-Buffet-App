package com.buffetbogo.hunter.ingest

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Snapshot test: `sc-offers-page.html` is the real page as fetched on 24 October 2026.
 * It fails loudly if the bank ever changes the markup the parser depends on.
 */
class StandardCharteredLiveMarkupTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val now: Long = LocalDate.of(2026, 10, 24)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private val html: String =
        javaClass.classLoader!!.getResource("sc-offers-page.html")!!.readText()

    @Test
    fun `parses the real page`() {
        val offers = StandardCharteredSource.parsePage(html, now, zone)
        offers.forEach { offer ->
            val until = offer.validUntil?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString()
            }
            println(
                listOf(
                    offer.restaurant,
                    offer.headline,
                    offer.area,
                    offer.paymentNote,
                    "until=$until",
                    offer.imageUrl,
                ).joinToString(" | "),
            )
        }

        assertTrue("expected the whole buffet list, got ${offers.size}", offers.size >= 12)
        val names = offers.map { it.restaurant }
        listOf("Six Seasons Hotel", "Amari Dhaka", "Le Meridien Dhaka", "Sheraton Dhaka")
            .forEach { expected -> assertTrue("missing $expected in $names", expected in names) }
        assertTrue(offers.all { it.imageUrl?.startsWith("https://av.sc.com/") == true })
        assertTrue(offers.all { it.headline.isNotBlank() })
        // The bank page also lists Chattogram hotels; the app is Dhaka-only.
        assertTrue(offers.none { it.restaurant.contains("Chattogram", ignoreCase = true) })
        assertTrue(offers.none { it.restaurant.contains("Sylhet", ignoreCase = true) })
    }
}
