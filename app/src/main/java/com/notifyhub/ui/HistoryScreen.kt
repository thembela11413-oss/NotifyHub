package com.notifyhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notifyhub.data.db.EventType
import com.notifyhub.data.db.NotificationEvent

/** Best available preview text for an event. */
fun previewText(e: NotificationEvent): String? =
    e.text ?: e.bigText ?: e.messages.lastOrNull()?.let { m -> listOfNotNull(m.sender, m.text).joinToString(": ") }

@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val events by vm.events.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ScreenTitle("History")
                Text("$total stored · showing latest ${events.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { confirm = true }, enabled = total > 0) { Text("Delete all") }
        }
        if (events.isEmpty()) {
            Text("No notifications collected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.eventId }) { EventRow(it) }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete all collected data?") },
            text = { Text("This permanently removes every stored notification event from this device, including events not yet synchronized.") },
            confirmButton = { TextButton(onClick = { vm.deleteAll(); confirm = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EventRow(e: NotificationEvent) {
    val removed = e.eventType == EventType.REMOVED
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(e.appName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (removed) MaterialTheme.colorScheme.error.copy(alpha = 0.18f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            ) {
                Text(
                    if (removed) "REMOVED" else "POSTED",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (removed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                )
            }
        }
        e.title?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        previewText(e)?.let {
            Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "${formatTime(e.postedAt)} · ${e.deliveryStatus.name.lowercase()}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
