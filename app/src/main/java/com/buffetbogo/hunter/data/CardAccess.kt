package com.buffetbogo.hunter.data

import java.util.Locale

/**
 * What a deal asks of a diner's wallet, read from the deal's own payment note and terms.
 *
 * The feed's "My cards" order keeps only the deals at least one saved card unlocks. That is only
 * honest when the app can tell what the ad asked for, so a deal that mentions "selected bank
 * cards" without naming the bank is [Unresolved] rather than guessed open to everyone.
 */
sealed interface CardRule {

    /** No bank-card condition: any card, or cash, gets this deal. */
    data object AnyPayment : CardRule

    /**
     * The ad names [bank] and, when it says so, narrows the offer to some card families.
     * Empty sets mean the bank did not narrow that dimension.
     */
    data class BankRestricted(
        val bank: String,
        val brands: Set<String>,
        val types: Set<String>,
        val tiers: Set<String>,
    ) : CardRule

    /** A card condition is stated, but the bank is missing, so no saved card can be matched. */
    data object Unresolved : CardRule
}

private val ANY_PAYMENT = Regex(
    "(?i)(any payment|no card|card or cash|cash or any|any card|no bank card|" +
        "no payment condition|irrespective of (the )?card)",
)

private val CARD_CONDITION = Regex(
    "(?i)\\b(card|cards|cardholder|cardholders|debit|credit|visa|mastercard|amex|bank card)\\b",
)

private val TYPE_WORDS: List<Pair<String, Regex>> = listOf(
    "Credit" to Regex("(?i)\\bcredit\\b"),
    "Debit" to Regex("(?i)\\bdebit\\b"),
)

private val BRAND_WORDS: List<Pair<String, Regex>> = listOf(
    "American Express" to Regex("(?i)\\b(american express|amex)\\b"),
    "Diners Club" to Regex("(?i)\\bdiners club\\b"),
    "Mastercard" to Regex("(?i)\\bmaster\\s?card\\b"),
    "UnionPay" to Regex("(?i)\\bunion\\s?pay\\b"),
    "Discover" to Regex("(?i)\\bdiscover\\b"),
    "Maestro" to Regex("(?i)\\bmaestro\\b"),
    "VISA" to Regex("(?i)\\bvisa\\b"),
)

/**
 * Card families worth naming. "Standard" is deliberately absent: bank names are stripped before
 * this runs, but "standard terms apply" is too easy to mistake for a card tier.
 */
private val TIER_WORDS: List<Pair<String, Regex>> = listOf(
    "Signature" to Regex("(?i)\\bsignature\\b"),
    "Infinite" to Regex("(?i)\\binfinite\\b"),
    "World" to Regex("(?i)\\bworld\\b"),
    "Platinum" to Regex("(?i)\\bplatinum\\b"),
    "Titanium" to Regex("(?i)\\btitanium\\b"),
    "Gold" to Regex("(?i)\\bgold\\b"),
    "Classic" to Regex("(?i)\\bclassic\\b"),
    "Priority" to Regex("(?i)\\bpriority\\b"),
    "Saadiq" to Regex("(?i)\\bsaadiq\\b"),
    "Prepaid" to Regex("(?i)\\bprepaid\\b"),
    "Business" to Regex("(?i)\\bbusiness\\b"),
    "Corporate" to Regex("(?i)\\bcorporate\\b"),
    "Elevate" to Regex("(?i)\\belevate\\b"),
    "Aspire" to Regex("(?i)\\baspire\\b"),
    "Zenith" to Regex("(?i)\\bzenith\\b"),
)

/**
 * Reads what the ad itself says about cards. The payment note and the terms carry the condition;
 * the title/details only help find the bank, because they also repeat the venue's name ("Platinum
 * Residence" is a hotel, not a card tier).
 */
fun Offer.cardRule(): CardRule {
    val allText = listOf(paymentNote, terms, details, title, headline).joinToString(" ")
    val scope = BankNames.strip(listOf(paymentNote, terms).joinToString(" "))
    val bank = BankNames.detect(allText)
    if (bank != null) {
        return CardRule.BankRestricted(
            bank = bank,
            brands = BRAND_WORDS.filter { (_, regex) -> regex.containsMatchIn(scope) }
                .map { it.first }
                .toSet(),
            types = TYPE_WORDS.filter { (_, regex) -> regex.containsMatchIn(scope) }
                .map { it.first }
                .toSet(),
            tiers = TIER_WORDS.filter { (_, regex) -> regex.containsMatchIn(scope) }
                .map { it.first }
                .toSet(),
        )
    }
    return when {
        ANY_PAYMENT.containsMatchIn(allText) -> CardRule.AnyPayment
        CARD_CONDITION.containsMatchIn(allText) -> CardRule.Unresolved
        else -> CardRule.AnyPayment
    }
}

/** True when [card] is one of the cards this deal accepts. */
fun Offer.acceptsCard(card: UserCard): Boolean = card.satisfies(cardRule())

/** True when at least one saved card unlocks this deal. The rule is read once per offer. */
fun Offer.acceptsAnyCard(cards: List<UserCard>): Boolean {
    if (cards.isEmpty()) return false
    val rule = cardRule()
    return cards.any { card -> card.satisfies(rule) }
}

private fun UserCard.satisfies(rule: CardRule): Boolean = when (rule) {
    CardRule.AnyPayment -> true
    CardRule.Unresolved -> false
    is CardRule.BankRestricted -> matches(rule)
}

private fun UserCard.matches(rule: CardRule.BankRestricted): Boolean {
    if (!BankNames.sameBank(bank, rule.bank)) return false
    if (rule.types.isNotEmpty() && normalizeType(type) !in rule.types.map(::normalizeType)) {
        return false
    }
    if (rule.brands.isNotEmpty() && normalizeBrand(brand) !in rule.brands.map(::normalizeBrand)) {
        return false
    }
    if (rule.tiers.isNotEmpty()) {
        val mine = tier.lowercase(Locale.ROOT)
        val named = rule.tiers.any { wanted ->
            mine == wanted.lowercase(Locale.ROOT) || cardName.contains(wanted, ignoreCase = true)
        }
        if (!named) return false
    }
    return true
}

/** "VISA", "visa" and "VISA Platinum" all mean the Visa network. */
fun normalizeBrand(raw: String): String {
    val value = raw.trim().lowercase(Locale.ROOT)
    return when {
        value.startsWith("visa") -> "VISA"
        value.contains("master") -> "Mastercard"
        value.contains("amex") || value.contains("american express") -> "American Express"
        value.contains("union") -> "UnionPay"
        value.contains("diners") -> "Diners Club"
        value.contains("discover") -> "Discover"
        value.contains("maestro") -> "Maestro"
        else -> raw.trim()
    }
}

private fun normalizeType(raw: String): String = raw.trim().lowercase(Locale.ROOT)
