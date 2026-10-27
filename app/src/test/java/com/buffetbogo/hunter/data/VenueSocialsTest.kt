package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VenueSocialsTest {

    private val directory = VenueSocials.parse(
        File("src/main/assets/venue_socials.json").readText(),
    )

    @Test
    fun `every tracked venue resolves to its own page`() {
        val expected = listOf(
            "Le Méridien Dhaka",
            "Sheraton Dhaka",
            "InterContinental Dhaka",
            "Renaissance Dhaka Gulshan Hotel",
            "Radisson Blu Dhaka Water Garden",
            "The Westin Dhaka",
            "Pan Pacific Sonargaon Dhaka",
            "Holiday Inn Dhaka City Centre",
            "Dhaka Regency Hotel & Resort",
            "Hyatt Place Dhaka Uttara",
            "Crowne Plaza Dhaka Gulshan",
            "Hotel Sarina Dhaka",
            "Amari Dhaka",
            "Six Seasons Hotel",
            "Beyond Buffet",
            "Teriyaki Boy & Sizzlin' Steak",
        )
        expected.forEach { name ->
            val social = VenueSocials.find(directory, name)
            assertNotNull("$name has no directory entry", social)
            assertTrue(
                "$name has no Facebook page or website",
                !social!!.facebook.isNullOrBlank() || !social.website.isNullOrBlank(),
            )
        }
    }

    @Test
    fun `bank spellings resolve to the same venue`() {
        assertEquals(
            "Pan Pacific Sonargaon",
            VenueSocials.find(directory, "Pan Pacific Sonargaon Dhaka")?.displayName,
        )
        assertEquals(
            "Hotel Sarina",
            VenueSocials.find(directory, "Hotel Sarina Dhaka")?.displayName,
        )
    }

    @Test
    fun `facebook links are classified by what they point at`() {
        assertEquals(
            AdKind.FACEBOOK_POST,
            VenueSocials.kindOf("https://www.facebook.com/SheratonDhaka/posts/1639808158163219/"),
        )
        assertEquals(
            AdKind.FACEBOOK_POST,
            VenueSocials.kindOf("https://www.facebook.com/AmariDhaka/videos/1447875447366872/"),
        )
        assertEquals(
            AdKind.FACEBOOK_PAGE,
            VenueSocials.kindOf("https://www.facebook.com/SheratonDhaka/"),
        )
        assertEquals(AdKind.WEBSITE, VenueSocials.kindOf("https://www.sarinahotel.com/offers/"))
    }

    @Test
    fun `page handles are extracted for the Graph API`() {
        assertEquals(
            "SheratonDhaka",
            VenueSocials.facebookHandle("https://www.facebook.com/SheratonDhaka/posts/1/"),
        )
        assertEquals("LMDhaka", VenueSocials.facebookHandle("https://www.facebook.com/LMDhaka/"))
        assertEquals(
            "61584765623675",
            VenueSocials.facebookHandle("https://www.facebook.com/61584765623675"),
        )
        assertEquals(
            "2470709666494903",
            VenueSocials.facebookHandle(
                "https://www.facebook.com/pages/Renaissance%20Dhaka%20Gulshan%20Hotel/2470709666494903/",
            ),
        )
    }

    @Test
    fun `the fallback search link is encoded and never a bank`() {
        val url = VenueSocials.facebookSearchUrl("Hotel Sarina")
        assertTrue(url.startsWith("https://www.facebook.com/search/top?q="))
        assertTrue(url.contains("Hotel%20Sarina"))
        assertTrue(!url.contains("sc.com"))
    }
}
