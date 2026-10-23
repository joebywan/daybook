package com.joebywan.daybook

import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.ui.home.LaunchPreferences
import com.joebywan.daybook.ui.home.timerShown
import com.joebywan.daybook.ui.theme.Appearance
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
    fun timerIsHiddenByDefaultAndFollowsTheSwitch() = runBlocking {
        val prefs = LaunchPreferences(MemoryStore())
        assertEquals("nothing stored hides the clock", false, prefs.showTimer.first())
        prefs.setShowTimer(true)
        assertEquals(true, prefs.showTimer.first())
        prefs.setShowTimer(false)
        assertEquals(false, prefs.showTimer.first())
    }

    @Test
    fun theTimerRuleKeepsAStoredChoiceAndOtherwiseAsksWhetherThePlayerHasPlayed() {
        assertEquals(false, timerShown(null, hasPlayed = false))
        assertEquals("an update must not take an existing player's clock away", true, timerShown(null, hasPlayed = true))
        for (played in listOf(false, true)) {
            assertEquals(true, timerShown("on", played))
            assertEquals(false, timerShown("off", played))
            assertEquals(true, timerShown("garbage", played))
        }
    }

    @Test
    fun settlingWritesTheDefaultOnceAndNeverOverwritesAChoice() = runBlocking {
        val returning = MemoryStore()
        LaunchPreferences(returning).settleShowTimer { true }
        assertEquals("on", returning.strings.value["show_timer"])

        val fresh = MemoryStore()
        LaunchPreferences(fresh).settleShowTimer { false }
        assertEquals("off", fresh.strings.value["show_timer"])
        // Once written, later progress does not turn it back on: the first answer is the default.
        LaunchPreferences(fresh).settleShowTimer { true }
        assertEquals("off", fresh.strings.value["show_timer"])

        val chose = MemoryStore()
        LaunchPreferences(chose).setShowTimer(false)
        LaunchPreferences(chose).settleShowTimer { true }
        assertEquals(false, LaunchPreferences(chose).showTimer.first())
    }

    @Test
    fun theWelcomeIsForANewPlayerOnceAndNeverForOneWhoHasPlayed() = runBlocking {
        val returning = MemoryStore()
        assertEquals(false, LaunchPreferences(returning).needsWelcome { true })
        assertEquals("marked, so losing every save later cannot bring it back", false, LaunchPreferences(returning).needsWelcome { false })

        val fresh = LaunchPreferences(MemoryStore())
        assertEquals(true, fresh.needsWelcome { false })
        assertEquals("not marked until Start", true, fresh.needsWelcome { false })
        fresh.setWelcomed()
        assertEquals(false, fresh.needsWelcome { false })
    }

    @Test
    fun appearanceFollowsTheSystemUntilChosenAndAnUnknownValueFallsBack() = runBlocking {
        val store = MemoryStore()
        val prefs = LaunchPreferences(store)
        assertEquals(Appearance.SYSTEM, prefs.appearance.first())
        prefs.setAppearance(Appearance.DARK)
        assertEquals(Appearance.DARK, prefs.appearance.first())
        assertEquals("the web page's start-up script reads this exact value", "DARK", store.strings.value["appearance"])
        store.putString("appearance", "garbage")
        assertEquals(Appearance.SYSTEM, prefs.appearance.first())
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
    fun vibrationIsOnByDefaultAndIndependentOfSound() = runBlocking {
        val prefs = LaunchPreferences(MemoryStore())
        assertEquals("a player who never opened Settings feels the buzz", true, prefs.vibrate.first())
        prefs.setVibrate(false)
        prefs.setPlaySound(true)
        assertEquals(false, prefs.vibrate.first())
        prefs.setVibrate(true)
        assertEquals(true, prefs.vibrate.first())
    }

    @Test
    fun soundAndTimerAreIndependentAndAnUnreadableSoundValueKeepsTheChime() = runBlocking {
        val store = MemoryStore()
        val prefs = LaunchPreferences(store)
        prefs.setPlaySound(false)
        assertEquals(false, prefs.showTimer.first())
        prefs.setShowTimer(true)
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
