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

    private fun solves(level: Int, count: Int = Levels.SIZE) =
        Levels.plan(level).take(count).map { LevelSolve(level, it.puzzleId, 60, 0).encode() }.toSet()

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
        assertEquals(Levels.plan(1).take(2).map { it.puzzleId }.toSet(), p.solved(1))
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
        assertEquals(25, p.poolSize(4))
        assertEquals(Levels.POOL.size, p.poolSize(9))
        assertTrue(p.solves.isEmpty())
        assertEquals(4, p.current)
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
        assertEquals(Levels.POOL.size, store.openLevel(1))
        kv.sets.value = kv.sets.value + ("levels" to setOf("1|25")) // as if opened by an older build
        assertEquals(25, store.openLevel(1))
        assertEquals(setOf("1|25"), kv.sets.value["levels"])
        val board = Levels.plan(1).first().puzzleId
        assertTrue(store.recordLevelSolve(LevelSolve(1, board, 40, 1)))
        assertFalse("a re-solve records nothing", store.recordLevelSolve(LevelSolve(1, board, 20, 0)))
        assertEquals(setOf("1|$board|40|1"), kv.sets.value["level_solves"])
        for (b in Levels.plan(1).drop(1)) store.recordLevelSolve(LevelSolve(1, b.puzzleId, 30, 0))
        val p = store.levelProgress.first()
        assertEquals(setOf(1), p.done)
        assertEquals(2, p.current)
    }
}
