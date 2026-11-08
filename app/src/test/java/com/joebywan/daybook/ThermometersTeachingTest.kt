package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.ThermometersLogic
import com.joebywan.daybook.puzzles.ThermometersState
import com.joebywan.daybook.puzzles.ThermometersTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Thermometers hint solver. As for Tally: on real boards every step must agree with the stored answer; and,
 * because on a one-answer board every true fact is derivable (which cannot tell reasoning from peeking), on boards
 * with *several* answers every step must hold for every answer the marks still allow ([ThermometersOracle.answers]).
 */
class ThermometersTeachingTest {

    /** [s] with [step] laid on, as Show me does. */
    private fun applied(s: ThermometersState, step: ThermometersTeacher.Step): ThermometersState {
        val next = s.marks.toCharArray()
        step.clears.forEach { next[it] = ThermometersLogic.UNMARKED }
        step.full.forEach { next[it] = ThermometersLogic.FILLED }
        step.empty.forEach { next[it] = ThermometersLogic.CROSSED }
        return s.copy(marks = String(next))
    }

    /** Marks taken from the answer, each square marked with chance [keep]/10, tidied the way taps leave a tube. */
    private fun partial(s: ThermometersState, rng: Rng, keep: Int): ThermometersState =
        s.copy(marks = String(CharArray(s.marks.length) {
            if (rng.nextInt(10) >= keep) ThermometersLogic.UNMARKED
            else if (s.fill[it] == '1') ThermometersLogic.FILLED else ThermometersLogic.CROSSED
        }))

    private fun deduce(s: ThermometersState) = ThermometersTeacher.deduce(s.size, s.tubes, ThermometersLogic.targets(s), s.marks)

    // ---- soundness on real boards ---------------------------------------------------------------

    @Test
    fun `every step on a real board agrees with the answer and changes something`() {
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(40, d, "thermometers-teach")) {
            var s = ThermometersLogic.generateVerified(seed, d)!!
            while (!s.solved) {
                val step = ThermometersTeacher.teach(s)
                assertNotNull("$d/$seed: no step on an unsolved board", step)
                step!!
                assertTrue(step.full.isNotEmpty() || step.empty.isNotEmpty())
                step.full.forEach { assertEquals("$d/$seed fill $it", '1', s.fill[it]); assertEquals('.', s.marks[it]) }
                step.empty.forEach { assertEquals("$d/$seed empty $it", '0', s.fill[it]); assertEquals('.', s.marks[it]) }
                assertTrue(step.technique in ThermometersTeacher.TECHNIQUES)
                s = applied(s, step)
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 1000)
    }

    @Test
    fun `hints alone finish every board of a year, with no mistake, ending with every count met`() {
        for (d in Difficulty.entries) for (day in 0 until 365) {
            val seed = daySeed(day, "thermometers", d)
            var s = ThermometersLogic.generateVerified(seed, d)!!
            var guard = 0
            while (!s.solved) {
                assertTrue("$d/$seed: walk did not finish", guard++ < 300)
                val step = ThermometersTeacher.teach(s)
                assertNotNull("$d/$seed: stuck with no step", step)
                assertFalse(step!!.technique == ThermometersTeacher.MISTAKE)
                s = applied(s, step)
            }
            assertEquals(s.fill, s.marks.map { if (it == ThermometersLogic.FILLED) '1' else '0' }.joinToString(""))
        }
    }

    @Test
    fun `hints stay sound and available from boards a player made, not just from the solver's own path`() {
        val rng = Rng(5150)
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(25, d, "thermometers-player")) {
            val full = ThermometersLogic.generateVerified(seed, d)!!
            repeat(8) {
                val s = partial(full, rng, rng.nextInt(1, 9))
                if (s.solved) return@repeat
                val step = ThermometersTeacher.teach(s)
                assertNotNull("$d/$seed: no step from marks ${s.marks}", step)
                step!!
                assertFalse("correct marks are never a mistake", step.technique == ThermometersTeacher.MISTAKE)
                step.full.forEach { assertEquals('1', full.fill[it]) }
                step.empty.forEach { assertEquals('0', full.fill[it]) }
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
            repeat(200) {
                val base = ThermometersLogic.draw(rng, ThermometersLogic.Spec(n, 2, n, 50, ThermometersLogic.SHORTCUTS)) ?: return@repeat
                val targets = ThermometersLogic.targets(base)
                val all = ThermometersOracle.answers(n, base.tubes, targets.take(n), targets.drop(n))
                if (all.size > 1) ambiguous++
                repeat(4) {
                    // Marks from one of the answers, so the board is still possible.
                    val from = base.copy(fill = all[rng.nextInt(all.size)])
                    val marked = partial(from, rng, rng.nextInt(0, 6))
                    val possible = all.filter { a ->
                        marked.marks.indices.all { i ->
                            when (marked.marks[i]) {
                                ThermometersLogic.FILLED -> a[i] == '1'
                                ThermometersLogic.CROSSED -> a[i] == '0'
                                else -> true
                            }
                        }
                    }
                    val step = deduce(marked) ?: return@repeat
                    if (step.technique == ThermometersTeacher.WHAT_IF) whatIfs++
                    step.full.forEach { i -> assertTrue("fill $i on ${base.tubes} marks ${marked.marks}", possible.all { it[i] == '1' }) }
                    step.empty.forEach { i -> assertTrue("empty $i on ${base.tubes} marks ${marked.marks}", possible.all { it[i] == '0' }) }
                    checked++
                }
            }
        }
        assertTrue("barely checked anything: $checked", checked > 800)
        assertTrue("the sample must include boards with more than one answer: $ambiguous", ambiguous > 30)
        println("several-answer sample: $checked steps, $whatIfs of them what-ifs, $ambiguous ambiguous boards")
    }

