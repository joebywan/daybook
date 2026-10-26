package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.Stats
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A streak day is a day the player solved that day's daily on that day; an archive solve counts for totals and best
 * times but not the streak. Records written before [Completion.playedOn] existed must load and count as they did.
 */
class ArchiveStreakTest {
    private fun day(i: Int) = LocalDate.fromEpochDays(20700L + i)
    private val today = day(10)

    @Test
    fun `an old five-field record decodes, counts and encodes unchanged`() {
        val old = "kings|HARD|${today.toEpochDays()}|75|1"
        val c = Completion.decode(old)!!
        assertNull(c.playedOn)
        assertEquals(today, c.streakDay)
        assertEquals(old, c.encode())
        // Old records of three days in a row, whatever they were, keep their streak.
        val history = (8..10).map { Completion.decode("kings|HARD|${day(it).toEpochDays()}|75|1")!! }
        assertEquals(3, Stats.streak(history, today).current)
    }

    @Test
    fun `a new record round-trips with the day it was played`() {
        val c = Completion("sudoku", Difficulty.EXPERT, day(3), 300, 0, playedOn = today)
        assertEquals("sudoku|EXPERT|${day(3).toEpochDays()}|300|0|${today.toEpochDays()}", c.encode())
        assertEquals(c, Completion.decode(c.encode()))
        val practice = Completion("sudoku", Difficulty.EXPERT, null, 300, 0, playedOn = today)
        assertEquals(practice, Completion.decode(practice.encode()))
        assertNull(practice.streakDay)
    }

    @Test
    fun `malformed records are refused`() {
        assertNull(Completion.decode("kings|HARD|20700|75|1|x"))
        assertNull(Completion.decode("kings|HARD|20700|75|1|20700|9"))
        assertNull(Completion.decode("kings|HARD|20700|75"))
    }

    @Test
    fun `an archive solve does not extend the streak but counts elsewhere`() {
        val onTheDay = listOf(Completion("kings", Difficulty.STANDARD, today, 60, 0, playedOn = today))
        val archive = (5..9).map { Completion("kings", Difficulty.STANDARD, day(it), 50, 0, playedOn = today) }
        assertEquals(1, Stats.streak(onTheDay + archive, today).current)
        assertEquals(1, Stats.streak(onTheDay + archive, today).best)
        assertEquals(50, Stats.bestTime(onTheDay + archive, "kings", Difficulty.STANDARD))
        // Nor does it earn a streak achievement, though it counts as a solve.
        val ids = earnedAchievements(onTheDay + archive, today, listOf("kings")).map { it.id }
        assertTrue("solves1" in ids)
        assertFalse("streak3" in ids)
        // The same days solved on their own days do.
        val ownDays = (5..10).map { Completion("kings", Difficulty.STANDARD, day(it), 50, 0, playedOn = day(it)) }
        assertEquals(6, Stats.streak(ownDays, today).current)
        assertTrue("streak3" in earnedAchievements(ownDays, today, listOf("kings")).map { it.id })
    }

    @Test
    fun `the web backup carries old and new records through unchanged`() {
        // WebStorage keeps a set as a JSON array of strings and the backup copies it verbatim; replay that.
        val records = setOf(
            "kings|HARD|${today.toEpochDays()}|75|1",
            "sudoku|EXPERT|-|300|0",
            Completion("tents", Difficulty.STANDARD, day(4), 90, 2, playedOn = today).encode(),
        )
        val list = ListSerializer(String.serializer())
        val inner = Json.encodeToString(list, records.toList())
        val file = Json.encodeToString(String.serializer(), inner)
        val back = Json.decodeFromString(list, Json.decodeFromString(String.serializer(), file)).toSet()
        assertEquals(records, back)
        val decoded = back.map { Completion.decode(it)!! }
        assertEquals(records, decoded.map(Completion::encode).toSet())
        assertEquals(1, Stats.streak(decoded, today).current)
    }
}
