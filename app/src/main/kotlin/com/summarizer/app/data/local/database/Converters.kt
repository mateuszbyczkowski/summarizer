package com.summarizer.app.data.local.database

import androidx.room.TypeConverter
import com.summarizer.app.domain.model.MessageType
import com.summarizer.app.domain.model.ActionItem
import com.summarizer.app.domain.model.KeyDate
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
        // A malformed row must not break every query that reads it
        return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromActionItemList(value: List<ActionItem>): String {
        return json.encodeToString<List<ActionItem>>(value)
    }

    @TypeConverter
    fun toActionItemList(value: String): List<ActionItem> {
        // A malformed row must not break every query that reads it
        return runCatching { json.decodeFromString<List<ActionItem>>(value) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromKeyDateList(value: List<KeyDate>): String {
        return json.encodeToString<List<KeyDate>>(value)
    }

    @TypeConverter
    fun toKeyDateList(value: String): List<KeyDate> {
        // A malformed row must not break every query that reads it
        return runCatching { json.decodeFromString<List<KeyDate>>(value) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromParticipantHighlightList(value: List<ParticipantHighlight>): String {
        return json.encodeToString<List<ParticipantHighlight>>(value)
    }

    @TypeConverter
    fun toParticipantHighlightList(value: String): List<ParticipantHighlight> {
        // A malformed row must not break every query that reads it
        return runCatching { json.decodeFromString<List<ParticipantHighlight>>(value) }.getOrDefault(emptyList())
    }
}
