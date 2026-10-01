package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pencil marks. The peers here are worked out from row, column and box arithmetic written out again,
 * not through [Sudoku.peers], so a slip in the shared table cannot hide on both sides of a test.
 */
class SudokuNotesTest {

    /**
     * The saved game the build before notes existed wrote for [oldBoard] (selected, a 6 placed, then
     * another cell selected; one undo step; one hint; 42 s). Captured by running that build's
     * `SavedGame.encode` on origin/main at 2da33c0, not written by hand.
     */
    private val oldJson = """{"state":{"type":"com.joebywan.daybook.puzzles.SudokuState","givens":[true,true,true,true,false,true,true,true,true,true,false,true,true,true,true,true,true,true,true,true,true,true,true,true,false,true,true,true,true,true,false,true,true,true,true,true,true,true,false,true,true,true,false,true,true,true,true,true,true,true,true,true,false,true,true,true,true,true,false,true,true,true,true,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false],"cells":[8,2,3,1,6,4,5,7,9,1,0,4,2,7,5,6,8,3,5,6,7,9,8,3,0,4,2,9,4,1,0,5,2,3,6,7,7,8,0,6,3,1,0,2,4,6,3,2,4,9,7,8,0,1,2,5,6,3,0,9,7,1,8,0,7,9,5,1,8,2,3,6,3,1,8,7,2,6,4,9,0],"solution":[8,2,3,1,6,4,5,7,9,1,9,4,2,7,5,6,8,3,5,6,7,9,8,3,1,4,2,9,4,1,8,5,2,3,6,7,7,8,5,6,3,1,9,2,4,6,3,2,4,9,7,8,5,1,2,5,6,3,4,9,7,1,8,4,7,9,5,1,8,2,3,6,3,1,8,7,2,6,4,9,5],"selected":10,"moves":1},"history":[{"type":"com.joebywan.daybook.puzzles.SudokuState","givens":[true,true,true,true,false,true,true,true,true,true,false,true,true,true,true,true,true,true,true,true,true,true,true,true,false,true,true,true,true,true,false,true,true,true,true,true,true,true,false,true,true,true,false,true,true,true,true,true,true,true,true,true,false,true,true,true,true,true,false,true,true,true,true,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false],"cells":[8,2,3,1,0,4,5,7,9,1,0,4,2,7,5,6,8,3,5,6,7,9,8,3,0,4,2,9,4,1,0,5,2,3,6,7,7,8,0,6,3,1,0,2,4,6,3,2,4,9,7,8,0,1,2,5,6,3,0,9,7,1,8,0,7,9,5,1,8,2,3,6,3,1,8,7,2,6,4,9,0],"solution":[8,2,3,1,6,4,5,7,9,1,9,4,2,7,5,6,8,3,5,6,7,9,8,3,1,4,2,9,4,1,8,5,2,3,6,7,7,8,5,6,3,1,9,2,4,6,3,2,4,9,7,8,5,1,2,5,6,3,4,9,7,1,8,4,7,9,5,1,8,2,3,6,3,1,8,7,2,6,4,9,5],"selected":4}],"hints":1,"seconds":42}"""

    private fun oldBoard(): SudokuState = Sudoku.tutorial[2].state as SudokuState

    private fun board(seed: Long = 7L, difficulty: Difficulty = Difficulty.HARD) =
        Sudoku.generate(seed, difficulty) as SudokuState

    private fun sees(a: Int, b: Int): Boolean =
        a != b && (a / 9 == b / 9 || a % 9 == b % 9 || (a / 27 == b / 27 && a % 9 / 3 == b % 9 / 3))

    private fun SudokuState.noted(i: Int): Set<Int> = (1..9).filter { hasNote(i, it) }.toSet()

    /** An empty cell, and a digit none of its peers holds. */
    private fun SudokuState.roomyCell(skip: Set<Int> = emptySet()): Pair<Int, Int> {
        for (i in cells.indices) {
            if (cells[i] != 0 || i in skip) continue
            val d = (1..9).firstOrNull { d -> cells.indices.none { sees(i, it) && cells[it] == d } }
            if (d != null) return i to d
        }
        error("no open cell with a free digit")
    }

    // ---- toggling --------------------------------------------------------------------------------

    @Test
    fun `a digit key in notes mode toggles that candidate, one state each`() {
        val s = board()
        val (cell, d) = s.roomyCell()
        val other = (1..9).first { it != d && s.canNote(cell, it) }

        val one = s.toggleNote(cell, d)
        assertEquals(setOf(d), one.noted(cell))
        assertEquals("one state, one move", s.moves + 1, one.moves)
        assertEquals("placing nothing", s.cells, one.cells)

        val two = one.toggleNote(cell, other)
        assertEquals(setOf(d, other), two.noted(cell))

        val back = two.toggleNote(cell, d)
        assertEquals(setOf(other), back.noted(cell))
        assertEquals("a note is the cell's own", emptySet<Int>(), back.noted(cell + 1))
    }

