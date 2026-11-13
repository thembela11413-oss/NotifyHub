package com.notifyhub.collector

import com.notifyhub.data.db.ExcludedAppDao
import com.notifyhub.data.db.NotificationDao
import com.notifyhub.data.db.NotificationEvent

class RoomEventStore(
    private val events: NotificationDao,
    private val excluded: ExcludedAppDao,
) : EventStore {
    override suspend fun insertIfNew(event: NotificationEvent): Boolean = events.insert(event) != -1L
    override suspend fun isExcluded(packageName: String): Boolean = excluded.isExcluded(packageName)
}
