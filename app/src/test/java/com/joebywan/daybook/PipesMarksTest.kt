package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.PipesTeacher
import com.joebywan.daybook.puzzles.pipesMark
import com.joebywan.daybook.puzzles.pipesMarkTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/**
 * Pipes' progress notes: a tenth of the tiles joined is a step of the phrase, up as it is reached, down as it is lost.
 * The tenths are reckoned here by counting which of 10%, 20% ... 90% the share has reached, with no division.
 */
class PipesMarksTest {

    private fun held(joined: Int, total: Int) = (1..9).count { k -> joined * 10 >= k * total }
    private fun note(mark: Int) = Tones.step(Tones.phraseStep(mark, 10))

    @Test
    fun `the tenth held is the marks the share has reached`() {
        for (total in listOf(35, 54, 88, 7)) for (j in 0..total) assertEquals("$j of $total", held(j, total), pipesMark(j, total))
    }

    @Test
    fun `up plays the mark reached, down the mark now held, several at once one note`() {
        assertEquals(note(1), pipesMarkTone(0, 1, false))
        assertEquals(note(5), pipesMarkTone(2, 5, false)) // three marks up: the highest, once
        assertEquals(note(3), pipesMarkTone(4, 3, false)) // down a step: the mark now held
        assertEquals(note(0), pipesMarkTone(1, 0, false)) // below 10%: the bottom note
        assertEquals(note(2), pipesMarkTone(6, 2, false)) // four marks down: one note
        assertNull(pipesMarkTone(4, 4, false))
        assertNull(pipesMarkTone(8, 9, true)) // the solve has the chime
    }

    @Test
    fun `re-crossing a mark plays it again`() {
        val total = 35
        val walk = listOf(3, 4, 3, 4, 7, 6, 7) // 4/35 is past 10%, 3/35 not; 7/35 is 20%, 6/35 not
        val notes = walk.zipWithNext { a, b -> pipesMarkTone(pipesMark(a, total), pipesMark(b, total), false) }
        assertEquals(listOf(note(1), note(0), note(1), note(2), note(1), note(2)), notes)
    }

    /**
     * Measured, not asserted: a player who takes the wrong tiles in a random order and turns each a quarter at a time
     * until it is right. Prints up and down notes per board.
     */
    @Test
    fun `notes per board for a realistic player`() {
        val report = StringBuilder()
        for (tier in Difficulty.entries) {
            var taps = 0; var ups = 0; var downs = 0; var boards = 0
            val lines = mutableListOf<String>()
            for (seed in 1L..20L) {
                var s = Pipes.generate(seed * 7919, tier) as PipesState
                val answer = s.cells.toMutableList()
                for ((cell, mask) in PipesTeacher.forcedOrder(s.width, s.height, s.cells)) answer[cell] = mask
                val rng = Random(seed)
                var u = 0; var d = 0; var t = 0
                for (i in s.cells.indices.filter { s.cells[it] != answer[it] }.shuffled(rng)) {
                    while (s.cells[i] != answer[i]) {
                        val before = pipesMark(s.cells.indices.count { Pipes.joined(s, it) }, s.cells.size)
                        s = s.rotate(i); t++
                        val after = pipesMark(s.cells.indices.count { Pipes.joined(s, it) }, s.cells.size)
                        if (pipesMarkTone(before, after, s.solved) != null) { if (after > before) u++ else d++ }
                    }
                }
                assert(s.solved)
                taps += t; ups += u; downs += d; boards++
                if (seed <= 5) lines += "  seed $seed: $t taps, $u up, $d down"
            }
            report.appendLine("$tier: per board ${taps / boards} taps, %.1f up, %.1f down".format(ups.toDouble() / boards, downs.toDouble() / boards))
            lines.forEach { report.appendLine(it) }
        }
        println(report)
    }
}
