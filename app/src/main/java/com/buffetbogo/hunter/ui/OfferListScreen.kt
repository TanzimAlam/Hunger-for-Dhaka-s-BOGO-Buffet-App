package com.buffetbogo.hunter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrunchDining
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buffetbogo.hunter.data.FeedGrouper
import com.buffetbogo.hunter.data.MealTime
import com.buffetbogo.hunter.data.OfferUi
import com.buffetbogo.hunter.data.RestaurantGroup
import com.buffetbogo.hunter.data.SortOrder
import com.buffetbogo.hunter.data.UserCard
import com.buffetbogo.hunter.data.acceptsAnyCard
import com.buffetbogo.hunter.data.isFiveStar
import com.buffetbogo.hunter.data.nameKey
import com.buffetbogo.hunter.data.parseMeals
import com.buffetbogo.hunter.data.priceIsEstimate
import com.buffetbogo.hunter.data.servesAny
import com.buffetbogo.hunter.ui.theme.BuffetColors
import java.util.Locale
import kotlinx.coroutines.delay

enum class FeedFilter(val label: String) {
    ALL("All Offers"),
    BOGO("Buy 1 Get 1+"),
    EAT("Eat 3/4/5 Pay 1"),
    FIVE_STAR("5★ Hotels"),
    ;

    fun matches(item: OfferUi): Boolean = when (this) {
        ALL -> true
        BOGO -> item.offer.offerKind.startsWith("BOGO_")
        EAT -> item.offer.offerKind.startsWith("EAT_")
        FIVE_STAR -> item.offer.isFiveStar()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferListScreen(
    offers: List<OfferUi>,
    sync: SyncState,
    sortOrder: SortOrder,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    query: String,
    filterName: String,
    mealKeys: String,
    cards: List<UserCard>,
    onQueryChange: (String) -> Unit,
    onFilterChange: (FeedFilter) -> Unit,
    onToggleMeal: (MealTime) -> Unit,
    onRefresh: () -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onToggleCards: () -> Unit,
    onOpenCardInput: () -> Unit,
    onCardFilterEmpty: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOffer: (String) -> Unit,
) {
    val filter = FeedFilter.valueOf(filterName)
    val meals = remember(mealKeys) { parseMeals(mealKeys) }

    val cardFilterOn = sortOrder == SortOrder.MY_CARDS
    // Deals at least one saved card unlocks. Card-free deals count too: they accept any card.
    val cardMatches = remember(offers, cards) {
        if (cards.isEmpty()) emptyList() else offers.filter { it.offer.acceptsAnyCard(cards) }
    }

    // One card per restaurant: the deal chips pick the deals, the meal toggles pick the serving
    // windows (any of them is enough), the wallet picks what the cards unlock, and the grouping
    // collapses the venues.
    val groups = remember(offers, query, filter, meals, sortOrder, cardMatches) {
        val needle = query.trim()
        val pool = if (cardFilterOn) cardMatches else offers
        val matching = pool.filter { item ->
            filter.matches(item) &&
                item.offer.servesAny(meals) &&
                (needle.isBlank() || item.matches(needle))
        }
        FeedGrouper.group(matching, sortOrder)
    }

    // "My cards" only works when something matches. Rather than leave an empty feed — which
    // looks broken — say so once and fall back to every offer.
    var reportedCardMiss by remember { mutableStateOf(false) }
    LaunchedEffect(sortOrder, cards, cardMatches, offers.isEmpty()) {
        if (sortOrder != SortOrder.MY_CARDS) {
            reportedCardMiss = false
            return@LaunchedEffect
        }
        // An empty wallet is not a failed match: the feed shows the wallet explanation and
        // the gold add button instead of silently falling back to every offer.
        if (cards.isEmpty() || offers.isEmpty() || cardMatches.isNotEmpty() || reportedCardMiss) {
            return@LaunchedEffect
        }
        reportedCardMiss = true
        onCardFilterEmpty()
    }
    val dealCount = remember(groups) { groups.sumOf { it.offerCount } }

    Scaffold(
        containerColor = BuffetColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (cardFilterOn && cards.isEmpty()) {
                AddCardFab(onClick = onOpenCardInput)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .foodBackdrop()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
        ) {
            Header(onAddCard = onOpenCardInput)
            SearchField(query = query, onQueryChange = onQueryChange)
            Spacer(Modifier.height(14.dp))
            FilterRow(
                selected = filter,
                meals = meals,
                cardFilterOn = cardFilterOn,
                cardCount = cards.size,
                onSelect = onFilterChange,
                onToggleMeal = onToggleMeal,
                onToggleCards = onToggleCards,
            )
            Spacer(Modifier.height(9.dp))
            MetaRow(
                restaurants = groups.size,
                deals = dealCount,
                running = sync.running,
                sortOrder = sortOrder,
                cardCount = cards.size,
                onSortChange = onSortChange,
                onRefresh = onRefresh,
            )

            if (cardFilterOn && cards.isEmpty()) {
                NoCardsState(modifier = Modifier.weight(1f))
            } else if (groups.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    filtered = filter != FeedFilter.ALL ||
                        meals.isNotEmpty() ||
                        query.isNotBlank() ||
                        cardFilterOn,
                    onRefresh = onRefresh,
                    onOpenSettings = onOpenSettings,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(groups, key = { nameKey(it.restaurant) }) { group ->
                        RestaurantCard(group = group, onOpenOffer = onOpenOffer)
                    }
                }
            }
        }
    }

}

@Composable
private fun Header(onAddCard: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark()
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Hunger for Dhaka's\nBOGO Buffet",
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = BuffetColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveChip()
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Tracking 5★ Hotels & Restaurants",
                    fontSize = 10.5.sp,
                    color = BuffetColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        AddCardButton(onClick = onAddCard)
    }
}

