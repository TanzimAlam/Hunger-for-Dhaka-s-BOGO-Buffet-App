package com.buffetbogo.hunter.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.buffetbogo.hunter.MainActivity
import com.buffetbogo.hunter.R
import com.buffetbogo.hunter.data.Offer
import com.buffetbogo.hunter.data.adToOpen
import com.buffetbogo.hunter.data.facebookSearchUrl
import com.buffetbogo.hunter.ui.expiryLabel
import java.util.Locale

object Notifier {

    private const val CHANNEL_ID = "buffet_offers"
    private const val GROUP = "buffet_offers_group"

    /** Enough to be useful without burying the notification shade in one sync's finds. */
    private const val MAX_NOTIFICATIONS = 5

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Posts one notification per new offer, each opening that offer's own detail screen. Offers
     * are never bundled into a single "N new offers" alert, because tapping that could not take
     * the user to any one ad.
     */
    fun notifyNewOffers(context: Context, offers: List<Offer>) {
        if (offers.isEmpty()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        offers.sortedByDescending { it.postedAt }
            .take(MAX_NOTIFICATIONS)
            .forEach { offer -> runCatching { post(context, offer) } }
    }

    private fun post(context: Context, offer: Offer) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DeepLinks.EXTRA_OFFER_ID, offer.id)
        }
        val requestCode = offer.id.hashCode() and 0x7fffffff
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val price = offer.priceBdt?.let { "৳" + String.format(Locale.US, "%,d", it) }
        val body = listOfNotNull(
            price?.plus(offer.priceUnit?.let { unit -> " $unit" }.orEmpty()),
            expiryLabel(offer.validUntil),
            offer.paymentNote.takeIf { it != "No card or payment condition mentioned" },
        ).joinToString(" · ")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${offer.headline} · ${offer.restaurant}")
            .setContentText(body.ifBlank { offer.area })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    listOf(body, offer.title).filter { it.isNotBlank() }.joinToString("\n"),
                ),
            )
            .setWhen(offer.postedAt)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP)
            .setContentIntent(contentIntent)

        // Second action: straight to the venue's ad, for people who already know they want it.
        val adUri = offer.adToOpen() ?: offer.facebookSearchUrl()
        val adIntent = Intent(Intent.ACTION_VIEW, Uri.parse(adUri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val adPending = PendingIntent.getActivity(
            context,
            requestCode xor 0x5f5e100,
            adIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(0, "Open the ad", adPending)

        NotificationManagerCompat.from(context).notify(offerNotificationId(offer), builder.build())
    }

    /** Stable per offer, so a re-sync replaces the same alert instead of stacking another. */
    private fun offerNotificationId(offer: Offer): Int = (offer.id.hashCode() and 0x7fffffff).coerceAtLeast(1)

    /** Used by the settings screen to clear alerts the user has already acted on. */
    fun clearAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
