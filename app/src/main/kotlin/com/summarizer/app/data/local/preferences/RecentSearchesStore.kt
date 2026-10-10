package com.summarizer.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentSearchesDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_searches")

/**
 * The last few search queries, newest first, kept on the device in their own DataStore file so
 * the search screen can offer them again. Wiped by a reset.
 */
@Singleton
class RecentSearchesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val recentSearches: Flow<List<String>> = context.recentSearchesDataStore.data.map { decode(it[KEY]) }

    suspend fun add(query: String) {
        context.recentSearchesDataStore.edit { preferences ->
            preferences[KEY] = encode(withQuery(decode(preferences[KEY]), query))
        }
    }

    suspend fun remove(query: String) {
        context.recentSearchesDataStore.edit { preferences ->
            preferences[KEY] = encode(decode(preferences[KEY]).filterNot { it == query })
        }
    }

    suspend fun clear() {
        context.recentSearchesDataStore.edit { it.clear() }
    }

    companion object {
        const val MAX_RECENT_SEARCHES = 8

        private val KEY = stringPreferencesKey("queries")
        private val serializer = ListSerializer(String.serializer())

        /**
         * [current] with [query] moved to the front: trimmed, blank queries ignored, an earlier
         * entry that differs only in case replaced, and at most [max] entries kept.
         */
        fun withQuery(current: List<String>, query: String, max: Int = MAX_RECENT_SEARCHES): List<String> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return current
            return (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(max)
        }

        internal fun encode(queries: List<String>): String = Json.encodeToString(serializer, queries)

        internal fun decode(value: String?): List<String> =
            value?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
    }
}
