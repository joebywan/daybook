package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.LevelProgress
import com.joebywan.daybook.data.LevelSolve
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.data.Stats
import com.joebywan.daybook.data.StoredGame
import com.joebywan.daybook.data.fits
import com.joebywan.daybook.data.replaceEntries
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What a crafted backup file or link could do before: hang the page, crash it on every load, or open what the player
 * has not reached. Each case here was a probe that failed on main.
 */
class UntrustedInputTest {

    private val today = LocalDate(2026, 10, 27)

    @Test(timeout = 5_000)
    fun `a solve on a far-off day is refused, not walked by the streak`() {
        // Before: decoded, and the streak walked 300 billion days from it on every load.
        val raw = listOf("sudoku|STANDARD|-300000000000|1|0", "sudoku|STANDARD|${today.toEpochDays()}|60|0")
        val all = raw.mapNotNull(Completion::decode)
        assertEquals(1, all.size)
        assertEquals(1, Stats.streak(all, today).current)
    }

    @Test
    fun `a day too large for a date is refused, not thrown`() {
        // Before: LocalDate.fromEpochDays threw inside the completions flow, a crash on every load.
        assertNull(Completion.decode("sudoku|STANDARD|999999999999|1|0"))
        assertNull(Completion.decode("sudoku|STANDARD|${today.toEpochDays()}|1|0|999999999999"))
        assertNull(Completion.decode("sudoku|STANDARD|${DailySeed.DAYS.first - 1}|1|0"))
        // Still read: a practice solve, an old five-field record and a new six-field one.
        assertNotNull(Completion.decode("sudoku|STANDARD|-|1|0"))
        assertNotNull(Completion.decode("sudoku|STANDARD|${today.toEpochDays()}|1|0"))
        assertNotNull(Completion.decode("sudoku|STANDARD|${today.toEpochDays()}|1|0|${today.toEpochDays()}"))
    }

    @Test
    fun `a link's date outside the calendar is ignored`() {
        assertNull(DailySeed.fromLink("-999999-01-01"))
        assertNull(DailySeed.fromLink("+999999-01-01"))
        assertNull(DailySeed.fromLink("1999-12-31"))
        assertNull(DailySeed.fromLink("nonsense"))
        assertEquals(LocalDate(2026, 3, 4), DailySeed.fromLink("2026-03-04"))
    }

    @Test
    fun `a level number that overflows the deal is refused`() {
        // Before: SIZE * level overflowed and the deal indexed out of range, at app start and on drawing the levels.
        assertNull(LevelSolve.decode("429496731|sudoku|1|0"))
        assertEquals(LevelProgress.FRESH, LevelProgress.of(emptySet(), setOf("429496731|sudoku|1|0")))
        assertTrue(LevelProgress.of(setOf("429496730|25|2"), emptySet()).opened.isEmpty())
        assertTrue(runCatching { Levels.plan(429496731) }.exceptionOrNull() is IllegalArgumentException)
        Levels.plan(Levels.LEVELS.last) // the last level accepted still deals
    }

    @Test
    fun `a link to a level not unlocked has no board`() {
        // Before: `?puzzle=sudoku&level=N` played any level, or crashed for a huge N.
        val fresh = LevelProgress.FRESH
        val first = fresh.plan(1).first()
        assertEquals(first, fresh.board(1, first.puzzleId))
        assertNull(fresh.board(2, fresh.plan(2).first().puzzleId))
        assertNull(fresh.board(429496731, "sudoku"))
    }

    @Test
    fun `a restore that does not fit puts back what was there`() {
        val disk = mutableMapOf("a" to "1", "b" to "2")
        val before = disk.toMap()
        // The third write fails, as a full localStorage does.
        var writes = 0
        val set = { k: String, v: String -> (++writes != 3).also { if (it) disk[k] = v } }
        assertFalse(replaceEntries(before, mapOf("a" to "x", "c" to "y", "d" to "z"), set) { disk.remove(it) })
        assertEquals(before, disk)
        // One that fits replaces everything: new entries in, stale ones out.
        assertTrue(replaceEntries(disk.toMap(), mapOf("b" to "3", "c" to "4"), { k, v -> disk[k] = v; true }) { disk.remove(it) })
        assertEquals(mapOf("b" to "3", "c" to "4"), disk)
    }

    @Test
    fun `a saved game that does not fit the board is dropped`() {
        val seed = 7L
        val sudoku = Sudoku.generate(seed, Difficulty.STANDARD) as SudokuState
        // Before: a Kings state under a Sudoku key was cast to SudokuState and crashed.
        assertFalse(SavedGame(Kings.generate(seed, Difficulty.STANDARD)).fits(sudoku))
        // Before: a Sudoku of one-cell lists decoded, and indexed out of range once drawn.
        val tiny = SudokuState(listOf(false), listOf(0), listOf(1))
        assertFalse(SavedGame(tiny).fits(sudoku))
        assertFalse(SavedGame(sudoku, history = listOf(tiny)).fits(sudoku))
        assertFalse(SavedGame(sudoku.copy(selected = 81)).fits(sudoku))
        assertTrue(SavedGame(sudoku.withCell(sudoku.givens.indexOf(false), 5), history = listOf(sudoku)).fits(sudoku))
    }

    /** The check must never cost a player a real game: every released save fits its board, old boards included. */
    @Test
    fun `every save a released build wrote fits its board`() {
        val dir = File(checkNotNull(javaClass.getResource("/saves")).toURI())
        for (file in dir.listFiles { f -> f.extension == "json" }!!) {
            for (raw in Json.parseToJsonElement(file.readText()).jsonArray) {
                val stored = StoredGame.decode(raw.toString())!!
                val (id, tier, seed) = stored.key.split("|")
                val initial = PuzzleRegistry.byId(id)!!.generate(seed.toLong(), Difficulty.valueOf(tier))
                assertTrue("${file.name} ${(raw as JsonObject)["key"]}", stored.game.fits(initial))
            }
        }
    }

    /** ...and neither does a game played out by hints from the first move to the solve, undo stack and all. */
    @Test
    fun `a game played to the end by hints fits its board at every step`() {
        for (puzzle in PuzzleRegistry.all) for (tier in listOf(Difficulty.STANDARD, Difficulty.EXPERT)) {
            val initial = puzzle.generate(DailySeed.seedFor(today, puzzle.id, tier), tier)
            var state: PuzzleState = initial
            val history = ArrayList<PuzzleState>()
            while (history.size < 400) {
                val next = puzzle.teach(state)?.apply?.invoke(state) ?: puzzle.hint(state) ?: break
                if (next == state) break
                history += state
                state = next
                assertTrue("${puzzle.id} $tier move ${history.size}", SavedGame(state, history.takeLast(SavedGame.UNDO_DEPTH)).fits(initial))
            }
        }
    }
}
