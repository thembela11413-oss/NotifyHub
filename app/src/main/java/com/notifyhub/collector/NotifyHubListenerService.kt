package com.notifyhub.collector

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.notifyhub.NotifyHubApplication
import com.notifyhub.data.db.EventType
import com.notifyhub.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object CollectorState {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    internal fun set(value: Boolean) { _connected.value = value }

    fun isAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Asks Android to rebind the listener if access is granted but the service is not connected. */
    fun ensureBound(context: Context) {
        if (isAccessGranted(context) && !_connected.value) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, NotifyHubListenerService::class.java)
                )
            }
        }
    }
}

class NotifyHubListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() { CollectorState.set(true) }

    override fun onListenerDisconnected() { CollectorState.set(false) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) dispatch(sbn, EventType.POSTED, null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn != null) dispatch(sbn, EventType.REMOVED, reason)
    }

    /** Extracts synchronously (cheap), then persists on a background coroutine. No network here. */
    private fun dispatch(sbn: StatusBarNotification, type: EventType, reason: Int?) {
        val raw = try {
            NotificationExtractor.extract(applicationContext, sbn, reason)
        } catch (e: Exception) {
            Log.w(TAG, "extract failed: ${e.javaClass.simpleName}")
            return
        }
        val container = (application as NotifyHubApplication).container
        scope.launch {
            try {
                val outcome = container.pipeline.handle(raw, type)
                if (outcome == Outcome.STORED && container.settings.isSyncConfigured()) {
                    SyncScheduler.syncNow(applicationContext)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never log notification content, only the failure type.
                Log.w(TAG, "event dropped: ${e.javaClass.simpleName}")
            }
        }
    }

    override fun onDestroy() {
        CollectorState.set(false)
        scope.cancel()
        super.onDestroy()
    }

    private companion object { const val TAG = "NotifyHubListener" }
}
