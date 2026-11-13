package com.notifyhub.collector

import com.notifyhub.data.db.EventType
import com.notifyhub.data.db.NotificationEvent
import java.util.UUID

interface EventStore {
    /** Stores the event; returns false if an identical event already exists. */
    suspend fun insertIfNew(event: NotificationEvent): Boolean
    suspend fun isExcluded(packageName: String): Boolean
}

interface CollectionSettings {
    suspend fun isPaused(): Boolean
    suspend fun deviceId(): String
}

enum class Outcome { STORED, DUPLICATE, PAUSED, EXCLUDED, OWN_APP, INVALID }

/** Decides whether a notification is collected and persists it. Never touches the network. */
class CollectionPipeline(
    private val store: EventStore,
    private val settings: CollectionSettings,
    private val ownPackage: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun handle(raw: RawNotification, type: EventType): Outcome {
        val pkg = raw.packageName?.trim().orEmpty()
        if (pkg == ownPackage) return Outcome.OWN_APP
        if (settings.isPaused()) return Outcome.PAUSED
        if (pkg.isNotEmpty() && store.isExcluded(pkg)) return Outcome.EXCLUDED
        val event = NotificationParser.parse(raw, type, settings.deviceId(), clock(), newId())
            ?: return Outcome.INVALID
        return if (store.insertIfNew(event)) Outcome.STORED else Outcome.DUPLICATE
    }
}
