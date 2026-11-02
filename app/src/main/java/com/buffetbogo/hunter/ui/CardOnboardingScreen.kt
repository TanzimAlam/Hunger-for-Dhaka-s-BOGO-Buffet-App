package com.buffetbogo.hunter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buffetbogo.hunter.data.BankCards
import com.buffetbogo.hunter.data.CardCatalogue
import com.buffetbogo.hunter.data.CardProduct
import com.buffetbogo.hunter.data.UserCard
import com.buffetbogo.hunter.ui.theme.BuffetColors

/**
 * The card input page: bank first, then that bank's product.
 *
 * It is the first screen on a fresh install, and the same page opens again from the My Cards
 * empty state. The three actions at the bottom are the whole flow: [onAddCard] saves this page
 * and resets it for another card, Done saves it and hands over to the feed, and Skip leaves with
 * nothing added. Only names are ever collected — no card number, expiry or PIN.
 */
@Composable
fun CardOnboardingScreen(
    cards: List<UserCard>,
    firstRun: Boolean,
    showBack: Boolean,
    onAddCard: (UserCard) -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onDone: (UserCard?) -> Unit,
) {
    val context = LocalContext.current
    val catalogue by produceState(initialValue = emptyList<BankCards>()) {
        value = CardCatalogue.load(context)
    }
    var bank by remember { mutableStateOf<BankCards?>(null) }
    var product by remember { mutableStateOf<CardProduct?>(null) }

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
    val alreadyAdded = chosen != null && cards.any { it.key == chosen.key }

    Scaffold(
        containerColor = BuffetColors.Background,
        bottomBar = {
            OnboardingActions(
                canAddAnother = chosen != null && !alreadyAdded,
                onAddAnother = {
                    chosen?.let(onAddCard)
                    bank = null
                    product = null
                },
                onDone = { onDone(chosen) },
                onSkip = onSkip,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .foodBackdrop()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = BuffetColors.TextPrimary,
                        )
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hunger for Dhaka's",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = BuffetColors.TextSecondary,
                        )
                        Text(
                            text = "BOGO Buffet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = BuffetColors.TextPrimary,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = if (firstRun) "Which cards\ndo you carry?" else "Add a card",
                    style = MaterialTheme.typography.headlineSmall,
                    color = BuffetColors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (firstRun) {
                        "Pick the bank and the card you hold. I'll show the buffets those cards " +
                            "unlock — and you can skip this for now."
                    } else {
                        "Pick another bank and card to widen the offers you can see."
                    },
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = BuffetColors.TextSecondary,
                )

                Spacer(Modifier.height(20.dp))
                Surface(
                    color = BuffetColors.Surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, BuffetColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CreditCard,
                                contentDescription = null,
                                tint = BuffetColors.GoldDeep,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "YOUR BANK & CARD",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp,
                                color = BuffetColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(14.dp))

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
                        } else {
                            CardPickerFields(
                                catalogue = catalogue,
                                bank = bank,
                                product = product,
                                onBankSelected = { option ->
                                    bank = option
                                    product = null
                                },
                                onProductSelected = { option -> product = option },
                            )
                        }

                        if (alreadyAdded) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "This card is already in your wallet.",
                                fontSize = 11.sp,
                                color = BuffetColors.Clay,
                            )
                        }

                        if (cards.isNotEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = if (cards.size == 1) {
                                    "1 CARD ADDED"
                                } else {
                                    "${cards.size} CARDS ADDED"
                                },
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp,
                                color = BuffetColors.GoldDeep,
                            )
                            Spacer(Modifier.height(8.dp))
                            WalletPills(cards)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Only the bank and product name are saved, on this phone. No card " +
                        "number, PIN or expiry date is ever asked for.",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = BuffetColors.TextSecondary,
                )
            }
        }
    }
}

/** The cards saved so far, short enough to sit under the pickers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletPills(cards: List<UserCard>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        cards.forEach { card ->
            Surface(
                color = BuffetColors.GoldSoft,
                contentColor = BuffetColors.GoldDeep,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BuffetColors.Gold),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = card.shortName,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingActions(
    canAddAnother: Boolean,
    onAddAnother: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(color = BuffetColors.Surface, shadowElevation = 12.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onAddAnother,
                    enabled = canAddAnother,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (canAddAnother) BuffetColors.Gold else BuffetColors.Border,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BuffetColors.GoldDeep,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Add another card",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BuffetColors.Accent,
                        contentColor = BuffetColors.OnAccent,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Done",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Skip for now",
                    fontSize = 12.5.sp,
                    color = BuffetColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
