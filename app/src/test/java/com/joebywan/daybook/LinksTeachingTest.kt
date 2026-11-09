package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Links
import com.joebywan.daybook.puzzles.LinksLogic
import com.joebywan.daybook.puzzles.LinksState
import com.joebywan.daybook.puzzles.LinksTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinksTeachingTest {

    private fun board(seed: Long, d: Difficulty) = Links.generate(seed, d) as LinksState

    private fun applied(s: LinksState, step: LinksTeacher.Step) = Links.applyStep(s, step)

    /** Walks [s] by hints alone to solved, handing every step to [each]; fails on a stall or a mistake. */
    private fun walk(start: LinksState, label: String, each: (LinksState, LinksTeacher.Step) -> Unit = { _, _ -> }) {
        var s = start
        var guard = 0
        while (!s.solved) {
            val step = LinksTeacher.teach(s)
            assertNotNull("$label: no step from ${s.links}", step)
            step!!
            assertFalse("$label: a hint walk never makes a mistake", step.technique == LinksTeacher.MISTAKE)
            each(s, step)
            val next = applied(s, step)
            assertTrue("$label: the step changed nothing", next.links != s.links)
            assertTrue("$label: the step is reached once made", Links.reached(next, step))
            s = next
            assertTrue(label, guard++ < 400)
        }
    }

    private fun lineIn(answer: String, n: Int, a: Int, b: Int) = LinksLogic.hasLine(n, LinksLogic.masksOf(answer), a, b)

    @Test
    fun `every step on a real board agrees with the answer and changes something`() {
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 20 else 40, d, "links-real")) {
            val full = board(seed, d)
            walk(full, "$d/$seed") { _, step ->
                for ((a, b) in step.lines) assertTrue("$d/$seed: ${step.technique} line $a-$b", lineIn(full.answer, full.size, a, b))
                assertTrue(step.technique in LinksTeacher.TECHNIQUES)
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 2000)
    }

    /** [full] with a random [k] of its answer's lines drawn. */
    private fun partial(full: LinksState, rng: Rng, k: Int): LinksState {
        val n = full.size
        val answer = LinksLogic.masksOf(full.answer)
        val lines = (0 until n * n).flatMap { q -> listOf(LinksLogic.RIGHT, LinksLogic.DOWN).filter { answer[q] and (1 shl it) != 0 }.map { q to LinksLogic.next(n, q, it) } }
        val m = IntArray(n * n)
        for ((a, b) in rng.shuffled(lines).take(k)) LinksLogic.setLine(n, m, a, b, true)
        return full.copy(links = LinksLogic.maskString(m))
    }

    @Test
    fun `hints stay sound and available from boards a player made, not just from the solver's own path`() {
        val rng = Rng(5150)
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(15, d, "links-player")) {
            val full = board(seed, d)
            repeat(6) {
                val s = partial(full, rng, rng.nextInt(1, full.size * full.size / 2))
                if (s.solved) return@repeat
                val step = LinksTeacher.teach(s)
                assertNotNull("$d/$seed: no step from ${s.links}", step)
                step!!
                assertFalse("correct lines are never a mistake", step.technique == LinksTeacher.MISTAKE)
                for ((a, b) in step.lines) assertTrue(lineIn(full.answer, full.size, a, b))
                walk(s, "$d/$seed partial")
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 200)
    }

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        val rng = Rng(8675309)
        var checked = 0
        var ambiguous = 0
        val kinds = mutableMapOf<String, Int>()
        for (n in listOf(4, 5)) repeat(250) {
            // A random cover of a small board: most have more than one answer.
            val cover = LinksLogic.draw(rng, LinksLogic.Spec(n, 2, n, LinksLogic.LOCAL..LinksLogic.WHAT_IF)) ?: return@repeat
            val all = LinksOracle.count(n, cover.ends)
            if (all > 1) ambiguous++
            repeat(3) {
                val marked = partial(cover, rng, rng.nextInt(0, n))
                val m = LinksLogic.masksOf(marked.links)
                if (LinksOracle.count(n, cover.ends, must = m) == 0L) return@repeat
                val step = LinksTeacher.deduce(n, cover.ends, marked.links) ?: return@repeat
                kinds[step.technique] = (kinds[step.technique] ?: 0) + 1
                for ((a, b) in step.lines) {
                    val not = IntArray(n * n)
                    val (lo, hi) = if (a < b) a to b else b to a
                    not[lo] = 1 shl LinksLogic.dirTo(n, lo, hi)
                    assertEquals("${step.technique} $a-$b on ${cover.ends} lines ${marked.links}", 0L, LinksOracle.count(n, cover.ends, must = m, not = not))
                }
                checked++
            }
        }
        println("several-answer sample: $checked steps by technique $kinds, $ambiguous ambiguous boards")
        assertTrue("barely checked anything: $checked", checked > 400)
        assertTrue("the sample must include boards with more than one answer: $ambiguous", ambiguous > 30)
    }

    @Test
    fun `a wrong line is addressed first, a right one never is, and taking it out clears it`() {
        for (d in Difficulty.entries) for (seed in seeds(10, d, "links-mistake")) {
            val full = board(seed, d)
            val n = full.size
            val answer = LinksLogic.masksOf(full.answer)
            // A line between two squares the answer does not join, drawn on a board with a few right lines.
            val wrong = (0 until n * n).firstNotNullOf { q ->
                val x = LinksLogic.next(n, q, LinksLogic.RIGHT)
                if (x >= 0 && answer[q] and 2 == 0) q to x else null
            }
            val m = IntArray(n * n)
            LinksLogic.setLine(n, m, wrong.first, wrong.second, true)
            val s = full.copy(links = LinksLogic.maskString(m))
            val step = LinksTeacher.teach(s)!!
            assertEquals(LinksTeacher.MISTAKE, step.technique)
            assertEquals(listOf(wrong), step.clears)
            val fixed = Links.applyStep(s, step)
            assertTrue(Links.reached(fixed, step))
            assertFalse(LinksTeacher.teach(fixed)!!.technique == LinksTeacher.MISTAKE)
        }
    }

    @Test
    fun `techniques are tried simplest first, and every tier needs its own rung`() {
        for (d in Difficulty.entries) for (seed in seeds(10, d, "links-order")) {
            val used = mutableListOf<Int>()
            walk(board(seed, d), "$d/$seed") { _, step -> used += LinksTeacher.TECHNIQUES.indexOf(step.technique) }
            val deepest = used.max()
            val want = when (d) {
                Difficulty.STANDARD -> setOf(0, 1)
                Difficulty.HARD -> setOf(2)
                Difficulty.EXPERT -> setOf(2, 3)
            }
            assertTrue("$d/$seed deepest ${LinksTeacher.TECHNIQUES[deepest]}", deepest in want)
        }
    }

    @Test
    fun `an end with one way on takes it, and an empty corner with two ways in joins both`() {
        // The walkthrough board, blank: the 3 in the top-right corner has the 5's end below it, so it can only go left.
        val step = LinksTeacher.teach(Links.tutorialBoard())!!
        assertEquals(LinksTeacher.ONE_WAY, step.technique)
        assertEquals(listOf(4 to 3), step.lines)
        // One pair in two corners of a 4x4: both ends have two ways, and the empty top-right corner only its two neighbours.
        val corner = LinksTeacher.deduce(4, "a" + ".".repeat(14) + "a", "0".repeat(16))!!
        assertEquals(LinksTeacher.TWO_WAYS, corner.technique)
        assertEquals(listOf(7 to 3, 3 to 2), corner.lines)
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = 0
        var longestNudge = 0
        var sample = ""
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 30 else 60, d, "links-text")) {
            walk(board(seed, d), "$d/$seed") { _, step ->
                if (step.explanation.length > longest) {
                    longest = step.explanation.length
                    sample = step.explanation
                }
                longestNudge = maxOf(longestNudge, step.nudge.length)
            }
        }
        println("longest explanation $longest: $sample; longest nudge $longestNudge")
        assertTrue("nudge $longestNudge", longestNudge <= LinksTeacher.MAX_NUDGE)
        assertTrue("explanation $longest: $sample", longest <= LinksTeacher.MAX_EXPLANATION)
    }

    @Test
    fun `a hint is quick on an expert board`() {
        val times = ArrayList<Double>()
        for (seed in seeds(10, Difficulty.EXPERT, "links-speed")) {
            var s = board(seed, Difficulty.EXPERT)
            while (!s.solved) {
                val t0 = System.nanoTime()
                val step = LinksTeacher.teach(s)!!
                times += (System.nanoTime() - t0) / 1e6
                s = applied(s, step)
            }
        }
        times.sort()
        println("TIME expert hint median ${"%.2f".format(times[times.size / 2])} p95 ${"%.2f".format(times[times.size * 95 / 100])} max ${"%.2f".format(times.last())} ms")
        assertTrue("slowest hint ${times.last()} ms", times.last() < 300)
    }

    /**
     * A measurement, printed and written to `app/build/reports/links-teaching-coverage.txt`: which technique a player
     * walking a board by hints alone needs, per tier. Nothing can fall back (a generated board is finished by this very
     * ladder), so the assertion is that the walk never runs out of steps.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 150
        val report = StringBuilder()
        report.appendLine("Links teaching coverage: $perTier boards per tier, walked from blank by hints alone")
        for (d in Difficulty.entries) {
            val stepCounts = LinksTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = LinksTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var total = 0
            for (seed in seeds(perTier, d, "links-coverage")) {
                val used = mutableSetOf<String>()
                walk(board(seed, d), "$d/$seed") { _, step ->
                    stepCounts[step.technique] = stepCounts.getValue(step.technique) + 1
                    used += step.technique
                    total++
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${d.name}: $total steps, ${"%.1f".format(total / perTier.toDouble())} per board")
            for (t in LinksTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-10s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / total,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    ),
                )
            }
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/links-teaching-coverage.txt").writeText(report.toString())
    }
}
