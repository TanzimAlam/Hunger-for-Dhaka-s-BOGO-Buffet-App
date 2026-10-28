package com.buffetbogo.hunter.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.buffetbogo.hunter.data.LinkTargets

/**
 * Opens an ad link without ever letting a bad one take the app down.
 *
 * The previous code called `LocalUriHandler.openUri(adTarget)` directly. When nothing on the
 * phone could handle the address — no browser, a stripped ROM, a link that never got a scheme —
 * Android threw [android.content.ActivityNotFoundException] inside a click handler, and the
 * process died: the app vanished to the home screen and started from scratch next launch. Now
 * the address is sanitised first, the launch is guarded, and a failure copies the link so the
 * user can still get to it.
 */
object LinkOpener {

    enum class Result {
        /** A browser (or the Facebook app) took the link. */
        OPENED,

        /** Nothing could take it; the address is on the clipboard instead. */
        COPIED,

        /** The link could not be repaired into a URL at all. */
        UNUSABLE,
    }

    fun open(context: Context, rawUrl: String?): Result {
        val url = LinkTargets.clean(rawUrl) ?: return Result.UNUSABLE
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            // A dialog or a service can hand us a context that is not an activity.
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.OPENED
        } catch (notFound: android.content.ActivityNotFoundException) {
            copy(context, url)
            Result.COPIED
        } catch (denied: SecurityException) {
            copy(context, url)
            Result.COPIED
        } catch (badIntent: RuntimeException) {
            // IllegalArgumentException from a malformed URI, or an OEM's own resolver failing.
            copy(context, url)
            Result.COPIED
        }
    }

    private fun copy(context: Context, url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("Offer link", url)) }
    }
}
