package com.notifyhub.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.notifyhub.collector.CollectionSettings
import com.notifyhub.sync.UrlCheck
import com.notifyhub.sync.UrlPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notifyhub_settings")

enum class ConnectionState { NOT_CONFIGURED, UNKNOWN, OK, ERROR }

data class AppSettings(
    val serverUrl: String = "",
    val deviceId: String = "",
    val allowHttp: Boolean = false,
    val paused: Boolean = false,
    val lastSyncAt: Long? = null,
    val connectionState: ConnectionState = ConnectionState.NOT_CONFIGURED,
    val connectionMessage: String? = null,
)

class SettingsRepository(context: Context) : CollectionSettings {
    private val ds = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = ds.data.map { p ->
        AppSettings(
            serverUrl = p[SERVER_URL].orEmpty(),
            deviceId = p[DEVICE_ID].orEmpty(),
            allowHttp = p[ALLOW_HTTP] ?: false,
            paused = p[PAUSED] ?: false,
            lastSyncAt = p[LAST_SYNC],
            connectionState = p[CONN_STATE]?.let { runCatching { ConnectionState.valueOf(it) }.getOrNull() }
                ?: ConnectionState.NOT_CONFIGURED,
            connectionMessage = p[CONN_MSG],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    override suspend fun isPaused(): Boolean = current().paused

    /** Returns the device ID, generating and persisting a random UUID on first use. */
    override suspend fun deviceId(): String {
        val existing = current().deviceId
        if (existing.isNotBlank()) return existing
        val generated = UUID.randomUUID().toString()
        ds.edit { if (it[DEVICE_ID].isNullOrBlank()) it[DEVICE_ID] = generated }
        return current().deviceId
    }

    suspend fun isSyncConfigured(): Boolean {
        val s = current()
        return UrlPolicy.validate(s.serverUrl, s.allowHttp) is UrlCheck.Valid
    }

    suspend fun setPaused(value: Boolean) = ds.edit { it[PAUSED] = value }.let { }
    suspend fun setServer(url: String, allowHttp: Boolean) = ds.edit {
        it[SERVER_URL] = url
        it[ALLOW_HTTP] = allowHttp
    }.let { }
    suspend fun setDeviceId(id: String) = ds.edit { it[DEVICE_ID] = id }.let { }
    suspend fun setLastSync(ts: Long) = ds.edit { it[LAST_SYNC] = ts }.let { }
    suspend fun setConnection(state: ConnectionState, message: String? = null) = ds.edit {
        it[CONN_STATE] = state.name
        if (message == null) it.remove(CONN_MSG) else it[CONN_MSG] = message
    }.let { }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ALLOW_HTTP = booleanPreferencesKey("allow_http")
        val PAUSED = booleanPreferencesKey("paused")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val CONN_STATE = stringPreferencesKey("conn_state")
        val CONN_MSG = stringPreferencesKey("conn_msg")
    }
}
