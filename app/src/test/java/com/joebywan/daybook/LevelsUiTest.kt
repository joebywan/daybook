package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.LevelProgress
import com.joebywan.daybook.data.LevelSolve
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import com.joebywan.daybook.ui.home.levelNote
import com.joebywan.daybook.ui.home.solvedCount
import com.joebywan.daybook.ui.levelFinishedBy
import com.joebywan.daybook.ui.play.NextKind
import com.joebywan.daybook.ui.play.levelNextOptions
import com.joebywan.daybook.ui.play.shareCard
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure parts of the Levels screens: the saved route, the finish frame's buttons, the home card's line. */
class LevelsUiTest {

    private fun ids(level: Int) = Levels.plan(level).map { it.puzzleId }

    private fun progress(vararg solved: Pair<Int, Int>): LevelProgress = LevelProgress.of(
        solved.map { "${it.first}|${Levels.POOL.size}" }.toSet(),
        solved.flatMap { (level, count) -> ids(level).take(count).map { LevelSolve(level, it, 60, 0).encode() } }.toSet(),
    )

    @Test fun `every route survives the saver`() {
        val routes = listOf(
            Route.Home, Route.Stats, Route.Settings, Route.Achievements(true), Route.Archive("kings"),
            Route.Levels(), Route.Levels(14),
            Route.Play("kings", Difficulty.HARD, LocalDate(2026, 10, 1)),
            Route.Play("kings", Difficulty.HARD, null, 7L),
            Route.Play("kings", Difficulty.EXPERT, null, level = 14),
        )
        for (r in routes) assertEquals(r, decodeRoute(encodeRoute(r)))
    }

    @Test fun `a play route saved before levels still reads`() {
        // Exactly what the released build wrote: five fields, no level.
        assertEquals(Route.Play("kings", Difficulty.HARD, LocalDate(2026, 10, 1), 0L), decodeRoute("play|kings|HARD|2026-10-01|0"))
        assertEquals(Route.Play("sets", Difficulty.STANDARD, null, 3L), decodeRoute("play|sets|STANDARD||3"))
    }

    @Test fun `mid level offers the first unsolved board, then home`() {
        val p = progress(1 to 2)
        val o = levelNextOptions(1, p.plan(1), p.solved(1), ids(1)[2], p.current)
        val next = Levels.plan(1)[3]
        assertEquals(listOf("Next in level 1", "Home"), o.map { it.label })
        assertEquals(NextKind.LEVEL, o[0].kind)
        assertEquals(next.puzzleId, o[0].puzzleId)
        assertEquals(1, o[0].level)
        assertEquals("${PuzzleRegistry.byId(next.puzzleId)!!.displayName} · ${next.difficulty.label}", o[0].note)
        assertEquals(NextKind.DONE, o[1].kind)
    }

    @Test fun `solving out of order skips what is done`() {
        // Boards 0, 1 and 3 solved, now board 2: the next is board 4.
        val p = LevelProgress.of(setOf("1|${Levels.POOL.size}"), listOf(0, 1, 3).map { LevelSolve(1, ids(1)[it], 60, 0).encode() }.toSet())
        assertEquals(ids(1)[4], levelNextOptions(1, p.plan(1), p.solved(1), ids(1)[2], p.current)[0].puzzleId)
    }

    @Test fun `the fifth board offers the next level`() {
        val p = progress(1 to 4)
        val o = levelNextOptions(1, p.plan(1), p.solved(1), ids(1)[4], p.current)
        assertEquals(listOf("Level 2", "Home"), o.map { it.label })
        assertEquals(2, o[0].level)
        assertEquals(null, o[0].puzzleId)
        assertTrue(levelFinishedBy(p, 1, ids(1)[4]))
        assertFalse(levelFinishedBy(p, 1, ids(1)[0])) // already solved: the level is still a board short
    }

    @Test fun `a replay of a finished level offers the level the player is on`() {
        val p = progress(1 to 5, 2 to 5, 3 to 1)
        assertEquals(3, p.current)
        val o = levelNextOptions(1, p.plan(1), p.solved(1), ids(1)[0], p.current)
        assertEquals("Level 3", o[0].label)
        assertFalse(levelFinishedBy(p, 1, ids(1)[0])) // done before: "Level 1 done" is not said twice
    }

    @Test fun `the home line says the last level is done until the new one starts`() {
        assertEquals(Levels.summary(Levels.plan(1)), levelNote(LevelProgress.FRESH, 1))
        val justDone = progress(1 to 5)
        assertEquals(2, justDone.current)
        assertEquals("Level 1 done  ·  ${Levels.summary(Levels.plan(2))}", levelNote(justDone, 2))
        assertEquals(Levels.summary(Levels.plan(2)), levelNote(progress(1 to 5, 2 to 1), 2))
        assertEquals(0, solvedCount(justDone, 2))
        assertEquals(1, solvedCount(progress(1 to 5, 2 to 1), 2))
    }

    @Test fun `a level's share card names the level where the daily has its date`() {
        val state = Sudoku.generate(1L, Difficulty.HARD)
        val card = shareCard(Sudoku, Difficulty.HARD, null, "3:10", 0, state, "mmm", 0, level = 14)
        assertEquals("Daybook · Level 14 · Sudoku · Hard", card.header)
    }
}
