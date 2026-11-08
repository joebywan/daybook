package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LoopEdges
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Loop
import com.joebywan.daybook.puzzles.LoopLogic
import com.joebywan.daybook.puzzles.LoopState
import com.joebywan.daybook.puzzles.LoopTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Loop hint solver. On real boards every step must agree with the stored loop; and, because on a one-answer board
 * every true fact is derivable (which cannot tell reasoning from peeking), on boards with *several* answers every step
 * must hold for every loop the marks still allow ([LoopOracle.answers], which works on squares, not edges).
 */
class LoopTeachingTest {

    private fun board(seed: Long, d: Difficulty) = LoopLogic.generateVerified(seed, d)!!

    private fun applied(s: LoopState, step: LoopTeacher.Step) = Loop.applyStep(s, step)

    /** Marks taken from the answer: each loop edge drawn, each other edge crossed, with chance [keep]/10. */
    private fun partial(s: LoopState, rng: Rng, keep: Int): LoopState =
        s.copy(edges = String(CharArray(s.edges.length) {
            if (rng.nextInt(10) >= keep) '.' else if (s.loop[it] == '1') '-' else 'x'
        }))

    private fun walk(start: LoopState, label: String, each: (LoopState, LoopTeacher.Step) -> Unit = { _, _ -> }): LoopState {
        var s = start
        var guard = 0
        while (!s.solved) {
            assertTrue("$label: walk did not finish", guard++ < 1000)
            val step = LoopTeacher.teach(s)
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
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 15 else 40, d, "loop-teach")) {
            walk(board(seed, d), "$d/$seed") { s, step ->
                assertTrue(step.lines.isNotEmpty() || step.crosses.isNotEmpty())
                assertFalse(step.technique == LoopTeacher.MISTAKE)
                step.lines.forEach { assertEquals("$d/$seed line $it", '1', s.loop[it]); assertEquals('.', s.edges[it]) }
                step.crosses.forEach { assertEquals("$d/$seed cross $it", '0', s.loop[it]); assertEquals('.', s.edges[it]) }
                assertTrue(step.technique in LoopTeacher.TECHNIQUES)
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 2000)
    }

    @Test
    fun `hints stay sound and available from boards a player made, not just from the solver's own path`() {
        val rng = Rng(5150)
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(20, d, "loop-player")) {
            val full = board(seed, d)
            repeat(6) {
                val s = partial(full, rng, rng.nextInt(1, 9))
                if (s.solved) return@repeat
                val step = LoopTeacher.teach(s)
                assertNotNull("$d/$seed: no step from edges ${s.edges}", step)
                step!!
                assertFalse("correct marks are never a mistake", step.technique == LoopTeacher.MISTAKE)
                step.lines.forEach { assertEquals('1', full.loop[it]) }
                step.crosses.forEach { assertEquals('0', full.loop[it]) }
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 300)
    }

    @Test
    fun `on boards with several answers, a step holds for every loop still possible`() {
        val rng = Rng(8675309)
        var checked = 0
        var ambiguous = 0
        val kinds = mutableMapOf<String, Int>()
        for (n in listOf(3, 4, 5)) {
            repeat(400) {
                // A random loop and a few of its numbers: few numbers is what leaves several loops.
                val loop = LoopEdges.growLoop(rng, n + 1, 30, 90, 0) { 0 } ?: return@repeat
                val all = LoopLogic.cluesOf(n, loop)
                val clues = String(CharArray(n * n) { if (rng.nextInt(10) < 4) all[it] else '.' })
                val answers = LoopOracle.answers(n, clues, limit = 200).map { LoopOracle.loopOf(n, it) }
                if (answers.size > 1) ambiguous++
                val base = LoopState(n, clues, loop)
                repeat(3) {
                    val marked = partial(base, rng, rng.nextInt(0, 8))
                    val possible = answers.filter { a ->
                        marked.edges.indices.all { e ->
                            when (marked.edges[e]) {
                                '-' -> a[e] == '1'
                                'x' -> a[e] == '0'
                                else -> true
                            }
                        }
                    }
                    if (possible.isEmpty()) return@repeat
                    val step = LoopTeacher.deduce(n, clues, marked.edges) ?: return@repeat
                    kinds[step.technique] = (kinds[step.technique] ?: 0) + 1
                    step.lines.forEach { e -> assertTrue("line $e on $clues edges ${marked.edges}", possible.all { it[e] == '1' }) }
                    step.crosses.forEach { e -> assertTrue("cross $e on $clues edges ${marked.edges}", possible.none { it[e] == '1' }) }
                    checked++
                    // The loop rule rarely comes first on a board this small, so every edge it would cross is checked too.
                    for (loopStep in LoopTeacher.steps(n, clues, marked.edges, LoopTeacher.SMALL_LOOP)) {
                        kinds[LoopTeacher.SMALL_LOOP] = (kinds[LoopTeacher.SMALL_LOOP] ?: 0) + 1
                        loopStep.crosses.forEach { e -> assertTrue("loop cross $e on $clues edges ${marked.edges}", possible.none { it[e] == '1' }) }
                    }
                }
            }
        }
        println("several-answer sample: $checked steps by technique $kinds, $ambiguous ambiguous boards")
        assertTrue("barely checked anything: $checked", checked > 600)
        assertTrue("the sample must include boards with more than one answer: $ambiguous", ambiguous > 30)
        for (t in listOf(LoopTeacher.COUNT, LoopTeacher.CORNER, LoopTeacher.ONE_WAY, LoopTeacher.DEAD_END, LoopTeacher.SMALL_LOOP)) {
            assertTrue("the sample never tried $t: $kinds", (kinds[t] ?: 0) > 5)
        }
    }

