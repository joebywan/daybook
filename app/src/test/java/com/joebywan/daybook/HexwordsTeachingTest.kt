package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.HexLogic
import com.joebywan.daybook.puzzles.HexTake
import com.joebywan.daybook.puzzles.Hexwords
import com.joebywan.daybook.puzzles.HexwordsState
import com.joebywan.daybook.puzzles.HexwordsTeacher
import com.joebywan.daybook.puzzles.WheelWords
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HexwordsTeachingTest {

    private val start = LocalDate(2026, 1, 1)
    private fun board(day: Int, d: Difficulty) =
        Hexwords.generate(DailySeed.seedFor(start.plus(DatePeriod(days = day)), "hexwords", d), d) as HexwordsState

    @Test
    fun `coverage - hints alone finish every board with no fallback, per difficulty`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = sortedMapOf<String, Int>()
            val times = ArrayList<Double>()
            repeat(365) { day ->
                var s = board(day, d)
                var guard = 0
                while (!s.solved) {
                    val t0 = System.nanoTime()
                    val step = HexwordsTeacher.teach(s)!!
                    times += (System.nanoTime() - t0) / 1e6
                    counts.merge(step.technique, 1, Int::plus)
                    assertFalse("a hint walk must never err: ${step.technique}", step.technique == HexwordsTeacher.MISTAKE)
                    assertTrue("hints use common words: ${step.word}", step.word in HexLogic.gridWords)
                    s = s.take(step.word, step.path)
                    assertTrue(++guard < 40)
                }
            }
            times.sort()
            report.appendLine("$d: $counts; a hint in ms: median ${times[times.size / 2]} p95 ${times[times.size * 95 / 100]} max ${times.last()}")
            assertEquals(0, counts[HexwordsTeacher.UNCHECKED] ?: 0)
        }
        File("build/reports").mkdirs()
        File("build/reports/hexwords-teaching-coverage.txt").writeText(report.toString())
        print(report)
    }

    /** Plays a random player's boards: a mistake is flagged exactly when no accepted words can make up the points. */
    @Test
    fun `a mistake is called exactly when nothing left can reach the target`() {
        var mistakes = 0
        var fine = 0
        repeat(40) { day ->
            val start = board(day, Difficulty.STANDARD)
            val oracle = HexwordsOracle(start.rows, start.letters, true)
            val rng = Rng(day + 3L)
            var s = start
            while (!s.solved) {
                val free = s.free
                val live = oracle.found.filter { w -> w.path.all { free shr it and 1L == 1L } }
                if (live.isEmpty()) break
                val w = live[rng.nextInt(live.size)]
                s = s.take(w.word, w.path)
                if (s.solved) break
                val step = HexwordsTeacher.teach(s)!!
                val can = oracle.best(s.free) >= s.left
                assertEquals("${s.letters} ${s.taken}", !can, step.technique == HexwordsTeacher.MISTAKE)
                if (can) fine++ else mistakes++
                if (!can) {
                    val taken = s.taken
                    // Everything before the named word still reached; with it, nothing does.
                    val kept = taken.take(step.keep)
                    val freeBefore = kept.fold((1L shl s.letters.length) - 1) { m, t -> t.path.fold(m) { a, h -> a and (1L shl h).inv() } }
                    assertTrue(oracle.best(freeBefore) >= s.target - kept.sumOf { HexLogic.points(it.word.length) })
                }
            }
        }
        assertTrue("the test must see both: $mistakes $fine", mistakes > 10 && fine > 10)
    }

    @Test
    fun `a right move from a player's own board is not called wrong, and a step always keeps the target`() {
        repeat(30) { day ->
            val start = board(day, Difficulty.HARD)
            val oracle = HexwordsOracle(start.rows, start.letters, false)
            var s = start
            while (!s.solved) {
                val step = HexwordsTeacher.teach(s)!!
                val after = s.take(step.word, step.path)
                assertTrue(after.solved || oracle.best(after.free) >= after.left)
                s = after
            }
        }
    }

    @Test
    fun `a step names its word only by clue, and its reasoning cannot be spelled off the highlight`() {
        for (d in Difficulty.entries) repeat(120) { day ->
            var s = board(day, d)
            while (!s.solved) {
                val step = HexwordsTeacher.teach(s)!!
                assertTrue(step.explanation, step.explanation.contains(WheelWords.clue(step.word)))
                assertFalse(step.word, step.nudge.contains(step.word.uppercase()))
                assertFalse(step.word, step.explanation.contains(step.word.uppercase()))
                assertEquals(1, step.focus.size)
                assertEquals(step.focus, step.targets)
                assertTrue(step.nudge, step.nudge.length <= HexwordsTeacher.MAX_NUDGE)
                assertTrue(step.explanation, step.explanation.length <= HexwordsTeacher.MAX_EXPLANATION + 60)
                s = s.take(step.word, step.path)
            }
        }
    }

    @Test
    fun `show me takes the word, one state, and a mistake's show me takes it back`() {
        val s = board(0, Difficulty.STANDARD)
        val d = HexwordsTeacher.deduction(s)!!
        val after = d.apply(s) as HexwordsState
        assertEquals(1, after.taken.size)
        assertTrue(d.isReached(after))
        assertFalse(d.isReached(s))

        val tutorial = Hexwords.tutorialBoard().take("anchor", Hexwords.ANCHOR)
        val wrong = HexwordsTeacher.deduction(tutorial)!!
        assertTrue(wrong.mistake)
        val back = wrong.apply(tutorial) as HexwordsState
        assertEquals(emptyList<HexTake>(), back.taken)
        assertTrue(wrong.isReached(back))
    }

    // ---- the walkthrough ----------------------------------------------------------------------

    @Test
    fun `the walkthrough board is what its captions say`() {
        val s = Hexwords.tutorialBoard()
        val oracle = HexwordsOracle(s.rows, s.letters, true)
        val all = (1L shl 10) - 1
        assertEquals("DANCE and CHOIR make the target", 16, oracle.best(all))
        val anchor = Hexwords.ANCHOR.fold(all) { m, h -> m and (1L shl h).inv() }
        assertEquals("ANCHOR leaves nothing spellable", 0, oracle.best(anchor))
        assertTrue(oracle.found.any { it.word == "anchor" && it.path == Hexwords.ANCHOR })
        assertTrue(oracle.found.any { it.word == "dance" && it.path == Hexwords.DANCE })
        assertEquals(setOf('d', 'i', 'c', 'e'), s.letters.indices.filter { it !in Hexwords.ANCHOR }.map { s.letters[it] }.toSet())
    }

    @Test
    fun `each walkthrough frame accepts its move and refuses a near miss`() {
        val frames = Hexwords.tutorial
        assertEquals(5, frames.size)
        assertEquals(null, frames[0].accepts)
        assertEquals(null, frames[2].accepts)
        val s0 = Hexwords.tutorialBoard()
        assertTrue(frames[1].accepts!!(s0.take("anchor", Hexwords.ANCHOR)))
        assertFalse(frames[1].accepts!!(s0.take("dance", Hexwords.DANCE)))
        assertEquals(s0.take("anchor", Hexwords.ANCHOR), frames[2].state)
        assertTrue(frames[3].accepts!!(s0.take("dance", Hexwords.DANCE)))
        assertFalse(frames[3].accepts!!(s0.take("anchor", Hexwords.ANCHOR)))
        assertEquals(s0.take("dance", Hexwords.DANCE), frames[4].state)
        for (f in frames) assertTrue(f.caption, f.caption.length <= 170)
    }

    @Test
    fun `hints alone finish the free-play frame, with no fallback and no mistake`() {
        var s = Hexwords.tutorial.last().state as HexwordsState
        while (!s.solved) {
            val d = Hexwords.teach(s)!!
            assertFalse(d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as HexwordsState
        }
        assertEquals(listOf("dance", "choir"), s.taken.map { it.word })
    }
}
