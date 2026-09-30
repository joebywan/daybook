package com.joebywan.daybook.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One delegate per file, and each must stay the only one for its name in the process: DataStore
// refuses a second instance over the same file. These are the names every installed copy already
// has its progress under, so they can never change.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = KeyValueStore.PROGRESS)
private val Context.launchStore: DataStore<Preferences> by preferencesDataStore(name = KeyValueStore.LAUNCH)

/** The DataStore file behind [KeyValueStore] [name] on Android. */
fun Context.preferencesFile(name: String): DataStore<Preferences> = when (name) {
    KeyValueStore.PROGRESS -> dataStore
    KeyValueStore.LAUNCH -> launchStore
    else -> error("No DataStore file is declared for '$name'")
}

/**
 * [KeyValueStore] over Preferences DataStore — the same `stringSetPreferencesKey` and
 * `stringPreferencesKey` entries `ProgressStore` and `LaunchPreferences` always wrote, so what is on
 * a phone already loads exactly as before.
 */
class DataStoreKeyValueStore(private val store: DataStore<Preferences>) : KeyValueStore {

    override fun stringSet(key: String): Flow<Set<String>> {
        val k = stringSetPreferencesKey(key)
        return store.data.map { prefs -> prefs[k].orEmpty() }
    }

    override suspend fun updateStringSet(key: String, transform: (Set<String>) -> Set<String>) {
        val k = stringSetPreferencesKey(key)
        store.edit { prefs -> prefs[k] = transform(prefs[k].orEmpty()) }
    }

    override fun string(key: String): Flow<String?> {
        val k = stringPreferencesKey(key)
        return store.data.map { prefs -> prefs[k] }
    }

    override suspend fun putString(key: String, value: String) {
        val k = stringPreferencesKey(key)
        store.edit { prefs -> prefs[k] = value }
    }
}
