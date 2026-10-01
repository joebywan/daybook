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

    /** An empty cell with a peer digit to contradict: (cell, a digit one of its peers holds). */
    private fun SudokuState.contradicted(skip: Set<Int> = emptySet()): Pair<Int, Int> {
        for (i in cells.indices) {
            if (cells[i] != 0 || i in skip) continue
            val held = cells.indices.firstOrNull { sees(i, it) && cells[it] != 0 }
            if (held != null) return i to cells[held]
        }
        error("no open cell next to a digit")
    }

    /** An open cell and a different open cell that sees it. */
    private fun SudokuState.openPair(): Pair<Int, Int> {
        for (i in cells.indices) {
            if (cells[i] != 0) continue
            val j = cells.indices.firstOrNull { cells[it] == 0 && sees(i, it) }
            if (j != null) return i to j
        }
        error("no two open cells that see each other")
    }

    // ---- toggling --------------------------------------------------------------------------------

    @Test
    fun `a digit key in notes mode always toggles that candidate, one state each`() {
        val s = board()
        val cell = s.cells.indices.first { s.cells[it] == 0 }
        for (d in 1..9) {
            val on = s.toggleNote(cell, d)
            assertEquals("digit $d is drawn", setOf(d), on.noted(cell))
            assertEquals("one state, one move", s.moves + 1, on.moves)
            assertEquals("placing nothing", s.cells, on.cells)
            assertEquals("and off again", emptySet<Int>(), on.toggleNote(cell, d).noted(cell))
        }
        val two = s.toggleNote(cell, 2).toggleNote(cell, 7)
        assertEquals(setOf(2, 7), two.noted(cell))
        assertEquals("a note is the cell's own", emptySet<Int>(), two.noted(cell + 1))
    }

    @Test
    fun `a note for a digit a peer already holds is made and drawn, not refused`() {
        for (seed in 1L..20L) {
            val s = board(seed)
            val (cell, held) = s.contradicted()
            assertTrue(s.canNote(cell))
            val noted = s.toggleNote(cell, held)
            assertNotEquals("not a silent no-op", s, noted)
            assertEquals(setOf(held), noted.noted(cell))
            assertEquals(1 shl (held - 1), noted.visibleNotes(cell))
            // Still drawn after unrelated moves elsewhere.
            val other = s.cells.indices.first { s.cells[it] == 0 && !sees(cell, it) && it != cell }
            assertEquals(setOf(held), noted.withCell(other, 1).noted(cell))
        }
    }

    @Test
    fun `notes cannot go on a given or a filled cell, and the board says so rather than ignore a tap`() {
        val s = board()
        val given = s.givens.indexOfFirst { it }
        assertFalse(s.canNote(given))
        assertSame("a given", s, s.toggleNote(given, 5))
        val cell = s.cells.indices.first { s.cells[it] == 0 }
        val placed = s.withCell(cell, 4)
        assertFalse(placed.canNote(cell))
        assertSame("a filled cell carries no notes", placed, placed.toggleNote(cell, 3))
    }

    // ---- placing and erasing -----------------------------------------------------------------------

    @Test
    fun `placing a digit clears that cell's notes`() {
        val s0 = board()
        val a = s0.cells.indices.first { s0.cells[it] == 0 }
        val s = s0.toggleNote(a, 3).toggleNote(a, 8)
        val placed = s.withCell(a, 3)
        assertEquals(3, placed.cells[a])
        assertEquals(0, placed.notes[a])
        assertEquals(s.moves + 1, placed.moves)
    }

    @Test
    fun `placing a digit strikes it from the notes of every peer, and only peers, in the same state`() {
        val s0 = board()
        val a = s0.cells.indices.first { s0.cells[it] == 0 }
        // Notes 3 and 8 on every other open cell, so peers and strangers can be told apart.
        var s = s0
        val open = s0.cells.indices.filter { s0.cells[it] == 0 && it != a }
        for (i in open) s = s.toggleNote(i, 3).toggleNote(i, 8)
        val placed = s.withCell(a, 3)
        for (i in open) {
            val expected = if (sees(a, i)) setOf(8) else setOf(3, 8)
            assertEquals("cell $i", expected, placed.noted(i))
        }
        assertEquals("one state: one move", s.moves + 1, placed.moves)
        assertEquals(setOf(8), placed.noted(open.first { sees(a, it) }))
    }

    @Test
    fun `undo restores the digit and the peers' notes together, in one step`() {
        val initial = board()
        val (a, b) = initial.openPair()
        var game = SavedGame(initial)
        fun move(next: SudokuState) {
            game = game.copy(state = next, history = game.history + game.state)
        }
        move((game.state as SudokuState).toggleNote(b, 6))
        move((game.state as SudokuState).select(a))
        val before = game.history.size
        move((game.state as SudokuState).withCell(a, 6))
        assertEquals("one gesture, one undo entry", before + 1, game.history.size)
        assertEquals(emptySet<Int>(), (game.state as SudokuState).noted(b))
        game = game.copy(state = game.history.last(), history = game.history.dropLast(1))
        val back = game.state as SudokuState
        assertEquals(0, back.cells[a])
        assertEquals("the peer note is back with the digit gone", setOf(6), back.noted(b))
    }

    @Test
    fun `notes made after a peer digit exists are kept, and erasing a digit does not resurrect cleared ones`() {
        val s0 = board()
        val (a, b) = s0.openPair()
        val withNote = s0.toggleNote(b, 5)
        val placed = withNote.withCell(a, 5)
        assertEquals(emptySet<Int>(), placed.noted(b))
        // Added after the peer digit: shown, and survives unrelated moves.
        val again = placed.toggleNote(b, 5)
        assertEquals(setOf(5), again.noted(b))
        // Erasing the digit leaves what the player has now, and does not bring back what was cleared.
        assertEquals(emptySet<Int>(), placed.withCell(a, 0).noted(b))
        assertEquals(setOf(5), again.withCell(a, 0).noted(b))
    }

    @Test
    fun `erasing an empty cell clears its notes, and erasing a digit leaves none behind`() {
        val s0 = board()
        val a = s0.cells.indices.first { s0.cells[it] == 0 }
        val s = s0.toggleNote(a, 2).toggleNote(a, 9)
        assertEquals(2, s.noted(a).size)
        val erased = s.withCell(a, 0)
        assertEquals(0, erased.notes[a])
        assertEquals(0, erased.cells[a])
        assertEquals(emptySet<Int>(), s0.withCell(a, 4).withCell(a, 0).noted(a))
    }

    @Test
    fun `a given cell cannot be written to`() {
        val s = board()
        val given = s.givens.indexOfFirst { it }
        assertSame(s, s.withCell(given, 0))
        assertEquals(0, s.visibleNotes(given))
    }

    // ---- undo, restart, saves ----------------------------------------------------------------------

    @Test
    fun `notes ride the state, so undo and restart take them back with the move`() {
        // PlayScreen: a move pushes the old state; undo pops it; restart returns to `initial`.
        val initial = board()
        val a = initial.cells.indices.first { initial.cells[it] == 0 }
        val d = 3
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
        val a = s0.cells.indices.first { s0.cells[it] == 0 }
        val d = 3
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
        val a = s.cells.indices.first { s.cells[it] == 0 }
        val noted = s.toggleNote(a, 3)
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
        val elsewhere = Sudoku.TUTORIAL_OPEN.first { s.cells[it] == 0 && it != cell }
        val digit = 3
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
