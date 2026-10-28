package com.buffetbogo.hunter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.buffetbogo.hunter.R
import com.buffetbogo.hunter.data.Sources
import com.buffetbogo.hunter.ui.theme.BuffetColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Taken from the emblem and the table it advertises: steel blue, clay, herb green, saffron, ink.
private val heroPalettes = listOf(
    listOf(Color(0xFF2F5B82), Color(0xFF5C88AE)),
    listOf(Color(0xFF8C4327), Color(0xFFB96C48)),
    listOf(Color(0xFF35513A), Color(0xFF5F8265)),
    listOf(Color(0xFF7A5626), Color(0xFFA8834A)),
    listOf(Color(0xFF25333F), Color(0xFF4C6376)),
)

/** The server's own emblem, rounded into the header tile. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = "Hunger for Dhaka's BOGO Buffet",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp)),
    )
}

/** The small sage "LIVE" pill next to the app name. */
@Composable
fun LiveChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = BuffetColors.Green,
        contentColor = BuffetColors.OnGreen,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = "LIVE",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Cream deal badge shown over the photo, e.g. "Buy 1 Get 1". */
@Composable
fun DealBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = BuffetColors.Accent,
        contentColor = BuffetColors.OnAccent,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Dark translucent star pill over the photo. Hidden when no Google rating is known. */
@Composable
fun RatingPill(
    rating: Double?,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    if (rating == null) return
    // Sits over the photo, so it stays ink-dark with white type whatever the page colour is.
    Surface(
        modifier = modifier,
        color = BuffetColors.Scrim.copy(alpha = 0.78f),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "Google rating",
                tint = BuffetColors.Star,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            if (count != null && count > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "(${formatCount(count)})",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }
    }
}

/**
 * "৳8,950 per person". A price that was filled in from the price book or a web search is
 * marked "typical" so an estimate is never mistaken for the ad's own figure.
 */
