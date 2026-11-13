package com.notifyhub.collector

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts a StatusBarNotification into a [RawNotification] using only fields Android exposes
 * to notification listeners. Every read is defensive: malformed extras yield null, never a crash.
 * Redacted content (e.g. sensitive OTP notifications) is kept exactly as Android delivers it.
 */
object NotificationExtractor {
    private val labelCache = ConcurrentHashMap<String, String>()

    fun extract(context: Context, sbn: StatusBarNotification, removalReason: Int?): RawNotification {
        val n: Notification? = sbn.notification
        val extras: Bundle? = n?.extras
        fun str(key: String): String? = runCatching { extras?.getCharSequence(key)?.toString() }.getOrNull()

        return RawNotification(
            packageName = sbn.packageName,
            appName = appLabel(context, sbn.packageName),
            key = runCatching { sbn.key }.getOrNull(),
            id = sbn.id,
            tag = sbn.tag,
            title = str(Notification.EXTRA_TITLE) ?: str(Notification.EXTRA_TITLE_BIG),
            text = str(Notification.EXTRA_TEXT),
            bigText = str(Notification.EXTRA_BIG_TEXT),
            messages = extractMessages(extras),
            category = n?.category,
            channelId = n?.channelId,
            groupKey = runCatching { sbn.groupKey }.getOrNull(),
            isGroupSummary = n != null && (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            postTime = sbn.postTime,
            removalReason = removalReason,
        )
    }

    @Suppress("DEPRECATION")
    private fun extractMessages(extras: Bundle?): List<RawMessage> = runCatching {
        val parcels = extras?.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        Notification.MessagingStyle.Message.getMessagesFromBundleArray(parcels).map { m ->
            val sender = if (Build.VERSION.SDK_INT >= 28) {
                m.senderPerson?.name?.toString() ?: m.sender?.toString()
            } else {
                m.sender?.toString()
            }
            RawMessage(sender = sender, text = m.text?.toString(), timestamp = m.timestamp)
        }
    }.getOrDefault(emptyList())

    private fun appLabel(context: Context, pkg: String): String? = labelCache[pkg] ?: runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()?.also { labelCache[pkg] = it }
}
