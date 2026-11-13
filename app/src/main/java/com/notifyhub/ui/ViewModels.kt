package com.notifyhub.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.notifyhub.AppContainer
import com.notifyhub.NotifyHubApplication
import com.notifyhub.collector.CollectorState
import com.notifyhub.data.db.ExcludedApp
import com.notifyhub.data.db.NotificationEvent
import com.notifyhub.data.settings.AppSettings
import com.notifyhub.data.settings.ConnectionState
import com.notifyhub.sync.SendResult
import com.notifyhub.sync.SyncScheduler
import com.notifyhub.sync.UrlCheck
import com.notifyhub.sync.UrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NotifyHubApplication).container

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(container()) }
        initializer { AppsViewModel(container()) }
        initializer { ServerViewModel(container()) }
        initializer { HistoryViewModel(container()) }
    }
}

data class HomeUiState(
    val total: Int = 0,
    val pending: Int = 0,
    val settings: AppSettings = AppSettings(),
    val serviceConnected: Boolean = false,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        c.db.notificationDao().observeTotal(),
        c.db.notificationDao().observeUndelivered(),
        c.settings.settings,
        CollectorState.connected,
    ) { total, pending, settings, connected -> HomeUiState(total, pending, settings, connected) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setPaused(paused: Boolean) { viewModelScope.launch { c.settings.setPaused(paused) } }

    fun syncNow() {
        viewModelScope.launch { if (c.settings.isSyncConfigured()) SyncScheduler.syncNow(c.context) }
    }
}

data class AppEntry(val packageName: String, val label: String, val collected: Boolean)

class AppsViewModel(private val c: AppContainer) : ViewModel() {
    private val installed = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val query = MutableStateFlow("")

    val apps: StateFlow<List<AppEntry>> = combine(
        installed, c.db.excludedAppDao().observeAll(), query,
    ) { list, excluded, q ->
        val ex = excluded.toSet()
        val all = list + excluded.filter { p -> list.none { it.first == p } }.map { it to it }
        all.filter { (pkg, label) -> q.isBlank() || label.contains(q, true) || pkg.contains(q, true) }
            .map { (pkg, label) -> AppEntry(pkg, label, collected = pkg !in ex) }
            .sortedBy { it.label.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = c.context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            installed.value = pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != c.context.packageName }
        }
    }

    fun setQuery(q: String) { query.value = q }

    fun setCollected(packageName: String, collected: Boolean) {
        viewModelScope.launch {
            if (collected) c.db.excludedAppDao().remove(packageName)
            else c.db.excludedAppDao().add(ExcludedApp(packageName))
        }
    }
}

class ServerViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    var message by mutableStateOf<String?>(null)
        private set
    var hasCredential by mutableStateOf(c.credentials.hasCredential())
        private set
    var testing by mutableStateOf(false)
        private set

    fun save(url: String, deviceId: String, allowHttp: Boolean, credential: String) {
        viewModelScope.launch {
            val trimmedUrl = url.trim()
            if (trimmedUrl.isNotEmpty()) {
                val check = UrlPolicy.validate(trimmedUrl, allowHttp)
                if (check is UrlCheck.Invalid) { message = check.reason; return@launch }
            }
            if (deviceId.isBlank()) { message = "Device ID cannot be empty."; return@launch }
            c.settings.setServer(trimmedUrl.trimEnd('/'), allowHttp)
            c.settings.setDeviceId(deviceId.trim())
            if (credential.isNotBlank()) {
                withContext(Dispatchers.IO) { c.credentials.save(credential.trim()) }
                hasCredential = true
            }
            if (trimmedUrl.isEmpty()) {
                c.settings.setConnection(ConnectionState.NOT_CONFIGURED, null)
            } else {
                c.settings.setConnection(ConnectionState.UNKNOWN, null)
                SyncScheduler.ensurePeriodic(c.context)
                SyncScheduler.syncNow(c.context)
            }
            message = "Saved."
        }
    }

    fun clearCredential() {
        c.credentials.clear()
        hasCredential = false
        message = "Credential removed."
    }

    /** Placeholder test against GET {url}/api/v1/health using the saved settings. */
    fun testConnection() {
        viewModelScope.launch {
            val s = c.settings.current()
            val check = UrlPolicy.validate(s.serverUrl, s.allowHttp)
            if (check !is UrlCheck.Valid) { message = "Save a valid server URL first."; return@launch }
            testing = true
            val result = withContext(Dispatchers.IO) { c.transport.testConnection(check.url, c.credentials.load()) }
            testing = false
            when (result) {
                SendResult.Success -> { c.settings.setConnection(ConnectionState.OK, null); message = "Server reachable." }
                is SendResult.Failure -> { c.settings.setConnection(ConnectionState.ERROR, result.message); message = "Test failed: ${result.message}" }
            }
        }
    }
}

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val events: StateFlow<List<NotificationEvent>> = c.db.notificationDao().observeRecent(500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val total: StateFlow<Int> = c.db.notificationDao().observeTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun deleteAll() { viewModelScope.launch(Dispatchers.IO) { c.db.notificationDao().deleteAll() } }
}
