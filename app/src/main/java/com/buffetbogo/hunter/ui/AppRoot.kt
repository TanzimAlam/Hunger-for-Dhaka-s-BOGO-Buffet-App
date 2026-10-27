package com.buffetbogo.hunter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buffetbogo.hunter.BuffetApp

private enum class Screen { LIST, DETAIL, SETTINGS }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val container = (context.applicationContext as BuffetApp).container
    val viewModel: OffersViewModel = viewModel(
        factory = OffersViewModel.factory(container.repository, container.settingsStore),
    )

    val offers by viewModel.offers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var screenName by rememberSaveable { mutableStateOf(Screen.LIST.name) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    // The feed's own state lives here, above the screen switch, so coming back from a detail
    // lands on the exact card and filter the user left.
    val feedState = rememberLazyListState()
    var feedQuery by rememberSaveable { mutableStateOf("") }
    var feedFilter by rememberSaveable { mutableStateOf(FeedFilter.ALL.name) }

    LaunchedEffect(sync.message) {
        val message = sync.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    BackHandler(enabled = screenName != Screen.LIST.name) {
        screenName = Screen.LIST.name
    }

    when (Screen.valueOf(screenName)) {
        Screen.LIST -> OfferListScreen(
            offers = offers,
            sync = sync,
            sortOrder = sortOrder,
            snackbarHostState = snackbarHostState,
            listState = feedState,
            query = feedQuery,
            filterName = feedFilter,
            onQueryChange = { feedQuery = it },
            onFilterChange = { feedFilter = it.name },
            onRefresh = viewModel::refresh,
            onSortChange = viewModel::setSortOrder,
            onOpenSettings = { screenName = Screen.SETTINGS.name },
            onOpenOffer = { id ->
                selectedId = id
                screenName = Screen.DETAIL.name
            },
        )

        Screen.DETAIL -> {
            val item = offers.firstOrNull { it.offer.id == selectedId }
            if (item == null) {
                LaunchedEffect(selectedId) { screenName = Screen.LIST.name }
            } else {
                OfferDetailScreen(
                    item = item,
                    snackbarHostState = snackbarHostState,
                    onBack = { screenName = Screen.LIST.name },
                    onHide = {
                        viewModel.hide(item.offer.id)
                        screenName = Screen.LIST.name
                    },
                )
            }
        }

        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            sync = sync,
            snackbarHostState = snackbarHostState,
            onBack = { screenName = Screen.LIST.name },
            onSaveKeys = viewModel::saveKeys,
            onSaveFacebookToken = viewModel::saveFacebookToken,
            onToggleNotifications = viewModel::setNotificationsEnabled,
            onSyncNow = viewModel::syncNow,
            onRestoreSamples = viewModel::restoreBundled,
            onRefreshRatings = viewModel::refreshRatings,
        )
    }
}
