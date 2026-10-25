package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Target
import com.joebywan.daybook.puzzles.TargetEntry
import com.joebywan.daybook.puzzles.TargetKeyAction
import com.joebywan.daybook.puzzles.TargetLogic
import com.joebywan.daybook.puzzles.TargetOp
import com.joebywan.daybook.puzzles.TargetState
import com.joebywan.daybook.puzzles.TargetTeacher
import com.joebywan.daybook.puzzles.press
import com.joebywan.daybook.puzzles.tapOp
import com.joebywan.daybook.puzzles.tapTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetTeachingTest {

    private fun board(day: Int, d: Difficulty) =
        TargetLogic.generateVerified(daySeed(day, Target.id, d), d)
            ?: error("no proved board for day $day ${d.name}")

    /** Every legal one-step successor of [s], as (a, op, b, board). */
    private fun successors(s: TargetState): List<Triple<Int, TargetOp, TargetState>> = buildList {
        for (a in s.slots.indices) for (b in s.slots.indices) for (op in TargetOp.entries) {
            val n = s.combine(a, op, b) ?: continue
            add(Triple(a, op, n))
        }
    }

    /** Takes each hint in turn until solved; every step must keep the target reachable and shorten the road by exactly one. */
    private fun walk(s: TargetState, onStep: (String) -> Unit = {}): TargetState {
        var now = s
        var guard = 0
        while (!now.solved) {
            val d = TargetTeacher.deduction(now) ?: error("no step on an unsolved, reachable board")
            assertFalse("unexpected mistake", d.mistake)
            assertFalse(d.fallback)
            onStep(d.technique)
            val before = TargetOracle.minOps(now.tiles)[now.target]
            assertTrue(before > 0)
            val next = d.apply(now) as TargetState
            assertEquals("a step is one operation", now.moves + 1, next.moves)
            assertTrue(d.isReached(next))
            assertFalse(d.isReached(now))
            val after = if (next.solved) 0 else TargetOracle.minOps(next.tiles)[next.target]
            assertEquals("the step must stay on a shortest road", before - 1, after)
            now = next
            assertTrue("walk does not end", ++guard <= 6)
        }
        return now
    }

    @Test
    fun `coverage and fallback rate per tier`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { TargetTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var steps = 0
            for (day in 0 until 200) {
                val s = board(day, d)
                val end = walk(s) { t -> counts[t] = counts.getValue(t) + 1; steps++ }
                assertTrue(end.solved)
            }
            val line = "${d.name}: no fallback exists (the teacher reads no stored answer), hints alone finished 200/200, steps ${steps / 200.0}/board, $counts"
            println(line)
            report.appendLine(line)
        }
        writeReport("target-teaching-coverage.txt", report.toString())
    }

    @Test
    fun `a hint's step takes exactly the shortest number of operations the oracle says`() {
        for (d in Difficulty.entries) for (day in 0 until 30) {
            val s = board(day, d)
            var n = 0
            walk(s) { n++ }
            assertEquals("${d.name} day $day", s.par, n)
        }
    }

    @Test
    fun `steps stay sound from boards a player made, whatever road they took`() {
        val rng = Rng(5)
        for (d in Difficulty.entries) for (day in 0 until 40) {
            var now = board(day, d)
            // Wander: random legal operations, but only ones that keep the target reachable (the player's road, not the teacher's).
            repeat(rng.nextInt(3)) {
                val ok = successors(now).filter { (_, _, n) -> n.solved.not() && TargetOracle.reachable(n.tiles, n.target) }
                if (ok.isNotEmpty()) now = rng.pick(ok).third
            }
            if (now.solved) continue
            assertNull("a reachable board has no mistake", TargetTeacher.badStep(now))
            walk(now)
        }
    }

    @Test
    fun `a step that lengthens the road is not a mistake, a step that loses the target is`() {
        var lengthened = 0
        var lost = 0
        for (d in Difficulty.entries) for (day in 0 until 25) {
            val s = board(day, d)
            val par = s.par
            for ((_, _, n) in successors(s)) {
                if (n.solved) continue
                val left = TargetOracle.minOps(n.tiles)[n.target]
                val step = TargetTeacher.deduction(n)
                if (left >= par) {
                    // Reachable but slower, or lost altogether.
                    if (left >= 0) {
                        lengthened++
                        assertFalse("slower is not wrong", step!!.mistake)
                    }
                }
                if (left < 0) {
                    lost++
                    val m = step!!
                    assertTrue("${d.name} day $day should flag a lost position", m.mistake)
                    assertEquals(setOf(Target.TARGET), m.targets)
                    assertFalse(m.isReached(n))
                    val back = m.apply(n) as TargetState
                    assertEquals(s.slots, back.slots)
                    assertTrue(m.isReached(back))
                    assertFalse(TargetTeacher.deduction(back)!!.mistake)
                    assertTrue(TargetTeacher.teach(n)!!.explanation.contains(n.log.last().text()))
                }
            }
        }
        println("lengthened $lengthened, lost $lost")
        assertTrue("the sample must include both", lengthened > 0 && lost > 0)
    }

    @Test
    fun `the first losing step is the one named, even when more steps followed`() {
        var checked = 0
        for (d in Difficulty.entries) for (day in 0 until 40) {
            val s = board(day, d)
            val lost = successors(s).firstOrNull { (_, _, n) -> !n.solved && TargetOracle.minOps(n.tiles)[n.target] < 0 && n.tiles.size >= 4 } ?: continue
            val onward = successors(lost.third).firstOrNull { (_, _, n) -> !n.solved } ?: continue
            val hint = TargetTeacher.teach(onward.third)!!
            assertEquals(TargetTeacher.MISTAKE, hint.technique)
            assertTrue(hint.explanation.contains(lost.third.log.single().text()))
            val back = TargetTeacher.deduction(onward.third)!!.apply(onward.third) as TargetState
            assertEquals(s.slots, back.slots)
            checked++
        }
        assertTrue(checked > 10)
    }

    @Test
    fun `the reasoning cannot see the answer`() {
        // The teacher is handed the tiles and the target; what the generator recorded (par) changes nothing.
        for (d in Difficulty.entries) {
            val s = board(3, d)
            val a = TargetTeacher.teach(s)!!
            val b = TargetTeacher.teach(s.copy(par = 0))!!
            assertEquals(a.explanation, b.explanation)
            assertEquals(a.targets, b.targets)
        }
        // And it works on a board nobody generated.
        val odd = TargetState(listOf(7, 7, 7, 3, 3, 2), 500, 0)
        if (TargetLogic.steps(odd.start, odd.target) > 0) assertNotNull(TargetTeacher.teach(odd))
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = 0
        for (d in Difficulty.entries) for (day in 0 until 365) {
            val s = board(day, d)
            val lost = successors(s).firstOrNull { (_, _, n) -> !n.solved && TargetOracle.minOps(n.tiles)[n.target] < 0 }?.third
            for (state in listOfNotNull(s, lost)) {
                var cur = state
                var guard = 0
                while (!cur.solved && guard++ < 8) {
                    val step = TargetTeacher.teach(cur) ?: break
                    assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= TargetTeacher.MAX_NUDGE)
                    assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= TargetTeacher.MAX_EXPLANATION)
                    longest = maxOf(longest, step.explanation.length)
                    cur = TargetTeacher.deduction(cur)!!.apply(cur) as TargetState
                }
            }
        }
        println("longest explanation: $longest")
    }

    @Test
    fun `a hint costs little to compute`() {
        // Expert is the slowest: six tiles and the longest road. The first hint of 100 boards, in ms (median / max).
        val times = (0 until 100).map { day ->
            val s = board(day, Difficulty.EXPERT)
            val t0 = System.nanoTime()
            TargetTeacher.teach(s)
            (System.nanoTime() - t0) / 1_000_000.0
        }.sorted()
        println("expert first hint ms: median ${times[50]} p95 ${times[95]} max ${times.last()}")
        assertTrue("slow hint ${times.last()} ms", times.last() < 1500)
    }

    // ---- the walkthrough ------------------------------------------------------------------

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Target.tutorial
        assertEquals(3, frames.size)
        assertTrue(frames[0].accepts == null)
        assertTrue(frames[2].freePlay)

        fun gesture(s: TargetState, first: Int, op: TargetOp, second: Int): TargetState {
            val picked = s.tapTile(TargetEntry(), first).entry
            val chosen = s.tapOp(picked, op).entry
            return s.tapTile(chosen, second).state ?: s
        }

        fun ok(frame: Int, first: Int, op: TargetOp, second: Int) =
            frames[frame].accepts!!(gesture(frames[frame].state as TargetState, first, op, second))

        // Frame 2: 25 x 4. Either order is the same step.
        assertTrue(ok(1, 0, TargetOp.Mul, 1))
        assertTrue(ok(1, 1, TargetOp.Mul, 0))
        assertFalse("wrong operator", ok(1, 0, TargetOp.Add, 1))
        assertFalse("wrong tiles", ok(1, 2, TargetOp.Mul, 3))
        assertFalse("nothing made", frames[1].accepts!!(frames[1].state))

        // Each frame's board is the previous frame's result, so a caption never describes a board the player is not on.
        assertEquals(frames[1].state, frames[0].state)
        assertEquals(gesture(frames[1].state as TargetState, 0, TargetOp.Mul, 1), frames[2].state)

        // The captions' claims about the board and the rules.
        assertTrue(frames[0].caption.contains("106"))
        assertEquals(106, (frames[0].state as TargetState).target)
        val s0 = frames[0].state as TargetState
        assertNull("7 / 2 is refused", s0.combine(4, TargetOp.Div, 3))
        assertTrue(frames[1].caption.contains("25") && frames[1].caption.contains("4") && frames[1].caption.contains("100"))
        // "You need not use every number": a three-step answer from here leaves tiles unused.
        assertTrue(gesture(gesture(frames[2].state as TargetState, 2, TargetOp.Mul, 3), 0, TargetOp.Add, 2).solved)
        assertTrue(frames.all { it.caption.length <= 170 })
    }

    @Test
    fun `the free-play frame is finished by hints alone, with no mistake`() {
        val free = Target.tutorial.last().state as TargetState
        assertFalse(free.solved)
        assertTrue(walk(free).solved)
        // And by the keyboard, the same as by taps.
        var s = Target.tutorialBoard()
        var e = TargetEntry()
        for (a in listOf(
            TargetKeyAction.Digit(2), TargetKeyAction.Digit(5), TargetKeyAction.Operator(TargetOp.Mul), TargetKeyAction.Digit(4), TargetKeyAction.Enter,
        )) {
            val step = s.press(e, a)
            e = step.entry
            step.state?.let { s = it }
        }
        assertEquals(listOf(100, 0, 3, 2, 7, 1), s.slots)
    }
}
