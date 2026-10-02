package com.joebywan.daybook

import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.ui.home.LaunchPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchPreferencesTest {

    private class MemoryStore : KeyValueStore {
        val strings = MutableStateFlow<Map<String, String>>(emptyMap())
        override fun stringSet(key: String): Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun updateStringSet(key: String, transform: (Set<String>) -> Set<String>) = Unit
        override fun string(key: String): Flow<String?> = strings.map { it[key] }
        override suspend fun putString(key: String, value: String) {
            strings.value = strings.value + (key to value)
        }
    }

    @Test
    fun timerShowsByDefaultAndFollowsTheSwitch() = runBlocking {
        val prefs = LaunchPreferences(MemoryStore())
        assertEquals("a player who never opened Settings keeps the clock", true, prefs.showTimer.first())
        prefs.setShowTimer(false)
        assertEquals(false, prefs.showTimer.first())
        prefs.setShowTimer(true)
        assertEquals(true, prefs.showTimer.first())
    }

    @Test
    fun soundIsOnByDefaultAndFollowsTheSwitch() = runBlocking {
        val prefs = LaunchPreferences(MemoryStore())
        assertEquals("a player who never opened Settings hears the chime", true, prefs.playSound.first())
        prefs.setPlaySound(false)
        assertEquals(false, prefs.playSound.first())
        prefs.setPlaySound(true)
        assertEquals(true, prefs.playSound.first())
    }

    @Test
    fun soundAndTimerAreIndependentAndAnUnreadableSoundValueKeepsTheChime() = runBlocking {
        val store = MemoryStore()
        val prefs = LaunchPreferences(store)
        prefs.setPlaySound(false)
        assertEquals(true, prefs.showTimer.first())
        prefs.setShowTimer(false)
        assertEquals(false, prefs.playSound.first())
        store.putString("play_sound", "garbage")
        assertEquals(true, prefs.playSound.first())
    }

    @Test
    fun anUnreadableValueKeepsTheClock() = runBlocking {
        val store = MemoryStore()
        store.putString("show_timer", "garbage")
        assertEquals(true, LaunchPreferences(store).showTimer.first())
    }
}
