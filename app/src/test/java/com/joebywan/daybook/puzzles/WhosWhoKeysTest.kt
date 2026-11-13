package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Who's Who's keys, on the walkthrough's board: three people (items 0-2), houses 1-3 (3-5), the cat, dog and fish (6-8),
 * so the pills stand in three columns of three. The keyboard's Enter and Escape are the tap's [WhosWhoGraphState.tap]
 * itself; these tests hold that tap to what a finger did before it was lifted out of the board.
 */
class WhosWhoKeysTest {

    private val s = WhosWho.tutorialBoard()
    private val plain = IntArray(s.size) { it % s.n }

    @Test
    fun `arrows move, Enter and Space tap, Escape lets go, and letters are left for the hint`() {
        assertEquals(WhosWhoKeyAction.Move(0, 1), whosWhoKeyAction(Key.DirectionRight))
        assertEquals(WhosWhoKeyAction.Move(-1, 0), whosWhoKeyAction(Key.DirectionUp))
        for (k in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)) assertEquals(WhosWhoKeyAction.Tap, whosWhoKeyAction(k))
        assertEquals(WhosWhoKeyAction.Clear, whosWhoKeyAction(Key.Escape))
        for (k in listOf(Key.H, Key.X, Key.One, Key.Backspace)) assertNull(whosWhoKeyAction(k))
    }

    @Test
    fun `the cursor walks the pills as they are drawn`() {
        assertEquals(0, s.cursorAfter(null, 0, 1, plain))
        assertEquals(3, s.cursorAfter(0, 0, 1, plain)) // Ann to house 1
        assertEquals(6, s.cursorAfter(3, 0, 1, plain)) // house 1 to the cat
        assertEquals(6, s.cursorAfter(6, 0, 1, plain)) // nothing further right
        assertEquals(1, s.cursorAfter(0, 1, 0, plain))
        assertEquals(0, s.cursorAfter(0, -1, 0, plain))
        assertEquals(5, s.cursorAfter(8, 0, -1, plain)) // the fish to house 3, its row
        // Grouped: Ann linked to house 2 draws house 2 in her row, so right from Ann reaches it.
        val linked = s.withMark(0, 4, WhosWhoGraphLogic.LINK)
        val grouped = WhosWho.rows(linked, WhosWhoGraphLogic.close(linked), grouped = true).first
        assertEquals(4, linked.cursorAfter(0, 0, 1, grouped))
    }

    @Test
    fun `a tap chooses, switches within a category, lets go, and draws from the chosen item`() {
        assertEquals(s to 0, s.tap(-1, 0))
        assertEquals(s to -1, s.tap(0, 0))
        assertEquals(s to 1, s.tap(0, 1)) // another person: the choice moves
        assertEquals(s to -1, s.tap(0, -1)) // the empty board, as Escape
        // Another category: the line, then the anti-link, then open, the choice kept throughout.
        val (line, keep) = s.tap(0, 4)
        assertEquals(0, keep)
        assertEquals(s.withLine(0, 4), line)
        assertEquals(1, line.moves)
        val (anti, _) = line.tap(0, 4)
        assertEquals(WhosWhoGraphLogic.ANTI, anti.mark(0, 4))
        val (open, _) = anti.tap(0, 4)
        assertEquals(WhosWhoGraphLogic.OPEN, open.mark(0, 4))
        // A choice alone changes no state: the same instance, so nothing reaches the undo list.
        assertSame(s, s.tap(-1, 3).first)
    }
}
