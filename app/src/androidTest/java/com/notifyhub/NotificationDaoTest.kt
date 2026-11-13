package com.notifyhub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notifyhub.collector.NotificationParser
import com.notifyhub.collector.RawNotification
import com.notifyhub.data.db.DeliveryStatus
import com.notifyhub.data.db.EventType
import com.notifyhub.data.db.ExcludedApp
import com.notifyhub.data.db.NotificationEvent
import com.notifyhub.data.db.NotifyHubDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDaoTest {
    private lateinit var ctx: Context
    private lateinit var db: NotifyHubDatabase

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, NotifyHubDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    private fun event(id: String, text: String = id, type: EventType = EventType.POSTED, received: Long = 1_000L): NotificationEvent =
        NotificationParser.parse(
            RawNotification(packageName = "com.example", key = "k-$id", id = 1, title = "T", text = text, postTime = 500L),
            type, "dev", received, id,
        )!!

    @Test fun insertsAndCounts() = runBlocking {
        assertTrue(db.notificationDao().insert(event("a")) != -1L)
        assertEquals(1, db.notificationDao().count())
    }

    @Test fun duplicateHashIsIgnored() = runBlocking {
        db.notificationDao().insert(event("a"))
        val dup = event("a").copy(eventId = "other-id")
        assertEquals(-1L, db.notificationDao().insert(dup))
        assertEquals(1, db.notificationDao().count())
    }

    @Test fun queueTransitionsPendingSendingDelivered() = runBlocking {
        val dao = db.notificationDao()
        dao.insert(event("a", received = 1)); dao.insert(event("b", received = 2))
        assertEquals(listOf("a", "b"), dao.getSendable(10, 10).map { it.eventId })
        dao.markSending(listOf("a"), 10L)
        assertEquals(listOf("b"), dao.getSendable(10, 10).map { it.eventId })
        dao.markDelivered(listOf("a"), 20L)
        assertEquals(1, dao.getSendable(10, 10).size)
    }

    @Test fun failedEventsAreRetriedUntilMaxAttempts() = runBlocking {
        val dao = db.notificationDao()
        dao.insert(event("a"))
        repeat(3) { dao.markFailed(listOf("a"), "HTTP 500") }
        assertEquals(1, dao.getSendable(10, 4).size)
        assertEquals(0, dao.getSendable(10, 3).size)
    }

    @Test fun staleSendingIsRecovered() = runBlocking {
        val dao = db.notificationDao()
        dao.insert(event("a")); dao.markSending(listOf("a"), 1L)
        assertEquals(0, dao.getSendable(10, 10).size)
        assertEquals(1, dao.resetStaleSending())
        assertEquals(1, dao.getSendable(10, 10).size)
    }

    @Test fun deleteAllRemovesEverything() = runBlocking {
        db.notificationDao().insert(event("a")); db.notificationDao().deleteAll()
        assertEquals(0, db.notificationDao().count())
    }

    @Test fun exclusionsCanBeAddedAndRemoved() = runBlocking {
        val dao = db.excludedAppDao()
        assertFalse(dao.isExcluded("com.example"))
        dao.add(ExcludedApp("com.example")); assertTrue(dao.isExcluded("com.example"))
        dao.remove("com.example"); assertFalse(dao.isExcluded("com.example"))
    }

    @Test fun eventsSurviveClosingAndReopeningTheDatabase() = runBlocking {
        val name = "persist-test.db"
        ctx.deleteDatabase(name)
        var file = Room.databaseBuilder(ctx, NotifyHubDatabase::class.java, name).allowMainThreadQueries().build()
        val e = event("a").copy(messages = listOf(com.notifyhub.data.db.MessageInfo("Ann", "Hi", 5)))
        file.notificationDao().insert(e)
        file.close() // simulates process death; restart behaves the same because data is on disk
        file = Room.databaseBuilder(ctx, NotifyHubDatabase::class.java, name).allowMainThreadQueries().build()
        val back = file.notificationDao().getSendable(10, 10)
        assertEquals(1, back.size)
        assertEquals(DeliveryStatus.PENDING, back[0].deliveryStatus)
        assertEquals("Ann", back[0].messages[0].sender)
        file.close(); ctx.deleteDatabase(name)
    }
}
