package com.joebywan.daybook

import com.joebywan.daybook.ui.TwinTaps
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared tap rule, on a fake clock: what reaches the screen, and when. */
class TwinTapsTest {
    /** A board of one string, a square per character; compared by identity, as the boards are. */
    private class B(val s: String)

    private var clock = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val emitted = mutableListOf<String>()
    private val taps = TwinTaps(B("...."), { clock }) { ms, action ->
        val t = clock + ms to action
        timers += t
        ({ timers.remove(t) })
    }.also { it.emit = { b -> emitted += b.s } }

    private fun pass(ms: Long) {
        clock += ms
        timers.filter { it.first <= clock }.forEach { timers.remove(it); it.second() }
    }

    private fun set(i: Int, c: Char) = { b: B -> B(b.s.substring(0, i) + c + b.s.substring(i + 1)) }
    private fun tap(i: Int) = taps.tap(i, set(i, 'x'), set(i, 'K'))

    @Test
    fun `a tap is drawn at once and handed over after the window`() {
        tap(1)
        assertEquals(".x..", taps.shown.s)
        assertEquals(emptyList<String>(), emitted)
        pass(TwinTaps.TWIN_MS)
        assertEquals(listOf(".x.."), emitted)
    }

    @Test
    fun `a double tap is one move of the other mark`() {
        tap(1)
        pass(100)
        tap(1)
        pass(1000)
        assertEquals(listOf(".K.."), emitted)
    }

    @Test
    fun `a long press replaces a tap held on the same square`() {
        tap(2)
        taps.press(2, set(2, 'K'))
        pass(1000)
        assertEquals(listOf("..K."), emitted)
    }

    @Test
    fun `a tap elsewhere hands the held one over first, and two squares never make a double`() {
        tap(0)
        pass(100)
        tap(3)
        pass(1000)
        assertEquals(listOf("x...", "x..x"), emitted)
    }

    @Test
    fun `a second tap after the window is a fresh tap`() {
        tap(1)
        pass(400)
        taps.tap(1, set(1, '.'), set(1, 'K'))
        pass(1000)
        assertEquals(listOf(".x..", "...."), emitted)
    }

    @Test
    fun `a tap that does not hold is emitted at once and starts no double`() {
        taps.tap(1, set(1, 'o'), set(1, 'K'), holds = false)
        assertEquals(listOf(".o.."), emitted)
        tap(1)
        pass(1000)
        assertEquals(listOf(".o..", ".x.."), emitted)
    }

    @Test
    fun `a held tap the board has moved past is dropped`() {
        tap(1)
        taps.board = B("undo") // the screen handed a new board (Undo, a hint) inside the window
        pass(1000)
        assertEquals(emptyList<String>(), emitted)
        assertEquals("undo", taps.shown.s)
    }
}
