package com.buffetbogo.hunter.ingest

import com.buffetbogo.hunter.data.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class StandardCharteredSourceTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val now: Long = LocalDate.of(2026, 10, 24)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private fun endOfDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

    // Trimmed from https://av.sc.com/bd/edm/b1g1-offers/ ; structure is what matters here.
    private val page = """
        <section class=offer-section id=local-section>
        <h2 class=section-heading>Local Hotels</h2>
        <div class=box-container>
        <div class=box>
        <div class=image>
        <img src="images/LE1.jpg" class=main-img alt="Six Seasons Hotel">
        </div>
        <div class=content>
        <h3>Six Seasons Hotel</h3>
        <p class="offer-info offer-featured"><span class=offer-highlight>Dine 4 Pay 1</span> on buffet dinner</p>
        <p class=offer-meta><strong>Valid till:</strong> 31-Dec-27</p>
        <div class=offer-contact>01987009810</div>
        <div class=offer-cards>• Offer applicable on all Standard Chartered Bank debit and credit cards<br>• Prior reservation is required</div>
        </div>
        </div>
        <div class=box>
        <div class=image>
        <img src="images/E1.jpg" class=main-img alt="Amari Dhaka">
        </div>
        <div class=content>
        <h3>Amari Dhaka</h3>
        <p class="offer-info offer-featured"><span class=offer-highlight>Dine 5 Pay 1</span> dinner - other days of the week</p>
        <p class="offer-info"><span class=offer-highlight>Dine 3 Pay 1</span> on buffet breakfast, brunch and lunch</p>
        <p class=offer-meta><strong>Address:</strong> House 47, Road 41, Gulshan, Dhaka 1212</p>
        <p class=offer-meta><strong>Valid till:</strong> 31-Aug-27</p>
        <div class=offer-contact>01777796444</div>
        <div class=offer-cards>• Dine up to 4, Pay 1 offer is applicable for VISA Signature and Platinum credit cards<br>• Prior reservation is required</div>
        </div>
        </div>
        <div class=box>
        <div class=image>
        <img src="images/X1.jpg" class=main-img alt="Expired Venue">
        </div>
        <div class=content>
        <h3>Expired Venue</h3>
        <p class="offer-info offer-featured"><span class=offer-highlight>Dine 2 Pay 1</span> on buffet dinner</p>
        <p class=offer-meta><strong>Valid till:</strong> 1-Jan-26</p>
        </div>
        </div>
        <div class=box>
        <div class=image>
        <img src="images/C1.jpg" class=main-img alt="Radisson Blu Chattogram Bay View">
        </div>
        <div class=content>
        <h3>Radisson Blu Chattogram Bay View</h3>
        <p class="offer-info offer-featured"><span class=offer-highlight>Dine 2 Pay 1</span> on buffet dinner</p>
        <p class=offer-meta><strong>Address:</strong> S S Khaled Road, Chattogram</p>
        <p class=offer-meta><strong>Valid till:</strong> 31-May-27</p>
        </div>
        </div>
        </div>
        </section>
    """.trimIndent()

    @Test
    fun `parses venues with name offer and validity`() {
        val offers = StandardCharteredSource.parsePage(page, now, zone)
        assertEquals(2, offers.size)

        val sixSeasons = offers.first { it.restaurant == "Six Seasons Hotel" }
        assertEquals("EAT_4_PAY_1", sixSeasons.offerKind)
        assertEquals("Dine 4 Pay 1", sixSeasons.headline)
        assertEquals(endOfDay(2027, 12, 31), sixSeasons.validUntil)
        assertEquals(Sources.BANK, sixSeasons.source)
        assertEquals("Standard Chartered cards", sixSeasons.paymentNote)
        assertEquals("Gulshan", sixSeasons.area)
        assertEquals(
            "https://av.sc.com/bd/edm/b1g1-offers/images/LE1.jpg",
            sixSeasons.imageUrl,
        )
    }

    @Test
    fun `keeps every offer line and the address area`() {
        val amari = StandardCharteredSource.parsePage(page, now, zone)
            .first { it.restaurant == "Amari Dhaka" }

        assertEquals("EAT_5_PAY_1", amari.offerKind)
        assertTrue(amari.details.contains("Dine 5 Pay 1"))
        assertTrue(amari.details.contains("Dine 3 Pay 1"))
        assertEquals(endOfDay(2027, 8, 31), amari.validUntil)
        assertEquals("Gulshan", amari.area)
        assertEquals("Visa Signature · Credit card", amari.paymentNote)
    }

    @Test
    fun `drops offers whose validity already ended`() {
        val names = StandardCharteredSource.parsePage(page, now, zone).map { it.restaurant }
        assertTrue(names.none { it == "Expired Venue" })
    }

    @Test
    fun `drops venues outside dhaka`() {
        val names = StandardCharteredSource.parsePage(page, now, zone).map { it.restaurant }
        assertEquals(listOf("Six Seasons Hotel", "Amari Dhaka"), names)
    }

    @Test
    fun `ids are stable and unique per venue`() {
        val first = StandardCharteredSource.parsePage(page, now, zone).associate { it.restaurant to it.id }
        val second = StandardCharteredSource.parsePage(page, now, zone).associate { it.restaurant to it.id }
        assertEquals(first, second)
        assertEquals(2, first.values.toSet().size)
    }

    @Test
    fun `empty markup yields nothing`() {
        assertTrue(StandardCharteredSource.parsePage("", now, zone).isEmpty())
        assertTrue(StandardCharteredSource.parsePage("<html><body>nope</body></html>", now, zone).isEmpty())
    }

    @Test
    fun `validity is end of day in the given zone`() {
        val offer = StandardCharteredSource.parsePage(page, now, zone)
            .first { it.restaurant == "Six Seasons Hotel" }
        val rendered = Instant.ofEpochMilli(offer.validUntil!!).atZone(zone)
        assertEquals(LocalDate.of(2027, 12, 31), rendered.toLocalDate())
        assertEquals("2027-12-31T23:59:59.999+06:00[Asia/Dhaka]", rendered.toString())
        assertNull(offer.validFrom)
    }
}