    // ---- the techniques, one by one ---------------------------------------------------------------

    @Test
    fun `a 0 crosses its sides, and a 3 in a corner takes both sides there`() {
        val g = LoopLogic.grid(3)
        val zero = LoopTeacher.deduce(3, "....0....", ".".repeat(g.edgeCount))!!
        assertEquals(LoopTeacher.COUNT, zero.technique)
        assertEquals(LoopLogic.sides(3, 4).toSet(), zero.crosses.toSet())
        val three = LoopTeacher.deduce(3, "3........", ".".repeat(g.edgeCount))!!
        assertEquals(LoopTeacher.CORNER, three.technique)
        val sides = LoopLogic.sides(3, 0)
        assertEquals(setOf(sides[0], sides[3]), three.lines.toSet())
        assertTrue(three.explanation, "no way out" in three.explanation)
        // A 1 in a corner never takes either side there.
        val one = LoopTeacher.deduce(3, "1........", ".".repeat(g.edgeCount))!!
        assertEquals(LoopTeacher.CORNER, one.technique)
        assertEquals(setOf(sides[0], sides[3]), one.crosses.toSet())
    }

    @Test
    fun `a line with one way on continues, and a dot with one way in is a dead end`() {
        val g = LoopLogic.grid(3)
        // Dot 0 (a corner) has a line to dot 1: its only other way is down.
        val edges = CharArray(g.edgeCount) { '.' }.also { it[g.between(0, 1)] = '-' }.concatToString()
        val step = LoopTeacher.deduce(3, ".".repeat(9), edges)!!
        assertEquals(LoopTeacher.ONE_WAY, step.technique)
        assertEquals(listOf(g.between(0, 4)), step.lines)
        val dead = CharArray(g.edgeCount) { '.' }.also { it[g.between(0, 1)] = 'x' }.concatToString()
        val d = LoopTeacher.deduce(3, ".".repeat(9), dead)!!
        assertEquals(LoopTeacher.DEAD_END, d.technique)
        assertEquals(listOf(g.between(0, 4)), d.crosses)
    }