/**
 * The always-available way into the wallet: a golden card with a plus badge, in the spot the
 * alerts bell used to take. Adding a card no longer needs the sort menu or Settings, and on the
 * empty My Cards feed it works alongside the pulsing button in the corner.
 */
@Composable
private fun AddCardButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = BuffetColors.GoldSoft,
        border = BorderStroke(1.dp, BuffetColors.Gold),
        modifier = Modifier.size(44.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.CreditCard,
                    contentDescription = "Add a bank card",
                    tint = BuffetColors.GoldDeep,
                    modifier = Modifier
                        .size(25.dp)
                        .align(Alignment.Center),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .background(BuffetColors.Gold, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        color = BuffetColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BuffetColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = BuffetColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search hotel, restaurant or offer (e.g. Sheraton)…",
                        fontSize = 13.sp,
                        color = BuffetColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = BuffetColors.TextPrimary, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(BuffetColors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear search",
                    tint = BuffetColors.TextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onQueryChange("") },
                )
            }
        }
    }
}

/**
 * Deal-type chips on the left, serving-window toggles on the right. The deal chips are
 * single-select (a category questionnaire), while the meal chips are independent switches: any
 * number can be on, and tapping one again turns it off.
 */
@Composable
private fun FilterRow(
    selected: FeedFilter,
    meals: Set<MealTime>,
    cardFilterOn: Boolean,
    cardCount: Int,
    onSelect: (FeedFilter) -> Unit,
    onToggleMeal: (MealTime) -> Unit,
    onToggleCards: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = FeedFilter.ALL.name) {
            FilterChipView(
                label = FeedFilter.ALL.label,
                selected = selected == FeedFilter.ALL && !cardFilterOn,
                onClick = { onSelect(FeedFilter.ALL) },
            )
        }
        item(key = "my_cards") {
            MyCardsChip(selected = cardFilterOn, count = cardCount, onClick = onToggleCards)
        }
        item(key = "cards_split") { ChipDivider() }
        FeedFilter.entries.filter { it != FeedFilter.ALL }.forEach { filter ->
            item(key = filter.name) {
                FilterChipView(
                    label = filter.label,
                    selected = filter == selected,
                    onClick = { onSelect(filter) },
                )
            }
        }
        item(key = "meal_split") { ChipDivider() }
        items(MEAL_FILTERS, key = { it.name }) { meal ->
            MealChipView(
                meal = meal,
                selected = meal in meals,
                onClick = { onToggleMeal(meal) },
            )
        }
    }
}

