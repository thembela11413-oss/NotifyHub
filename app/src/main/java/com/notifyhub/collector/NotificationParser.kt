package com.notifyhub.collector

import com.notifyhub.data.db.EventType
import com.notifyhub.data.db.MessageInfo
import com.notifyhub.data.db.NotificationEvent
import java.security.MessageDigest
import java.util.UUID

/** One MessagingStyle message, already converted to plain strings. */
data class RawMessage(val sender: String?, val text: String?, val timestamp: Long = 0L)

/** Plain-Kotlin snapshot of a StatusBarNotification, so parsing is testable on the JVM. */
data class RawNotification(
    val packageName: String? = null,
    val appName: String? = null,
    val key: String? = null,
    val id: Int = 0,
    val tag: String? = null,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val messages: List<RawMessage> = emptyList(),
    val category: String? = null,
    val channelId: String? = null,
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
    val postTime: Long = 0L,
    val removalReason: Int? = null,
)

object NotificationParser {
    const val MAX_TEXT_LENGTH = 20_000
    const val MAX_MESSAGES = 50

    /** Returns null only when the notification has no usable package name. */
    fun parse(
        raw: RawNotification,
        type: EventType,
        deviceId: String,
        receivedAt: Long,
        eventId: String = UUID.randomUUID().toString(),
    ): NotificationEvent? {
        val pkg = clean(raw.packageName) ?: return null
        val key = clean(raw.key) ?: "$pkg|${raw.id}|${raw.tag.orEmpty()}"
        val title = clean(raw.title)
        val text = clean(raw.text)
        val bigText = clean(raw.bigText)
        val category = clean(raw.category)
        val channelId = clean(raw.channelId)
        val groupKey = clean(raw.groupKey)
        val messages = raw.messages.mapNotNull { m ->
            val sender = clean(m.sender)
            val body = clean(m.text)
            if (sender == null && body == null) null else MessageInfo(sender, body, m.timestamp)
        }.takeLast(MAX_MESSAGES)
        val postedAt = if (raw.postTime > 0) raw.postTime else receivedAt

        val hash = sha256(
            listOf(
                type.name, key, postedAt, title, text, bigText, category, channelId, groupKey, raw.removalReason,
                messages.joinToString("\u0003") { "${it.sender}|${it.text}|${it.timestamp}" },
            )
        )
        return NotificationEvent(
            eventId = eventId,
            deviceId = deviceId,
            packageName = pkg,
            appName = clean(raw.appName) ?: pkg,
            notificationKey = key,
            notificationId = raw.id,
            title = title,
            text = text,
            bigText = bigText,
            messages = messages,
            senderName = messages.lastOrNull { it.sender != null }?.sender,
            category = category,
            channelId = channelId,
            groupKey = groupKey,
            isGroupSummary = raw.isGroupSummary,
            eventType = type,
            removalReason = if (type == EventType.REMOVED) raw.removalReason else null,
            postedAt = postedAt,
            receivedAt = receivedAt,
            dedupHash = hash,
        )
    }

    internal fun clean(value: String?): String? {
        if (value == null) return null
        val t = value.replace("\u0000", "").trim()
        if (t.isEmpty()) return null
        return if (t.length > MAX_TEXT_LENGTH) t.substring(0, MAX_TEXT_LENGTH) else t
    }

    private fun sha256(parts: List<Any?>): String {
        val joined = parts.joinToString("\u0001") { it?.toString() ?: "\u0002" }
        return MessageDigest.getInstance("SHA-256")
            .digest(joined.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
