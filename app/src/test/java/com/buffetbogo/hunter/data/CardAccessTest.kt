package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * The "My cards" order hides a restaurant unless one of the saved cards unlocks its deal, so
 * these tests pin down who each kind of ad lets in — including the ads the app must not guess at.
 */
class CardAccessTest {

    private fun offer(
        paymentNote: String,
        terms: String = "",
        details: String = "",
        title: String = "Buffet offer",
        restaurant: String = "Test Hotel",
    ): Offer = Offer(
        id = "$restaurant|$paymentNote",
        title = title,
        restaurant = restaurant,
        area = "Dhaka",
        offerKind = "BOGO_1_1",
        headline = "Buy 1 Get 1",
        details = details,
        terms = terms,
        paymentNote = paymentNote,
        priceBdt = 1_500,
        postedAt = 0L,
        validFrom = null,
        validUntil = null,
        source = Sources.CURATED,
        sourceUrl = "https://example.com/$restaurant",
        imageUrl = null,
        rating = null,
        ratingCount = null,
        fetchedAt = 0L,
    )

    private fun card(
        bank: String,
        name: String,
        brand: String,
        type: String,
        tier: String,
    ) = UserCard(bank, name, brand, type, tier)

    private val ucbPlatinumCredit = card(
        "United Commercial Bank",
        "United Commercial Bank Mastercard Platinum Credit Card",
        "Mastercard",
        "Credit",
        "Platinum",
    )
    private val ucbGoldCredit = card(
        "United Commercial Bank",
        "United Commercial Bank VISA Gold Credit Card",
        "VISA",
        "Credit",
        "Gold",
    )
    private val ucbPlatinumDebit = card(
        "United Commercial Bank",
        "United Commercial Bank Mastercard Platinum Debit Card",
        "Mastercard",
        "Debit",
        "Platinum",
    )
    private val bracPlatinumCredit = card(
        "BRAC Bank",
        "BRAC Bank VISA Platinum Credit Card",
        "VISA",
        "Credit",
        "Platinum",
    )
    private val scbPlatinumCredit = card(
        "Standard Chartered Bank",
        "Standard Chartered Bank VISA Platinum Credit Card",
        "VISA",
        "Credit",
        "Platinum",
    )
    private val scbSignatureCredit = card(
        "Standard Chartered Bank",
        "Standard Chartered Bank VISA Signature Credit Card",
        "VISA",
        "Credit",
        "Signature",
    )
    private val premierCredit = card(
        "Premier Bank",
        "Premier Bank VISA Credit Card",
        "VISA",
        "Credit",
        "Standard",
    )
    private val premierDebit = card(
        "Premier Bank",
        "Premier Bank VISA Debit Card",
        "VISA",
        "Debit",
        "Classic",
    )

    @Test
    fun `a card-free deal is open to every card`() {
        val anyPayment = offer(
            paymentNote = "Any payment method",
            terms = "Any payment method. Dinner is served daily 6:30-10:30 pm.",
        )
        assertEquals(CardRule.AnyPayment, anyPayment.cardRule())
        assertTrue(anyPayment.acceptsCard(bracPlatinumCredit))
        assertTrue(anyPayment.acceptsCard(scbSignatureCredit))
    }

    @Test
    fun `an explicitly card-free deal stays open even when a bank is named`() {
        val openDeal = offer(
            paymentNote = "Card or cash - no bank card required",
            terms = "No bank card condition. The deal is also listed on UCB's card-offer page.",
            details = "Royal Treat's breakfast buffet is open to everyone; UCB lists it too.",
        )
        assertEquals(CardRule.AnyPayment, openDeal.cardRule())
        assertTrue(openDeal.acceptsCard(bracPlatinumCredit))
    }