    // ---- the techniques, one by one ---------------------------------------------------------------

    /**
     * A 3x3: a bent thermometer from the top middle round to the right column, one from the middle left round to the
     * bottom right, and one up column 1 from the bottom, the only one filled. Counts: rows 1 1 1, columns 3 0 0.
     */
    private val small = ThermometersState(3, listOf(listOf(1, 2, 5), listOf(4, 7, 8), listOf(6, 3, 0)), "100" + "100" + "100")

    @Test
    fun `a filled square fills its tube below it, and a cross empties above it`() {
        val filled = deduce(small.copy(marks = "#" + ".".repeat(8)))!!
        assertEquals(ThermometersTeacher.TUBE, filled.technique)
        assertEquals(listOf(3, 6), filled.full.sorted())
        val crossed = deduce(small.copy(marks = ".x" + ".".repeat(7)))!!
        assertEquals(ThermometersTeacher.TUBE, crossed.technique)
        assertEquals(listOf(2, 5), crossed.empty.sorted())
    }

    @Test
    fun `a line already holding its count empties the rest, and a line with just enough room fills`() {
        assertEquals(listOf(1, 1, 1, 3, 0, 0), ThermometersLogic.targets(small))
        val done = deduce(small)!!
        assertEquals(ThermometersTeacher.DONE, done.technique)
        assertEquals(listOf(1, 4, 7), done.empty.sorted())
        assertTrue(done.explanation, done.explanation.startsWith("Column 2 counts 0"))
        assertTrue(ThermometersLogic.colCountIndex(3, 1) in done.focus)
        // With the middle and right columns out, each row has one square left for its 1.
        val room = deduce(small.copy(marks = ".xx" + ".xx" + ".xx"))!!
        assertEquals(ThermometersTeacher.ROOM, room.technique)
        assertEquals(listOf(0), room.full)
    }

    @Test
    fun `techniques are tried simplest first, and every board needs its tier's rung`() {
        for (d in Difficulty.entries) for (seed in seeds(40, d, "thermometers-order")) {
            var s = ThermometersLogic.generateVerified(seed, d)!!
            val used = mutableSetOf<String>()
            while (!s.solved) {
                val step = ThermometersTeacher.teach(s)!!
                used += step.technique
                s = applied(s, step)
            }
            val bounds = ThermometersTeacher.AT_MOST in used || ThermometersTeacher.AT_LEAST in used
            when (d) {
                Difficulty.STANDARD -> assertTrue("$d/$seed used $used", !bounds && ThermometersTeacher.WHAT_IF !in used)
                Difficulty.HARD -> assertTrue("$d/$seed used $used", bounds && ThermometersTeacher.WHAT_IF !in used)
                Difficulty.EXPERT -> assertTrue("$d/$seed used $used", ThermometersTeacher.WHAT_IF in used)
            }
        }
    }

    @Test
    fun `a what-if is a step no line gives, and its opposite really breaks a line`() {
        var seen = 0
        for (seed in seeds(30, Difficulty.EXPERT, "thermometers-whatif")) {
            var s = ThermometersLogic.generateVerified(seed, Difficulty.EXPERT)!!
            while (!s.solved) {
                val step = ThermometersTeacher.teach(s)!!
                if (step.technique == ThermometersTeacher.WHAT_IF) {
                    seen++
                    val l = ThermometersLogic.layout(s)
                    val grid = ThermometersLogic.markState(s.marks)
                    val targets = ThermometersLogic.targets(s)
                    assertEquals(null, ThermometersLogic.nextFact(l, grid, targets, ThermometersLogic.AT_LEAST))
                    val square = step.focus.single()
                    val g = grid.copyOf()
                    g[square] = if (square in step.full) ThermometersLogic.EMPTY else ThermometersLogic.FULL
                    assertEquals(null, ThermometersLogic.solveBy(l, targets, g, ThermometersLogic.AT_LEAST))
                    assertTrue(step.explanation, step.explanation.startsWith("If the glowing square were "))
                }
                s = applied(s, step)
            }
        }
        assertTrue("barely saw any: $seen", seen > 30)
    }

