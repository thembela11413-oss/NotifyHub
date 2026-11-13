package com.notifyhub.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AccessScreen() {
    val ctx = LocalContext.current
    val granted by rememberAccessGranted()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Notification access")
        SectionCard {
            StatRow(
                "Status", if (granted) "Granted" else "Not granted",
                if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (granted) "Manage notification access" else "Open notification access settings") }
        }
        SectionCard {
            Text("What this permission does", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(
                "Notification access lets NotifyHub read the notifications other apps show: titles, message text, " +
                    "sender names and when they are dismissed. Notifications can contain highly sensitive information " +
                    "such as private messages, banking alerts and verification codes.",
            )
            Text(
                "NotifyHub stores what Android provides on this device and never logs notification content. " +
                    "Android may redact sensitive notifications, such as one-time codes; NotifyHub does not try to get around that.",
            )
            Text(
                "You stay in control: pause collection on the Home screen, exclude individual apps, " +
                    "or delete everything from History. You can revoke access at any time in Android settings.",
            )
        }
        SectionCard {
            Text("Sideloaded install?", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(
                "On Android 13 and newer, if the access toggle is greyed out, open App info for NotifyHub, " +
                    "tap the ⋮ menu, choose “Allow restricted settings”, then return here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
