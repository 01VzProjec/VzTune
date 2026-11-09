package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Base class for every small "list persisted as JSON" store (history, followed artists,
 * favorite playlists, local playlists...). Subclasses only declare the file name,
 * the item serializer and how to identify an item; all list operations live here.
 */
abstract class JsonListStore<T : Any>(
    context: Context,
    storeName: String,
    keyName: String,
    serializer: KSerializer<T>,
    private val idOf: (T) -> String,
    private val maxItems: Int = Int.MAX_VALUE,
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(storeName) }
    )
    private val key = stringPreferencesKey(keyName)
    private val listSerializer = ListSerializer(serializer)
    private val json = Json { ignoreUnknownKeys = true }

    val items: Flow<List<T>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.readList() }

    /** Emits whether an item with [id] is stored. */
    fun contains(id: String): Flow<Boolean> = items.map { list -> list.any { idOf(it) == id } }

    /** Puts [item] at the top, replacing any previous entry with the same id. */
    suspend fun addFirst(item: T) = edit { current ->
        (listOf(item) + current.filterNot { idOf(it) == idOf(item) }).take(maxItems)
    }

    /** Adds the item if missing, removes it otherwise. */
    suspend fun toggle(item: T) = edit { current ->
        if (current.any { idOf(it) == idOf(item) }) {
            current.filterNot { idOf(it) == idOf(item) }
        } else {
            listOf(item) + current
        }
    }

    suspend fun remove(id: String) = edit { current -> current.filterNot { idOf(it) == id } }

    suspend fun update(id: String, transform: (T) -> T) = edit { current ->
        current.map { if (idOf(it) == id) transform(it) else it }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }

    /** Rewrites the whole list; subclasses use it for operations the base class does not cover. */
    protected suspend fun edit(transform: (List<T>) -> List<T>) {
        dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(listSerializer, transform(prefs.readList()))
        }
    }

    private fun Preferences.readList(): List<T> =
        this[key]?.let {
            runCatching { json.decodeFromString(listSerializer, it) }.getOrDefault(emptyList())
        } ?: emptyList()
}