    @Test
    fun `a UCB platinum credit deal is narrowed to platinum credit cards`() {
        val ucbOnly = offer(
            paymentNote = "UCB Platinum credit cardholder",
            terms = "UCB Platinum credit cardholders on both Visa and Mastercard.",
        )
        assertTrue(ucbOnly.acceptsCard(ucbPlatinumCredit))
        assertFalse(ucbOnly.acceptsCard(ucbGoldCredit))
        assertFalse(ucbOnly.acceptsCard(ucbPlatinumDebit))
        assertFalse(ucbOnly.acceptsCard(bracPlatinumCredit))
    }

    @Test
    fun `all Standard Chartered cards match without a phantom Standard tier`() {
        val scbOnly = offer(
            paymentNote = "All Standard Chartered cards",
            terms = "All Standard Chartered debit and credit cards. Prior reservation required.",
        )
        val rule = scbOnly.cardRule()
        assertTrue(rule is CardRule.BankRestricted)
        rule as CardRule.BankRestricted
        assertEquals("Standard Chartered Bank", rule.bank)
        assertTrue("Standard Chartered must not become the tier Standard", rule.tiers.isEmpty())
        assertTrue(scbOnly.acceptsCard(scbPlatinumCredit))
        assertTrue(scbOnly.acceptsCard(scbSignatureCredit))
        assertFalse(scbOnly.acceptsCard(bracPlatinumCredit))
    }

    @Test
    fun `a restaurant named Platinum does not add a card tier`() {
        val offer = offer(
            paymentNote = "All Standard Chartered cards",
            terms = "All Standard Chartered debit and credit cards.",
            details = "Platinum Residence gives Dine 2 Pay 1 on its buffet breakfast.",
            restaurant = "Platinum Residence",
        )
        val rule = offer.cardRule() as CardRule.BankRestricted
        assertTrue(rule.tiers.isEmpty())
        assertTrue(offer.acceptsCard(scbPlatinumCredit))
    }

    @Test
    fun `selected bank cards without a named bank are never guessed open`() {
        val vague = offer(
            paymentNote = "Selected bank cards for the Buy 1 Get 1 variant",
            terms = "Selected bank cards get Buy 1 Get 1 at regular prices.",
        )
        assertEquals(CardRule.Unresolved, vague.cardRule())
        assertFalse(vague.acceptsAnyCard(listOf(bracPlatinumCredit, scbPlatinumCredit)))
    }

    @Test
    fun `a Signature deal does not admit a Platinum card from the same bank`() {
        val signature = offer(
            paymentNote = "SCB Visa Signature / Priority Banking cards",
            terms = "Visa Signature credit, Priority Banking and Saadiq Priority debit cards.",
        )
        val rule = signature.cardRule() as CardRule.BankRestricted
        assertEquals("Standard Chartered Bank", rule.bank)
        assertTrue(signature.acceptsCard(scbSignatureCredit))
        assertFalse(signature.acceptsCard(scbPlatinumCredit))
    }

    @Test
    fun `a debit-only deal rejects a credit card from the same bank`() {
        val debitOnly = offer(
            paymentNote = "Premier Bank debit card",
            terms = "Premier Bank debit cards only.",
        )
        assertFalse(debitOnly.acceptsCard(premierCredit))
        assertTrue(debitOnly.acceptsCard(premierDebit))
    }

    @Test
    fun `one matching card is enough when several are saved`() {
        val ucbOnly = offer(
            paymentNote = "UCB credit or debit card",
            terms = "UCB credit and debit cardholders.",
        )
        assertTrue(ucbOnly.acceptsAnyCard(listOf(bracPlatinumCredit, ucbGoldCredit)))
        assertFalse(ucbOnly.acceptsAnyCard(listOf(bracPlatinumCredit, scbPlatinumCredit)))
    }

    @Test
    fun `the longest bank name wins when aliases overlap`() {
        assertEquals("Mutual Trust Bank", BankNames.detect("Mutual Trust Bank B1G1"))
        assertEquals("Trust Bank", BankNames.detect("Trust Bank B1G1"))
        assertEquals("Standard Chartered Bank", BankNames.detect("Standard Chartered cards"))
        assertEquals("Standard Bank", BankNames.detect("Standard Bank cards"))
        assertEquals("United Commercial Bank", BankNames.detect("UCB cards"))
    }

