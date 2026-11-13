package com.notifyhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notifyhub.data.settings.ConnectionState

@Composable
fun HomeScreen(
    onOpenAccess: () -> Unit,
    onOpenServer: () -> Unit,
    vm: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val granted by rememberAccessGranted()
    val s = ui.settings
    val ok = MaterialTheme.colorScheme.secondary
    val bad = MaterialTheme.colorScheme.error

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("NotifyHub")

        SectionCard {
            StatRow("Notification access", if (granted) "Granted" else "Not granted", if (granted) ok else bad)
            StatRow(
                "Collector",
                collectorStatus(granted, s.paused, ui.serviceConnected),
                if (granted && !s.paused && ui.serviceConnected) ok else MaterialTheme.colorScheme.onSurface,
            )
            if (!granted) Button(onClick = onOpenAccess, Modifier.fillMaxWidth()) { Text("Grant notification access") }
        }

        SectionCard {
            StatRow("Notifications collected", ui.total.toString())
            StatRow("Pending synchronization", ui.pending.toString())
            StatRow("Last successful sync", formatTime(s.lastSyncAt))
            StatRow(
                "Server connection",
                when (s.connectionState) {
                    ConnectionState.NOT_CONFIGURED -> "Not configured"
                    ConnectionState.UNKNOWN -> "Not tested"
                    ConnectionState.OK -> "Connected"
                    ConnectionState.ERROR -> "Error"
                },
                when (s.connectionState) {
                    ConnectionState.OK -> ok
                    ConnectionState.ERROR -> bad
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            s.connectionMessage?.let { Text(it, color = bad, style = MaterialTheme.typography.bodySmall) }
            if (s.connectionState == ConnectionState.NOT_CONFIGURED) {
                OutlinedButton(onClick = onOpenServer, Modifier.fillMaxWidth()) { Text("Configure server") }
            } else {
                OutlinedButton(onClick = vm::syncNow, Modifier.fillMaxWidth()) { Text("Sync now") }
            }
        }

        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pause collection", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    Text(
                        "While paused, no new notifications are stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.paused, onCheckedChange = vm::setPaused)
            }
        }
    }
}
