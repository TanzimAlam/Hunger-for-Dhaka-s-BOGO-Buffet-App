package com.buffetbogo.hunter.data

import java.util.Locale

/**
 * The serving window an offer names. The ad's own wording decides it: a hotel that advertises a
 * "buffet dinner" is a dinner offer even though the same restaurant also serves lunch.
 *
 * These sit next to the deal-type chips on the feed (never inside the sort menu), so a user can
 * ask for lunch on its own and keep the chosen order. The feed's meal chips are independent
 * toggles: breakfast and dinner can be on at once, and turning them all off means "any time".
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

/**
 * True when the offer serves at least one of the chosen meals. An empty selection means the user
 * has not narrowed by meal at all, so every offer passes.
 */
fun Offer.servesAny(meals: Set<MealTime>): Boolean =
    meals.isEmpty() || meals.any { serves(it) }

/** Reads the saved chip state ("breakfast,lunch") into the set of chosen meals. */
fun parseMeals(keys: String?): Set<MealTime> {
    if (keys.isNullOrBlank()) return emptySet()
    return keys.split(',')
        .mapNotNull { key -> MealTime.entries.firstOrNull { it.key == key.trim() } }
        .filter { it != MealTime.ALL_DAY }
        .toSet()
}

/** Adds [meal] to the chip state, or removes it when it is already on. Never returns "all_day". */
fun toggleMeal(keys: String?, meal: MealTime): String {
    // ALL_DAY is not a chip; if it ever arrives, leave the selection alone.
    if (meal == MealTime.ALL_DAY) return parseMeals(keys).joinToString(",") { it.key }
    val current = parseMeals(keys).toMutableSet()
    if (!current.add(meal)) current.remove(meal)
    // Stable order so the saved state does not churn between taps.
    return MealTime.entries.filter { it in current }.joinToString(",") { it.key }
}
