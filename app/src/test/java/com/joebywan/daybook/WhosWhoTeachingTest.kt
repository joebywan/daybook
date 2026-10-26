package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoLogic
import com.joebywan.daybook.puzzles.WhosWhoState
import com.joebywan.daybook.puzzles.WhosWhoTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhosWhoTeachingTest {

    /** Steps walked, and what-ifs that needed the teacher's longer chain (a board marked the player's own way). */
    private var steps = 0
    private var longWhatIfs = 0

    private fun daily(day: Int, tier: Difficulty): WhosWhoState =
        WhosWho.generate(daySeed(day, WhosWho.id, tier), tier) as WhosWhoState

    private fun isAnswer(s: WhosWhoState, mark: Int): Boolean {
        val cell = mark / s.n
        return s.answer[cell % s.columns][cell / s.columns] == mark % s.n
    }

    /** Hints alone from [from] to solved, every step checked against the answer; the steps' techniques in order. */
    private fun walk(from: WhosWhoState): List<String> {
        var s = from
        val used = ArrayList<String>()
        while (!s.solved) {
            val lesson = WhosWhoTeacher.teach(s)
            assertNotNull("no hint on an unsolved board", lesson)
            lesson!!
            assertFalse("a correct board was called wrong", lesson.technique == WhosWhoTeacher.MISTAKE)
            for (t in lesson.ticks) assertTrue("tick off the answer: ${lesson.explanation}", isAnswer(s, t))
            for (c in lesson.crosses) assertFalse("cross on the answer: ${lesson.explanation}", isAnswer(s, c))
            assertFalse("already reached: ${lesson.explanation}", WhosWho.reached(s, lesson))
            val next = WhosWho.applyStep(s, lesson)
            assertTrue("Show me reaches it", WhosWho.reached(next, lesson))
            assertEquals("one move", s.moves + 1, next.moves)
            used += lesson.technique
            if (lesson.explanation.contains("following clues")) longWhatIfs++
            steps++
            s = next
            assertTrue("runaway walk", used.size < 200)
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
                val used = walk(daily(day, tier))
                used.forEach { counts[it] = counts.getValue(it) + 1 }
                steps += used.size
                if (WhosWhoTeacher.WHAT_IF in used) needWhatIf++
                when (tier) {
                    Difficulty.STANDARD -> assertTrue(used.all { it == WhosWhoTeacher.CLUE || it == WhosWhoTeacher.ONLY })
                    Difficulty.HARD -> {
                        assertTrue(used.any { it == WhosWhoTeacher.LINK || it == WhosWhoTeacher.HOUSE })
                        assertFalse(WhosWhoTeacher.WHAT_IF in used)
                    }
                    Difficulty.EXPERT -> assertTrue("expert day $day needs a what-if", WhosWhoTeacher.WHAT_IF in used)
                }
            }
            val line = "whoswho $tier: ${"%.1f".format(steps / boards.toDouble())} steps a board; " +
                counts.entries.joinToString(", ") { "${it.key} ${"%.2f".format(it.value / boards.toDouble())}" } +
                "; boards needing a what-if $needWhatIf of $boards; fallback none exists"
            println("$line; longer what-ifs $longWhatIfs")
            report.appendLine(line)
        }
        writeReport("whoswho-teaching-coverage.txt", report.toString())
    }

    @Test
    fun `hints stay sound and available from boards a player made, not just from the solver's own path`() {
        val rng = Rng(11)
        for (tier in Difficulty.entries) for (day in 0 until 120) {
            var s = daily(day * 3 + 1, tier)
            // Some true facts the player found their own way: ticks of the answer, crosses off it.
            repeat(rng.nextInt(1, 2 * s.n)) {
                val p = rng.nextInt(s.n)
                val c = rng.nextInt(s.columns)
                val slot = rng.nextInt(s.n)
                val right = s.answer[c][p] == slot
                if (right && rng.nextBoolean()) s = s.mark(p, c, slot, tick = true)
                if (!right && s.marks[s.markIndex(p, c, slot)] == WhosWhoLogic.EMPTY) s = s.mark(p, c, slot, tick = false)
            }
            if (!s.solved) {
                try { walk(s) } catch (e: AssertionError) { throw AssertionError("$tier day ${day * 3 + 1} marks ${s.marks}: ${e.message}") }
            }
        }
        println("whoswho from player-made boards: $steps steps, $longWhatIfs needed the longer what-if")
    }

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        var checked = 0
        for (day in 0 until 30) {
            val full = daily(day, Difficulty.HARD)
            // Drop clues until there are several answers.
            for (drop in full.clues.indices) {
                val s = full.copy(clues = full.clues.filterIndexed { i, _ -> i != drop })
                val p = WhosWhoLogic.puzzleOf(s)
                if (WhosWhoLogic.count(p) !is Verdict.Several) continue
                var grid = WhosWhoLogic.gridOf(s.n, s.columns, s.marks)
                var marks = s.marks
                while (true) {
                    val lesson = WhosWhoTeacher.deduce(s, marks) ?: break
                    val alive = WhosWhoOracle.answers(s.n, s.columns, s.clues) { person, column, slot ->
                        grid.cand[person * s.columns + column] shr slot and 1 == 1
                    }
                    assertTrue(alive.isNotEmpty())
                    for (a in alive) {
                        for (t in lesson.ticks) assertEquals(lesson.explanation, a[t / s.n % s.columns][t / s.n / s.columns], t % s.n)
                        for (c in lesson.crosses) assertTrue(lesson.explanation, a[c / s.n % s.columns][c / s.n / s.columns] != c % s.n)
                    }
                    checked++
                    marks = WhosWho.applyStep(s.copy(marks = marks), lesson).marks
                    grid = WhosWhoLogic.gridOf(s.n, s.columns, marks)
                }
                break
            }
        }
        println("whoswho several-answer steps checked: $checked")
        assertTrue(checked > 50)
    }

    @Test
    fun `a planted mistake outranks every step, and taking it back clears it`() {
        val rng = Rng(3)
        for (tier in Difficulty.entries) for (day in 0 until 30) {
            val s = daily(day, tier)
            val p = rng.nextInt(s.n)
            val c = rng.nextInt(s.columns)
            val wrong = (s.answer[c][p] + 1 + rng.nextInt(s.n - 1)) % s.n
            for (bad in listOf(s.mark(p, c, wrong, tick = true), s.mark(p, c, s.answer[c][p], tick = false))) {
                val lesson = WhosWhoTeacher.teach(bad)!!
                assertEquals(WhosWhoTeacher.MISTAKE, lesson.technique)
                assertEquals(setOf(p * s.columns + c), lesson.targets)
                val fixed = WhosWho.applyStep(bad, lesson)
                assertEquals(s.marks, fixed.marks)
                assertTrue(WhosWho.reached(fixed, lesson))
            }
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = ""
        val rng = Rng(9)
        for (tier in Difficulty.entries) for (day in 0 until 120) {
            var s = daily(day, tier)
            val p = rng.nextInt(s.n)
            val c = rng.nextInt(s.columns)
            listOf(WhosWhoTeacher.teach(s.mark(p, c, (s.answer[c][p] + 1) % s.n, true))!!, WhosWhoTeacher.teach(s.mark(p, c, s.answer[c][p], false))!!).forEach {
                assertTrue(it.explanation, it.explanation.length <= WhosWhoTeacher.MAX_EXPLANATION)
            }
            while (!s.solved) {
                val lesson = WhosWhoTeacher.teach(s)!!
                assertTrue(lesson.nudge, lesson.nudge.length <= WhosWhoTeacher.MAX_NUDGE)
                if (lesson.explanation.length > longest.length) longest = lesson.explanation
                assertTrue("${lesson.explanation.length}: ${lesson.explanation}", lesson.explanation.length <= WhosWhoTeacher.MAX_EXPLANATION)
                assertFalse(lesson.explanation, lesson.explanation.contains("  ") || lesson.explanation.contains(" ,"))
                s = WhosWho.applyStep(s, lesson)
            }
        }
        println("whoswho longest explanation (${longest.length}): $longest")
    }

    @Test
    fun `a what-if's contradiction is real, and no simpler step was there`() {
        var seen = 0
        for (day in 0 until 40) {
            val s = daily(day, Difficulty.EXPERT)
            val p = WhosWhoLogic.puzzleOf(s)
            val g = WhosWhoLogic.blankGrid(s.n, s.columns)
            while (!g.finished) {
                val step = WhosWhoLogic.deduce(p, g)!!
                if (step.technique == WhosWhoLogic.WHAT_IF) {
                    seen++
                    assertEquals(null, WhosWhoLogic.simpleStep(p, g, WhosWhoLogic.LINKED))
                    val trial = g.copy()
                    trial.tick(step.supposed)
                    for (link in step.chain) WhosWhoLogic.apply(trial, link)
                    assertEquals(step.broke, WhosWhoLogic.broken(p, trial))
                    assertTrue(step.chain.size <= WhosWhoLogic.MAX_LINKS)
                }
                WhosWhoLogic.apply(g, step)
            }
        }
        assertTrue(seen >= 40)
    }

    @Test
    fun `the teacher reads no answer`() {
        for (day in 0 until 20) {
            val s = daily(day, Difficulty.EXPERT)
            val other = s.copy(answer = s.answer.map { it.reversed() })
            val a = WhosWhoTeacher.deduce(s, s.marks)!!
            val b = WhosWhoTeacher.deduce(other, s.marks)!!
            assertEquals(a.explanation, b.explanation)
            assertEquals(a.crosses + a.ticks, b.crosses + b.ticks)
        }
    }
}
