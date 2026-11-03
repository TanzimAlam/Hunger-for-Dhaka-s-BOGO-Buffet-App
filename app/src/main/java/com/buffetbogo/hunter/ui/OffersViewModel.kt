package com.buffetbogo.hunter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.buffetbogo.hunter.data.AppSettings
import com.buffetbogo.hunter.data.OfferRepository
import com.buffetbogo.hunter.data.OfferUi
import com.buffetbogo.hunter.data.SettingsStore
import com.buffetbogo.hunter.data.SortOrder
import com.buffetbogo.hunter.data.UserCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncState(
    val running: Boolean = false,
    val message: String? = null,
)

class OffersViewModel(
    private val repository: OfferRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val offers: StateFlow<List<OfferUi>> = repository.offers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    val sortOrder: StateFlow<SortOrder> = settings
        .map { it.sortOrder }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SortOrder.ENDS_SOONEST,
        )

    /**
     * null means "not read from the phone yet", so a returning diner never sees the first-run
     * screen flash while DataStore is still opening the file.
     */
    val onboardingDone: StateFlow<Boolean?> = repository.settings
        .map<AppSettings, Boolean?> { it.onboardingDone }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    fun refresh() {
        if (_sync.value.running) return
        viewModelScope.launch {
            _sync.value = SyncState(running = true)
            val result = repository.refresh()
            _sync.value = SyncState(running = false, message = result.message)
        }
    }

    /**
     * Called when the feed comes back to the foreground. A feed older than half an hour is
     * refreshed quietly, so returning to the app never shows a spinner over an unchanged list.
     */
    fun refreshOnOpen() {
        viewModelScope.launch(Dispatchers.IO) { repository.refreshIfStale() }
    }

    fun hide(offerId: String) {
        viewModelScope.launch { repository.hide(offerId) }
    }

    fun saveKeys(braveKey: String, placesKey: String) {
        viewModelScope.launch {
            settingsStore.setBraveApiKey(braveKey)
            settingsStore.setPlacesApiKey(placesKey)
            if (placesKey.isBlank()) {
                _sync.value = SyncState(message = "Key saved")
                return@launch
            }
            val result = repository.refreshRatings(force = true)
            val message = when {
                result.updated > 0 ->
                    "Key saved — updated ${result.updated} Google rating${if (result.updated == 1) "" else "s"}."
                else -> "Key saved. No ratings came back — check that Places API (New) is enabled."
            }
            _sync.value = SyncState(message = message)
        }
    }

    /** Saves the Facebook token and immediately pulls the venues' posts, reporting Meta's reply. */
    fun saveFacebookToken(token: String) {
        viewModelScope.launch {
            settingsStore.setFacebookToken(token)
            if (token.isBlank()) {
                _sync.value = SyncState(
                    message = "Facebook is off — offers link to each restaurant's own page.",
                )
                return@launch
            }
            _sync.value = SyncState(running = true)
            val result = repository.refresh()
            _sync.value = SyncState(running = false, message = result.message)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settingsStore.setSortOrder(order) }
    }

    /** Adds a card picked from the bundled bank/card list; adding the same card twice is a no-op. */
    fun addCard(card: UserCard) {
        viewModelScope.launch { settingsStore.addCard(card) }
    }

    fun removeCard(card: UserCard) {
        viewModelScope.launch { settingsStore.removeCard(card) }
    }

    /**
     * Closes the first-run screen. [card] is the selection still open on it, if any. Done (and
     * adding a card from the My Cards empty state) switches the feed to the cards order as soon
     * as the wallet actually holds a card; Skip leaves the full feed alone.
     */
    fun finishOnboarding(card: UserCard?, sortByCards: Boolean) {
        viewModelScope.launch {
            if (card != null) settingsStore.addCard(card)
            val wallet = settingsStore.settings.first().cards
            settingsStore.setOnboardingDone(true)
            settingsStore.setSortOrder(
                if (sortByCards && wallet.isNotEmpty()) SortOrder.MY_CARDS else SortOrder.ENDS_SOONEST,
            )
        }
    }

    /**
     * Closes the card page opened from the header or the empty My Cards feed. The card is appended
     * to whatever is already saved, and the current order is left alone: adding a card from the
     * header should not silently re-filter the feed.
     */
    fun finishCardInput(card: UserCard?) {
        viewModelScope.launch { if (card != null) settingsStore.addCard(card) }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            settingsStore.setOnboardingDone(true)
            settingsStore.setSortOrder(SortOrder.ENDS_SOONEST)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _sync.value = SyncState(running = true)
            val result = repository.refresh()
            _sync.value = SyncState(running = false, message = result.message)
        }
    }

    fun refreshRatings() {
        if (_sync.value.running) return
        viewModelScope.launch {
            _sync.value = SyncState(running = true)
            val result = repository.refreshRatings(force = true)
            val message = when {
                result.needsKey -> "Add a Google Places key in Settings to refresh ratings."
                result.updated == 0 -> "Ratings are already up to date."
                else -> "Updated ${result.updated} Google rating${if (result.updated == 1) "" else "s"}."
            }
            _sync.value = SyncState(running = false, message = message)
        }
    }

    fun restoreBundled() {
        viewModelScope.launch {
            repository.restoreBundled()
            _sync.value = SyncState(running = false, message = "Bundled offers reloaded.")
        }
    }

    fun clearMessage() {
        _sync.value = _sync.value.copy(message = null)
    }

    companion object {
        fun factory(
            repository: OfferRepository,
            settingsStore: SettingsStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { OffersViewModel(repository, settingsStore) }
        }
    }
}
