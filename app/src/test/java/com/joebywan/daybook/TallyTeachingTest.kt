package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.TallyState
import com.joebywan.daybook.puzzles.TallyTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The Tally hint solver. As for Nonogram and Kings: on real boards every step must agree with the
 * stored answer; and, because on a one-answer board every true fact is derivable (which cannot tell
 * reasoning from peeking), on boards with *several* answers every step must hold for every answer
 * the marks still allow ([TallyOracle.answers]).
 */
class TallyTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String = "tally-teach") = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    /** [s] with [step] laid on, as Show me does. */
    private fun applied(s: TallyState, step: TallyTeacher.Step): TallyState {
        val next = s.marks.toCharArray()
        step.clears.forEach { next[it] = TallyLogic.UNMARKED }
        step.stays.forEach { next[it] = TallyLogic.KEPT }
        step.goes.forEach { next[it] = TallyLogic.REMOVED }
        return s.copy(marks = String(next))
    }

    /** Marks taken from the answer: each kept square circled, each removed one removed, with chance [keep]/10. */
    private fun partial(s: TallyState, rng: Rng, keep: Int): TallyState =
        s.copy(marks = String(CharArray(s.marks.length) {
            if (rng.nextInt(10) >= keep) TallyLogic.UNMARKED
            else if (s.keep[it] == '1') TallyLogic.KEPT else TallyLogic.REMOVED
        }))

    private fun deduce(s: TallyState) = TallyTeacher.deduce(s.size, TallyLogic.digitArray(s), TallyLogic.targets(s), s.marks)

    // ---- soundness on real boards ---------------------------------------------------------------

    @Test
    fun `every step on a real board agrees with the answer and changes something`() {
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(40, d)) {
            var s = TallyLogic.generateVerified(seed, d)!!
            while (!s.solved) {
                val step = TallyTeacher.teach(s)
                assertNotNull("$d/$seed: no step on an unsolved board", step)
                step!!
                assertTrue(step.stays.isNotEmpty() || step.goes.isNotEmpty())
                step.stays.forEach { assertEquals("$d/$seed stay $it", '1', s.keep[it]); assertEquals('.', s.marks[it]) }
                step.goes.forEach { assertEquals("$d/$seed go $it", '0', s.keep[it]); assertEquals('.', s.marks[it]) }
                assertTrue(step.technique in TallyTeacher.TECHNIQUES)
                s = applied(s, step)
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 2000)
    }

    @Test
    fun `hints alone finish every board of a year, with no mistake, ending with every line adding up`() {
        for (d in Difficulty.entries) for (seed in seeds(365, d, "tally-walk")) {
            var s = TallyLogic.generateVerified(seed, d)!!
            var guard = 0
            // The walk circles every number that stays, so it ends only when no square is untouched.
            while (s.marks.contains(TallyLogic.UNMARKED)) {
                assertTrue("$d/$seed: walk did not finish", guard++ < 200)
                val step = TallyTeacher.deduce(s.size, TallyLogic.digitArray(s), TallyLogic.targets(s), s.marks)
                assertNotNull("$d/$seed: stuck with no step", step)
                s = applied(s, step!!)
            }
            assertTrue(s.solved)
            assertEquals(s.keep, s.marks.map { if (it == TallyLogic.KEPT) '1' else '0' }.joinToString(""))
        }
    }

    @Test
    fun `hints stay sound and available from boards a player made, not just from the solver's own path`() {
        val rng = Rng(5150)
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(25, d, "tally-player")) {
            val full = TallyLogic.generateVerified(seed, d)!!
            repeat(8) {
                val s = partial(full, rng, rng.nextInt(1, 9))
                if (s.solved) return@repeat
                val step = TallyTeacher.teach(s)
                assertNotNull("$d/$seed: no step from marks ${s.marks}", step)
                step!!
                assertFalse("correct marks are never a mistake", step.technique == TallyTeacher.MISTAKE)
                step.stays.forEach { assertEquals('1', full.keep[it]) }
                step.goes.forEach { assertEquals('0', full.keep[it]) }
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 400)
    }

    // ---- soundness when there are several answers -----------------------------------------------

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        val rng = Rng(8675309)
        var checked = 0
        var ambiguous = 0
        var whatIfs = 0
        for (n in listOf(3, 4, 5)) {
            repeat(150) {
                // Small digits repeat a lot, which is what makes several answers.
                val digits = String(CharArray(n * n) { '0' + rng.nextInt(1, 4) })
                val keep = String(CharArray(n * n) { if (rng.nextInt(10) < 6) '1' else '0' })
                val base = TallyState(n, digits, keep)
                val rows = TallyLogic.rowTargets(base)
                val cols = TallyLogic.colTargets(base)
                val all = TallyOracle.answers(n, digits.map { it - '0' }, rows, cols)
                if (all.size > 1) ambiguous++
                repeat(4) {
                    val marked = partial(base, rng, rng.nextInt(0, 6))
                    val possible = all.filter { a ->
                        marked.marks.indices.all { i ->
                            when (marked.marks[i]) {
                                TallyLogic.KEPT -> a[i]
                                TallyLogic.REMOVED -> !a[i]
                                else -> true
                            }
                        }
                    }
                    val step = deduce(marked) ?: return@repeat
                    if (step.technique == TallyTeacher.WHAT_IF) whatIfs++
                    step.stays.forEach { i -> assertTrue("stay $i on $digits marks ${marked.marks}", possible.all { it[i] }) }
                    step.goes.forEach { i -> assertTrue("go $i on $digits marks ${marked.marks}", possible.none { it[i] }) }
                    checked++
                }
            }
        }
        assertTrue("barely checked anything: $checked", checked > 800)
        assertTrue("the sample must include boards with more than one answer: $ambiguous", ambiguous > 30)
        println("several-answer sample: $checked steps, $whatIfs of them what-ifs, $ambiguous ambiguous boards")
    }

    // ---- the techniques, one by one ---------------------------------------------------------------

    @Test
    fun `circled numbers that make the target send the rest away`() {
        // Row 1: 4 7 2 needs 6; with the 4 and 2 circled, the 7 goes.
        val s = TallyState(3, "472" + "935" + "618", "101" + "011" + "110", marks = "o.o" + "......")
        val step = deduce(s)!!
        assertEquals(TallyTeacher.DONE, step.technique)
        assertEquals(listOf(1), step.goes)
        assertTrue(step.explanation, "already make 6" in step.explanation && "the 7" in step.explanation)
        assertTrue(TallyLogic.rowTargetIndex(3, 0) in step.focus)
    }

    @Test
    fun `what is left making the target exactly keeps all of it`() {
        // Four 1s needing 1 each: with row 1's second 1 removed, row 1 (and column 2) add up already.
        val s = TallyState(2, "1111", "1001", marks = ".x..")
        val step = deduce(s)!!
        assertEquals(TallyTeacher.ALL_LEFT, step.technique)
        assertEquals(listOf(0), step.stays)
    }

    @Test
    fun `circling a line that already adds up waits while an unfinished line has a shortcut`() {
        // Row 1 (4 7 2, needing 6) adds up once the 7 goes; row 3 (6 1 8, needing 7) still holds an 8 too big to stay.
        val s = TallyState(3, "472" + "935" + "618", "101" + "011" + "110", marks = ".x." + "......")
        val step = deduce(s)!!
        assertEquals(TallyTeacher.TOO_BIG, step.technique)
        assertTrue(step.goes.none { it < 3 })
    }

    @Test
    fun `a number bigger than its line needs goes, and one too big to lose stays`() {
        // Row 1: 4 7 2 needs 6, so the 7 is too big. Nothing is marked, and no line is done or exact.
        val s = TallyState(3, "472" + "935" + "618", "101" + "011" + "110")
        val step = deduce(s)!!
        assertEquals(TallyTeacher.TOO_BIG, step.technique)
        assertEquals(listOf(1), step.goes)
        assertTrue(step.explanation, step.explanation.startsWith("Row 1 needs 6:"))
        // Row 1: 9 1 1 needing 10 is 1 over, so removing the 9 would leave too little. Nothing is too
        // big anywhere (every target is at least each number in its line, or the line is all 1s).
        val t = TallyState(3, "911" + "111" + "111", "110" + "011" + "101")
        val surplus = TallyTeacher.deduce(3, TallyLogic.digitArray(t), listOf(10, 2, 2, 10, 2, 2), t.marks)!!
        assertEquals(TallyTeacher.TOO_MUCH, surplus.technique)
        assertEquals(listOf(0), surplus.stays)
        assertTrue(surplus.explanation, "1 over its 10" in surplus.explanation)
    }

    @Test
    fun `a ways step that lists its ways lists true ones, and names what they agree on`() {
        var listed = 0
        for (seed in seeds(60, Difficulty.HARD, "tally-ways")) {
            var s = TallyLogic.generateVerified(seed, Difficulty.HARD)!!
            while (!s.solved) {
                val step = TallyTeacher.teach(s)!!
                val m = Regex("^(Row|Column) (\\d+) makes (\\d+) only as ([0-9+ or]+)\\. ").find(step.explanation)
                if (step.technique == TallyTeacher.WAYS && m != null) {
                    listed++
                    val target = m.groupValues[3].toInt()
                    val ways = m.groupValues[4].split(" or ")
                    assertTrue(step.explanation, ways.size in 1..3)
                    ways.forEach { w -> assertEquals(step.explanation, target, w.split("+").sumOf { it.toInt() }) }
                    assertTrue(step.explanation, "Every one uses" in step.explanation || "None uses" in step.explanation)
                }
                s = applied(s, step)
            }
        }
        assertTrue("barely saw any: $listed", listed > 20)
    }

    @Test
    fun `techniques are tried simplest first, and every expert board needs a what-if at least once`() {
        for (d in Difficulty.entries) for (seed in seeds(40, d, "tally-order")) {
            var s = TallyLogic.generateVerified(seed, d)!!
            val used = mutableSetOf<String>()
            while (!s.solved) {
                val step = TallyTeacher.teach(s)!!
                used += step.technique
                s = applied(s, step)
            }
            when (d) {
                Difficulty.STANDARD -> assertTrue("$d/$seed used $used", TallyTeacher.WAYS !in used && TallyTeacher.WHAT_IF !in used)
                Difficulty.HARD -> assertTrue("$d/$seed used $used", TallyTeacher.WAYS in used && TallyTeacher.WHAT_IF !in used)
                Difficulty.EXPERT -> assertTrue("$d/$seed used $used", TallyTeacher.WHAT_IF in used)
            }
        }
    }

    @Test
    fun `a what-if is a step no single line gives, and the line it ends on really has no way`() {
        var seen = 0
        for (seed in seeds(30, Difficulty.EXPERT, "tally-whatif")) {
            var s = TallyLogic.generateVerified(seed, Difficulty.EXPERT)!!
            while (!s.solved) {
                val step = TallyTeacher.teach(s)!!
                if (step.technique == TallyTeacher.WHAT_IF) {
                    seen++
                    val n = s.size
                    val grid = TallyLogic.markState(s.marks)
                    val targets = TallyLogic.targets(s)
                    // No line alone settles anything here.
                    for (line in 0 until 2 * n) {
                        val at = TallyLogic.lineCells(n, line)
                        val f = TallyLogic.forced(IntArray(n) { s.digitAt(at[it]) }, IntArray(n) { grid[at[it]] }, targets[line])!!
                        assertTrue(f.all { it == TallyLogic.OPEN })
                    }
                    // The opposite of the conclusion, followed by lines alone, contradicts.
                    val square = (step.stays + step.goes).single()
                    val g = grid.copyOf()
                    g[square] = if (square in step.stays) TallyLogic.GO else TallyLogic.STAY
                    assertEquals(null, TallyLogic.solveByLines(n, TallyLogic.digitArray(s), targets, g))
                    assertTrue(step.explanation, step.explanation.startsWith("If the ${s.digitAt(square)} at row"))
                }
                s = applied(s, step)
            }
        }
        assertTrue("barely saw any: $seen", seen > 30)
    }

    // ---- mistakes -----------------------------------------------------------------------------------

    @Test
    fun `a wrong removal or a wrong circle is addressed first, with its targets cited`() {
        val base = TallyState(3, "472" + "935" + "618", "101" + "011" + "110")
        val removed = TallyTeacher.teach(base.copy(marks = "x" + ".".repeat(8)))!!
        assertEquals(TallyTeacher.MISTAKE, removed.technique)
        assertEquals(listOf(0), removed.clears)
        assertTrue(removed.explanation, "has to stay" in removed.explanation)
        assertEquals(setOf(TallyLogic.rowTargetIndex(3, 0), TallyLogic.colTargetIndex(3, 0)), removed.cited)
        val circled = TallyTeacher.teach(base.copy(marks = ".o" + ".".repeat(7)))!!
        assertEquals(TallyTeacher.MISTAKE, circled.technique)
        assertTrue(circled.explanation, "can't stay" in circled.explanation)
        val fine = TallyTeacher.teach(base.copy(marks = "ox" + ".".repeat(7)))!!
        assertFalse(fine.technique == TallyTeacher.MISTAKE)
    }

    @Test
    fun `a planted mistake on a real board outranks every step`() {
        val rng = Rng(31337)
        for (d in Difficulty.entries) for (seed in seeds(15, d, "tally-mistake")) {
            val full = TallyLogic.generateVerified(seed, d)!!
            val s0 = partial(full, rng, 5)
            val i = rng.nextInt(s0.marks.length)
            val wrong = if (full.keep[i] == '1') TallyLogic.REMOVED else TallyLogic.KEPT
            val s = s0.copy(marks = String(s0.marks.toCharArray().also { it[i] = wrong }))
            val step = TallyTeacher.teach(s)!!
            assertEquals("$d/$seed", TallyTeacher.MISTAKE, step.technique)
            assertTrue(step.clears.single() <= i)
        }
    }

    // ---- text -----------------------------------------------------------------------------------------

    @Test
    fun `every nudge and explanation fits the panel`() {
        val rng = Rng(2468)
        var checked = 0
        val longest = mutableMapOf<String, Int>()
        for (d in Difficulty.entries) for (seed in seeds(120, d, "tally-text")) {
            val full = TallyLogic.generateVerified(seed, d)!!
            // Every step of the walk from blank, plus some player-made and planted-mistake boards.
            var s = full
            while (!s.solved) {
                val step = TallyTeacher.teach(s)!!
                check(step, longest)
                checked++
                s = applied(s, step)
            }
            repeat(3) { round ->
                var p = partial(full, rng, rng.nextInt(0, 9))
                if (round == 2) {
                    val i = rng.nextInt(p.marks.length)
                    p = p.copy(marks = String(p.marks.toCharArray().also {
                        it[i] = if (full.keep[i] == '1') TallyLogic.REMOVED else TallyLogic.KEPT
                    }))
                }
                val step = TallyTeacher.teach(p) ?: return@repeat
                check(step, longest)
                checked++
            }
        }
        println("longest explanation per technique: $longest")
        assertTrue("barely checked anything: $checked", checked > 5000)
    }

    private fun check(step: TallyTeacher.Step, longest: MutableMap<String, Int>) {
        assertTrue("nudge too long (${step.nudge.length}): ${step.nudge}", step.nudge.length <= TallyTeacher.MAX_NUDGE)
        assertTrue(
            "explanation too long (${step.explanation.length}): ${step.explanation}",
            step.explanation.length <= TallyTeacher.MAX_EXPLANATION,
        )
        longest[step.technique] = maxOf(longest[step.technique] ?: 0, step.explanation.length)
    }

    // ---- coverage -------------------------------------------------------------------------------------

    /**
     * A measurement, printed and written to `app/build/reports/tally-teaching-coverage.txt`: which
     * technique a player walking a board by hints alone needs, per tier. Nothing can fall back (a
     * generated board is finished by this very ladder), so the assertion is that the walk never runs
     * out of steps.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 200
        val report = StringBuilder()
        report.appendLine("Tally teaching coverage: $perTier boards per tier, walked from blank by hints alone")
        for (d in Difficulty.entries) {
            val stepCounts = TallyTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = TallyTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var total = 0
            for (seed in seeds(perTier, d, "tally-coverage")) {
                var s = TallyLogic.generateVerified(seed, d)!!
                val used = mutableSetOf<String>()
                while (!s.solved) {
                    val step = TallyTeacher.teach(s)
                    assertNotNull("$d/$seed: the walk ran out of steps", step)
                    stepCounts[step!!.technique] = stepCounts.getValue(step.technique) + 1
                    used += step.technique
                    total++
                    s = applied(s, step)
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${d.name}: $total steps, ${"%.1f".format(total / perTier.toDouble())} per board")
            for (t in TallyTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-10s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / total,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/tally-teaching-coverage.txt").writeText(report.toString())
    }
}
