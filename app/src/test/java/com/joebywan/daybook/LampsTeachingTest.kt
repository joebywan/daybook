package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.LampsLogic
import com.joebywan.daybook.puzzles.LampsState
import com.joebywan.daybook.puzzles.LampsTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LampsTeachingTest {

    private fun board(day: Int, d: Difficulty) =
        LampsLogic.generateVerified(daySeed(day, "lamps", d), d)
            ?: error("no proved board for day $day ${d.name}")

    /** Every step's conclusion must hold in the (unique) answer; applies each until solved. */
    private fun walk(s: LampsState, onStep: (String) -> Unit = {}): LampsState = hintWalk(s, 400) { now ->
        val d = LampsTeacher.deduction(now) ?: error("no step on an unsolved board")
        assertFalse("unexpected mistake", d.mistake)
        onStep(d.technique)
        val lamp = LampsTeacher.teach(now)!!.value == LampsLogic.LAMP
        for (t in d.targets) assertEquals("${d.technique} is wrong at $t", lamp, s.solution[t] == '1')
        val next = d.apply(now) as LampsState
        assertNotEquals("a step must change something", now.marks, next.marks)
        assertTrue(d.isReached(next))
        next
    }

    @Test
    fun `coverage and fallback rate per tier`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { LampsTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var fellBack = 0
            var steps = 0
            val firstMs = ArrayList<Double>()
            for (day in 0 until 200) {
                var used = false
                val b = board(day, d)
                val t = System.nanoTime()
                LampsTeacher.teach(b)
                firstMs += (System.nanoTime() - t) / 1e6
                walk(b) { tech -> counts[tech] = counts.getValue(tech) + 1; steps++; if (tech == LampsTeacher.FALLBACK) used = true }
                if (used) fellBack++
            }
            firstMs.sort()
            val perBoard = counts.entries.joinToString { (k, v) -> "$k ${"%.1f".format(v / 200.0)}" }
            val line = "${d.name}: boards fell back $fellBack/200, steps ${"%.1f".format(steps / 200.0)}/board ($perBoard); " +
                "first hint ms median ${"%.2f".format(firstMs[100])} p95 ${"%.2f".format(firstMs[190])} max ${"%.2f".format(firstMs.last())}"
            println(line)
            report.appendLine(line)
            assertEquals("${d.name} fell back", 0, fellBack)
        }
        writeReport("lamps-teaching-coverage.txt", report.toString())
    }

    @Test
    fun `steps stay sound from boards a player made`() {
        val rng = Rng(7)
        for (d in Difficulty.entries) for (day in 0 until 40) {
            val s = board(day, d)
            var now = s
            // Some correct lamps and some arbitrary crosses (notes, even wrong ones), in any order.
            val lamps = s.solution.indices.filter { s.solution[it] == '1' }
            for (i in rng.shuffled(lamps).take(rng.nextInt(lamps.size + 1))) now = now.withMark(i, LampsLogic.LAMP_MARK)
            for (i in rng.shuffled(s.walls.indices.filter { s.walls[it] == '.' && now.marks[it] == '.' }).take(rng.nextInt(12))) {
                now = now.withMark(i, LampsLogic.CROSS)
            }
            if (now.solved) continue
            val step = LampsTeacher.deduction(now)!!
            assertFalse(step.mistake)
            assertFalse("${d.name} day $day fell back from a correct partial board", step.fallback)
            walk(now)
        }
    }

    @Test
    fun `a wrong lamp is addressed first and a right one is never called wrong`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val right = s.solution.indices.filter { s.solution[it] == '1' }
            val half = right.take(right.size / 2).fold(s) { a, i -> a.withMark(i, LampsLogic.LAMP_MARK) }
            assertFalse(LampsTeacher.deduction(half)?.mistake ?: false)
            val at = rng.pick(s.walls.indices.filter { s.walls[it] == '.' && s.solution[it] == '0' })
            val bad = half.withMark(at, LampsLogic.LAMP_MARK)
            val step = LampsTeacher.deduction(bad)!!
            assertTrue("${d.name} day $day did not flag the wrong lamp", step.mistake)
            assertEquals(setOf(at), step.targets)
            assertFalse(step.isReached(bad))
            assertTrue(step.isReached(bad.withMark(at, LampsLogic.NONE)))
            assertEquals(LampsLogic.NONE, (step.apply(bad) as LampsState).marks[at])
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = 0
        for (d in Difficulty.entries) for (day in 0 until 365) {
            val s = board(day, d)
            val at = s.walls.indices.first { s.walls[it] == '.' && s.solution[it] == '0' }
            for (state in listOf(s, s.withMark(at, LampsLogic.LAMP_MARK))) {
                hintWalk(state, 400) { cur ->
                    val step = LampsTeacher.teach(cur)!!
                    assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= LampsTeacher.MAX_NUDGE)
                    assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= LampsTeacher.MAX_EXPLANATION)
                    longest = maxOf(longest, step.explanation.length)
                    LampsTeacher.deduction(cur)!!.apply(cur) as LampsState
                }
            }
        }
        println("longest explanation: $longest")
    }

    @Test
    fun `the reasoning cannot see the answer`() {
        for (day in 0 until 5) {
            val s = board(day, Difficulty.EXPERT)
            val blind = s.copy(solution = "0".repeat(s.solution.length))
            val a = LampsTeacher.teach(s)!!
            val b = LampsTeacher.teach(blind)!!
            assertEquals(a.explanation, b.explanation)
            assertEquals(a.targets, b.targets)
        }
    }

    /** Boards with several answers: every non-fallback step must hold in every answer that keeps the player's lamps. */
    @Test
    fun `steps hold in every answer of boards that have several`() {
        val rng = Rng(3)
        var boards = 0
        var steps = 0
        repeat(400) {
            val n = 5 + rng.nextInt(2)
            val w = CharArray(n * n) { if (rng.nextInt(4) == 0) '#' else '.' }
            val walls0 = w.concatToString()
            val lamp = BooleanArray(n * n)
            for (i in rng.shuffled((0 until n * n).filter { w[it] == '.' })) if (!LampsLogic.lit(n, walls0, lamp)[i]) lamp[i] = true
            for (i in 0 until n * n) if (w[i] == '#' && rng.nextBoolean()) w[i] = '0' + LampsLogic.orth(n, i).count { lamp[it] }
            val walls = w.concatToString()
            val all = LampsOracle.solutions(n, walls, limit = 40)
            if (all.size < 2 || all.size == 40) return@repeat
            boards++
            hintWalk(LampsState(n, walls, all[0]), 100) { now ->
                val step = LampsTeacher.deduce(n, walls, now.marks) ?: return@hintWalk null
                val keep = all.filter { a -> now.marks.indices.all { now.marks[it] != LampsLogic.LAMP_MARK || a[it] == '1' } }
                for (a in keep) for (t in step.targets) assertEquals("${step.technique} at $t on $walls", step.value == LampsLogic.LAMP, a[t] == '1')
                steps++
                step.targets.fold(now) { acc, t -> acc.withMark(t, if (step.value == LampsLogic.LAMP) LampsLogic.LAMP_MARK else LampsLogic.CROSS) }
            }
        }
        println("multi-answer check: $boards boards, $steps steps")
        assertTrue("too little checked: $boards boards, $steps steps", boards > 50 && steps > 200)
    }
}
