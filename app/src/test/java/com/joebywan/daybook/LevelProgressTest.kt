package com.joebywan.daybook

import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.data.LevelProgress
import com.joebywan.daybook.data.LevelSolve
import com.joebywan.daybook.data.ProgressStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the player stands in the levels, derived from the `levels` and `level_solves` sets. */
class LevelProgressTest {

    /** Entries written as `"n|25"` are from before deal 2, so these default to deal 1. */
    private fun solves(level: Int, count: Int = Levels.SIZE, deal: Int = 1) =
        Levels.plan(level, 25, deal).take(count).map { LevelSolve(level, it.puzzleId, 60, 0).encode() }.toSet()

    @Test
    fun `a fresh install is on level 1 with nothing done`() {
        val p = LevelProgress.FRESH
        assertEquals(1, p.current)
        assertTrue(p.done.isEmpty())
        assertTrue(p.isUnlocked(1))
        assertFalse(p.isUnlocked(2))
        assertEquals(Levels.plan(1), p.plan(1))
    }

    @Test
    fun `in the middle of a level`() {
        val p = LevelProgress.of(setOf("1|25"), solves(1, 2))
        assertEquals(1, p.current)
        assertEquals(Levels.plan(1, 25, 1).take(2).map { it.puzzleId }.toSet(), p.solved(1))
        assertTrue(p.done.isEmpty())
    }

    @Test
    fun `a level just done opens the next`() {
        val p = LevelProgress.of(setOf("1|25"), solves(1))
        assertEquals(setOf(1), p.done)
        assertEquals(2, p.current)
        assertTrue(p.isUnlocked(1))
        assertTrue(p.isUnlocked(2))
        assertFalse(p.isUnlocked(3))
    }

    @Test
    fun `replaying a done level leaves the current level where it is`() {
        val p = LevelProgress.of(setOf("1|25", "2|25", "3|25"), solves(1) + solves(2) + solves(3, 1) + solves(1, 2))
        assertEquals(setOf(1, 2), p.done)
        assertEquals(3, p.current)
    }

    @Test
    fun `an opened level keeps its pool size and bad entries are skipped`() {
        val p = LevelProgress.of(setOf("4|25", "junk", "5|", "6|999", "x|25"), setOf("1|sudoku|x|0", "2|kings|5"))
        assertEquals(mapOf(4 to 25), p.opened)
        assertEquals(1, p.deal)
        assertEquals(25, p.poolSize(4))
        assertEquals(Levels.POOL.size, p.poolSize(9))
        assertTrue(p.solves.isEmpty())
        assertEquals(4, p.current)
    }

    @Test
    fun `an entry from before deal 2 plans with deal 1, a new one with its stored deal`() {
        val old = LevelProgress.of(setOf("1|25", "2|25"), emptySet())
        assertEquals(1, old.deal)
        assertEquals(Levels.plan(2, 25, 1), old.plan(2))
        assertEquals("a level not yet opened keeps the player's deal", Levels.plan(3, 25, 1), old.plan(3))
        val new = LevelProgress.of(setOf("1|25|2"), emptySet())
        assertEquals(2, new.deal)
        assertEquals(Levels.plan(1, 25, 2), new.plan(1))
        assertEquals(Levels.LATEST, LevelProgress.FRESH.deal)
        val bad = LevelProgress.of(setOf("1|25|9", "2|25|x", "3|25|2|1"), emptySet())
        assertTrue(bad.opened.isEmpty())
    }

    @Test
    fun `a solve round-trips its encoding`() {
        val s = LevelSolve(14, "kings", 125, 2)
        assertEquals("14|kings|125|2", s.encode())
        assertEquals(s, LevelSolve.decode(s.encode()))
    }

    private class MemoryStore : KeyValueStore {
        val sets = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
        override fun stringSet(key: String): Flow<Set<String>> = sets.map { it[key].orEmpty() }
        override suspend fun updateStringSet(key: String, transform: (Set<String>) -> Set<String>) {
            sets.value = sets.value + (key to transform(sets.value[key].orEmpty()))
        }
        override fun string(key: String): Flow<String?> = MutableStateFlow(null)
        override suspend fun putString(key: String, value: String) = Unit
    }

    @Test
    fun `the store opens once and records a level's board once`() = runBlocking {
        val kv = MemoryStore()
        val store = ProgressStore(kv)
        assertEquals(Levels.plan(1, Levels.POOL.size, Levels.LATEST), store.openLevel(1))
        assertEquals(setOf("1|${Levels.POOL.size}|${Levels.LATEST}"), kv.sets.value["levels"])
        kv.sets.value = kv.sets.value + ("levels" to setOf("1|25")) // as if opened by an older build
        assertEquals(Levels.plan(1, 25, 1), store.openLevel(1))
        assertEquals(setOf("1|25"), kv.sets.value["levels"])
        val board = Levels.plan(1, 25, 1).first().puzzleId
        assertTrue(store.recordLevelSolve(LevelSolve(1, board, 40, 1)))
        assertFalse("a re-solve records nothing", store.recordLevelSolve(LevelSolve(1, board, 20, 0)))
        assertEquals(setOf("1|$board|40|1"), kv.sets.value["level_solves"])
        for (b in Levels.plan(1, 25, 1).drop(1)) store.recordLevelSolve(LevelSolve(1, b.puzzleId, 30, 0))
        val p = store.levelProgress.first()
        assertEquals(setOf(1), p.done)
        assertEquals(2, p.current)
    }

    @Test
    fun `a player who opened levels before deal 2 keeps deal 1, so levels 1-5 still deal every starter once`() = runBlocking {
        val kv = MemoryStore()
        kv.sets.value = mapOf("levels" to setOf("1|25", "2|25", "3|25"))
        val store = ProgressStore(kv)
        assertEquals(Levels.plan(4, Levels.POOL.size, 1), store.openLevel(4))
        assertEquals(setOf("1|25", "2|25", "3|25", "4|${Levels.POOL.size}"), kv.sets.value["levels"])
        val p = store.levelProgress.first()
        val starters = (1..5).flatMap { n -> p.plan(n).map { it.puzzleId } }
        assertEquals(Levels.POOL.take(Levels.STARTER).sorted(), starters.sorted())
    }

    @Test
    fun `a new player keeps the latest deal on every level they open`() = runBlocking {
        val kv = MemoryStore()
        val store = ProgressStore(kv)
        for (n in 1..3) store.openLevel(n)
        val size = Levels.POOL.size
        val d = Levels.LATEST
        assertEquals(setOf("1|$size|$d", "2|$size|$d", "3|$size|$d"), kv.sets.value["levels"])
    }
}