/** ALL_DAY is not a chip: with every toggle off, the feed already shows every serving window. */
private val MEAL_FILTERS = MealTime.entries.filter { it != MealTime.ALL_DAY }

@Composable
private fun ChipDivider() {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(width = 1.dp, height = 22.dp)
            .background(BuffetColors.Border),
    )
}

/**
 * The wallet as a chip, right beside All Offers. Gold separates it from the blue category
 * chips: it narrows the feed to what the saved cards unlock, while a category can still narrow
 * it further. Tapping it again turns the card filter off.
 */
@Composable
private fun MyCardsChip(selected: Boolean, count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) BuffetColors.GoldDeep else BuffetColors.Chip,
        contentColor = if (selected) Color.White else BuffetColors.GoldDeep,
        border = if (selected) null else BorderStroke(1.dp, BuffetColors.Gold.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CreditCard,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (count > 0) "My Cards ($count)" else "My Cards",
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MealChipView(meal: MealTime, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) BuffetColors.Clay else BuffetColors.Chip,
        contentColor = if (selected) BuffetColors.OnClay else BuffetColors.TextSecondary,
        border = if (selected) null else BorderStroke(1.dp, BuffetColors.Border),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = meal.icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = meal.label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private val MealTime.icon: ImageVector
    get() = when (this) {
        MealTime.ALL_DAY -> Icons.Rounded.Restaurant
        MealTime.BREAKFAST -> Icons.Rounded.FreeBreakfast
        MealTime.BRUNCH -> Icons.Rounded.BrunchDining
        MealTime.LUNCH -> Icons.Rounded.LunchDining
        MealTime.DINNER -> Icons.Rounded.DinnerDining
    }

@Composable
private fun FilterChipView(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) BuffetColors.Accent else BuffetColors.Chip,
        contentColor = if (selected) BuffetColors.OnAccent else BuffetColors.TextSecondary,
        border = if (selected) null else BorderStroke(1.dp, BuffetColors.Border),
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun MetaRow(
    restaurants: Int,
    deals: Int,
    running: Boolean,
    sortOrder: SortOrder,
    cardCount: Int,
    onSortChange: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.5.dp,
                color = BuffetColors.Accent,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = if (running) {
                "Checking sources…"
            } else {
                val venues = "$restaurants restaurant${if (restaurants == 1) "" else "s"}"
                val offers = "$deals deal${if (deals == 1) "" else "s"}"
                "$venues · $offers"
            },
            fontSize = 11.5.sp,
            color = BuffetColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        SortMenu(
            sortOrder = sortOrder,
            cardCount = cardCount,
            onSortChange = onSortChange,
        )
        if (!running) {
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = onRefresh,
                shape = RoundedCornerShape(10.dp),
                color = BuffetColors.Chip,
                border = BorderStroke(1.dp, BuffetColors.Border),
                modifier = Modifier.size(32.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Check for new offers",
                        tint = BuffetColors.TextSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    sortOrder: SortOrder,
    cardCount: Int,
    onSortChange: (SortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = BuffetColors.Chip,
            border = BorderStroke(1.dp, BuffetColors.Border),
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "Sort offers",
                    tint = BuffetColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (sortOrder == SortOrder.MY_CARDS) {
                        "My cards ($cardCount)"
                    } else {
                        sortOrder.label
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BuffetColors.TextPrimary,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = BuffetColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.filter { it != SortOrder.MY_CARDS }.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = order.label,
                            fontSize = 13.sp,
                            color = if (order == sortOrder) BuffetColors.Accent else BuffetColors.TextPrimary,
                        )
                    },
                    onClick = {
                        onSortChange(order)
                        expanded = false
                    },
                    trailingIcon = {
                        if (order == sortOrder) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = BuffetColors.Accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestaurantCard(group: RestaurantGroup, onOpenOffer: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BuffetColors.Surface),
        border = BorderStroke(1.dp, BuffetColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box {
                OfferHero(imageUrl = group.heroImage, name = group.restaurant, height = 186.dp)
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1f to BuffetColors.Scrim.copy(alpha = 0.9f),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Every deal this restaurant runs sits side by side on the thumbnail.
                    FlowRow(
                        modifier = Modifier.widthIn(max = 232.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        group.dealLabels.forEach { label -> DealBadge(label) }
                    }
                    Spacer(Modifier.weight(1f))
                    RatingPill(group.rating)
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                ) {
                    Text(
                        text = postedLongLabel(group.newest),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.74f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = group.restaurant,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = listOfNotNull(
                        group.area,
                        if (group.offerCount > 1) "${group.offerCount} offers" else null,
                    ).joinToString(" · "),
                    fontSize = 11.5.sp,
                    color = BuffetColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                group.offers.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    OfferLine(item = item, onClick = { onOpenOffer(item.offer.id) })
                }
            }
        }
    }
}

/** One deal inside a restaurant's card; tapping it opens that deal's own ad and conditions. */
@Composable
private fun OfferLine(item: OfferUi, onClick: () -> Unit) {
    val offer = item.offer
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = BuffetColors.SurfaceHigh,
        border = BorderStroke(1.dp, BuffetColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = offer.headline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BuffetColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    ExpiryLine(offer.validUntil)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    PriceTagCompact(
                        priceBdt = offer.priceBdt,
                        typical = offer.priceIsEstimate(),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = offer.priceUnit ?: "per person",
                        fontSize = 10.sp,
                        color = BuffetColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = offer.paymentNote,
                    fontSize = 10.5.sp,
                    color = BuffetColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Open this deal",
                tint = BuffetColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    filtered: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Restaurant,
            contentDescription = null,
            tint = BuffetColors.Accent,
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (filtered) "Nothing matches this filter" else "No live buffet offers",
            style = MaterialTheme.typography.titleLarge,
            color = BuffetColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (filtered) {
                "Try clearing the search, or picking another category."
            } else {
                "Offers disappear on their own once they expire, and new ones are picked up " +
                    "automatically every 30 minutes."
            },
            style = MaterialTheme.typography.bodySmall,
            color = BuffetColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRefresh) { Text("Check now") }
        TextButton(onClick = onOpenSettings) { Text("Open settings") }
    }
}

/**
 * The wallet is empty and My Cards is on. This is not a failed filter — there is nothing to
 * filter by yet — so the feed is replaced by an explanation and the gold add button is the one
 * action on the screen.
 */
@Composable
private fun NoCardsState(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(BuffetColors.GoldSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CreditCard,
                contentDescription = null,
                tint = BuffetColors.GoldDeep,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No cards in your wallet",
            style = MaterialTheme.typography.titleLarge,
            color = BuffetColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add the credit or debit cards you carry, and I'll show only the buffets " +
                "they unlock. No card number, PIN or expiry is ever asked for.",
            style = MaterialTheme.typography.bodySmall,
            color = BuffetColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Tap the gold card button to add your first card.",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = BuffetColors.GoldDeep,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The gold add-a-card button. It floats over the empty corner of the page, never over the copy,
 * and pulses for two seconds when the empty wallet appears so the one action is noticed. The
 * icon is a bank card with a plus badge, not a generic "+".
 */
@Composable
private fun AddCardFab(onClick: () -> Unit) {
    var glowing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2_000L)
        glowing = false
    }

    Box(contentAlignment = Alignment.Center) {
        if (glowing) {
            val transition = rememberInfiniteTransition(label = "card-button-glow")
            val pulse by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "card-button-pulse",
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = 1f + 0.35f * pulse
                        scaleY = 1f + 0.35f * pulse
                        alpha = 0.45f * (1f - pulse)
                    }
                    .background(BuffetColors.Gold, CircleShape),
            )
        }

        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = BuffetColors.Surface,
            contentColor = BuffetColors.GoldDeep,
            border = BorderStroke(2.dp, BuffetColors.Gold),
            shadowElevation = 8.dp,
            modifier = Modifier.size(64.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.CreditCard,
                        contentDescription = "Add a card",
                        tint = BuffetColors.GoldDeep,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(17.dp)
                            .background(BuffetColors.Gold, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun OfferUi.matches(needle: String): Boolean {
    val haystack = listOf(
        offer.restaurant,
        offer.area,
        offer.title,
        offer.headline,
        offer.paymentNote,
    ).joinToString(" ").lowercase(Locale.ROOT)
    return haystack.contains(needle.lowercase(Locale.ROOT))
}
