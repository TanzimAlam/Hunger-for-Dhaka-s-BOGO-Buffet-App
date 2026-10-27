package com.buffetbogo.hunter.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a tapped notification's offer id from the Activity into the feed.
 *
 * A notification is always about one offer, so tapping it has to land on that offer's detail
 * screen — even when the app was closed, and even if the feed has not finished loading yet.
 * The id waits here until the list contains it (or the wait times out in AppRoot).
 */
object DeepLinks {

    const val EXTRA_OFFER_ID = "com.buffetbogo.hunter.extra.OFFER_ID"

    private val _pendingOfferId = MutableStateFlow<String?>(null)
    val pendingOfferId: StateFlow<String?> = _pendingOfferId.asStateFlow()

    fun open(offerId: String?) {
        if (!offerId.isNullOrBlank()) _pendingOfferId.value = offerId
    }

    fun clear() {
        _pendingOfferId.value = null
    }
}
