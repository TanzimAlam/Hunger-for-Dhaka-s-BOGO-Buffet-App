package com.buffetbogo.hunter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.buffetbogo.hunter.notify.DeepLinks
import com.buffetbogo.hunter.ui.AppRoot
import com.buffetbogo.hunter.ui.theme.BuffetTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The user can re-enable alerts from Settings at any time. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cream paper behind the bars, so their icons and gesture handle are ink, not white.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            BuffetTheme {
                AppRoot()
            }
        }

        // A cold launch straight from a notification still has to land on that offer.
        DeepLinks.open(intent?.getStringExtra(DeepLinks.EXTRA_OFFER_ID))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The app is usually already running when an alert is tapped; launchMode is singleTop. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.open(intent.getStringExtra(DeepLinks.EXTRA_OFFER_ID))
    }
}
