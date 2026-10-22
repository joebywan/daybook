package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.WheelKeyAction
import com.joebywan.daybook.puzzles.WordWheel
import com.joebywan.daybook.puzzles.swipeWheelLetter
import com.joebywan.daybook.puzzles.tapWheelLetter
import com.joebywan.daybook.puzzles.typeWheelLetter
import com.joebywan.daybook.puzzles.wordWheelKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The word being spelled, by keyboard, taps and swipes: pure functions of wheel positions, so all three agree. */
class WordWheelKeysTest {

    @Test
    fun `keys map`() {
        assertEquals(WheelKeyAction.Letter('q'), wordWheelKeyAction(Key.Q))
        assertEquals(WheelKeyAction.Letter('a'), wordWheelKeyAction(Key.A))
        assertEquals(WheelKeyAction.Enter, wordWheelKeyAction(Key.Enter))
        assertEquals(WheelKeyAction.Enter, wordWheelKeyAction(Key.NumPadEnter))
        assertEquals(WheelKeyAction.Backspace, wordWheelKeyAction(Key.Backspace))
        assertEquals(WheelKeyAction.Backspace, wordWheelKeyAction(Key.Delete))
        assertEquals(WheelKeyAction.Clear, wordWheelKeyAction(Key.Escape))
        assertNull(wordWheelKeyAction(Key.One))
        assertNull(wordWheelKeyAction(Key.Spacebar))
    }

    @Test
    fun `typing takes letters from the wheel, each copy once`() {
        val wheel = "rteeps" // two Es
        var picked = listOf<Int>()
        for (c in "steep".dropLast(1)) picked = typeWheelLetter(wheel, picked, c)!!
        assertEquals(listOf(5, 1, 2, 3), picked) // s, t, the first e, the second e
        assertNull("no third E", typeWheelLetter(wheel, picked, 'e'))
        assertNull("no X at all", typeWheelLetter(wheel, picked, 'x'))
        assertEquals(listOf(5, 1, 2, 3, 4), typeWheelLetter(wheel, picked, 'p'))
        // The board on screen spells what the positions say.
        val s = WordWheel.tutorialBoard()
        var p = listOf<Int>()
        for (c in "brain") p = typeWheelLetter(s.wheel, p, c)!!
        assertEquals("brain", p.map { s.wheel[it] }.joinToString(""))
    }

    @Test
    fun `a tap adds a letter, a second tap on the last takes it back, and a used letter is ignored`() {
        assertEquals(listOf(3), tapWheelLetter(emptyList(), 3))
        assertEquals(listOf(3, 1), tapWheelLetter(listOf(3), 1))
        assertEquals(listOf(3), tapWheelLetter(listOf(3, 1), 1))
        assertEquals(listOf(3, 1), tapWheelLetter(listOf(3, 1), 3))
    }

    @Test
    fun `a swipe adds new letters and backs out the way it came`() {
        var path = listOf(0)
        for (i in listOf(0, 2, 2, 4)) path = swipeWheelLetter(path, i)
        assertEquals(listOf(0, 2, 4), path)
        path = swipeWheelLetter(path, 2) // back to the one before: the last is taken off
        assertEquals(listOf(0, 2), path)
        path = swipeWheelLetter(path, 0)
        assertEquals(listOf(0), path)
        // Crossing an earlier letter that is not the one before changes nothing.
        assertEquals(listOf(0, 1, 2), swipeWheelLetter(listOf(0, 1, 2), 0))
    }
}
