package com.buffetbogo.hunter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.buffetbogo.hunter.data.AppSettings
import com.buffetbogo.hunter.ui.theme.BuffetColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    sync: SyncState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSaveKeys: (braveKey: String, placesKey: String) -> Unit,
    onSaveFacebookToken: (String) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onRestoreSamples: () -> Unit,
    onRefreshRatings: () -> Unit,
    onOpenCards: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var braveKey by remember(settings.braveApiKey) { mutableStateOf(settings.braveApiKey) }
    var placesKey by remember(settings.placesApiKey) { mutableStateOf(settings.placesApiKey) }
    var braveVisible by rememberSaveable { mutableStateOf(false) }
    var placesVisible by rememberSaveable { mutableStateOf(false) }
    var facebookToken by remember(settings.facebookToken) { mutableStateOf(settings.facebookToken) }
    var facebookVisible by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onToggleNotifications(true)
    }

    val updateNotifications: (Boolean) -> Unit = { enabled ->
        val needsPermission = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onToggleNotifications(enabled)
        }
    }

    Scaffold(
        containerColor = BuffetColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .foodBackdrop()
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
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = BuffetColors.TextPrimary,
                    )
                }
            }

            item {
                SectionCard(title = "My cards") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = if (settings.cards.isEmpty()) {
                                "EMPTY"
                            } else {
                                "${settings.cards.size} ADDED"
                            },
                            positive = settings.cards.isNotEmpty(),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (settings.cards.isEmpty()) {
                                "No cards yet."
                            } else {
                                settings.cards.joinToString(", ") { it.shortName }
                            },
                            fontSize = 11.5.sp,
                            color = BuffetColors.TextSecondary,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Add the credit and debit cards you carry. The feed's \"My cards\" " +
                            "order then shows only the deals those cards unlock. The bank and " +
                            "card names stay on this phone.",
                        fontSize = 11.5.sp,
                        color = BuffetColors.TextSecondary,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenCards,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BuffetColors.Border),
                    ) {
                        Text(
                            text = if (settings.cards.isEmpty()) "Add a card" else "Manage cards",
                            fontSize = 12.5.sp,
                            color = BuffetColors.TextPrimary,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Star ratings from Google") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = if (settings.canFetchRatings) "CONNECTED" else "CACHED",
                            positive = settings.canFetchRatings,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (settings.canFetchRatings) {
                                "Ratings refresh from Google Places every 30 days."
                            } else {
                                "Showing the ratings checked on 25 Oct 2026."
                            },
                            fontSize = 11.5.sp,
                            color = BuffetColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Google does not allow keyless access to review scores. " +
                            "A Google Places API key is free to create and the free monthly quota " +
                            "covers roughly 30 venues a month.",
                        fontSize = 11.5.sp,
                        color = BuffetColors.TextSecondary,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = placesKey,
                        onValueChange = { placesKey = it },
                        label = { Text("Google Places API key") },
                        singleLine = true,
                        visualTransformation = if (placesVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { placesVisible = !placesVisible }) {
                                Icon(
                                    imageVector = if (placesVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (placesVisible) "Hide key" else "Show key",
                                )
                            }
                        },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (settings.placesKeyFromBuild) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Using the key baked into this build.",
                            fontSize = 11.sp,
                            color = BuffetColors.Green,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSaveKeys(braveKey, placesKey) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BuffetColors.Accent,
                                contentColor = BuffetColors.OnAccent,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Save key", fontSize = 12.5.sp)
                        }
                        OutlinedButton(
                            onClick = onRefreshRatings,
                            enabled = !sync.running,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BuffetColors.Border),
                        ) {
                            Text("Refresh ratings", fontSize = 12.5.sp, color = BuffetColors.TextPrimary)
                        }
                    }
                    TextButton(onClick = { uriHandler.openUri("https://console.cloud.google.com/google/maps-apis/credentials") }) {
                        Text("Get a Places API key", color = BuffetColors.Accent, fontSize = 12.5.sp)
                    }
                }
            }

            item {
                SectionCard(title = "Facebook posts (optional)") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = if (settings.usesFacebook) "CONNECTED" else "OFF",
                            positive = settings.usesFacebook,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (settings.usesFacebook) {
                                "Reading the venues' official pages for their own posts."
                            } else {
                                "Offers still link to each restaurant's official page."
                            },
                            fontSize = 11.5.sp,
                            color = BuffetColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Facebook is where Dhaka's hotels publish these offers. Meta only lets " +
                            "an app read another page's posts with Page Public Content Access, which has " +
                            "to be approved for your Meta app. With a Page token here the app reads each " +
                            "venue's posts directly — price, dates, timings and conditions — and those " +
                            "posts become the offer details. Without it, the app links to the venue's own " +
                            "public page instead.",
                        fontSize = 11.5.sp,
                        color = BuffetColors.TextSecondary,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = facebookToken,
                        onValueChange = { facebookToken = it },
                        label = { Text("Facebook Page / User access token") },
                        singleLine = true,
                        visualTransformation = if (facebookVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { facebookVisible = !facebookVisible }) {
                                Icon(
                                    imageVector = if (facebookVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (facebookVisible) "Hide token" else "Show token",
                                )
                            }
                        },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (settings.facebookTokenFromBuild) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Using the token baked into this build.",
                            fontSize = 11.sp,
                            color = BuffetColors.Green,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSaveFacebookToken(facebookToken) },
                            enabled = !sync.running,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BuffetColors.Accent,
                                contentColor = BuffetColors.OnAccent,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Save & fetch posts", fontSize = 12.5.sp)
                        }
                        OutlinedButton(
                            onClick = { onSaveFacebookToken("") },
                            enabled = !sync.running && settings.usesFacebook,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BuffetColors.Border),
                        ) {
                            Text("Turn off", fontSize = 12.5.sp, color = BuffetColors.TextPrimary)
                        }
                    }
                    TextButton(
                        onClick = {
                            uriHandler.openUri(
                                "https://developers.facebook.com/docs/features-reference/" +
                                    "page-public-content-access/",
                            )
                        },
                    ) {
                        Text("What Meta requires", color = BuffetColors.Accent, fontSize = 12.5.sp)
                    }
                }
            }

            item {
                SectionCard(title = "Alerts") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("New offer notifications", fontSize = 13.5.sp, color = BuffetColors.TextPrimary)
                            Text(
                                "Alert me when a new buffet offer appears.",
                                fontSize = 11.5.sp,
                                color = BuffetColors.TextSecondary,
                            )
                        }
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = updateNotifications,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BuffetColors.OnAccent,
                                checkedTrackColor = BuffetColors.Accent,
                                uncheckedThumbColor = BuffetColors.TextSecondary,
                                uncheckedTrackColor = BuffetColors.Border,
                            ),
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Live sources") {
                    Text(
                        text = "Discovery runs with no setup and no account:",
                        fontSize = 12.sp,
                        color = BuffetColors.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Bullet(
                        "Seven bank dining pages — Standard Chartered, City Bank, Mutual Trust Bank, " +
                            "Prime Bank, Trust Bank, Shimanto Bank and NCC Bank — list the Dhaka " +
                            "venues currently running a BOGO or Eat-N-Pay buffet.",
                    )
                    Bullet("Google News RSS — dated press coverage of new buffet campaigns.")
                    Bullet("The checked price book — a typical buffet rate when an ad prints no price.")
                    Bullet("Each venue's own Facebook page and website — the ad every card links to.")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Last check: ${lastSyncLabel(settings.lastSyncAt)}",
                        fontSize = 11.5.sp,
                        color = BuffetColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onSyncNow,
                            enabled = !sync.running,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BuffetColors.Border),
                        ) {
                            Text(
                                text = if (sync.running) "Checking…" else "Check now",
                                fontSize = 12.5.sp,
                                color = BuffetColors.TextPrimary,
                            )
                        }
                        OutlinedButton(
                            onClick = onRestoreSamples,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BuffetColors.Border),
                        ) {
                            Text("Reload listings", fontSize = 12.5.sp, color = BuffetColors.TextPrimary)
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Optional: wider web search") {
                    Text(
                        text = "Prices missing from an ad are looked up with a keyless DuckDuckGo " +
                            "search by default. A Brave key upgrades that lookup and adds general web " +
                            "results, including public Facebook pages that search engines have indexed. " +
                            "The app works without it.",
                        fontSize = 11.5.sp,
                        color = BuffetColors.TextSecondary,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = braveKey,
                        onValueChange = { braveKey = it },
                        label = { Text("Brave Search API key") },
                        singleLine = true,
                        visualTransformation = if (braveVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { braveVisible = !braveVisible }) {
                                Icon(
                                    imageVector = if (braveVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (braveVisible) "Hide key" else "Show key",
                                )
                            }
                        },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { uriHandler.openUri("https://brave.com/search/api/") }) {
                        Text("Get a Brave Search key", color = BuffetColors.Accent, fontSize = 12.5.sp)
                    }
                }
            }

            item {
                SectionCard(title = "How offers are found") {
                    Bullet(
                        "Checks run every 30 minutes, and again when you open the app with a feed " +
                            "older than 30 minutes.",
                    )
                    Bullet("A bank page or news story is only used to discover an offer. The card always links to the restaurant's own post or page, never to the bank.")
                    Bullet("When the restaurant's own post is available it supplies the price, the dates, the serving times and the conditions.")
                    Bullet("Every card carries a price: the post's own figure, the restaurant's checked buffet rate, or a price found by searching the restaurant. Filled-in prices are marked typical.")
                    Bullet("The default order is by end date, so the offers that run out first are at the top.")
                    Bullet("Expired offers disappear on their own.")
                    Bullet("A news-only listing with no stated end date is kept for 21 days.")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = BuffetColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BuffetColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = BuffetColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun StatusPill(text: String, positive: Boolean) {
    Surface(
        color = if (positive) BuffetColors.GreenSoft else BuffetColors.Chip,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, BuffetColors.Border),
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (positive) BuffetColors.Green else BuffetColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(bottom = 5.dp)) {
        Text("•", fontSize = 12.sp, color = BuffetColors.Accent)
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            fontSize = 11.5.sp,
            color = BuffetColors.TextSecondary,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = BuffetColors.TextPrimary,
    unfocusedTextColor = BuffetColors.TextPrimary,
    focusedBorderColor = BuffetColors.Accent,
    unfocusedBorderColor = BuffetColors.Border,
    focusedLabelColor = BuffetColors.Accent,
    unfocusedLabelColor = BuffetColors.TextSecondary,
    cursorColor = BuffetColors.Accent,
    // Cream fields sit inside white cards, so the input area is visible without a heavy border.
    focusedContainerColor = BuffetColors.SurfaceHigh,
    unfocusedContainerColor = BuffetColors.SurfaceHigh,
    focusedTrailingIconColor = BuffetColors.TextSecondary,
    unfocusedTrailingIconColor = BuffetColors.TextSecondary,
)

private fun lastSyncLabel(at: Long): String {
    if (at == 0L) return "never"
    val formatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.US)
    return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(formatter)
}