@Composable
fun PriceTag(
    priceBdt: Int?,
    unit: String? = null,
    typical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        if (priceBdt == null) {
            Text(
                text = "Price being checked",
                fontSize = 12.5.sp,
                color = BuffetColors.TextSecondary,
            )
            return@Row
        }
        Text(
            text = "৳",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = BuffetColors.Accent,
            modifier = Modifier.padding(bottom = 1.dp, end = 1.dp),
        )
        Text(
            text = formatBdt(priceBdt),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BuffetColors.TextPrimary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = unit ?: "per person",
            fontSize = 10.5.sp,
            color = BuffetColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        if (typical) {
            Spacer(Modifier.width(6.dp))
            Surface(
                color = BuffetColors.AmberSoft,
                contentColor = BuffetColors.Amber,
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                Text(
                    text = "typical",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * One deal's price inside a restaurant card. Smaller than [PriceTag] so several deals fit on a
 * single card, but still shows the exact figure and flags a fallback estimate.
 */
@Composable
fun PriceTagCompact(
    priceBdt: Int?,
    typical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        if (priceBdt == null) {
            Text(
                text = "Price being checked",
                fontSize = 11.sp,
                color = BuffetColors.TextSecondary,
            )
            return@Row
        }
        Text(
            text = "৳",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = BuffetColors.Accent,
            modifier = Modifier.padding(bottom = 1.dp, end = 1.dp),
        )
        Text(
            text = formatBdt(priceBdt),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = BuffetColors.TextPrimary,
        )
        if (typical) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "typical",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = BuffetColors.Amber,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** Payment / card condition pill with the bank glyph. */
@Composable
fun PaymentChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = BuffetColors.Surface,
        contentColor = BuffetColors.TextPrimary,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BuffetColors.Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalance,
                contentDescription = null,
                tint = BuffetColors.Accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 11.5.sp,
                color = BuffetColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** Green countdown line in the card footer. */
@Composable
fun ExpiryLine(until: Long?, modifier: Modifier = Modifier) {
    val soon = endsSoon(until)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (soon) BuffetColors.Green else BuffetColors.TextSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = expiryCountdown(until),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (soon) BuffetColors.Green else BuffetColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Photo filling the card, with a gradient fallback when the ad carried no image. */
@Composable
fun OfferHero(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    val palette = heroPalettes[Math.floorMod(name.hashCode(), heroPalettes.size)]
    val base = modifier
        .fillMaxWidth()
        .height(height)

    if (imageUrl.isNullOrBlank()) {
        HeroFallback(name, palette, base)
        return
    }

    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = "Ad image for $name",
        contentScale = ContentScale.Crop,
        modifier = base,
        loading = { HeroFallback(name, palette, Modifier.fillMaxSize()) },
        error = { HeroFallback(name, palette, Modifier.fillMaxSize()) },
    )
}

@Composable
private fun HeroFallback(name: String, palette: List<Color>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase(Locale.US).ifBlank { "B" },
            color = Color.White.copy(alpha = 0.92f),
            fontWeight = FontWeight.Bold,
            fontSize = 54.sp,
        )
    }
}

/** "Today, 10:30 AM", "Yesterday, 6:30 PM" or "12 Oct". */
fun postedLongLabel(at: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val posted = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = posted.toLocalDate()
    return when (ChronoUnit.DAYS.between(day, today)) {
        0L -> "Today, " + posted.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        1L -> "Yesterday, " + posted.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        else -> day.format(
            DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMM" else "d MMM yyyy", Locale.US),
        )
    }
}

/** "Expires in 5 Days", "Expires tomorrow", "Expires today" or "Ongoing offer". */
fun expiryCountdown(until: Long?, now: Long = System.currentTimeMillis()): String {
    if (until == null) return "Ongoing offer"
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(until).atZone(zone).toLocalDate()
    return when (val days = ChronoUnit.DAYS.between(today, end)) {
        in Long.MIN_VALUE..-1L -> "Expired"
        0L -> "Expires today"
        1L -> "Expires tomorrow"
        else -> "Expires in $days Days"
    }
}

/** "Ends today", "Ends 24 Oct" or "Ongoing" when no expiry was found. */
fun expiryLabel(until: Long?): String {
    if (until == null) return "Ongoing"
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val endDate = Instant.ofEpochMilli(until).atZone(zone).toLocalDate()
    return when (ChronoUnit.DAYS.between(today, endDate)) {
        in Long.MIN_VALUE..-1L -> "Expired"
        0L -> "Ends today"
        1L -> "Ends tomorrow"
        else -> "Ends " + endDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.US))
    }
}

fun endsSoon(until: Long?, now: Long = System.currentTimeMillis()): Boolean {
    if (until == null) return false
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(until).atZone(zone).toLocalDate(),
    )
    return days in 0..3
}

fun fullValidityLabel(validFrom: Long?, validUntil: Long?): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    val zone = ZoneId.systemDefault()
    val from = validFrom?.let { Instant.ofEpochMilli(it).atZone(zone).format(formatter) }
    val until = validUntil?.let { Instant.ofEpochMilli(it).atZone(zone).format(formatter) }
    return when {
        from != null && until != null -> "$from – $until"
        until != null -> "Until $until"
        else -> "No end date stated — confirm with the restaurant"
    }
}

fun sourceLabel(source: String): String = when (source) {
    Sources.FACEBOOK -> "Restaurant's Facebook post"
    Sources.GOOGLE -> "Web search"
    Sources.CURATED -> "Checked listing"
    Sources.BANK -> "Bank card-offer page"
    Sources.NEWS -> "News report"
    else -> "Added"
}

fun formatBdt(price: Int): String = String.format(Locale.US, "%,d", price)

fun formatCount(count: Int): String =
    if (count >= 1_000) String.format(Locale.US, "%.1fk", count / 1_000.0) else count.toString()