    @Test
    fun `a tap that cannot change anything is refused rather than made an undo step`() {
        val s = board()
        val given = s.givens.indexOfFirst { it }
        assertSame("a given", s, s.toggleNote(given, 5))

        val (cell, d) = s.roomyCell()
        val placed = s.withCell(cell, d)
        assertSame("a filled cell carries no notes", placed, placed.toggleNote(cell, 3))
        assertFalse(placed.canNote(cell, 3))

        // A digit a peer already holds would be hidden the moment it was made.
        val open = s.cells.indices.first { s.cells[it] == 0 }
        val held = s.cells.indices.first { sees(open, it) && s.cells[it] != 0 }
        assertFalse(s.canNote(open, s.cells[held]))
        assertSame(s, s.toggleNote(open, s.cells[held]))
    }

    // ---- placing and erasing -----------------------------------------------------------------------

    @Test
    fun `placing a digit clears that cell's notes and only that cell's`() {
        val s0 = board()
        val (a, da) = s0.roomyCell()
        val (b, db) = s0.roomyCell(skip = setOf(a))
        val s = s0.toggleNote(a, da).toggleNote(b, db)
        val placed = s.withCell(a, da)
        assertEquals(da, placed.cells[a])
        assertEquals(0, placed.notes[a])
        assertEquals("the other cell keeps its note", s.notes[b], placed.notes[b])
        assertEquals(s.moves + 1, placed.moves)
    }

    @Test
    fun `erasing an empty cell clears its notes, and erasing a digit leaves none behind`() {
        val s0 = board()
        val (a, d) = s0.roomyCell()
        val s = s0.toggleNote(a, d).toggleNote(a, (1..9).first { it != d && s0.canNote(a, it) })
        assertEquals(2, s.noted(a).size)
        val erased = s.withCell(a, 0)
        assertEquals(0, erased.notes[a])
        assertEquals(0, erased.cells[a])

        // The key's own toggle: tap a placed digit again to clear it; the cell is then clean.
        val round = s0.withCell(a, d).withCell(a, 0)
        assertEquals(emptySet<Int>(), round.noted(a))
    }

    @Test
    fun `a given cell cannot be written to`() {
        val s = board()
        val given = s.givens.indexOfFirst { it }
        assertSame(s, s.withCell(given, 0))
        assertEquals(0, s.visibleNotes(given))
    }

    // ---- derived hiding ----------------------------------------------------------------------------

    @Test
    fun `a note a peer's digit rules out is hidden, not deleted, and returns with the digit gone`() {
        val s0 = board()
        val (a, d) = s0.roomyCell()
        val noted = s0.toggleNote(a, d)
        // Put d in some other open cell that shares a row, column or box with a.
        val peer = s0.cells.indices.first { s0.cells[it] == 0 && sees(a, it) && s0.canNote(it, d) }
        val shadowed = noted.copy(cells = noted.cells.toMutableList().also { it[peer] = d })

        assertEquals("hidden", emptySet<Int>(), shadowed.noted(a))
        assertEquals("not deleted", noted.notes, shadowed.notes)

        val lifted = shadowed.copy(cells = shadowed.cells.toMutableList().also { it[peer] = 0 })
        assertEquals("back again", setOf(d), lifted.noted(a))
        assertEquals(noted, lifted)
    }

    @Test
    fun `only a peer rules a note out, and only the digit it holds`() {
        val s0 = board()
        val (a, d) = s0.roomyCell()
        val other = (1..9).first { it != d && s0.canNote(a, it) }
        val s = s0.toggleNote(a, d).toggleNote(a, other)
        val stranger = s.cells.indices.first { s.cells[it] == 0 && !sees(a, it) && it != a }
        val far = s.copy(cells = s.cells.toMutableList().also { it[stranger] = d })
        assertEquals("a non-peer holding d changes nothing", setOf(d, other), far.noted(a))
        val near = s.cells.indices.first { s.cells[it] == 0 && sees(a, it) }
        val shadow = s.copy(cells = s.cells.toMutableList().also { it[near] = other })
        assertEquals("a peer holding $other hides only $other", setOf(d), shadow.noted(a))
    }

    @Test
    fun `a hidden note cannot be toggled, since nothing shows to toggle`() {
        val s0 = board()
        val (a, d) = s0.roomyCell()
        val peer = s0.cells.indices.first { s0.cells[it] == 0 && sees(a, it) && s0.canNote(it, d) }
        val s = s0.toggleNote(a, d).copy(cells = s0.cells.toMutableList().also { it[peer] = d })
        assertFalse(s.canNote(a, d))
        assertSame(s, s.toggleNote(a, d))
    }

