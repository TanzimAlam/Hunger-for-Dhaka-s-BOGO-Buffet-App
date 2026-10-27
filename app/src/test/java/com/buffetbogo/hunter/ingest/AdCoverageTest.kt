package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.VenueSocials
import com.buffetbogo.hunter.net.VenueAdFinder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Runs the real saved bank page through the parser and the venue directory, so a card can never
 * end up linking back to the bank because a venue was missing from the directory.
 */
class AdCoverageTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val now: Long = LocalDate.of(2026, 10, 26)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private val offers = StandardCharteredSource.parsePage(
        javaClass.classLoader!!.getResource("sc-offers-page.html")!!.readText(),
        now,
        zone,
    )

    private val resolver = AdLinkResolver(
        VenueSocials.parse(File("src/main/assets/venue_socials.json").readText()),
        VenueAdFinder(""),
    )

    @Test
    fun `no bank link survives as an ad`() {
        val linked = offers.map { resolver.withDirectoryLink(it, now) }
        linked.forEach { offer ->
            val url = offer.adUrl
            if (url != null) {
                assertFalse("${offer.restaurant} still links to the bank: $url", url.contains("sc.com"))
            }
        }
    }

    @Test
    fun `nearly every bank venue has its own page in the directory`() {
        val linked = offers.map { resolver.withDirectoryLink(it, now) }
        val missing = linked.filter { it.adUrl == null }.map { it.restaurant }
        println("bank venues without a directory link: $missing")
        assertTrue("no offers were parsed at all", offers.isNotEmpty())
        assertTrue(
            "too many venues have no ad link: $missing",
            missing.size <= 2,
        )
    }
}
