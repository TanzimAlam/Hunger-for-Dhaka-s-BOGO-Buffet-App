package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Guards the checked Google ratings that ship with the app. */
class VenueRatingsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val checkedOn: Long = LocalDate.of(2026, 10, 25)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private val checkedOnEnd: Long = LocalDate.of(2026, 10, 25)
        .atTime(LocalTime.MAX)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private val json: String = File("src/main/assets/venue_ratings.json").readText()
    private val rows: List<RestaurantRating> by lazy { SeedLoader.parseVenueRatings(json, zone) }

    @Test
    fun `every bundled rating is a plausible google score`() {
        assertTrue("expected the main venues to be rated", rows.size >= 15)
        rows.forEach { row ->
            assertTrue("${row.displayName} has no rating", row.rating != null)
            assertTrue("${row.displayName} rating out of range", row.rating!! in 1.0..5.0)
            assertTrue("${row.displayName} has no review count", (row.ratingCount ?: 0) > 0)
        }
    }

    @Test
    fun `ratings are dated in the past`() {
        rows.forEach { row -> assertTrue(row.updatedAt <= checkedOnEnd) }
    }

    @Test
    fun `aliases resolve the names bank pages use`() {
        val sarina = rows.first { it.nameKey == nameKey("Hotel Sarina Dhaka") }
        assertEquals(4.3, sarina.rating!!, 0.001)
        assertEquals("Hotel Sarina", sarina.displayName)

        val panPacific = rows.first { it.nameKey == nameKey("Pan Pacific Sonargaon Dhaka") }
        assertEquals(4.5, panPacific.rating!!, 0.001)

        val lakeshore = rows.first { it.nameKey == nameKey("Lakeshore Hotel Gulshan") }
        assertEquals(4.3, lakeshore.rating!!, 0.001)
    }

    @Test
    fun `accented hotel names share one key`() {
        assertEquals(nameKey("Le Méridien Dhaka"), nameKey("Le Meridien Dhaka"))
        assertEquals("le meridien dhaka", nameKey("Le Méridien Dhaka"))
    }

    @Test
    fun `the rated venues cover most of the bundled catalogue`() {
        val offers = SeedLoader.parse(
            File("src/main/assets/seed_offers.json").readText(),
            checkedOn,
            zone,
        )
        val keys = rows.map { it.nameKey }.toSet()
        val rated = offers.count { nameKey(it.restaurant) in keys }
        // A newly added venue may not have had its Google score checked yet — the card simply
        // hides the star pill — but most of the catalogue must still carry a rating.
        assertTrue("only $rated of ${offers.size} venues are rated", rated >= offers.size / 2)
    }
}
