package com.buffetbogo.hunter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the two "add a card" lists shipped in the APK: every known bank must be there, and every
 * bank must offer at least one selectable product. A bank without products would leave the card
 * picker empty and the filter unusable.
 */
class CardCatalogueTest {

    private val banks = CardCatalogue.parse(
        File("src/main/assets/bank_cards.json").readText(),
    )

    @Test
    fun `every bank the app can match is in the picker`() {
        assertEquals(BankNames.canonical.sorted(), banks.map { it.bank }.sorted())
    }

    @Test
    fun `every bank offers at least one card product`() {
        banks.forEach { bank ->
            assertTrue("${bank.bank} has no cards", bank.cards.isNotEmpty())
            assertEquals(
                "${bank.bank} has duplicate products",
                bank.cards.size,
                bank.cards.map { it.name }.distinct().size,
            )
            bank.cards.forEach { card ->
                assertTrue("${bank.bank}: blank product name", card.name.isNotBlank())
                assertTrue("${bank.bank}: ${card.name} has no brand", card.brand.isNotBlank())
                assertTrue(
                    "${bank.bank}: ${card.name} has odd type '${card.type}'",
                    card.type in setOf("Credit", "Debit"),
                )
            }
        }
    }

    @Test
    fun `the parser keeps a well-formed row and drops a broken one`() {
        val parsed = CardCatalogue.parse(
            """
            [
              {"bank":"Zeta Bank","cards":[
                {"name":"Zeta Bank VISA Gold Credit Card","brand":"VISA","type":"Credit","tier":"Gold"},
                {"name":"Zeta Bank VISA Gold Credit Card","brand":"VISA","type":"Credit","tier":"Gold"},
                {"name":"","brand":"VISA","type":"Credit","tier":"Gold"}
              ]},
              {"bank":"","cards":[{"name":"Ghost","brand":"VISA","type":"Credit","tier":"Gold"}]},
              {"bank":"Alpha Bank","cards":[]}
            ]
            """.trimIndent(),
        )
        assertEquals(1, parsed.size)
        assertEquals("Zeta Bank", parsed.first().bank)
        assertEquals(1, parsed.first().cards.size)
        assertEquals("VISA · Credit · Gold", parsed.first().cards.first().detail)
    }

    @Test
    fun `the displayed product name drops the bank prefix`() {
        val scb = banks.first { it.bank == "Standard Chartered Bank" }
        val signature = scb.cards.first { it.tier == "Signature" }
        assertEquals("VISA Signature Credit Card", signature.shortName(scb.bank))
    }
}
