package com.buffetbogo.hunter.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * A credit or debit card the diner carries, picked from the bundled bank/card reference.
 *
 * Only the bank and the product name are kept. The app never asks for — and never stores — a
 * card number, expiry date, CVV, PIN or any part of an account. The BIN reference behind the
 * picker lists public routing ranges, not anyone's card.
 */
data class UserCard(
    val bank: String,
    val cardName: String,
    /** VISA, Mastercard, American Express, UnionPay … */
    val brand: String,
    /** Credit or Debit. */
    val type: String,
    /** Signature, Platinum, Gold, Classic … empty when the reference does not state one. */
    val tier: String,
) {
    /** Stable identity, so the same card cannot be added twice. */
    val key: String get() = "${bank.lowercase(Locale.ROOT)}|${cardName.lowercase(Locale.ROOT)}"

    /** The product name without the bank prefix, which the row above it already states. */
    val shortName: String
        get() {
            val prefix = "$bank "
            return if (cardName.startsWith(prefix, ignoreCase = true)) {
                cardName.substring(prefix.length)
            } else {
                cardName
            }
        }
}

/** Reads and writes the wallet stored in DataStore. Malformed rows are dropped, never fatal. */
object UserCards {

    fun decode(raw: String?): List<UserCard> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val bank = item.optString("bank").trim()
                val name = item.optString("card").trim()
                if (bank.isBlank() || name.isBlank()) return@mapNotNull null
                UserCard(
                    bank = bank,
                    cardName = name,
                    brand = item.optString("brand"),
                    type = item.optString("type"),
                    tier = item.optString("tier"),
                )
            }.distinctBy { it.key }
        }.getOrDefault(emptyList())
    }

    fun encode(cards: List<UserCard>): String {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(
                JSONObject()
                    .put("bank", card.bank)
                    .put("card", card.cardName)
                    .put("brand", card.brand)
                    .put("type", card.type)
                    .put("tier", card.tier),
            )
        }
        return array.toString()
    }
}
