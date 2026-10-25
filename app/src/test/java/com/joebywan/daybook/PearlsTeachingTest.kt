package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Pearls
import com.joebywan.daybook.puzzles.PearlsGrid
import com.joebywan.daybook.puzzles.PearlsLogic
import com.joebywan.daybook.puzzles.PearlsState
import com.joebywan.daybook.puzzles.PearlsTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The Pearls hint solver. As for Tally and Nonogram: on real boards every step must agree with the stored
 * loop; and, because on a one-answer board every true fact is derivable (which cannot tell reasoning from
 * peeking), on boards with *several* answers every step must hold for every loop the marks still allow
 * ([PearlsOracle.answers]).
 */
class PearlsTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    private fun board(seed: Long, d: Difficulty) = Pearls.generateVerified(seed, d)!!

    /** [s] with [step] laid on, as Show me does. */
    private fun applied(s: PearlsState, step: PearlsTeacher.Step) = Pearls.applyStep(s, step)

    /** Marks taken from the answer: each loop edge drawn, each other edge crossed, with chance [keep]/10. */
    private fun partial(s: PearlsState, rng: Rng, keep: Int): PearlsState =
        s.copy(edges = String(CharArray(s.edges.length) {
            if (rng.nextInt(10) >= keep) '.' else if (s.loop[it] == '1') '-' else 'x'
        }))

    /** The walk by hints alone from [start], every step checked by [each]; returns the solved board. */
    private fun walk(start: PearlsState, label: String, each: (PearlsState, PearlsTeacher.Step) -> Unit = { _, _ -> }): PearlsState {
        var s = start
        var guard = 0
        while (!s.solved) {
            assertTrue("$label: walk did not finish", guard++ < 1000)
            val step = PearlsTeacher.teach(s)
            assertNotNull("$label: no step on an unsolved board, edges ${s.edges}", step)
            each(s, step!!)
            s = applied(s, step)
        }
        return s
    }

    // ---- soundness on real boards ---------------------------------------------------------------

    @Test
    fun `every step on a real board agrees with the answer and changes something`() {
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 15 else 40, d, "pearls-teach")) {
            walk(board(seed, d), "$d/$seed") { s, step ->
                assertTrue(step.lines.isNotEmpty() || step.crosses.isNotEmpty())
                assertFalse(step.technique == PearlsTeacher.MISTAKE)
                step.lines.forEach { assertEquals("$d/$seed line $it", '1', s.loop[it]); assertEquals('.', s.edges[it]) }
                step.crosses.forEach { assertEquals("$d/$seed cross $it", '0', s.loop[it]); assertEquals('.', s.edges[it]) }
                assertTrue(step.technique in PearlsTeacher.TECHNIQUES)
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 2000)
    }

    @Test
    fun `hints stay sound and available from boards a player made, not just from the solver's own path`() {
        val rng = Rng(5150)
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(20, d, "pearls-player")) {
            val full = board(seed, d)
            repeat(6) {
                val s = partial(full, rng, rng.nextInt(1, 9))
                if (s.solved) return@repeat
                val step = PearlsTeacher.teach(s)
                assertNotNull("$d/$seed: no step from edges ${s.edges}", step)
                step!!
                assertFalse("correct marks are never a mistake", step.technique == PearlsTeacher.MISTAKE)
                step.lines.forEach { assertEquals('1', full.loop[it]) }
                step.crosses.forEach { assertEquals('0', full.loop[it]) }
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 300)
    }

    // ---- soundness when there are several answers -----------------------------------------------

    @Test
    fun `on boards with several answers, a step holds for every loop still possible`() {
        val rng = Rng(8675309)
        var checked = 0
        var ambiguous = 0
        val kinds = mutableMapOf<String, Int>()
        for (n in listOf(4, 5)) {
            val g = PearlsGrid.of(n)
            repeat(300) {
                // A random loop and a few of the pearls it allows: few pearls is what leaves several loops.
                val loop = PearlsLogic.randomLoop(rng, n, 50, 90) ?: return@repeat
                val all = PearlsLogic.implied(n, loop)
                val pearls = String(CharArray(n * n) { if (all[it] != '.' && rng.nextInt(10) < 5) all[it] else '.' })
                if (pearls.all { it == '.' }) return@repeat
                val answers = PearlsOracle.answers(n, pearls, limit = 50).map { sq ->
                    val on = BooleanArray(g.edgeCount)
                    for (i in sq.indices) on[g.between(sq[i], sq[(i + 1) % sq.size])] = true
                    on
                }
                if (answers.size > 1) ambiguous++
                val base = PearlsState(n, pearls, loop.toString())
                repeat(3) {
                    val marked = partial(base, rng, rng.nextInt(0, 5))
                    val possible = answers.filter { a ->
                        marked.edges.indices.all { e ->
                            when (marked.edges[e]) {
                                '-' -> a[e]
                                'x' -> !a[e]
                                else -> true
                            }
                        }
                    }
                    if (possible.isEmpty()) return@repeat
                    val step = PearlsTeacher.deduce(n, pearls, marked.edges) ?: return@repeat
                    kinds[step.technique] = (kinds[step.technique] ?: 0) + 1
                    step.lines.forEach { e -> assertTrue("line $e on $pearls edges ${marked.edges}", possible.all { it[e] }) }
                    step.crosses.forEach { e -> assertTrue("cross $e on $pearls edges ${marked.edges}", possible.none { it[e] }) }
                    checked++
                }
            }
        }
        println("several-answer sample: $checked steps by technique $kinds, $ambiguous ambiguous boards")
        assertTrue("barely checked anything: $checked", checked > 600)
        assertTrue("the sample must include boards with more than one answer: $ambiguous", ambiguous > 30)
    }

    // ---- the techniques, one by one ---------------------------------------------------------------

    private fun pearlsAt(n: Int, vararg at: Pair<Int, Char>): String {
        val p = CharArray(n * n) { '.' }
        for ((i, c) in at) p[i] = c
        return p.concatToString()
    }

    @Test
    fun `a black pearl in a corner runs two squares along each side`() {
        val g = PearlsGrid.of(4)
        val step = PearlsTeacher.deduce(4, pearlsAt(4, 0 to 'b'), ".".repeat(g.edgeCount))!!
        assertEquals(PearlsTeacher.BLACK_PEARL, step.technique)
        assertEquals(setOf(g.between(0, 1), g.between(1, 2), g.between(0, 4), g.between(4, 8)), step.lines.toSet())
        assertTrue(step.explanation, "edge of the board" in step.explanation)
    }

    @Test
    fun `a white pearl on the edge of the board runs along it, and turns on one side`() {
        val g = PearlsGrid.of(5)
        // A white pearl on the top edge: it cannot go up, so it runs across.
        val step = PearlsTeacher.deduce(5, pearlsAt(5, 2 to 'w'), ".".repeat(g.edgeCount))!!
        assertEquals(PearlsTeacher.WHITE_PEARL, step.technique)
        assertEquals(setOf(g.between(1, 2), g.between(2, 3)), step.lines.toSet())
        // With the line running straight on through its left neighbour, it must turn at the right one.
        val edges = CharArray(g.edgeCount) { '.' }
        for (e in listOf(g.between(0, 1), g.between(1, 2), g.between(2, 3))) edges[e] = '-'
        val turn = PearlsTeacher.deduce(5, pearlsAt(5, 2 to 'w'), edges.concatToString())!!
        assertEquals(PearlsTeacher.WHITE_TURN, turn.technique)
        assertEquals(listOf(g.between(3, 4)), turn.crosses)
    }

    @Test
    fun `a line with one way on continues, and a square with one way in is a dead end`() {
        val g = PearlsGrid.of(4)
        // Square 0 (a corner) has a line to 1: its only other way is down.
        val edges = CharArray(g.edgeCount) { '.' }
        edges[g.between(0, 1)] = '-'
        val step = PearlsTeacher.deduce(4, ".".repeat(16), edges.concatToString())!!
        assertEquals(PearlsTeacher.ONE_WAY, step.technique)
        assertEquals(listOf(g.between(0, 4)), step.lines)
        // Corner 0 with its right edge crossed has one way in: cross it.
        val dead = CharArray(g.edgeCount) { '.' }.also { it[g.between(0, 1)] = 'x' }.concatToString()
        val d = PearlsTeacher.deduce(4, ".".repeat(16), dead)!!
        assertEquals(PearlsTeacher.DEAD_END, d.technique)
        assertEquals(listOf(g.between(0, 4)), d.crosses)
    }

    @Test
    fun `a square with two lines is never asked to be tidied`() {
        // The teacher reads a full square's other edges as crossed, so no step only crosses them.
        for (seed in seeds(30, Difficulty.HARD, "pearls-tidy")) {
            walk(board(seed, Difficulty.HARD), "tidy/$seed") { s, step ->
                val view = PearlsTeacher.view(s.size, s.edges)
                step.crosses.forEach { assertEquals(PearlsLogic.OPEN, view[it]) }
            }
        }
    }

    @Test
    fun `techniques are tried simplest first, and every tier needs its own rung`() {
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 15 else 40, d, "pearls-order")) {
            val used = mutableSetOf<String>()
            walk(board(seed, d), "$d/$seed") { _, step -> used += step.technique }
            when (d) {
                Difficulty.STANDARD -> assertTrue("$d/$seed used $used", PearlsTeacher.SMALL_LOOP !in used && PearlsTeacher.WHAT_IF !in used)
                Difficulty.HARD -> assertTrue("$d/$seed used $used", PearlsTeacher.SMALL_LOOP in used && PearlsTeacher.WHAT_IF !in used)
                Difficulty.EXPERT -> assertTrue("$d/$seed used $used", PearlsTeacher.WHAT_IF in used)
            }
        }
    }

    @Test
    fun `a what-if is a step nothing simpler gives, and its opposite really breaks`() {
        var seen = 0
        for (seed in seeds(10, Difficulty.EXPERT, "pearls-whatif")) {
            walk(board(seed, Difficulty.EXPERT), "whatif/$seed") { s, step ->
                if (step.technique != PearlsTeacher.WHAT_IF) return@walk
                seen++
                val g = PearlsGrid.of(s.size)
                val view = PearlsTeacher.view(s.size, s.edges)
                // Nothing simpler applies: the local and loop rules settle nothing more.
                val st = view.copyOf()
                assertEquals(null, PearlsLogic.propagate(g, s.pearls, st, loop = true).contradiction)
                assertTrue(st.contentEquals(view))
                // The opposite of the conclusion, followed by the rules, breaks something.
                val e = (step.lines + step.crosses).single()
                val opposite = view.copyOf().also { it[e] = if (e in step.lines) PearlsLogic.CROSS else PearlsLogic.LINE }
                assertNotNull(PearlsLogic.propagate(g, s.pearls, opposite, loop = true).contradiction)
                assertTrue(step.explanation, step.explanation.startsWith("With "))
            }
        }
        assertTrue("barely saw any: $seen", seen > 10)
    }

    // ---- mistakes -----------------------------------------------------------------------------------

    @Test
    fun `a wrong line or a wrong cross is addressed first, and a right one never is`() {
        val rng = Rng(31337)
        for (d in Difficulty.entries) for (seed in seeds(15, d, "pearls-mistake")) {
            val full = board(seed, d)
            val s0 = partial(full, rng, 5)
            val e = rng.nextInt(s0.edges.length)
            val wrong = if (full.loop[e] == '1') 'x' else '-'
            val s = s0.copy(edges = String(s0.edges.toCharArray().also { it[e] = wrong }))
            val step = PearlsTeacher.teach(s)!!
            assertEquals("$d/$seed", PearlsTeacher.MISTAKE, step.technique)
            assertTrue(step.clears.single() <= e)
            assertTrue(step.explanation, if (wrong == '-') "Rub it out" in step.explanation else "take the cross off" in step.explanation)
            // Taking it back clears the hint.
            val fixed = applied(s, step)
            assertTrue(Pearls.reached(fixed, step, s.edges))
        }
    }

    // ---- text -----------------------------------------------------------------------------------------

    @Test
    fun `every nudge and explanation fits the panel`() {
        val rng = Rng(2468)
        var checked = 0
        val longest = mutableMapOf<String, Int>()
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 40 else 120, d, "pearls-text")) {
            val full = board(seed, d)
            walk(full, "$d/$seed") { _, step -> check(step, longest); checked++ }
            repeat(3) { round ->
                var p = partial(full, rng, rng.nextInt(0, 9))
                if (round == 2) {
                    val e = rng.nextInt(p.edges.length)
                    p = p.copy(edges = String(p.edges.toCharArray().also { it[e] = if (full.loop[e] == '1') 'x' else '-' }))
                }
                val step = PearlsTeacher.teach(p) ?: return@repeat
                check(step, longest)
                checked++
            }
        }
        println("longest explanation per technique: $longest")
        assertTrue("barely checked anything: $checked", checked > 5000)
    }

    private fun check(step: PearlsTeacher.Step, longest: MutableMap<String, Int>) {
        assertTrue("nudge too long (${step.nudge.length}): ${step.nudge}", step.nudge.length <= PearlsTeacher.MAX_NUDGE)
        assertTrue(
            "explanation too long (${step.explanation.length}): ${step.explanation}",
            step.explanation.length <= PearlsTeacher.MAX_EXPLANATION,
        )
        longest[step.technique] = maxOf(longest[step.technique] ?: 0, step.explanation.length)
    }

    // ---- speed -----------------------------------------------------------------------------------------

    @Test
    fun `a hint is quick on an expert board`() {
        val times = ArrayList<Double>()
        for (seed in seeds(10, Difficulty.EXPERT, "pearls-speed")) {
            var s = board(seed, Difficulty.EXPERT)
            while (!s.solved) {
                val t0 = System.nanoTime()
                val step = PearlsTeacher.teach(s)!!
                times += (System.nanoTime() - t0) / 1e6
                s = applied(s, step)
            }
        }
        times.sort()
        println("TIME expert hint median ${"%.2f".format(times[times.size / 2])} p95 ${"%.2f".format(times[times.size * 95 / 100])} max ${"%.2f".format(times.last())} ms")
        assertTrue("slowest hint ${times.last()} ms", times.last() < 300)
    }

    // ---- coverage -------------------------------------------------------------------------------------

    /**
     * A measurement, printed and written to `app/build/reports/pearls-teaching-coverage.txt`: which technique a
     * player walking a board by hints alone needs, per tier. Nothing can fall back (a generated board is finished
     * by this very ladder), so the assertion is that the walk never runs out of steps.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 200
        val report = StringBuilder()
        report.appendLine("Pearls teaching coverage: $perTier boards per tier, walked from blank by hints alone")
        for (d in Difficulty.entries) {
            val stepCounts = PearlsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = PearlsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var total = 0
            for (seed in seeds(perTier, d, "pearls-coverage")) {
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
            for (t in PearlsTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-12s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / total,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/pearls-teaching-coverage.txt").writeText(report.toString())
    }
}
