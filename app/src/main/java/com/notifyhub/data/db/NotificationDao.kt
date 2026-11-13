package com.notifyhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    /** Returns the new row id, or -1 when ignored because the dedupHash already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: NotificationEvent): Long

    @Query("SELECT * FROM notification_events ORDER BY receivedAt DESC, rowid DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationEvent>>

    @Query("SELECT COUNT(*) FROM notification_events")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_events")
    suspend fun count(): Int

    /** Everything not yet delivered: PENDING, SENDING and FAILED. */
    @Query("SELECT COUNT(*) FROM notification_events WHERE deliveryStatus != 'DELIVERED'")
    fun observeUndelivered(): Flow<Int>

    @Query(
        "SELECT * FROM notification_events " +
            "WHERE deliveryStatus IN ('PENDING','FAILED') AND attemptCount < :maxAttempts " +
            "ORDER BY receivedAt ASC, rowid ASC LIMIT :limit"
    )
    suspend fun getSendable(limit: Int, maxAttempts: Int): List<NotificationEvent>

    @Query("UPDATE notification_events SET deliveryStatus = 'SENDING', lastAttemptAt = :now WHERE eventId IN (:ids)")
    suspend fun markSending(ids: List<String>, now: Long)

    @Query("UPDATE notification_events SET deliveryStatus = 'DELIVERED', deliveredAt = :now, lastError = NULL WHERE eventId IN (:ids)")
    suspend fun markDelivered(ids: List<String>, now: Long)

    @Query("UPDATE notification_events SET deliveryStatus = 'FAILED', attemptCount = attemptCount + 1, lastError = :error WHERE eventId IN (:ids)")
    suspend fun markFailed(ids: List<String>, error: String)

    /** Recovers rows left in SENDING by a crash or killed worker. */
    @Query("UPDATE notification_events SET deliveryStatus = 'PENDING' WHERE deliveryStatus = 'SENDING'")
    suspend fun resetStaleSending(): Int

    @Query("DELETE FROM notification_events")
    suspend fun deleteAll()
}
