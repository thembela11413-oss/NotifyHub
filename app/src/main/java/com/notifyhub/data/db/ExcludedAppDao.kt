package com.notifyhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(app: ExcludedApp)

    @Query("DELETE FROM excluded_apps WHERE packageName = :packageName")
    suspend fun remove(packageName: String)

    @Query("SELECT packageName FROM excluded_apps")
    fun observeAll(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM excluded_apps WHERE packageName = :packageName)")
    suspend fun isExcluded(packageName: String): Boolean
}
