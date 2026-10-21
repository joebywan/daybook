package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Sand
import com.joebywan.daybook.puzzles.SandLogic
import com.joebywan.daybook.puzzles.SandState
import com.joebywan.daybook.puzzles.SandTeacher
import com.joebywan.daybook.puzzles.tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Sand's hints. Soundness is checked with the test's own rules (`SandSupport`): a board is sortable when the search's
 * road replays to sorted under those rules, and unsortable only when `SandSupport.sortable`, a breadth-first search of
 * raw positions sharing nothing with `SandLogic.solve`, exhausts it.
 */
class SandTeachingTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun board(day: Int, d: Difficulty) =
        Sand.generateVerified(DailySeed.seedFor(start.plusDays(day.toLong()), Sand.id, d), d) ?: error("no board day $day ${d.name}")

    /** Sortable, proved by a witness that replays under the test's own rules. */
    private fun provedSortable(s: SandState): Boolean {
        val v = SandLogic.solve(s.tubes, s.height, SandTeacher.HINT_BUDGET)
        return v is SandLogic.Verdict.Solvable && SandSupport.replays(s.tubes, s.height, v.path)
    }

    /** Takes each hint in turn until sorted; every step is one pour that keeps the board sortable. Returns the steps taken. */
    private fun walk(s: SandState, onStep: (String) -> Unit = {}): Int {
        var now = s
        var steps = 0
        while (!now.solved) {
            val d = SandTeacher.deduction(now) ?: error("no step on an unsolved board: ${now.tubes}")
            assertFalse("unexpected mistake on ${now.tubes}", d.mistake)
            assertFalse(d.fallback)
            onStep(d.technique)
            val next = d.apply(now) as SandState
            assertEquals("a step is one pour", now.moves + 1, next.moves)
            assertTrue("the step is a legal pour", SandSupport.pours(now.tubes, now.height).any { (a, b) -> SandSupport.pour(now.tubes, now.height, a, b) == next.tubes })
            assertTrue(d.isReached(next))
            assertFalse(d.isReached(now))
            assertTrue("a step keeps the board sortable: ${next.tubes}", next.solved || provedSortable(next))
            now = next
            assertTrue("hints go round in circles on ${s.tubes}", ++steps <= 120)
        }
        return steps
    }

    @Test
    fun `coverage - hints alone sort every board, per tier`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { SandTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var steps = 0
            for (day in 0 until 200) steps += walk(board(day, d)) { t -> counts[t] = counts.getValue(t) + 1 }
            val line = "${d.name}: no fallback exists (nothing is hidden), hints alone sorted 200/200, ${steps / 200.0} pours a board, $counts"
            println(line)
            report.appendLine(line)
        }
        File("build/reports").mkdirs()
        File("build/reports/sand-teaching-coverage.txt").writeText(report.toString())
    }

    /** Random legal pours from a daily board: where a real player might stand, sortable or not. */
    private fun wander(s: SandState, rng: Rng, pours: Int): SandState {
        var now = s
        repeat(pours) {
            val ps = SandSupport.pours(now.tubes, now.height)
            if (ps.isEmpty() || now.solved) return now
            val (a, b) = rng.pick(ps)
            now = now.pour(a, b)!!
        }
        return now
    }

    @Test
    fun `from positions a player made, a mistake is called only where the board is proved unsortable, and Show me mends it`() {
        val rng = Rng(9)
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            var sortable = 0
            var mistakes = 0
            var stuck = 0
            var unsure = 0
            for (day in 0 until 120) for (k in 0 until 3) {
                val now = wander(board(day, d), rng, 5 + rng.nextInt(40))
                if (now.solved) continue
                val step = SandTeacher.teach(now) ?: error("no hint on ${now.tubes}")
                val hint = SandTeacher.deduction(now)!!
                when (step.technique) {
                    SandTeacher.UNSURE -> unsure++
                    SandTeacher.MISTAKE, SandTeacher.STUCK -> {
                        if (step.technique == SandTeacher.STUCK) stuck++ else mistakes++
                        assertTrue(hint.mistake)
                        assertEquals(step.technique == SandTeacher.STUCK, now.stuck)
                        assertEquals("only a proved unsortable board is a mistake: ${now.tubes}", false, SandSupport.sortable(now.tubes, now.height))
                        val mended = hint.apply(now) as SandState
                        assertEquals(now.moves + 1, mended.moves)
                        assertTrue("Show me leaves it sortable", provedSortable(mended))
                        assertTrue("to a board on the player's own road", now.log.subList(0, mended.log.size) == mended.log)
                        val wrong = now.log[mended.log.size]
                        assertEquals("whose next pour loses it", false, SandSupport.sortable(SandSupport.pour(mended.tubes, mended.height, wrong.from, wrong.to), mended.height))
                        assertTrue(hint.isReached(mended))
                        assertFalse(hint.isReached(now))
                    }
                    else -> {
                        sortable++
                        assertFalse(hint.mistake)
                        assertTrue(provedSortable(now))
                        walk(now)
                    }
                }
            }
            val line = "${d.name}: from 360 wandering positions, sortable $sortable, mistake $mistakes, stuck $stuck, search out of budget $unsure"
            println(line)
            report.appendLine(line)
            assertEquals("a hint's search ran out of budget", 0, unsure)
            assertTrue("the sample must hold both kinds", sortable > 20 && mistakes + stuck > 20)
        }
        File("build/reports").mkdirs()
        File("build/reports/sand-teaching-mistakes.txt").writeText(report.toString())
    }

    @Test
    fun `the pour named is the player's own, and Show me goes back to the last board that sorts`() {
        // Expert, 2026-10-21: tube 1 into the empty tube 9, then tube 4 into the empty tube 10 leaves no pour at all.
        val s = Sand.generate(DailySeed.seedFor(LocalDate.of(2026, 10, 21), Sand.id, Difficulty.EXPERT), Difficulty.EXPERT) as SandState
        val one = s.pour(0, 8)!!
        val stuck = one.pour(3, 9)!!
        assertTrue(stuck.stuck)
        val step = SandTeacher.teach(stuck)!!
        assertEquals(SandTeacher.STUCK, step.technique)
        assertTrue(step.explanation, step.explanation.contains("from tube 4 onto tube 10"))
        val back = SandTeacher.deduction(stuck)!!.apply(stuck) as SandState
        assertEquals(one.tubes, back.tubes)
        assertEquals(one.log, back.log)
        assertEquals(stuck.moves + 1, back.moves)
        // A board with no pours recorded still gets a mend: the sand of one pour put back, leaving it sortable.
        val bare = stuck.copy(log = emptyList())
        val mended = SandTeacher.deduction(bare)!!.apply(bare) as SandState
        assertTrue(provedSortable(mended))
        assertTrue(SandSupport.pours(mended.tubes, 4).any { (a, b) -> SandSupport.pour(mended.tubes, 4, a, b) == bare.tubes })
        // Taking back exactly undoes: every board on the way, pour by pour.
        var t = stuck
        while (t.log.isNotEmpty()) t = t.withoutFrom(t.log.size - 1)
        assertEquals(s.tubes, t.tubes)
    }

    @Test
    fun `the search's verdict from positions a player made - how often it runs out of budget`() {
        // Truncated is never a mistake; measure how often it would happen. Random play, a verdict every five pours.
        val rng = Rng(21)
        for (d in Difficulty.entries) {
            var truncated = 0
            var verdicts = 0
            var largest = 0
            for (day in 0 until 120) {
                var now = board(day, d)
                for (step in 0 until 60) {
                    now = wander(now, rng, 1)
                    if (now.solved || step % 5 != 4) continue
                    val (v, n) = SandLogic.search(now.tubes, now.height, SandTeacher.HINT_BUDGET)
                    verdicts++
                    largest = maxOf(largest, n)
                    if (v == SandLogic.Verdict.Truncated) truncated++
                }
            }
            println("${d.name}: Truncated $truncated of $verdicts verdicts; largest search $largest positions (budget ${SandTeacher.HINT_BUDGET})")
            assertEquals(0, truncated)
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = 0
        val rng = Rng(4)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            for (s in listOf(board(day, d), wander(board(day, d), rng, 30))) {
                var now = s
                var guard = 0
                while (!now.solved && guard++ < 80) {
                    val step = SandTeacher.teach(now) ?: break
                    assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= SandTeacher.MAX_NUDGE)
                    assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= SandTeacher.MAX_EXPLANATION)
                    longest = maxOf(longest, step.explanation.length)
                    now = SandTeacher.deduction(now)!!.apply(now) as SandState
                }
            }
        }
        println("longest explanation: $longest")
    }

    @Test
    fun `a hint costs little to compute`() {
        val times = (0 until 100).map { day ->
            val s = board(day, Difficulty.EXPERT)
            val t0 = System.nanoTime()
            SandTeacher.teach(s)
            (System.nanoTime() - t0) / 1_000_000.0
        }.sorted()
        println("expert first hint ms: median ${times[50]} p95 ${times[95]} max ${times.last()}")
        assertTrue("slow hint ${times.last()} ms", times.last() < 1500)
    }

    @Test
    fun `a hint names tubes and colours the board shows`() {
        val s = board(0, Difficulty.HARD)
        val step = SandTeacher.teach(s)!!
        assertTrue(step.explanation.contains("tube ${step.from + 1}"))
        assertTrue(Sand.colourNames[s.tubes[step.from].last()] in step.explanation)
        assertEquals(setOf(step.from), step.focus)
        assertEquals(setOf(step.from, step.to), step.targets)
    }

    // ---- the walkthrough ------------------------------------------------------------------

    /** The board after tapping [a] then [b], the real gestures. */
    private fun gesture(s: SandState, a: Int, b: Int): SandState {
        val lifted = s.tap(null, a).lifted
        return s.tap(lifted, b).state ?: s
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Sand.tutorial
        assertEquals(6, frames.size)
        assertTrue(frames[0].accepts == null && frames[1].accepts == null)
        assertTrue(frames[5].freePlay)

        fun ok(frame: Int, a: Int, b: Int) = frames[frame].accepts!!(gesture(frames[frame].state as SandState, a, b))

        assertTrue(ok(2, 0, 1))
        assertFalse("the reverse is refused, so nothing is made", ok(2, 1, 0))
        assertFalse("into the empty tube instead", ok(2, 0, 4))
        assertFalse("nothing made", frames[2].accepts!!(frames[2].state))

        assertTrue(ok(3, 2, 4))
        assertFalse("another tube into the spare", ok(3, 3, 4))
        assertFalse(ok(3, 0, 4))

        assertTrue(ok(4, 1, 2))
        assertFalse(ok(4, 1, 4))

        // Each frame's board is the previous frame's result.
        assertEquals(frames[2].state, frames[1].state)
        assertEquals(gesture(frames[2].state as SandState, 0, 1), frames[3].state)
        assertEquals(gesture(frames[3].state as SandState, 2, 4), frames[4].state)
        assertEquals(gesture(frames[4].state as SandState, 1, 2), frames[5].state)

        // The captions' claims about the board: cream on top of tubes 1 and 2; two brown on tube 3 over a cream; tube 3 then all cream.
        val cream = Sand.colourNames.indexOf("cream")
        val brown = Sand.colourNames.indexOf("brown")
        val s0 = frames[2].state as SandState
        assertEquals(cream, s0.tubes[0].last())
        assertEquals(cream, s0.tubes[1].last())
        val s1 = frames[3].state as SandState
        assertEquals(listOf(cream, brown, brown), s1.tubes[2])
        assertTrue(s1.tubes[4].isEmpty())
        val s3 = frames[5].state as SandState
        assertEquals(listOf(cream, cream, cream), s3.tubes[2])
        assertTrue(frames.all { it.caption.length <= 170 })
        // A small board: three colours of three, five tubes.
        assertEquals(3, s0.colours)
        assertEquals(3, s0.height)
        assertEquals(5, s0.tubes.size)
    }

    @Test
    fun `the free-play frame is sorted by hints alone, with no mistake`() {
        val free = Sand.tutorial.last().state as SandState
        assertFalse(free.solved)
        assertTrue(walk(free) > 0)
        assertNotNull(SandTeacher.deduction(free))
    }
}
