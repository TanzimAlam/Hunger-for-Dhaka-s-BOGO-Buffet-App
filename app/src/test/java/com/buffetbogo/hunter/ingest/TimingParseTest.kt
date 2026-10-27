package com.buffetbogo.hunter.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimingParseTest {

    @Test
    fun `reads a labelled dinner window`() {
        assertEquals(
            "Dinner 6:30 pm – 10:30 pm",
            OfferParser.extractTiming("Dinner 6:30 pm – 10:30 pm, every day. BDT 8,999 net."),
        )
    }

    @Test
    fun `reads dot-separated times and copies the meridiem backwards`() {
        assertEquals(
            "6:30 pm – 10:30 pm",
            OfferParser.extractTiming("Buffet served 6.30pm to 10.30pm on all days"),
        )
    }

    @Test
    fun `reads an unlabelled afternoon window`() {
        assertEquals(
            "Lunch 12:30 pm – 3:30 pm",
            OfferParser.extractTiming("Lunch 12:30-3:30 PM · Buy 1 Get 1"),
        )
    }

    @Test
    fun `keeps two windows when lunch and dinner differ`() {
        val timing = OfferParser.extractTiming(
            "Lunch 12:30 pm – 3:00 pm and Dinner 6:30 pm – 10:30 pm",
        )
        assertEquals(
            "Lunch 12:30 pm – 3:00 pm · Dinner 6:30 pm – 10:30 pm",
            timing,
        )
    }

    @Test
    fun `no window means no timing`() {
        assertNull(OfferParser.extractTiming("Buy 1 Get 1 buffet dinner, BDT 4,999 net."))
        assertNull(OfferParser.extractTiming(""))
    }
}
