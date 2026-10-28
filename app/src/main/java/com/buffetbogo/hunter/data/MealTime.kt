package com.buffetbogo.hunter.data

import java.util.Locale

/**
 * The serving window an offer names. The ad's own wording decides it: a hotel that advertises a
 * "buffet dinner" is a dinner offer even though the same restaurant also serves lunch.
 *
 * These sit next to the deal-type chips on the feed (never inside the sort menu), so a user can
 * ask for lunch on its own and keep the chosen order.
 */
enum class MealTime(val key: String, val label: String) {
    ALL_DAY("all_day", "Any time"),
    BREAKFAST("breakfast", "Breakfast"),
    BRUNCH("brunch", "Brunch"),
    LUNCH("lunch", "Lunch"),
    DINNER("dinner", "Dinner"),
    ;

    companion object {
        fun fromKey(key: String?): MealTime = entries.firstOrNull { it.key == key } ?: ALL_DAY
    }
}

/**
 * True when the offer is advertised for [meal].
 *
 * Wording wins over any assumption: an ad naming lunch and dinner appears under both and under
 * nothing else. An ad that names no serving window at all is treated as the standard Dhaka
 * lunch-and-dinner buffet — never as breakfast, which almost every venue sells separately.
 */
fun Offer.serves(meal: MealTime): Boolean {
    if (meal == MealTime.ALL_DAY) return true
    val haystack = listOfNotNull(timing, title, headline, details, terms)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
    val named = MealTime.entries.filter { it != MealTime.ALL_DAY && it.key in haystack }
    return when {
        named.isNotEmpty() -> meal in named
        else -> meal == MealTime.LUNCH || meal == MealTime.DINNER
    }
}