    // ---- mistakes -----------------------------------------------------------------------------------

    @Test
    fun `a wrong fill or a wrong cross is addressed first, with its counts cited`() {
        val filled = ThermometersTeacher.teach(small.copy(marks = ".#" + ".".repeat(7)))!!
        assertEquals(ThermometersTeacher.MISTAKE, filled.technique)
        assertEquals(listOf(1), filled.clears)
        assertTrue(filled.explanation, "can't be filled" in filled.explanation)
        assertEquals(setOf(ThermometersLogic.rowCountIndex(3, 0), ThermometersLogic.colCountIndex(3, 1)), filled.cited)
        val crossed = ThermometersTeacher.teach(small.copy(marks = "x" + ".".repeat(8)))!!
        assertEquals(ThermometersTeacher.MISTAKE, crossed.technique)
        assertTrue(crossed.explanation, "has to be filled" in crossed.explanation)
        val fine = ThermometersTeacher.teach(small.copy(marks = "#x" + ".".repeat(7)))!!
        assertFalse(fine.technique == ThermometersTeacher.MISTAKE)
    }

    @Test
    fun `a planted mistake on a real board outranks every step`() {
        val rng = Rng(31337)
        for (d in Difficulty.entries) for (seed in seeds(15, d, "thermometers-mistake")) {
            val full = ThermometersLogic.generateVerified(seed, d)!!
            val s0 = partial(full, rng, 5)
            val i = rng.nextInt(s0.marks.length)
            val wrong = if (full.fill[i] == '1') ThermometersLogic.CROSSED else ThermometersLogic.FILLED
            val s = s0.copy(marks = String(s0.marks.toCharArray().also { it[i] = wrong }))
            val step = ThermometersTeacher.teach(s)!!
            assertEquals("$d/$seed", ThermometersTeacher.MISTAKE, step.technique)
            assertTrue(step.clears.single() <= i)
        }
    }

    // ---- text -----------------------------------------------------------------------------------------

    @Test
    fun `every nudge and explanation fits the panel`() {
        val rng = Rng(2468)
        var checked = 0
        val longest = mutableMapOf<String, Int>()
        for (d in Difficulty.entries) for (seed in seeds(120, d, "thermometers-text")) {
            val full = ThermometersLogic.generateVerified(seed, d)!!
            var s = full
            while (!s.solved) {
                val step = ThermometersTeacher.teach(s)!!
                check(step, longest)
                checked++
                s = applied(s, step)
            }
            repeat(3) { round ->
                var p = partial(full, rng, rng.nextInt(0, 9))
                if (round == 2) {
                    val i = rng.nextInt(p.marks.length)
                    p = p.copy(marks = String(p.marks.toCharArray().also {
                        it[i] = if (full.fill[i] == '1') ThermometersLogic.CROSSED else ThermometersLogic.FILLED
                    }))
                }
                val step = ThermometersTeacher.teach(p) ?: return@repeat
                check(step, longest)
                checked++
            }
        }
        println("longest explanation per technique: $longest")
        assertTrue("barely checked anything: $checked", checked > 4000)
    }

    private fun check(step: ThermometersTeacher.Step, longest: MutableMap<String, Int>) {
        assertTrue("nudge too long (${step.nudge.length}): ${step.nudge}", step.nudge.length <= ThermometersTeacher.MAX_NUDGE)
        assertTrue(
            "explanation too long (${step.explanation.length}): ${step.explanation}",
            step.explanation.length <= ThermometersTeacher.MAX_EXPLANATION,
        )
        longest[step.technique] = maxOf(longest[step.technique] ?: 0, step.explanation.length)
    }

    // ---- coverage -------------------------------------------------------------------------------------

    /**
     * A measurement, printed and written to `app/build/reports/thermometers-teaching-coverage.txt`: which technique a
     * player walking a board by hints alone needs, per tier. Nothing can fall back (a generated board is finished by
     * this very ladder), so the assertion is that the walk never runs out of steps.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 200
        val report = StringBuilder()
        report.appendLine("Thermometers teaching coverage: $perTier boards per tier, walked from blank by hints alone")
        for (d in Difficulty.entries) {
            val stepCounts = ThermometersTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = ThermometersTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var total = 0
            for (seed in seeds(perTier, d, "thermometers-coverage")) {
                var s = ThermometersLogic.generateVerified(seed, d)!!
                val used = mutableSetOf<String>()
                while (!s.solved) {
                    val step = ThermometersTeacher.teach(s)
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
            for (t in ThermometersTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-14s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / total,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    ),
                )
            }
        }
        println(report)
        writeReport("thermometers-teaching-coverage.txt", report.toString())
    }
}
