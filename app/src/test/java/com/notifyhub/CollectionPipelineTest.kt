package com.notifyhub

import com.notifyhub.collector.CollectionPipeline
import com.notifyhub.collector.CollectionSettings
import com.notifyhub.collector.EventStore
import com.notifyhub.collector.Outcome
import com.notifyhub.collector.RawNotification
import com.notifyhub.data.db.EventType
import com.notifyhub.data.db.NotificationEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeStore : EventStore {
    val events = mutableListOf<NotificationEvent>()
    val excluded = mutableSetOf<String>()
    override suspend fun insertIfNew(event: NotificationEvent): Boolean {
        if (events.any { it.dedupHash == event.dedupHash }) return false
        events += event
        return true
    }
    override suspend fun isExcluded(packageName: String) = packageName in excluded
}

private class FakeSettings(var paused: Boolean = false) : CollectionSettings {
    override suspend fun isPaused() = paused
    override suspend fun deviceId() = "dev-1"
}

class CollectionPipelineTest {
    private lateinit var store: FakeStore
    private lateinit var settings: FakeSettings
    private lateinit var pipeline: CollectionPipeline
    private var n = 0

    @Before fun setUp() {
        store = FakeStore(); settings = FakeSettings(); n = 0
        pipeline = CollectionPipeline(store, settings, "com.notifyhub", clock = { 5_000L }, newId = { "id-${n++}" })
    }

    private fun raw(pkg: String? = "com.example.app", text: String = "Hello", post: Long = 1_000L) =
        RawNotification(packageName = pkg, key = "k1", id = 1, title = "T", text = text, postTime = post)

    private fun run(r: RawNotification, t: EventType = EventType.POSTED) = runBlocking { pipeline.handle(r, t) }

    @Test fun storesPostedEvent() {
        assertEquals(Outcome.STORED, run(raw()))
        assertEquals(1, store.events.size)
        assertEquals("dev-1", store.events[0].deviceId)
    }

    @Test fun excludedAppIsNotCollectedAndCanBeReEnabled() {
        store.excluded += "com.example.app"
        assertEquals(Outcome.EXCLUDED, run(raw()))
        assertEquals(0, store.events.size)
        store.excluded -= "com.example.app"
        assertEquals(Outcome.STORED, run(raw()))
    }

    @Test fun exclusionAppliesToRemovalsToo() {
        store.excluded += "com.example.app"
        assertEquals(Outcome.EXCLUDED, run(raw(), EventType.REMOVED))
    }

    @Test fun pausedCollectionStoresNothing() {
        settings.paused = true
        assertEquals(Outcome.PAUSED, run(raw()))
        assertEquals(0, store.events.size)
    }

    @Test fun ownAppIsIgnored() {
        assertEquals(Outcome.OWN_APP, run(raw(pkg = "com.notifyhub")))
    }

    @Test fun duplicateCallbackIsStoredOnce() {
        assertEquals(Outcome.STORED, run(raw()))
        assertEquals(Outcome.DUPLICATE, run(raw()))
        assertEquals(1, store.events.size)
    }

    @Test fun updateIsStoredAsNewEvent() {
        run(raw(text = "one")); run(raw(text = "two"))
        assertEquals(2, store.events.size)
    }

    @Test fun postedThenRemovedAreTwoSeparateEvents() {
        run(raw()); run(raw(), EventType.REMOVED)
        assertEquals(listOf(EventType.POSTED, EventType.REMOVED), store.events.map { it.eventType })
    }

    @Test fun missingPackageIsInvalid() {
        assertEquals(Outcome.INVALID, run(raw(pkg = null)))
    }
}
