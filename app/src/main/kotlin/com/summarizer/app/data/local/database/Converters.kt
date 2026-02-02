package com.summarizer.app.data.local.database

import androidx.room.TypeConverter
import com.summarizer.app.data.local.entity.MessageType
import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.ParticipantHighlight
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromMessageType(value: MessageType): String {
        return value.name
    }

    @TypeConverter
    fun toMessageType(value: String): MessageType {
        return try {
            MessageType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MessageType.UNKNOWN
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString<List<String>>(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return json.decodeFromString<List<String>>(value)
    }

    @TypeConverter
    fun fromActionItemList(value: List<ActionItem>): String {
        return json.encodeToString<List<ActionItem>>(value)
    }

    @TypeConverter
    fun toActionItemList(value: String): List<ActionItem> {
        return json.decodeFromString<List<ActionItem>>(value)
    }

    @TypeConverter
    fun fromParticipantHighlightList(value: List<ParticipantHighlight>): String {
        return json.encodeToString<List<ParticipantHighlight>>(value)
    }

    @TypeConverter
    fun toParticipantHighlightList(value: String): List<ParticipantHighlight> {
        return json.decodeFromString<List<ParticipantHighlight>>(value)
    }
}
