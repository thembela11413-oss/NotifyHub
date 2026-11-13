package com.notifyhub.sync

import com.notifyhub.data.db.MessageInfo
import com.notifyhub.data.db.NotificationEvent
import kotlinx.serialization.Serializable

/** Wire format for the future backend (contract is provisional until Phase 2). */
@Serializable
data class EventPayload(
    val eventId: String,
    val deviceId: String,
    val packageName: String,
    val appName: String,
    val notificationKey: String,
    val notificationId: Int,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val messages: List<MessageInfo> = emptyList(),
    val senderName: String? = null,
    val category: String? = null,
    val channelId: String? = null,
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
    val eventType: String,
    val removalReason: Int? = null,
    val postedAt: Long,
    val receivedAt: Long,
)

@Serializable
data class EventBatch(val deviceId: String, val events: List<EventPayload>)

fun NotificationEvent.toPayload() = EventPayload(
    eventId = eventId, deviceId = deviceId, packageName = packageName, appName = appName,
    notificationKey = notificationKey, notificationId = notificationId, title = title, text = text,
    bigText = bigText, messages = messages, senderName = senderName, category = category,
    channelId = channelId, groupKey = groupKey, isGroupSummary = isGroupSummary,
    eventType = eventType.name, removalReason = removalReason, postedAt = postedAt, receivedAt = receivedAt,
)
