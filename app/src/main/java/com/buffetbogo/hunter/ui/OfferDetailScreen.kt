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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buffetbogo.hunter.data.AdLinkState
import com.buffetbogo.hunter.data.OfferUi
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.data.adLinkState
import com.buffetbogo.hunter.data.adToOpen
import com.buffetbogo.hunter.data.facebookSearchUrl
import com.buffetbogo.hunter.data.hasExactAdLink
import com.buffetbogo.hunter.data.priceIsEstimate
import com.buffetbogo.hunter.ui.theme.BuffetColors
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adLink = offer.adToOpen()
    val exactAd = offer.hasExactAdLink()
    val adState = offer.adLinkState()
    val adTarget = adLink ?: offer.facebookSearchUrl()

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
                                    0f to BuffetColors.Scrim.copy(alpha = 0.45f),
                                    0.4f to Color.Transparent,
                                    1f to BuffetColors.Scrim.copy(alpha = 0.92f),
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
                            color = BuffetColors.Scrim.copy(alpha = 0.55f),
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
                    Timeline(start = offer.validFrom, end = offer.validUntil, timing = offer.timing)

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
                    SectionTitle("The ad")
                    if (adState != AdLinkState.DIRECT) {
                        AdLinkNotice(
                            state = adState,
                            restaurant = offer.restaurant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    AdReference(
                        label = adLink?.let { offer.adLabel ?: "Restaurant page" }
                            ?: "Facebook search",
                        url = adLink ?: offer.facebookSearchUrl(),
                        restaurant = offer.restaurant,
                        note = discoveryNote(
                            source = offer.source,
                            restaurant = offer.restaurant,
                            posted = offer.postedAt,
                            linked = adLink != null,
                        ),
                        isSearch = adLink == null,
                    )

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            val message = when (LinkOpener.open(context, adTarget)) {
                                LinkOpener.Result.OPENED -> null
                                LinkOpener.Result.COPIED ->
                                    "No app could open that link, so the address is on your clipboard."
                                LinkOpener.Result.UNUSABLE ->
                                    "This offer's ad link is broken. Try again after the next refresh."
                            }
                            message?.let { text ->
                                scope.launch { snackbarHostState.showSnackbar(text) }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BuffetColors.Accent,
                            contentColor = BuffetColors.OnAccent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                exactAd -> "Open the original ad"
                                adLink != null -> "Open ${offer.restaurant}'s page"
                                else -> "Search Facebook for this offer"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
private fun Timeline(start: Long?, end: Long?, timing: String? = null) {
    val soon = endsSoon(end)
    Surface(
        color = BuffetColors.Surface,
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
                if (!timing.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Restaurant,
                            contentDescription = null,
                            tint = BuffetColors.TextSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Served $timing",
                            fontSize = 11.5.sp,
                            color = BuffetColors.TextPrimary.copy(alpha = 0.85f),
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Says plainly when the button below cannot open the deal itself.
 *
 * Some venues publish a deal only through a bank (Café Rio and Doreen are the bundled
 * examples). Their own page may never mention it, or cannot be found at all. Rather than
 * dressing a bank page or a bare Facebook search up as "the original ad", the card admits the
 * gap and tells the user what the button will actually do.
 */
@Composable
private fun AdLinkNotice(
    state: AdLinkState,
    restaurant: String,
    modifier: Modifier = Modifier,
) {
    val title: String
    val body: String
    when (state) {
        AdLinkState.NONE -> {
            title = "No verified ad for this offer"
            body = "We could not find a post or page from $restaurant that carries this deal, " +
                "so the button below opens a Facebook search instead. Treat the price and " +
                "conditions here as unconfirmed and check them with the restaurant."
        }
        AdLinkState.GENERIC_PAGE -> {
            title = "The restaurant's page, not the deal's own post"
            body = "We found $restaurant's official page, but not the post for this offer. " +
                "Its latest promotions sit on that page — look there before relying on the " +
                "details above."
        }
        AdLinkState.DIRECT -> return
    }

    Surface(
        color = BuffetColors.ClaySoft,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BuffetColors.Clay.copy(alpha = 0.30f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = BuffetColors.Clay,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp),
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BuffetColors.Clay,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = body,
                    fontSize = 11.sp,
                    color = BuffetColors.TextPrimary.copy(alpha = 0.78f),
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

/**
 * The link card above the "Open the original ad" button: where the link goes and why it is the
 * right place to check price, timings and conditions.
 */
@Composable
private fun AdReference(
    label: String,
    url: String,
    restaurant: String,
    note: String,
    isSearch: Boolean = false,
) {
    Surface(
        color = BuffetColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BuffetColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = BuffetColors.Accent,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(17.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BuffetColors.TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = hostOf(url),
                    fontSize = 11.5.sp,
                    color = BuffetColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isSearch) {
                        "No restaurant link is confirmed for this deal, so this opens a Facebook " +
                            "search instead of pretending to be the ad." +
                            if (note.isNotBlank()) " $note" else ""
                    } else {
                        "$restaurant publishes the price, dates, timings and conditions here." +
                            if (note.isNotBlank()) " $note" else ""
                    },
                    fontSize = 11.sp,
                    color = BuffetColors.TextSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

/** Explains how the app found the offer without ever advertising a bank as the ad's home. */
private fun discoveryNote(
    source: String,
    restaurant: String,
    posted: Long,
    linked: Boolean,
): String {
    return when {
        !linked -> "No post or page has been matched yet, so the button opens a Facebook search."
        source == Sources.BANK ->
            "Listed on a bank card-offer page on ${postedLongLabel(posted)}; the link above is " +
                "$restaurant's own."
        source == Sources.NEWS ->
            "Reported in the press on ${postedLongLabel(posted)}; the link above is " +
                "$restaurant's own."
        else -> ""
    }.trim()
}

private fun hostOf(url: String): String =
    url.substringAfter("://", url).substringBefore("/").removePrefix("www.")

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
