package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class NewsRssSourceTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val now: Long = LocalDate.of(2026, 10, 24)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private fun item(title: String, pubDate: String, link: String, source: String = "The Daily Star") = """
        <item>
          <title>$title</title>
          <link>$link</link>
          <pubDate>$pubDate</pubDate>
          <description>&lt;a href="$link"&gt;$title&lt;/a&gt;</description>
          <source url="https://example.com">$source</source>
        </item>
    """.trimIndent()

    private fun feed(vararg items: String) =
        """<?xml version="1.0"?><rss version="2.0"><channel><title>news</title>${items.joinToString("")}</channel></rss>"""

    @Test
    fun `keeps a dated buffet offer article`() {
        val xml = feed(
            item(
                "Midland Bank credit cardholders to get 'Buy 1 Get 3 Free' buffet at Dhaka Regency",
                "Mon, 12 Oct 2026 08:25:20 GMT",
                "https://news.google.com/rss/articles/abc",
            ),
            item(
                "Something about cricket",
                "Mon, 12 Oct 2026 08:25:20 GMT",
                "https://news.google.com/rss/articles/def",
            ),
        )

        val offers = NewsRssSource.parseFeed(xml, now)
        assertEquals(1, offers.size)
        val offer = offers.single()
        assertEquals("BOGO_1_3", offer.offerKind)
        assertEquals("Buy 1 Get 3", offer.headline)
        assertEquals("Dhaka Regency Hotel", offer.restaurant)
        assertEquals(Sources.NEWS, offer.source)
        assertEquals("https://news.google.com/rss/articles/abc", offer.sourceUrl)
        assertTrue(offer.title.startsWith("Midland Bank"))
        // The feed carries no end date, so the 21-day window is applied and explained.
        assertEquals(offer.postedAt + TimeUnit.DAYS.toMillis(21), offer.validUntil)
        assertTrue(offer.details.contains("21 days"))
    }

    @Test
    fun `strips the publisher suffix from the headline`() {
        val xml = feed(
            item(
                "Hotel Sarina announces Eat 4 Pay 1 buffet dinner - The Business Standard",
                "Mon, 12 Oct 2026 08:25:20 GMT",
                "https://news.google.com/rss/articles/ghi",
            ),
        )
        val offer = NewsRssSource.parseFeed(xml, now).single()
        assertEquals("Hotel Sarina announces Eat 4 Pay 1 buffet dinner", offer.title)
    }

    @Test
    fun `ignores items without an offer or outside the freshness window`() {
        val xml = feed(
            item("Buffet prices rise in Dhaka", "Mon, 12 Oct 2026 08:25:20 GMT", "https://a/1"),
            item(
                "Old campaign: Buy 1 Get 1 buffet at Le Meridien Dhaka",
                "Mon, 01 Jan 2024 08:25:20 GMT",
                "https://a/2",
            ),
            item(
                "Buy 1 Get 1 buffet at Pan Pacific Sonargaon",
                "Mon, 26 Oct 2026 08:25:20 GMT",
                "https://a/3",
            ),
        )
        val offers = NewsRssSource.parseFeed(xml, now)
        assertTrue(offers.isEmpty())
    }

    @Test
    fun `ids stay stable for the same article`() {
        val xml = feed(
            item(
                "Buy 1 Get 2 free buffet dinner at Hyatt Place Dhaka Uttara",
                "Mon, 12 Oct 2026 08:25:20 GMT",
                "https://news.google.com/rss/articles/stable",
            ),
        )
        assertEquals(
            NewsRssSource.parseFeed(xml, now).single().id,
            NewsRssSource.parseFeed(xml, now).single().id,
        )
    }
}
