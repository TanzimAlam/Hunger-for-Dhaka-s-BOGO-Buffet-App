package com.buffetbogo.hunter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buffetbogo.hunter.data.OfferUi
import com.buffetbogo.hunter.data.priceIsEstimate
import com.buffetbogo.hunter.ui.theme.BuffetColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferDetailScreen(
    item: OfferUi,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onHide: () -> Unit,
) {
    val offer = item.offer
    val uriHandler = LocalUriHandler.current
    val canOpenLink = offer.sourceUrl.isNotBlank()

    Scaffold(
        containerColor = BuffetColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
            contentPadding = PaddingValues(bottom = 34.dp),
        ) {
            item {
                Box {
                    OfferHero(imageUrl = offer.imageUrl, name = offer.restaurant, height = 250.dp)
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to BuffetColors.Background.copy(alpha = 0.35f),
                                    0.45f to Color.Transparent,
                                    1f to BuffetColors.Background,
                                ),
                            ),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = onBack,
                            shape = CircleShape,
                            color = BuffetColors.Background.copy(alpha = 0.62f),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        DealBadge(offer.headline)
                        if (item.rating != null) {
                            Spacer(Modifier.width(8.dp))
                            RatingPill(item.rating)
                        }
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    ) {
                        Text(
                            text = postedLongLabel(offer.postedAt),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = offer.restaurant,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text(
                        text = offer.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BuffetColors.TextPrimary,
                        lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    PriceTag(
                        priceBdt = offer.priceBdt,
                        unit = offer.priceUnit,
                        typical = offer.priceIsEstimate(),
                    )
                    if (offer.priceIsEstimate()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "The ad did not print a price. This is the restaurant's " +
                                "typical buffet rate, checked online — confirm it when booking.",
                            fontSize = 10.5.sp,
                            color = BuffetColors.TextSecondary,
                            lineHeight = 15.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = listOfNotNull(
                            offer.area,
                            item.rating?.let { rating ->
                                "Google " + String.format(Locale.US, "%.1f", rating) +
                                    (item.ratingCount?.let { " · ${formatCount(it)} reviews" } ?: "")
                            },
                        ).joinToString(" · "),
                        fontSize = 11.5.sp,
                        color = BuffetColors.TextSecondary,
                    )

                    Spacer(Modifier.height(16.dp))
                    PaymentChip(text = offer.paymentNote, modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(14.dp))
                    Timeline(start = offer.validFrom, end = offer.validUntil)

                    Spacer(Modifier.height(22.dp))
                    SectionTitle("The offer")
                    Text(
                        text = offer.details.ifBlank { offer.title },
                        fontSize = 13.5.sp,
                        color = BuffetColors.TextPrimary.copy(alpha = 0.9f),
                        lineHeight = 20.sp,
                    )

                    Spacer(Modifier.height(20.dp))
                    SectionTitle("Payment & conditions")
                    Text(
                        text = offer.paymentNote,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BuffetColors.TextPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = offer.terms.ifBlank { "Confirm the conditions with the restaurant." },
                        fontSize = 12.5.sp,
                        color = BuffetColors.TextSecondary,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(20.dp))
                    SectionTitle("Source")
                    Text(
                        text = "${sourceLabel(offer.source)} · listed ${postedLongLabel(offer.postedAt)}",
                        fontSize = 12.5.sp,
                        color = BuffetColors.TextSecondary,
                    )

                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { uriHandler.openUri(offer.sourceUrl) },
                        enabled = canOpenLink,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BuffetColors.Accent,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier
                                .size(17.dp)
                                .padding(end = 0.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (canOpenLink) "Open the original ad" else "No link available",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onHide,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BuffetColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = BuffetColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Hide this offer",
                            color = BuffetColors.TextSecondary,
                            fontSize = 13.sp,
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Offers are collected automatically and can change without notice. " +
                            "Confirm availability, price and card conditions with the restaurant before booking.",
                        fontSize = 11.5.sp,
                        color = BuffetColors.TextSecondary,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Timeline(start: Long?, end: Long?) {
    val soon = endsSoon(end)
    Surface(
        color = BuffetColors.SurfaceHigh,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BuffetColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = if (soon) BuffetColors.Green else BuffetColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = expiryCountdown(end),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (soon) BuffetColors.Green else BuffetColors.TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = fullValidityLabel(start, end),
                    fontSize = 11.5.sp,
                    color = BuffetColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(Locale.US),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = BuffetColors.Accent,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
