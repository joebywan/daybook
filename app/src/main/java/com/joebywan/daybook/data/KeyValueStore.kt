package com.joebywan.daybook.data

import kotlinx.coroutines.flow.Flow

/**
 * The little the app asks of persistent storage: named string sets and named strings, in a
 * handful of separate files.
 *
 * An interface so that [ProgressStore] and `LaunchPreferences` compile unchanged for both targets.
 * Android backs it with the same DataStore files, keys and value types it always used
 * (`DataStoreKeyValueStore`), so nothing already saved on a phone changes shape; the web backs it
 * with `localStorage`.
 */
interface KeyValueStore {

    /** The set stored under [key], empty when there is none; re-emitted whenever the file changes. */
    fun stringSet(key: String): Flow<Set<String>>

    /** Replaces the set under [key] with [transform] of its current value, as one atomic write. */
    suspend fun updateStringSet(key: String, transform: (Set<String>) -> Set<String>)

    /** The string stored under [key], or null; re-emitted whenever the file changes. */
    fun string(key: String): Flow<String?>

    suspend fun putString(key: String, value: String)

    companion object {
        /** Solved days and games in progress. */
        const val PROGRESS = "daybook"

        /** View settings, kept apart from progress; see `LaunchPreferences`. */
        const val LAUNCH = "daybook_launch"
    }
}
