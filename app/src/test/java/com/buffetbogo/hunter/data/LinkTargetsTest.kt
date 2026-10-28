package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every case here once ended in "the app closed when I tapped Open the original ad". */
class LinkTargetsTest {

    @Test
    fun `a bare host gains https`() {
        assertEquals(
            "https://www.facebook.com/HotelSarinaDhaka/",
            LinkTargets.clean("www.facebook.com/HotelSarinaDhaka/"),
        )
        assertEquals(
            "https://platinumhotels.com.bd/restaurant/terrabistro-banani",
            LinkTargets.clean("platinumhotels.com.bd/restaurant/terrabistro-banani"),
        )
    }

    @Test
    fun `a protocol relative link keeps its host and becomes https`() {
        assertEquals(
            "https://www.facebook.com/PlatinumGrandBySHELTECH/",
            LinkTargets.clean("//www.facebook.com/PlatinumGrandBySHELTECH/"),
        )
    }

    @Test
    fun `http and https are left alone`() {
        assertEquals("http://example.com/a", LinkTargets.clean("http://example.com/a"))
        assertEquals("https://example.com/a?b=c", LinkTargets.clean("https://example.com/a?b=c"))
    }

    @Test
    fun `scraped quotes brackets and entities are repaired`() {
        assertEquals(
            "https://www.facebook.com/permalink.php?story_fbid=123&id=456",
            LinkTargets.clean(
                "\"https://www.facebook.com/permalink.php?story_fbid=123&amp;id=456\"",
            ),
        )
        assertEquals(
            "https://example.com/offer",
            LinkTargets.clean(" <https://example.com/offer> "),
        )
        assertEquals(
            "https://example.com/offer?x=1&y=2",
            LinkTargets.clean("'https://example.com/offer?x=1&amp;y=2'"),
        )
    }

    @Test
    fun `raw markup is refused rather than half opened`() {
        // Scrapers feed the extracted href, never the whole tag; a whole tag has no host.
        assertNull(LinkTargets.clean("<a href=https://example.com/offer>"))
    }

    @Test
    fun `invisible characters from copied text are dropped`() {
        assertEquals(
            "https://example.com/offer",
            LinkTargets.clean("\u200bhttps://example.com/offer\ufeff"),
        )
    }

    @Test
    fun `spaces and non ascii become percent escapes`() {
        assertEquals(
            "https://example.com/buffet%20dinner",
            LinkTargets.clean("https://example.com/buffet dinner"),
        )
        assertEquals(
            "https://example.com/%E0%A6%AC%E0%A6%BE%E0%A6%82%E0%A6%B2%E0%A6%BE",
            LinkTargets.clean("https://example.com/বাংলা"),
        )
    }

    @Test
    fun `existing escapes are not double encoded`() {
        assertEquals(
            "https://www.facebook.com/search/top?q=Hotel%20Sarina%20buffet",
            LinkTargets.clean("https://www.facebook.com/search/top?q=Hotel%20Sarina%20buffet"),
        )
    }

    @Test
    fun `dangerous schemes are refused instead of crashing the launcher`() {
        assertNull(LinkTargets.clean("javascript:alert(1)"))
        assertNull(LinkTargets.clean("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(LinkTargets.clean("file:///sdcard/secret.txt"))
        assertFalse(LinkTargets.isOpenable("tel:+8801700000000"))
    }

    @Test
    fun `junk is refused`() {
        assertNull(LinkTargets.clean(null))
        assertNull(LinkTargets.clean(""))
        assertNull(LinkTargets.clean("   "))
        assertNull(LinkTargets.clean("see the post"))
        assertNull(LinkTargets.clean("https://"))
        assertNull(LinkTargets.clean("https:///offer"))
    }

    @Test
    fun `a realistic facebook post survives unchanged`() {
        val post = "https://www.facebook.com/AmariDhaka/posts/elevate-your-lunch-experience-at-amaya/1447875447366872/"
        assertEquals(post, LinkTargets.clean(post))
        assertTrue(LinkTargets.isOpenable(post))
    }
}
