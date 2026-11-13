package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.WhosWhoGraph
import com.joebywan.daybook.puzzles.WhosWhoGraphLogic
import com.joebywan.daybook.puzzles.WhosWhoGraphState
import com.joebywan.daybook.puzzles.WhosWhoTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Logic Web's hints on its own daily boards. Truth is read off the stored answer (who holds each item, [WhosWhoGraphLogic.holder]),
 * never through the closure or the teacher, so every step's links are checked against something they do not share.
 */
class WhosWhoGraphTeachingTest {

    private fun daily(day: Int, tier: Difficulty) =
        WhosWhoGraph.generate(daySeed(day, WhosWhoGraph.id, tier), tier) as WhosWhoGraphState

    private fun same(s: WhosWhoGraphState, a: Int, b: Int) = WhosWhoGraphLogic.holder(s.board, a) == WhosWhoGraphLogic.holder(s.board, b)

    /** Nothing the marks imply contradicts the answer. */
    private fun assertTrueMarks(s: WhosWhoGraphState, why: String) {
        val c = WhosWhoGraphLogic.close(s)
        assertFalse("$why: a clash", c.clash)
        for (a in 0 until s.size) for (b in a + 1 until s.size) {
            val v = c.at(a, b)
            if (v != 0) assertEquals("$why: $a-$b", same(s, a, b), v == 1)
        }
    }

    /** The table's words, which mean nothing on a graph. */
    private val tableWords = Regex("\\b(row|rows|column|columns|tick|ticked|cross|crossed|table|square)\\b", RegexOption.IGNORE_CASE)

    private fun assertGraphWords(d: Deduction) {
        assertFalse("table words: ${d.nudge}", tableWords.containsMatchIn(d.nudge))
        assertFalse("table words: ${d.explanation}", tableWords.containsMatchIn(d.explanation))
        assertTrue("${d.nudge.length}: ${d.nudge}", d.nudge.length <= WhosWhoTeacher.MAX_NUDGE)
        assertTrue("${d.explanation.length}: ${d.explanation}", d.explanation.length <= WhosWhoTeacher.MAX_EXPLANATION)
    }

    /** Hints alone from [from] to solved; every step sound, reached by its own move and not before. The techniques in order. */
    private fun walk(from: WhosWhoGraphState, why: String): List<String> {
        var s = from
        val used = ArrayList<String>()
        while (!s.solved) {
            val d = WhosWhoGraph.teach(s)
            assertNotNull("$why: no hint on an unsolved board", d)
            d!!
            assertFalse("$why: a correct board was called wrong: ${d.explanation}", d.mistake)
            assertFalse("$why: no fallback exists", d.fallback)
            assertFalse("$why: already reached: ${d.explanation}", d.isReached(s))
            assertGraphWords(d)
            val next = d.apply(s) as WhosWhoGraphState
            assertTrue("$why: Show me reaches it", d.isReached(next))
            assertEquals("$why: one move", s.moves + 1, next.moves)
            assertTrueMarks(next, why)
            used += d.technique
            s = next
            assertTrue("$why: runaway walk", used.size < 200)
        }
        return used
    }

    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val report = StringBuilder()
        for (tier in Difficulty.entries) {
            val counts = WhosWhoTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var steps = 0
            var needWhatIf = 0
            val boards = 200
            for (day in 0 until boards) {
                val used = walk(daily(day, tier), "$tier day $day")
                used.forEach { counts[it] = counts.getValue(it) + 1 }
                steps += used.size
                if (WhosWhoTeacher.WHAT_IF in used) needWhatIf++
                if (tier != Difficulty.EXPERT) assertFalse("$tier day $day: a what-if", WhosWhoTeacher.WHAT_IF in used)
            }
            val line = "logicweb $tier: ${"%.1f".format(steps / boards.toDouble())} steps a board; " +
                counts.entries.joinToString(", ") { "${it.key} ${"%.2f".format(it.value / boards.toDouble())}" } +
                "; boards needing a what-if $needWhatIf of $boards; fallback none exists"
            println(line)
            report.appendLine(line)
        }
        writeReport("logicweb-teaching-coverage.txt", report.toString())
    }

    @Test
    fun `hints stay sound from boards a player marked their own way`() {
        val rng = Rng(5)
        for (tier in Difficulty.entries) for (day in 0 until 80) {
            var s = daily(day * 3 + 2, tier)
            val n = s.n
            // True facts drawn the player's way: a person's lines into a category that include the right item, anti-links off it.
            repeat(rng.nextInt(1, 2 * n)) {
                val a = rng.nextInt(s.size)
                val category = (0..s.board.columns).filter { it != a / n }[rng.nextInt(s.board.columns)]
                val items = (category * n until category * n + n).toList()
                val right = items.first { same(s, a, it) }
                when (rng.nextInt(3)) {
                    0 -> s = s.withLine(a, right)
                    1 -> s = items.filter { it == right || rng.nextBoolean() }.fold(s) { acc, z -> acc.withLine(a, z) }
                    else -> items.firstOrNull { it != right && s.mark(a, it) == WhosWhoGraphLogic.OPEN }?.let { s = s.withMark(a, it, WhosWhoGraphLogic.ANTI) }
                }
            }
            if (!s.solved) walk(s, "$tier day ${day * 3 + 2} links ${s.links}")
        }
    }

    @Test
    fun `a planted mistake outranks every step, is said in the graph's words, and taking it back clears it`() {
        val rng = Rng(3)
        for (tier in Difficulty.entries) for (day in 0 until 40) {
            val s = daily(day, tier)
            val n = s.n
            val a = rng.nextInt(n)
            val partner = (n until 2 * n).first { same(s, a, it) }
            val wrong = (n until 2 * n).first { !same(s, a, it) }
            for (bad in listOf(s.withMark(a, partner, WhosWhoGraphLogic.ANTI), s.withLine(a, wrong))) {
                val d = WhosWhoGraph.teach(bad)!!
                assertTrue(d.mistake)
                assertGraphWords(d)
                val fixed = d.apply(bad) as WhosWhoGraphState
                assertEquals(s.links, fixed.links)
                assertTrue(d.isReached(fixed))
            }
        }
    }
}
