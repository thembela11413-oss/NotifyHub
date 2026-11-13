package com.notifyhub.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromMessages(value: List<MessageInfo>): String = Json.encodeToString(value)

    @TypeConverter
    fun toMessages(value: String): List<MessageInfo> =
        runCatching { Json.decodeFromString<List<MessageInfo>>(value) }.getOrDefault(emptyList())
}
