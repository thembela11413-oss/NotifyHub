package com.notifyhub.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [NotificationEvent::class, ExcludedApp::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NotifyHubDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun excludedAppDao(): ExcludedAppDao

    companion object {
        const val FILE_NAME = "notifyhub.db"
        fun build(context: Context): NotifyHubDatabase =
            Room.databaseBuilder(context.applicationContext, NotifyHubDatabase::class.java, FILE_NAME).build()
    }
}