    @Test
    fun `techniques are tried simplest first, and every tier needs its own rung`() {
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 15 else 40, d, "loop-order")) {
            val used = mutableSetOf<String>()
            walk(board(seed, d), "$d/$seed") { _, step -> used += step.technique }
            when (d) {
                Difficulty.STANDARD -> assertTrue("$d/$seed used $used", LoopTeacher.SMALL_LOOP !in used && LoopTeacher.WHAT_IF !in used)
                Difficulty.HARD -> assertTrue("$d/$seed used $used", LoopTeacher.SMALL_LOOP in used && LoopTeacher.WHAT_IF !in used)
                Difficulty.EXPERT -> assertTrue("$d/$seed used $used", LoopTeacher.WHAT_IF in used)
            }
        }
    }

    @Test
    fun `a what-if is a step nothing simpler gives, and its opposite really breaks`() {
        var seen = 0
        for (seed in seeds(10, Difficulty.EXPERT, "loop-whatif")) {
            walk(board(seed, Difficulty.EXPERT), "whatif/$seed") { s, step ->
                if (step.technique != LoopTeacher.WHAT_IF) return@walk
                seen++
                val rules = LoopLogic.Rules(s.size, s.clues)
                val view = LoopEdges.view(rules.g, s.edges)
                val st = view.copyOf()
                assertEquals(null, LoopEdges.propagate(rules, st, loop = true).contradiction)
                assertTrue(st.contentEquals(view))
                val e = (step.lines + step.crosses).single()
                val opposite = view.copyOf().also { it[e] = if (e in step.lines) LoopEdges.CROSS else LoopEdges.LINE }
                assertNotNull(LoopEdges.propagate(rules, opposite, loop = true).contradiction)
                assertTrue(step.explanation, step.explanation.startsWith("With "))
            }
        }
        assertTrue("barely saw any: $seen", seen > 10)
    }

    // ---- mistakes -----------------------------------------------------------------------------------

    @Test
    fun `a wrong line or a wrong cross is addressed first, and a right one never is`() {
        val rng = Rng(31337)
        for (d in Difficulty.entries) for (seed in seeds(15, d, "loop-mistake")) {
            val full = board(seed, d)
            val s0 = partial(full, rng, 5)
            val e = rng.nextInt(s0.edges.length)
            val wrong = if (full.loop[e] == '1') 'x' else '-'
            val s = s0.copy(edges = String(s0.edges.toCharArray().also { it[e] = wrong }))
            val step = LoopTeacher.teach(s)!!
            assertEquals("$d/$seed", LoopTeacher.MISTAKE, step.technique)
            assertTrue(step.clears.single() <= e)
            assertTrue(step.explanation, if (wrong == '-') "Rub it out" in step.explanation else "take it off" in step.explanation)
            val fixed = applied(s, step)
            assertTrue(Loop.reached(fixed, step, s.edges))
        }
    }

    // ---- text -----------------------------------------------------------------------------------------

    @Test
    fun `every nudge and explanation fits the panel`() {
        val rng = Rng(2468)
        var checked = 0
        val longest = mutableMapOf<String, Int>()
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 40 else 120, d, "loop-text")) {
            val full = board(seed, d)
            walk(full, "$d/$seed") { _, step -> check(step, longest); checked++ }
            repeat(3) { round ->
                var p = partial(full, rng, rng.nextInt(0, 9))
                if (round == 2) {
                    val e = rng.nextInt(p.edges.length)
                    p = p.copy(edges = String(p.edges.toCharArray().also { it[e] = if (full.loop[e] == '1') 'x' else '-' }))
                }
                val step = LoopTeacher.teach(p) ?: return@repeat
                check(step, longest)
                checked++
            }
        }
        println("longest explanation per technique: $longest")
        assertTrue("barely checked anything: $checked", checked > 5000)
    }

    private fun check(step: LoopTeacher.Step, longest: MutableMap<String, Int>) {
        assertTrue("nudge too long (${step.nudge.length}): ${step.nudge}", step.nudge.length <= LoopTeacher.MAX_NUDGE)
        assertTrue(
            "explanation too long (${step.explanation.length}): ${step.explanation}",
            step.explanation.length <= LoopTeacher.MAX_EXPLANATION,
        )
        longest[step.technique] = maxOf(longest[step.technique] ?: 0, step.explanation.length)
    }

    // ---- speed -----------------------------------------------------------------------------------------

    @Test
    fun `a hint is quick on an expert board`() {
        val times = ArrayList<Double>()
        for (seed in seeds(10, Difficulty.EXPERT, "loop-speed")) {
            var s = board(seed, Difficulty.EXPERT)
            while (!s.solved) {
                val t0 = System.nanoTime()
                val step = LoopTeacher.teach(s)!!
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
     * A measurement, printed and written to `app/build/reports/loop-teaching-coverage.txt`: which technique a player
     * walking a board by hints alone needs, per tier. Nothing can fall back (a generated board is finished by this very
     * ladder), so the assertion is that the walk never runs out of steps.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 200
        val report = StringBuilder()
        report.appendLine("Loop teaching coverage: $perTier boards per tier, walked from blank by hints alone")
        for (d in Difficulty.entries) {
            val stepCounts = LoopTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = LoopTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var total = 0
            for (seed in seeds(perTier, d, "loop-coverage")) {
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
            for (t in LoopTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-12s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / total,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
        }
        println(report)
        writeReport("loop-teaching-coverage.txt", report.toString())
    }

    // ---- the walkthrough --------------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one loop, finished by the local rules, and it is the stored one`() {
        val s = Loop.tutorial.first().state as LoopState
        val answers = LoopOracle.answers(4, s.clues, limit = 5)
        assertEquals(1, answers.size)
        assertEquals(s.loop, LoopOracle.loopOf(4, answers.single()))
        assertEquals(LoopLogic.LOCAL, LoopLogic.depth(s))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Loop.tutorial
        assertEquals(6, frames.size)
        fun board(i: Int) = frames[i].state as LoopState
        val g = LoopLogic.grid(4)
        fun dot(r: Int, c: Int) = r * 5 + c
        assertNull(frames[0].accepts)
        assertTrue(frames[5].freePlay)
        assertTrue(board(0).solved)
        // The first frame's claims: the 3 has three sides on the loop, the 0 none.
        assertEquals(3, Loop.linesRound(board(0), 0))
        assertEquals(0, Loop.linesRound(board(0), 2))

        // 2: one drag round the 3's corner, made of the dots the finger passes.
        val corner = frames[1].accepts!!
        val afterCorner = board(1).drag(listOf(dot(1, 0), dot(0, 0), dot(0, 1)))
        assertTrue(corner(afterCorner))
        assertFalse("one side only", corner(board(1).drag(listOf(dot(0, 0), dot(0, 1)))))
        assertFalse("too far", corner(board(1).drag(listOf(dot(1, 0), dot(0, 0), dot(0, 1), dot(0, 2)))))
        assertEquals(board(2).edges, afterCorner.edges)

        // 3: a tap on the 0's top edge, found by the board's own hit test (dots half a step in from the box).
        val cross = frames[2].accepts!!
        val e = g.between(dot(0, 2), dot(0, 3))
        val tapAt = LoopEdges.edgeNear(5, 3.0f, 0.5f)
        assertEquals(e, tapAt)
        val afterCross = board(2).tap(tapAt)
        assertTrue(cross(afterCross))
        assertFalse("another edge", cross(board(2).tap(g.between(dot(0, 3), dot(0, 4)))))
        assertEquals(board(3).edges, afterCross.edges)

        // 4: the slip between the two 0s, which turns both red and is a mistake.
        val slip = frames[3].accepts!!
        val afterSlip = board(3).drag(listOf(dot(1, 2), dot(1, 3)))
        assertTrue(slip(afterSlip))
        assertFalse(slip(board(3).drag(listOf(dot(1, 2), dot(2, 2)))))
        assertEquals(board(4).edges, afterSlip.edges)
        assertEquals(setOf(2, 6), Loop.breaks(afterSlip).squares)
        assertEquals(LoopTeacher.MISTAKE, LoopTeacher.teach(afterSlip)!!.technique)

        // 5: drag back along it to rub it out.
        val rub = frames[4].accepts!!
        val afterRub = board(4).drag(listOf(dot(1, 3), dot(1, 2)))
        assertTrue(rub(afterRub))
        assertFalse(rub(board(4)))
        assertEquals(board(5).edges, afterRub.edges)
        assertTrue(Loop.breaks(afterRub).isEmpty)

        frames.forEachIndexed { i, f ->
            if (i != 4) assertNull(LoopTeacher.teach(f.state as LoopState)?.takeIf { it.technique == LoopTeacher.MISTAKE })
            assertTrue("caption too long (${f.caption.length}): ${f.caption}", f.caption.length <= 170)
        }
    }

    @Test
    fun `the 3 in the walkthrough's corner is a corner step, as its caption says`() {
        val step = LoopTeacher.teach(Loop.tutorial[1].state as LoopState)!!
        // The 0s' counts come first in the hints, but the corner's own step is the 3's two sides.
        val corner = LoopTeacher.steps(4, Loop.TUTORIAL_CLUES, (Loop.tutorial[1].state as LoopState).edges, LoopTeacher.CORNER)
        val g = LoopLogic.grid(4)
        assertTrue(corner.any { it.lines.toSet() == setOf(g.between(0, 1), g.between(0, 5)) })
        assertTrue(step.technique in LoopTeacher.TECHNIQUES)
    }

    @Test
    fun `free play is finishable by hints alone, with no mistake on the way`() {
        var s = Loop.tutorial.last().state as LoopState
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 60)
            val d = Loop.teach(s)
            assertNotNull(d)
            assertFalse(d!!.mistake)
            assertFalse(d.fallback)
            s = d.apply(s) as LoopState
        }
    }

    @Test
    fun `a hint's move reaches its own goal, and one move is one undo entry`() {
        for (d in Difficulty.entries) for (seed in seeds(4, d, "loop-glue")) {
            var s = Loop.generate(seed, d) as LoopState
            while (!s.solved) {
                val deduction = Loop.teach(s)!!
                assertFalse("not reached before it is made", deduction.isReached(s))
                val next = deduction.apply(s) as LoopState
                assertTrue(deduction.isReached(next))
                assertEquals("one move per edge changed", deduction.targets.size + s.moves, next.moves)
                s = next
            }
        }
    }
}
