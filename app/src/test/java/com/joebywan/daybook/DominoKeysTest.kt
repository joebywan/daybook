package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.DominoKeyAction
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoPick
import com.joebywan.daybook.puzzles.Dominoes
import com.joebywan.daybook.puzzles.Placement
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.dominoKeyAction
import com.joebywan.daybook.puzzles.dragSquares
import com.joebywan.daybook.puzzles.flipAt
import com.joebywan.daybook.puzzles.tapSquare
import com.joebywan.daybook.puzzles.tapTray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Dominoes' tap model and its keys: the keys go through the taps' own functions, one state per move. */
class DominoKeysTest {

    // Tutorial board: tray 0 = 1-0, 1 = 4-1, 2 = 5-2, 3 = 6-6; a 4 x 2 box, every square open.
    private val s = Dominoes.tutorialBoard()

    @Test
    fun `the keys map as the spec says, and nothing else`() {
        assertEquals(DominoKeyAction.Move(-1, 0), dominoKeyAction(Key.DirectionUp))
        assertEquals(DominoKeyAction.Move(0, 1), dominoKeyAction(Key.DirectionRight))
        assertEquals(DominoKeyAction.Cycle(1), dominoKeyAction(Key.RightBracket))
        assertEquals(DominoKeyAction.Cycle(1), dominoKeyAction(Key.Tab))
        assertEquals(DominoKeyAction.Cycle(-1), dominoKeyAction(Key.LeftBracket))
        assertEquals(DominoKeyAction.Flip, dominoKeyAction(Key.F))
        assertEquals(DominoKeyAction.Flip, dominoKeyAction(Key.Spacebar))
        assertEquals(DominoKeyAction.Enter, dominoKeyAction(Key.Enter))
        assertEquals(DominoKeyAction.Enter, dominoKeyAction(Key.NumPadEnter))
        assertEquals(DominoKeyAction.Lift, dominoKeyAction(Key.Backspace))
        assertEquals(DominoKeyAction.Lift, dominoKeyAction(Key.Delete))
        assertNull(dominoKeyAction(Key.A))
        assertNull(dominoKeyAction(Key.One))
    }

    @Test
    fun `a tap selects, a second turns, two neighbouring squares lay it with its lead on the first`() {
        val picked = s.tapTray(DominoPick(), 2)
        assertEquals(DominoPick(2), picked.pick)
        assertNull(picked.board)
        val turned = s.tapTray(picked.pick, 2)
        assertEquals(DominoPick(2, flipped = true), turned.pick)
        // Unturned, the 5-2 leads with its 5.
        val first = s.tapSquare(picked.pick, 1)
        assertEquals(1, first.pick.first)
        val laid = s.tapSquare(first.pick, 5)
        assertEquals(Placement(5, 1), laid.board!!.placed[2])
        assertEquals(s.moves + 1, laid.board.moves)
        assertEquals(DominoPick(), laid.pick)
        // Turned, the 2 leads.
        val laid2 = s.tapSquare(s.tapSquare(turned.pick, 1).pick, 5)
        assertEquals(Placement(1, 5), laid2.board!!.placed[2])
        // A double cannot be turned.
        assertEquals(DominoPick(3), s.tapTray(DominoPick(3), 3).pick)
    }

    @Test
    fun `a far square moves the first, the first again lets it go, and no selection takes the first in the tray`() {
        val a = s.tapSquare(DominoPick(), 0)
        assertEquals(DominoPick(0, false, 0), a.pick)
        assertEquals(3, s.tapSquare(a.pick, 3).pick.first)
        assertNull(s.tapSquare(a.pick, 0).pick.first)
        val laid = s.tapSquare(a.pick, 1).board!!
        assertEquals("the 1-0, its 1 first", Placement(1, 0), laid.placed[0])
    }

    @Test
    fun `a tap on a laid domino lifts it into the hand, a double tap or long press turns it in place`() {
        val laid = DominoLogic.place(s, 2, Placement(1, 5))
        val lifted = laid.tapSquare(DominoPick(), 5)
        assertNull(lifted.board!!.placed[2])
        assertEquals(DominoPick(2), lifted.pick)
        assertEquals(laid.moves + 1, lifted.board.moves)
        val flipped = laid.flipAt(DominoPick(), 1)
        assertEquals(Placement(5, 1), flipped.board!!.placed[2])
        assertNull("nothing to turn on an empty square", laid.flipAt(DominoPick(), 0).board)
    }

    @Test
    fun `a drag is the two taps, and a drag that is not to a free neighbour does nothing`() {
        assertEquals(s.tapSquare(s.tapSquare(DominoPick(2), 1).pick, 5).board, s.dragSquares(DominoPick(2), 1, 5).board)
        assertNull(s.dragSquares(DominoPick(2), 1, 6).board)
        assertNull("not across a row's end", s.dragSquares(DominoPick(2), 3, 4).board)
        val laid = DominoLogic.place(s, 3, Placement(0, 4))
        assertNull("not onto a laid domino", laid.dragSquares(DominoPick(2), 1, 0).board)
    }

    @Test
    fun `a pick the board no longer allows is cleared`() {
        val laid = DominoLogic.place(s, 2, Placement(1, 5))
        assertEquals(DominoPick(), DominoPick(2, true, 1).on(laid))
        val same = DominoPick(1, false, 0)
        assertSame(same, same.on(laid))
    }

    @Test
    fun `keys do what taps do`() {
        // Arrows: the first lands on the first square, then walk; the edge holds.
        var r = s.applyKey(null, DominoPick(), DominoKeyAction.Move(0, 1))
        assertEquals(0, r.cursor)
        r = s.applyKey(r.cursor, r.pick, DominoKeyAction.Move(0, -1))
        assertEquals(0, r.cursor)
        r = s.applyKey(r.cursor, r.pick, DominoKeyAction.Move(0, 1))
        assertEquals(1, r.cursor)
        // Cycle picks tray dominoes in order, wrapping; F turns the one in hand.
        r = s.applyKey(r.cursor, r.pick, DominoKeyAction.Cycle(1))
        assertEquals(0, r.pick.selected)
        r = s.applyKey(r.cursor, r.pick, DominoKeyAction.Cycle(-1))
        assertEquals(3, r.pick.selected)
        r = s.applyKey(r.cursor, r.pick, DominoKeyAction.Cycle(-1))
        assertEquals(2, r.pick.selected)
        r = s.applyKey(r.cursor, r.pick, DominoKeyAction.Flip)
        assertEquals(true, r.pick.flipped)
        // Enter marks the first square, then Enter beside it lays the domino.
        r = s.applyKey(r.cursor, r.pick, DominoKeyAction.Enter)
        assertEquals(1, r.pick.first)
        r = s.applyKey(r.cursor, r.pick, DominoKeyAction.Move(1, 0))
        assertEquals(5, r.cursor)
        r = s.applyKey(r.cursor, r.pick, DominoKeyAction.Enter)
        val laid = r.board!!
        assertEquals(s.tapSquare(s.tapSquare(DominoPick(2, true), 1).pick, 5).board, laid)
        // Space with nothing in hand turns the domino under the cursor; Backspace lifts it.
        val turned = laid.applyKey(5, DominoPick(), DominoKeyAction.Flip)
        assertEquals(Placement(5, 1), turned.board!!.placed[2])
        val lifted = laid.applyKey(5, DominoPick(), DominoKeyAction.Lift)
        assertNull(lifted.board!!.placed[2])
        assertEquals(2, lifted.pick.selected)
        assertNull("Backspace on an empty square does nothing", laid.applyKey(0, DominoPick(), DominoKeyAction.Lift).board)
    }
}
