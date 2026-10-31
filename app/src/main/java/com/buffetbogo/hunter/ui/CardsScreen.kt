package com.buffetbogo.hunter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buffetbogo.hunter.data.BankCards
import com.buffetbogo.hunter.data.CardCatalogue
import com.buffetbogo.hunter.data.CardProduct
import com.buffetbogo.hunter.data.UserCard
import com.buffetbogo.hunter.ui.theme.BuffetColors

/**
 * The diner's own cards. They choose a bank, then a product that bank issues, and the feed can
 * then keep only the offers those cards unlock. Nothing typed here is a card number: the wallet
 * holds a bank name and a product name, stored on this phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    cards: List<UserCard>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddCard: (UserCard) -> Unit,
    onRemoveCard: (UserCard) -> Unit,
) {
    val context = LocalContext.current
    val catalogue by produceState(initialValue = emptyList<BankCards>()) {
        value = CardCatalogue.load(context)
    }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val cardCount = catalogue.sumOf { it.cards.size }

    Scaffold(
        containerColor = BuffetColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = BuffetColors.TextPrimary,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "My cards",
                            style = MaterialTheme.typography.titleLarge,
                            color = BuffetColors.TextPrimary,
                        )
                        Text(
                            text = if (cards.isEmpty()) {
                                "No cards added yet"
                            } else {
                                "${cards.size} card${if (cards.size == 1) "" else "s"} in your wallet"
                            },
                            fontSize = 11.5.sp,
                            color = BuffetColors.TextSecondary,
                        )
                    }
                }
            }

            item {
                Surface(
                    color = BuffetColors.AccentSoft,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BuffetColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Rounded.CreditCard,
                            contentDescription = null,
                            tint = BuffetColors.Accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Add the credit and debit cards you carry. The feed's " +
                                "\"My cards\" order then keeps only the buffet deals those cards " +
                                "unlock. Your bank and card names stay on this phone — no card " +
                                "number, PIN or expiry is ever asked for.",
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = BuffetColors.TextPrimary,
                        )
                    }
                }
            }

            if (cards.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(BuffetColors.SurfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CreditCard,
                                contentDescription = null,
                                tint = BuffetColors.TextSecondary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Nothing in the wallet yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BuffetColors.TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Add one card to start sorting by the deals it unlocks.",
                            fontSize = 11.5.sp,
                            color = BuffetColors.TextSecondary,
                        )
                    }
                }
            } else {
                items(cards, key = { it.key }) { card ->
                    WalletRow(card = card, onRemove = { onRemoveCard(card) })
                }
            }

            item {
                Button(
                    onClick = { showAdd = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BuffetColors.Accent,
                        contentColor = BuffetColors.OnAccent,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (cards.isEmpty()) "Add your first card" else "Add another card",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            item {
                Text(
                    text = if (catalogue.isEmpty()) {
                        "Loading the bank list…"
                    } else {
                        "The lists come from the bundled BIN reference: $cardCount cards from " +
                            "${catalogue.size} banks. If your bank or card is missing, it is not " +
                            "in that reference yet."
                    },
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = BuffetColors.TextSecondary,
                )
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            containerColor = BuffetColors.Surface,
            contentColor = BuffetColors.TextPrimary,
        ) {
            AddCardSheet(
                catalogue = catalogue,
                existing = cards,
                onDismiss = { showAdd = false },
                onAdd = onAddCard,
            )
        }
    }
}

@Composable
private fun WalletRow(card: UserCard, onRemove: () -> Unit) {
    Surface(
        color = BuffetColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BuffetColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(BuffetColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CreditCard,
                    contentDescription = null,
                    tint = BuffetColors.Accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.bank,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BuffetColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = card.shortName,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BuffetColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = listOf(card.brand, card.type, card.tier)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = detail,
                        fontSize = 10.5.sp,
                        color = BuffetColors.TextSecondary,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove ${card.shortName}",
                    tint = BuffetColors.TextSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun AddCardSheet(
    catalogue: List<BankCards>,
    existing: List<UserCard>,
    onDismiss: () -> Unit,
    onAdd: (UserCard) -> Unit,
) {
    var bank by remember { mutableStateOf<BankCards?>(null) }
    var product by remember { mutableStateOf<CardProduct?>(null) }
    var bankMenu by remember { mutableStateOf(false) }
    var cardMenu by remember { mutableStateOf(false) }

    val chosen = bank?.let { selectedBank ->
        product?.let { selectedProduct ->
            UserCard(
                bank = selectedBank.bank,
                cardName = selectedProduct.name,
                brand = selectedProduct.brand,
                type = selectedProduct.type,
                tier = selectedProduct.tier,
            )
        }
    }
    val alreadyAdded = chosen != null && existing.any { it.key == chosen.key }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
    ) {
        Text(
            text = "Add a card",
            style = MaterialTheme.typography.titleLarge,
            color = BuffetColors.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Choose the bank that issued your card, then the card itself.",
            fontSize = 11.5.sp,
            color = BuffetColors.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))

        if (catalogue.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 1.6.dp,
                    color = BuffetColors.Accent,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Loading the bank list…",
                    fontSize = 12.sp,
                    color = BuffetColors.TextSecondary,
                )
            }
            return@Column
        }

        Box(Modifier.fillMaxWidth()) {
            PickerField(
                label = "Bank",
                value = bank?.bank,
                placeholder = "Choose your bank",
                enabled = true,
                onClick = { bankMenu = true },
            )
            DropdownMenu(
                expanded = bankMenu,
                onDismissRequest = { bankMenu = false },
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                catalogue.forEach { option ->
                    val selected = option.bank == bank?.bank
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.bank,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) BuffetColors.Accent else BuffetColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            bank = option
                            product = null
                            bankMenu = false
                        },
                        trailingIcon = {
                            if (selected) {
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

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth()) {
            PickerField(
                label = "Card",
                value = product?.let { it.shortName(bank?.bank.orEmpty()) },
                placeholder = if (bank == null) {
                    "Choose a bank first"
                } else {
                    "Choose your ${bank?.bank} card"
                },
                enabled = bank != null,
                onClick = { if (bank != null) cardMenu = true },
            )
            DropdownMenu(
                expanded = cardMenu,
                onDismissRequest = { cardMenu = false },
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                val selectedBank = bank
                if (selectedBank != null) {
                    selectedBank.cards.forEach { option ->
                        val selected = option.name == product?.name
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = option.shortName(selectedBank.bank),
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) BuffetColors.Accent else BuffetColors.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (option.detail.isNotBlank()) {
                                        Text(
                                            text = option.detail,
                                            fontSize = 10.5.sp,
                                            color = BuffetColors.TextSecondary,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                product = option
                                cardMenu = false
                            },
                            trailingIcon = {
                                if (selected) {
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

        if (alreadyAdded) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "This card is already in your wallet.",
                fontSize = 11.sp,
                color = BuffetColors.Clay,
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                chosen?.let(onAdd)
                onDismiss()
            },
            enabled = chosen != null && !alreadyAdded,
            colors = ButtonDefaults.buttonColors(
                containerColor = BuffetColors.Accent,
                contentColor = BuffetColors.OnAccent,
                disabledContainerColor = BuffetColors.SurfaceHigh,
                disabledContentColor = BuffetColors.TextSecondary,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Add card",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = BuffetColors.TextSecondary, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Only the bank and product name are saved, on this phone. No card number, " +
                "PIN or expiry date is collected.",
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            color = BuffetColors.TextSecondary,
        )
    }
}

/** A select-style field: tapping it opens the matching drop-down list. */
@Composable
private fun PickerField(
    label: String,
    value: String?,
    placeholder: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = BuffetColors.SurfaceHigh,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BuffetColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BuffetColors.TextSecondary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value ?: placeholder,
                    fontSize = 13.5.sp,
                    fontWeight = if (value == null) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (enabled) BuffetColors.TextPrimary else BuffetColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = BuffetColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

