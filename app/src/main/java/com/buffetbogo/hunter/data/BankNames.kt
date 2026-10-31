package com.buffetbogo.hunter.data

import java.util.Locale

/**
 * The banks the app knows by name, exactly as they are spelled in the bundled BIN reference
 * (`data/card-bins/banks/`, shipped to the app as `assets/bank_cards.json`).
 *
 * An offer almost never prints a bank's full registered name: a UCB page says "UCB", Standard
 * Chartered's says "SCB" or "Visa Signature", Dutch-Bangla's says "DBBL". This table turns those
 * words back into one canonical name so an offer can be compared with the card a diner added.
 *
 * Detection is word-boundary based and checks the longest alias first, which keeps
 * "Mutual Trust Bank" from being read as "Trust Bank" and "Standard Chartered Bank" from being
 * read as "Standard Bank".
 */
object BankNames {

    /** Sorted alphabetically, the same order the asset uses. */
    val canonical: List<String> = listOf(
        "Agrani Bank",
        "Al-Arafah Islami Bank",
        "Bangladesh Commerce Bank",
        "Bangladesh Post Office",
        "Bank Al-Falah",
        "Bank Asia",
        "Bank of Small Industries and Commerce Bangladesh",
        "Bengal Commercial Bank",
        "BRAC Bank",
        "Citizens Bank",
        "City Bank",
        "Commercial Bank of Ceylon",
        "Dhaka Bank",
        "Dutch-Bangla Bank",
        "Eastern Bank",
        "EXIM Bank",
        "First Security Islami Bank",
        "Global Islami Bank",
        "HSBC",
        "Habib Bank",
        "IFIC Bank",
        "Jamuna Bank",
        "Janata Bank",
        "Meghna Bank",
        "Mercantile Bank",
        "Midland Bank",
        "Modhumoti Bank",
        "Mutual Trust Bank",
        "National Bank",
        "NCC Bank",
        "NRB Bank",
        "ONE Bank",
        "Premier Bank",
        "Prime Bank",
        "Pubali Bank",
        "Rupali Bank",
        "Shahjalal Islami Bank",
        "Shimanto Bank",
        "Social Islami Bank",
        "Sonali Bank",
        "South Bangla Agriculture and Commerce Bank",
        "Southeast Bank",
        "Standard Bank",
        "Standard Chartered Bank",
        "State Bank of India",
        "Trust Bank",
        "Union Bank",
        "United Commercial Bank",
        "Uttara Bank",
        "Woori Bank",
    )

    /**
     * Extra spellings cards and bank pages use, mapped to the canonical name above. Full bank
     * names are added automatically, so only nicknames and acronyms belong here.
     */
    private val nicknames: Map<String, List<String>> = mapOf(
        "United Commercial Bank" to listOf("UCB"),
        "Standard Chartered Bank" to listOf("Standard Chartered", "SCB"),
        "Dutch-Bangla Bank" to listOf("Dutch Bangla Bank", "Dutch-Bangla", "Dutch Bangla", "DBBL"),
        "Mutual Trust Bank" to listOf("Mutual Trust", "MTB"),
        "Al-Arafah Islami Bank" to listOf("Al Arafah Islami Bank", "Al-Arafah", "Al Arafah", "AIBL"),
        "First Security Islami Bank" to listOf("First Security Islami", "FSIBL"),
        "Social Islami Bank" to listOf("Social Islami", "SIBL"),
        "Shahjalal Islami Bank" to listOf("Shahjalal Islami", "SJIBL"),
        "EXIM Bank" to listOf("EXIM"),
        "Eastern Bank" to listOf("EBL"),
        "Southeast Bank" to listOf("South East Bank", "SEBL"),
        "IFIC Bank" to listOf("IFIC"),
        "BRAC Bank" to listOf("BRAC"),
        "NCC Bank" to listOf("NCC"),
        "NRB Bank" to listOf("NRB"),
        "Bank Al-Falah" to listOf("Bank Alfalah", "Alfalah"),
        "South Bangla Agriculture and Commerce Bank" to listOf("South Bangla"),
        "State Bank of India" to listOf("SBI"),
        "Bank of Small Industries and Commerce Bangladesh" to listOf("BSCIC"),
    )

    /** (canonical name, lowercase alias) pairs, longest alias first. */
    private val aliases: List<Pair<String, String>> = buildList {
        canonical.forEach { bank -> add(bank to bank.lowercase(Locale.ROOT)) }
        nicknames.forEach { (bank, names) ->
            names.forEach { name -> add(bank to name.lowercase(Locale.ROOT)) }
        }
    }.sortedByDescending { (_, alias) -> alias.length }

    private val patterns: List<Pair<String, Regex>> = aliases.map { (bank, alias) ->
        bank to Regex("(?i)\\b${Regex.escape(alias)}\\b")
    }

    /** The canonical bank named anywhere in [text], or null when no known bank is mentioned. */
    fun detect(text: String): String? {
        if (text.isBlank()) return null
        return patterns.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first
    }

    /** Every canonical name [raw] could mean, itself included. Used for a saved card's bank. */
    fun canonicalOf(raw: String): String? {
        val needle = raw.trim().lowercase(Locale.ROOT)
        if (needle.isBlank()) return null
        return aliases.firstOrNull { (_, alias) -> alias == needle }?.first
    }

    fun sameBank(a: String, b: String): Boolean {
        val left = canonicalOf(a) ?: a.trim()
        val right = canonicalOf(b) ?: b.trim()
        return left.equals(right, ignoreCase = true)
    }

    /**
     * [text] with every bank name taken out, so card words can be read without a bank name
     * masquerading as a card tier ("Standard Chartered" must not become tier "Standard").
     */
    fun strip(text: String): String {
        var result = text
        patterns.forEach { (_, regex) -> result = regex.replace(result, " ") }
        return result
    }
}
