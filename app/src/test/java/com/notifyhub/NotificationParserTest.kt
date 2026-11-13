package com.notifyhub

import com.notifyhub.collector.NotificationParser
import com.notifyhub.collector.RawMessage
import com.notifyhub.collector.RawNotification
import com.notifyhub.data.db.DeliveryStatus
import com.notifyhub.data.db.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    private fun base() = RawNotification(
        packageName = "com.example.chat", appName = "Chat", key = "0|com.example.chat|1|null|10001", id = 1,
        title = "Alice", text = "Hello", category = "msg", channelId = "messages",
        groupKey = "g1", postTime = 1_000L,
    )

    private fun parse(raw: RawNotification, type: EventType = EventType.POSTED, received: Long = 2_000L, id: String = "e1") =
        NotificationParser.parse(raw, type, "dev-1", received, id)!!

    @Test fun parsesPostedNotification() {
        val e = parse(base())
        assertEquals("e1", e.eventId)
        assertEquals("dev-1", e.deviceId)
        assertEquals("com.example.chat", e.packageName)
        assertEquals("Chat", e.appName)
        assertEquals("Alice", e.title)
        assertEquals("Hello", e.text)
        assertEquals("msg", e.category)
        assertEquals("messages", e.channelId)
        assertEquals("g1", e.groupKey)
        assertEquals(EventType.POSTED, e.eventType)
        assertEquals(1_000L, e.postedAt)
        assertEquals(2_000L, e.receivedAt)
        assertEquals(DeliveryStatus.PENDING, e.deliveryStatus)
    }

    @Test fun missingExtrasDoNotCrashAndFallBack() {
        val e = parse(RawNotification(packageName = "pkg", id = 5, postTime = 0L))
        assertNull(e.title); assertNull(e.text); assertNull(e.bigText)
        assertNull(e.category); assertNull(e.channelId); assertNull(e.groupKey); assertNull(e.senderName)
        assertTrue(e.messages.isEmpty())
        assertEquals("pkg", e.appName)
        assertEquals("pkg|5|", e.notificationKey)
        assertEquals(2_000L, e.postedAt) // falls back to receipt time
    }

    @Test fun blankOrNullPackageIsRejected() {
        assertNull(NotificationParser.parse(RawNotification(packageName = "  "), EventType.POSTED, "d", 1L))
        assertNull(NotificationParser.parse(RawNotification(packageName = null), EventType.POSTED, "d", 1L))
    }

    @Test fun messagingStyleMessagesAreParsedAndCleaned() {
        val raw = base().copy(
            messages = listOf(
                RawMessage("Alice", "First", 10), RawMessage(null, "  ", 11), RawMessage(" ", null, 12),
                RawMessage("Bob", "Second", 13), RawMessage(null, "No sender", 14),
            ),
        )
        val e = parse(raw)
        assertEquals(listOf("First", "Second", "No sender"), e.messages.map { it.text })
        assertEquals("Bob", e.senderName)
    }

    @Test fun textIsTrimmedNulStrippedAndTruncated() {
        val e = parse(base().copy(title = "  Hi\u0000 there  ", bigText = "x".repeat(NotificationParser.MAX_TEXT_LENGTH + 500)))
        assertEquals("Hi there", e.title)
        assertEquals(NotificationParser.MAX_TEXT_LENGTH, e.bigText!!.length)
    }

    @Test fun updatedNotificationProducesDifferentHash() {
        val a = parse(base())
        val b = parse(base().copy(text = "Hello again"), id = "e2")
        assertNotEquals(a.dedupHash, b.dedupHash)
    }

    @Test fun identicalCallbackProducesSameHashRegardlessOfReceiptTimeOrId() {
        val a = parse(base(), received = 2_000L, id = "e1")
        val b = parse(base(), received = 9_999L, id = "e2")
        assertEquals(a.dedupHash, b.dedupHash)
    }

    @Test fun removalIsSeparateEventTypeWithReason() {
        val posted = parse(base())
        val removed = parse(base().copy(removalReason = 2), EventType.REMOVED, id = "e2")
        assertEquals(EventType.REMOVED, removed.eventType)
        assertEquals(2, removed.removalReason)
        assertNull(posted.removalReason)
        assertNotEquals(posted.dedupHash, removed.dedupHash)
    }

    @Test fun groupSummaryFlagIsPreserved() {
        assertTrue(parse(base().copy(isGroupSummary = true)).isGroupSummary)
    }
}