    @Test
    fun `the bundled catalogue's card rules come from its own wording`() {
        val zone = ZoneId.of("Asia/Dhaka")
        val now = LocalDate.of(2026, 10, 29).atStartOfDay(zone).toInstant().toEpochMilli()
        val rules = SeedLoader.parse(
            File("src/main/assets/seed_offers.json").readText(),
            now,
            zone,
        ).associate { "${it.restaurant}|${it.offerKind}" to it.cardRule() }

        // A bank card is not needed: every saved card keeps these.
        assertEquals(CardRule.AnyPayment, rules["Hotel Sarina Dhaka|BOGO_1_3"])
        assertEquals(CardRule.AnyPayment, rules["Ascott Palace Dhaka|BOGO_1_1"])

        // UCB names a product family, not just the bank.
        val sixSeasons = rules.getValue("Six Seasons Hotel|BOGO_1_3")
        assertTrue(sixSeasons is CardRule.BankRestricted)
        sixSeasons as CardRule.BankRestricted
        assertEquals("United Commercial Bank", sixSeasons.bank)
        assertEquals(setOf("Credit"), sixSeasons.types)
        assertEquals(setOf("Platinum"), sixSeasons.tiers)
        assertEquals(setOf("VISA", "Mastercard"), sixSeasons.brands)

        // Premier names debit and credit but no tier.
        val cafeRio = rules.getValue("Café Rio|PCT_15") as CardRule.BankRestricted
        assertEquals("Premier Bank", cafeRio.bank)
        assertTrue(cafeRio.tiers.isEmpty())
        assertTrue(cafeRio.types.containsAll(listOf("Credit", "Debit")))

        // "Selected bank cards" without a bank stays out of the card filter.
        assertEquals(CardRule.Unresolved, rules["Beyond Buffet|DISCOUNT"])
    }

    @Test
    fun `card-free restaurants are shown for any wallet`() {
        val zone = ZoneId.of("Asia/Dhaka")
        val now = LocalDate.of(2026, 10, 29).atStartOfDay(zone).toInstant().toEpochMilli()
        val showsForBrac = SeedLoader.parse(
            File("src/main/assets/seed_offers.json").readText(),
            now,
            zone,
        ).filter { it.acceptsCard(bracPlatinumCredit) }
            .map { it.restaurant }
            .toSet()

        // These restaurants ask for no bank card at all, so they stay valid whatever card the
        // diner holds: the wallet must never hide them.
        assertTrue("Hotel Sarina Dhaka" in showsForBrac)
        assertTrue("Ascott Palace Dhaka" in showsForBrac)
        assertTrue("Hotel Marco Polo Dhaka" in showsForBrac)
        assertTrue("Teriyaki Boy & Sizzlin' Steak" in showsForBrac)
        assertTrue("Amari Dhaka" in showsForBrac)
        assertTrue("Six Seasons Hotel" in showsForBrac)

        // Deals tied to another bank's card stay out for a BRAC wallet.
        assertFalse("Doreen Hotel & Resort" in showsForBrac)
        assertFalse("Café Rio" in showsForBrac)
    }

    @Test
    fun `a saved wallet survives a save and load`() {
        val cards = listOf(ucbPlatinumCredit, scbSignatureCredit)
        assertEquals(cards, UserCards.decode(UserCards.encode(cards)))
    }

    @Test
    fun `a broken wallet is dropped instead of crashing the feed`() {
        assertEquals(emptyList<UserCard>(), UserCards.decode("{not json"))
        assertEquals(emptyList<UserCard>(), UserCards.decode("[{\"bank\":\"\"}]"))
        assertEquals(emptyList<UserCard>(), UserCards.decode(null))
    }
}