    // ---- undo, restart, saves ----------------------------------------------------------------------

    @Test
    fun `notes ride the state, so undo and restart take them back with the move`() {
        // PlayScreen: a move pushes the old state; undo pops it; restart returns to `initial`.
        val initial = board()
        val (a, d) = initial.roomyCell()
        var game = SavedGame(initial)
        fun move(next: SudokuState) {
            game = game.copy(state = next, history = game.history + game.state)
        }

        move((game.state as SudokuState).select(a))
        move((game.state as SudokuState).toggleNote(a, d))
        assertEquals(setOf(d), (game.state as SudokuState).noted(a))
        assertEquals("select, note: two undo steps", 2, game.history.size)

        // Through the save path and back, as a process death would.
        game = SavedGame.decode(game.encode())!!
        assertEquals(setOf(d), (game.state as SudokuState).noted(a))

        game = game.copy(state = game.history.last(), history = game.history.dropLast(1))
        assertEquals("undo takes exactly the note", emptySet<Int>(), (game.state as SudokuState).noted(a))
        assertEquals(a, (game.state as SudokuState).selected)

        move((game.state as SudokuState).toggleNote(a, d))
        game = game.copy(state = initial, history = emptyList())
        assertEquals("restart", initial, game.state)
        assertTrue((game.state as SudokuState).notes.all { it == 0 })
    }

    @Test
    fun `a save written before notes existed still loads, with none`() {
        val game = SavedGame.decode(oldJson)
        assertNotNull("the old save was dropped", game)
        val state = game!!.state as SudokuState
        assertEquals(oldBoard().select(4).withCell(4, 6).select(10), state)
        assertTrue(state.notes.all { it == 0 })
        assertEquals(81, state.notes.size)
        assertEquals(1, game.hints)
        assertEquals(42, game.seconds)
        assertTrue(game.history.all { (it as SudokuState).notes.all { n -> n == 0 } })
        // A game with no notes is written exactly as it always was.
        assertEquals(oldJson, game.encode())
    }

    @Test
    fun `notes are written when there are some, and read back`() {
        val s0 = oldBoard()
        val (a, d) = s0.roomyCell()
        val s = s0.toggleNote(a, d)
        val saved = SavedGame(s, history = listOf(s0))
        val raw = saved.encode()
        assertTrue(raw, raw.contains("\"notes\""))
        assertEquals(saved, SavedGame.decode(raw))
        assertNotEquals(oldJson, raw)
    }

    @Test
    fun `notes stay out of the web parity fingerprint`() {
        val day = LocalDate.of(2026, 3, 3)
        val s = Sudoku.generate(DailySeed.seedFor(day, "sudoku", Difficulty.STANDARD), Difficulty.STANDARD) as SudokuState
        val (a, d) = s.roomyCell()
        val noted = s.toggleNote(a, d)
        assertNotEquals(s, noted)
        assertEquals(ParityFingerprint.body(s), ParityFingerprint.body(noted))
    }

    // ---- the walkthrough ------------------------------------------------------------------------------

    @Test
    fun `the walkthrough's notes frame wants the pencil mark made by the real gestures`() {
        val frames = Sudoku.tutorial
        assertEquals(6, frames.size)
        val frame = frames[4]
        val s = frame.state as SudokuState
        val cell = s.selected!!
        val accepts = frame.accepts!!
        assertEquals(0, s.cells[cell])

        // The key in notes mode: toggleNote of the glowing digit on the selected cell.
        val key = (frame.highlight.strong - cell).single { it != com.joebywan.daybook.puzzles.SudokuTeacher.NOTES_KEY } - 81
        assertEquals(5, key)
        assertTrue(accepts(s.toggleNote(cell, key)))
        // Without the pencil lit the same key places the digit, which the frame refuses.
        assertFalse("placing is not noting", accepts(s.withCell(cell, key)))
        // The cell has only one candidate left, so a near miss is the right digit on the wrong cell,
        // or a second mark somewhere else on top of the right one.
        val elsewhere = Sudoku.TUTORIAL_OPEN.filter { s.cells[it] == 0 && it != cell }.first { c -> (1..9).any { s.canNote(c, it) } }
        val digit = (1..9).first { s.canNote(elsewhere, it) }
        assertFalse("another cell", accepts(s.toggleNote(elsewhere, digit)))
        assertFalse("two notes", accepts(s.toggleNote(cell, key).toggleNote(elsewhere, digit)))
        assertFalse("a bare selection", accepts(s.select(cell)))
        assertTrue(com.joebywan.daybook.puzzles.SudokuTeacher.NOTES_KEY in frame.highlight.strong)

        // The next frame starts from exactly that result, and hints alone finish it.
        val next = frames[5]
        assertEquals(s.toggleNote(cell, key).notes, (next.state as SudokuState).notes)
        assertTrue(next.freePlay)
    }
}
