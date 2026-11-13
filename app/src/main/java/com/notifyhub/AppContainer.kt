package com.notifyhub

import android.app.Application
import android.content.Context
import com.notifyhub.collector.CollectionPipeline
import com.notifyhub.collector.RoomEventStore
import com.notifyhub.data.db.NotifyHubDatabase
import com.notifyhub.data.settings.CredentialStore
import com.notifyhub.data.settings.SettingsRepository
import com.notifyhub.sync.HttpSyncTransport
import com.notifyhub.sync.SyncScheduler
import com.notifyhub.sync.SyncTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; keeps the app free of a DI framework in Phase 1. */
class AppContainer(val context: Context) {
    val db: NotifyHubDatabase by lazy { NotifyHubDatabase.build(context) }
    val settings = SettingsRepository(context)
    val credentials = CredentialStore(context)
    val transport: SyncTransport = HttpSyncTransport()
    val pipeline: CollectionPipeline by lazy {
        CollectionPipeline(RoomEventStore(db.notificationDao(), db.excludedAppDao()), settings, context.packageName)
    }
}

class NotifyHubApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.settings.deviceId() // generate on first run
            if (container.settings.isSyncConfigured()) SyncScheduler.ensurePeriodic(this@NotifyHubApplication)
        }
    }
}
