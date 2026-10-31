package com.buffetbogo.hunter.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** One card product a bank issues, as listed in the bundled BIN reference. */
data class CardProduct(
    val name: String,
    val brand: String,
    val type: String,
    val tier: String,
) {
    /** The product name without the bank prefix; the picker already shows the bank above it. */
    fun shortName(bank: String): String {
        val prefix = "$bank "
        return if (name.startsWith(prefix, ignoreCase = true)) name.substring(prefix.length) else name
    }

    /** "VISA · Credit · Signature", skipping the parts the reference leaves blank. */
    val detail: String
        get() = listOf(brand, type, tier).filter { it.isNotBlank() }.joinToString(" · ")
}

/** Every card product one bank issues, in one alphabetically sorted list. */
data class BankCards(
    val bank: String,
    val cards: List<CardProduct>,
)

/**
 * The banks and card products offered in the two "add a card" lists.
 *
 * This is generated from the per-bank files in `data/card-bins/banks/` by
 * `tools/MakeBankCards.ps1`, and shipped
 * as `assets/bank_cards.json`. It carries no BINs and no card numbers: the BINs matter to a
 * backend matching a first-six-digits prefix, not to a diner choosing a product by name.
 */
object CardCatalogue {

    /** Pure parser, so the bundled list can be validated on the JVM. */
    fun parse(raw: String): List<BankCards> {
        val array = JSONArray(raw)
        val banks = ArrayList<BankCards>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val bank = item.optString("bank").trim()
            if (bank.isBlank()) continue
            val cards = item.optJSONArray("cards")
            val products = if (cards == null) {
                emptyList()
            } else {
                (0 until cards.length()).mapNotNull { cardIndex ->
                    val card = cards.optJSONObject(cardIndex) ?: return@mapNotNull null
                    val name = card.optString("name").trim()
                    if (name.isBlank()) return@mapNotNull null
                    CardProduct(
                        name = name,
                        brand = card.optString("brand").trim(),
                        type = card.optString("type").trim(),
                        tier = card.optString("tier").trim(),
                    )
                }.distinctBy { it.name }.sortedBy { it.name.lowercase() }
            }
            if (products.isNotEmpty()) {
                banks += BankCards(bank = bank, cards = products)
            }
        }
        return banks.sortedBy { it.bank.lowercase() }
    }

    suspend fun load(context: Context): List<BankCards> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.assets.open("bank_cards.json").bufferedReader().use { it.readText() }
            parse(raw)
        }.getOrDefault(emptyList())
    }
}
