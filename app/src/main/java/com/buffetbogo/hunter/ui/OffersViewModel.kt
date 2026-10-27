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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
