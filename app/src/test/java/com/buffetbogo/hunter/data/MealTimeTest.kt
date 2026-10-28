package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealTimeTest {

    private fun offer(title: String, details: String = "", timing: String? = null): Offer = Offer(
        id = title,
        title = title,
        restaurant = "Test Kitchen",
        area = "Dhaka",
        offerKind = "BOGO_1_1",
        headline = "Buy 1 Get 1",
        details = details,
        terms = "",
        paymentNote = "",
        priceBdt = 2_000,
        postedAt = 0L,
        validFrom = null,
        validUntil = null,
        source = Sources.CURATED,
        sourceUrl = "https://example.com/test",
        imageUrl = null,
        rating = null,
        ratingCount = null,
        fetchedAt = 0L,
        timing = timing,
    )

    @Test
    fun `a dinner ad is a dinner offer only`() {
        val dinner = offer("Buy 1 Get 3 on the buffet dinner", timing = "Dinner 6:30 pm – 10:30 pm")
        assertTrue(dinner.serves(MealTime.DINNER))
        assertFalse(dinner.serves(MealTime.LUNCH))
        assertFalse(dinner.serves(MealTime.BREAKFAST))
        assertTrue(dinner.serves(MealTime.ALL_DAY))
    }

    @Test
    fun `an ad naming lunch and dinner appears under both`() {
        val both = offer(
            title = "Weekend buffet",
            details = "Lunch BDT 1,250 and dinner BDT 1,500.",
        )
        assertTrue(both.serves(MealTime.LUNCH))
        assertTrue(both.serves(MealTime.DINNER))
        assertFalse(both.serves(MealTime.BRUNCH))
    }

    @Test
    fun `brunch is not lunch`() {
        val brunch = offer("Sunday brunch buffet", details = "Every Friday and Saturday, 11 am – 3 pm.")
        assertTrue(brunch.serves(MealTime.BRUNCH))
        assertFalse(brunch.serves(MealTime.LUNCH))
        assertFalse(brunch.serves(MealTime.BREAKFAST))
    }

    @Test
    fun `breakfast wording is honoured`() {
        val breakfast = offer("Breakfast buffet", details = "Daily 7 am – 10:30 am.")
        assertTrue(breakfast.serves(MealTime.BREAKFAST))
        assertFalse(breakfast.serves(MealTime.DINNER))
    }

    @Test
    fun `an ad with no serving window falls back to lunch and dinner`() {
        val silent = offer("Buy 1 Get 1 buffet")
        assertTrue(silent.serves(MealTime.LUNCH))
        assertTrue(silent.serves(MealTime.DINNER))
        assertFalse(silent.serves(MealTime.BREAKFAST))
    }

    @Test
    fun `several meals can be on at once`() {
        val breakfast = offer("Breakfast buffet", timing = "Daily 7 am – 10:30 am")
        val dinner = offer("Buy 1 Get 1 buffet dinner")
        val chosen = setOf(MealTime.BREAKFAST, MealTime.DINNER)

        assertTrue(breakfast.servesAny(chosen))
        assertTrue(dinner.servesAny(chosen))
        // One chosen meal still narrows the list.
        assertFalse(dinner.servesAny(setOf(MealTime.BREAKFAST)))
    }

    @Test
    fun `no meal chosen means every serving window`() {
        assertTrue(offer("Buy 1 Get 1 buffet").servesAny(emptySet()))
        assertTrue(offer("Breakfast buffet").servesAny(emptySet()))
    }

    @Test
    fun `tapping a meal selects it and tapping again clears it`() {
        var keys = ""
        keys = toggleMeal(keys, MealTime.BREAKFAST)
        assertEquals("breakfast", keys)

        keys = toggleMeal(keys, MealTime.DINNER)
        assertEquals("breakfast,dinner", keys)

        keys = toggleMeal(keys, MealTime.BREAKFAST)
        assertEquals("dinner", keys)

        keys = toggleMeal(keys, MealTime.DINNER)
        assertEquals("", keys)
        assertEquals(emptySet<MealTime>(), parseMeals(keys))
    }

    @Test
    fun `the saved chip state ignores junk and all day`() {
        assertEquals(setOf(MealTime.LUNCH), parseMeals("nonsense,lunch,all_day"))
        assertEquals(emptySet<MealTime>(), parseMeals(null))
        assertEquals(emptySet<MealTime>(), parseMeals("   "))
        // ALL_DAY is not a chip; it must leave an existing selection untouched.
        assertEquals("breakfast,lunch", toggleMeal("breakfast,lunch", MealTime.ALL_DAY))
    }

    @Test
    fun `an unknown key restores the all day option`() {
        assertEquals(MealTime.ALL_DAY, MealTime.fromKey(null))
        assertEquals(MealTime.ALL_DAY, MealTime.fromKey("nonsense"))
        assertEquals(MealTime.DINNER, MealTime.fromKey("dinner"))
    }
}
