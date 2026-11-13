package com.notifyhub.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class EventType { POSTED, REMOVED }

enum class DeliveryStatus { PENDING, SENDING, DELIVERED, FAILED }

/** One message of a MessagingStyle notification. */
@Serializable
data class MessageInfo(
    val sender: String? = null,
    val text: String? = null,
    val timestamp: Long = 0L,
)

/**
 * Normalized notification event. POSTED and REMOVED are separate rows.
 * [dedupHash] is a SHA-256 over the event's identifying content and is unique,
 * so a repeated callback with identical content is stored once.
 */
@Entity(
    tableName = "notification_events",
    indices = [
        Index(value = ["dedupHash"], unique = true),
        Index(value = ["deliveryStatus"]),
        Index(value = ["receivedAt"]),
    ],
)
data class NotificationEvent(
    @PrimaryKey val eventId: String,
    val deviceId: String,
    val packageName: String,
    val appName: String,
    val notificationKey: String,
    val notificationId: Int,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val messages: List<MessageInfo> = emptyList(),
    val senderName: String?,
    val category: String?,
    val channelId: String?,
    val groupKey: String?,
    val isGroupSummary: Boolean,
    val eventType: EventType,
    val removalReason: Int?,
    val postedAt: Long,
    val receivedAt: Long,
    val dedupHash: String,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val deliveredAt: Long? = null,
    val lastError: String? = null,
)

@Entity(tableName = "excluded_apps")
data class ExcludedApp(@PrimaryKey val packageName: String)
