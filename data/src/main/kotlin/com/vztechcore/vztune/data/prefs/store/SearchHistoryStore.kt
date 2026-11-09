package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.historyDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "vztune_search_history")

private const val MAX_HISTORY = 30

@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("history")

    val history: Flow<List<String>> = context.historyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.readList() }

    suspend fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        context.historyDataStore.edit { prefs ->
            val current = prefs.readList()
            val updated = (listOf(q) + current.filter { it != q }).take(MAX_HISTORY)
            prefs[key] = Json.encodeToString(updated)
        }
    }

    suspend fun remove(query: String) {
        context.historyDataStore.edit { prefs ->
            val filtered = prefs.readList().filter { it != query }
            prefs[key] = Json.encodeToString(filtered)
        }
    }

    suspend fun clear() {
        context.historyDataStore.edit { it.remove(key) }
    }

    private fun Preferences.readList(): List<String> =
        this[key]?.let {
            runCatching { Json.decodeFromString<List<String>>(it) }
                .getOrDefault(emptyList())
        } ?: emptyList()
}