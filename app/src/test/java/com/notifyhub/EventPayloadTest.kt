package com.notifyhub

import com.notifyhub.collector.NotificationParser
import com.notifyhub.collector.RawMessage
import com.notifyhub.collector.RawNotification
import com.notifyhub.data.db.EventType
import com.notifyhub.sync.EventBatch
import com.notifyhub.sync.EventPayload
import com.notifyhub.sync.toPayload
import com.notifyhub.ui.collectorStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPayloadTest {
    private val json = Json { encodeDefaults = true }

    @Test fun roundTripsThroughJson() {
        val e = NotificationParser.parse(
            RawNotification(packageName = "p", appName = "App", key = "k", id = 3, title = "T", text = "X",
                messages = listOf(RawMessage("Ann", "Hi", 7)), postTime = 100L, removalReason = 8),
            EventType.REMOVED, "dev", 200L, "e1",
        )!!
        val batch = EventBatch("dev", listOf(e.toPayload()))
        val decoded = json.decodeFromString<EventBatch>(json.encodeToString(batch))
        assertEquals(batch, decoded)
        assertEquals("REMOVED", decoded.events[0].eventType)
        assertEquals("Ann", decoded.events[0].messages[0].sender)
    }

    @Test fun toleratesMissingOptionalFieldsAndUnknownKeys() {
        val lenient = Json { ignoreUnknownKeys = true }
        val p = lenient.decodeFromString<EventPayload>(
            """{"eventId":"a","deviceId":"d","packageName":"p","appName":"A","notificationKey":"k","notificationId":1,"eventType":"POSTED","postedAt":1,"receivedAt":2,"extra":true}"""
        )
        assertNull(p.title)
        assertTrue(p.messages.isEmpty())
    }

    @Test fun collectorStatusLabels() {
        assertEquals("Notification access needed", collectorStatus(false, false, true))
        assertEquals("Paused", collectorStatus(true, true, true))
        assertEquals("Running", collectorStatus(true, false, true))
        assertEquals("Starting…", collectorStatus(true, false, false))
    }
}
