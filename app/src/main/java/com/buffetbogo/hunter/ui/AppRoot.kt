package com.buffetbogo.hunter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buffetbogo.hunter.BuffetApp
import com.buffetbogo.hunter.data.SortOrder
import com.buffetbogo.hunter.data.toggleMeal
import com.buffetbogo.hunter.notify.DeepLinks
import com.buffetbogo.hunter.ui.theme.BuffetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen { LIST, DETAIL, SETTINGS, CARDS, CARD_INPUT }

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
    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val pendingOfferId by DeepLinks.pendingOfferId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var screenName by rememberSaveable { mutableStateOf(Screen.LIST.name) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // The wallet opens from the feed and from Settings, so Back returns to where it started.
    var cardsReturnTo by rememberSaveable { mutableStateOf(Screen.LIST.name) }

    // The feed's own state lives here, above the screen switch, so coming back from a detail
    // lands on the exact card and filter the user left.
    val feedState = rememberLazyListState()
    var feedQuery by rememberSaveable { mutableStateOf("") }
    var feedFilter by rememberSaveable { mutableStateOf(FeedFilter.ALL.name) }
    // "breakfast,lunch": independent toggles, empty means any time.
    var feedMeals by rememberSaveable { mutableStateOf("") }

    // Every time the list comes back to the foreground, a feed older than half an hour refreshes.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnOpen() }

    // An empty wallet with the cards order on is a real screen, not an error: it explains the
    // wallet and shows the gold add button, so it is left alone here.

    // A new sort order silently rearranges the list, so jump back to the top: leaving the user
    // halfway down a reordered feed looks as if nothing happened. This only fires on an actual
    // change — coming back from a detail screen keeps the exact card they left.
    var previousSort by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sortOrder.name) {
        val last = previousSort
        previousSort = sortOrder.name
        if (last != null && last != sortOrder.name) feedState.animateScrollToItem(0)
    }

    // A tapped alert opens that exact offer, once the feed has the row to show.
    LaunchedEffect(pendingOfferId, offers) {
        val id = pendingOfferId ?: return@LaunchedEffect
        if (offers.any { it.offer.id == id }) {
            selectedId = id
            screenName = Screen.DETAIL.name
            DeepLinks.clear()
        }
    }

    // If the offer expired or was hidden before the tap was handled, do not hold it forever.
    LaunchedEffect(pendingOfferId) {
        val id = pendingOfferId ?: return@LaunchedEffect
        delay(12_000L)
        if (DeepLinks.pendingOfferId.value == id) DeepLinks.clear()
    }

    LaunchedEffect(sync.message) {
        val message = sync.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    // Leaving the wallet from the feed, after adding the first card, applies "My cards" at once.
    val closeCards: () -> Unit = {
        if (cardsReturnTo == Screen.LIST.name && settings.cards.isNotEmpty()) {
            viewModel.setSortOrder(SortOrder.MY_CARDS)
        }
        screenName = cardsReturnTo
    }

    BackHandler(enabled = screenName != Screen.LIST.name) {
        if (Screen.valueOf(screenName) == Screen.CARDS) closeCards() else screenName = Screen.LIST.name
    }

    // The first run opens on the card screen, once DataStore has confirmed it is really the
    // first run. null is "not read yet": draw paper instead of flashing this at a returning
    // diner, and Skip/Done persist the flag so it never appears again.
    val done = onboardingDone
    if (done == null) {
        Box(Modifier.fillMaxSize().background(BuffetColors.Background))
        return
    }
    if (!done) {
        CardOnboardingScreen(
            cards = settings.cards,
            firstRun = true,
            showBack = false,
            onAddCard = viewModel::addCard,
            onBack = {},
            onSkip = viewModel::skipOnboarding,
            onDone = { card -> viewModel.finishOnboarding(card, sortByCards = true) },
        )
        return
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
            mealKeys = feedMeals,
            cards = settings.cards,
            onQueryChange = { feedQuery = it },
            onFilterChange = { next ->
                feedFilter = next.name
                // "All Offers" is also the way out of the cards filter — otherwise tapping it
                // while the gold chip is on would look like nothing happened.
                if (next == FeedFilter.ALL && sortOrder == SortOrder.MY_CARDS) {
                    viewModel.setSortOrder(SortOrder.ENDS_SOONEST)
                }
            },
            onToggleMeal = { meal -> feedMeals = toggleMeal(feedMeals, meal) },
            onRefresh = viewModel::refresh,
            onSortChange = viewModel::setSortOrder,
            onToggleCards = {
                viewModel.setSortOrder(
                    if (sortOrder == SortOrder.MY_CARDS) {
                        SortOrder.ENDS_SOONEST
                    } else {
                        SortOrder.MY_CARDS
                    },
                )
            },
            onOpenCardInput = { screenName = Screen.CARD_INPUT.name },
            onCardFilterEmpty = {
                // Nothing among the diner's cards unlocks a current offer: say so, then show
                // every live offer again so the feed is never left mysteriously empty.
                feedFilter = FeedFilter.ALL.name
                feedMeals = ""
                viewModel.setSortOrder(SortOrder.ENDS_SOONEST)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Sorry — I couldn't find any offer for your saved cards. " +
                            "Showing all offers instead.",
                    )
                }
            },
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
            onOpenCards = {
                cardsReturnTo = Screen.SETTINGS.name
                screenName = Screen.CARDS.name
            },
        )

        Screen.CARDS -> CardsScreen(
            cards = settings.cards,
            snackbarHostState = snackbarHostState,
            onBack = closeCards,
            onAddCard = viewModel::addCard,
            onRemoveCard = viewModel::removeCard,
        )

        // Reached from the gold add-card button in the header, or from the empty My Cards feed:
        // the same card page, with a back arrow and no first-run gate. Done appends the card to
        // the wallet and returns to the feed.
        Screen.CARD_INPUT -> CardOnboardingScreen(
            cards = settings.cards,
            firstRun = false,
            showBack = true,
            onAddCard = viewModel::addCard,
            onBack = { screenName = Screen.LIST.name },
            onSkip = { screenName = Screen.LIST.name },
            onDone = { card ->
                viewModel.finishCardInput(card)
                screenName = Screen.LIST.name
            },
        )
    }
}
