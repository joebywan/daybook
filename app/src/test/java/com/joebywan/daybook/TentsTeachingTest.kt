package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.TentsState
import com.joebywan.daybook.puzzles.TentsTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class TentsTeachingTest {

    private val start = LocalDate.of(2026, 1, 1)
    private fun board(day: Int, d: Difficulty) =
        TentsLogic.generateVerified(DailySeed.seedFor(start.plusDays(day.toLong()), "tents", d), d)
            ?: error("no proved board for day $day ${d.name}")

    /** Every step's conclusion must hold in the (unique) answer; applies each until solved. */
    private fun walk(s: TentsState, onStep: (String) -> Unit = {}): TentsState {
        var now = s
        var guard = 0
        while (!now.solved) {
            val d = TentsTeacher.deduction(now) ?: error("no step on an unsolved board")
            assertFalse("unexpected mistake", d.mistake)
            onStep(d.technique)
            val tent = TentsTeacher.teach(now)!!.value == TentsLogic.TENT
            for (t in d.targets) assertEquals("${d.technique} is wrong at $t", tent, s.solution[t])
            val next = d.apply(now) as TentsState
            assertNotEquals("a step must change something", now.marks, next.marks)
            assertTrue(d.isReached(next))
            now = next
            assertTrue("walk does not end", ++guard <= 400)
        }
        return now
    }

    @Test
    fun `coverage and fallback rate per tier`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { TentsTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var fellBack = 0
            var steps = 0
            for (day in 0 until 200) {
                var used = false
                walk(board(day, d)) { t -> counts[t] = counts.getValue(t) + 1; steps++; if (t == TentsTeacher.FALLBACK) used = true }
                if (used) fellBack++
            }
            val line = "${d.name}: boards fell back $fellBack/200, steps $steps (${steps / 200.0}/board), $counts"
            println(line)
            report.appendLine(line)
            assertEquals("${d.name} fell back", 0, fellBack)
        }
        File("build/reports").mkdirs()
        File("build/reports/tents-teaching-coverage.txt").writeText(report.toString())
    }

    @Test
    fun `every sampled answer is the only one, by the independent oracle`() {
        for ((d, days) in listOf(Difficulty.STANDARD to 100, Difficulty.HARD to 20, Difficulty.EXPERT to 4)) for (day in 0 until days) {
            val s = board(day, d)
            assertEquals(1, TentsOracle.count(s.size, s.trees, s.rowCounts, s.colCounts))
        }
    }

    @Test
    fun `steps stay sound from boards a player made`() {
        val rng = Rng(7)
        for (d in Difficulty.entries) for (day in 0 until 40) {
            val s = board(day, d)
            var now = s
            // Some correct tents and some arbitrary crosses (notes), in any order.
            for (i in s.solution.indices.filter { s.solution[it] }.let { rng.shuffled(it) }.take(rng.nextInt(s.solution.count { it } + 1))) now = now.withMark(i, TentsLogic.TENT)
            for (i in s.trees.indices.filter { !s.trees[it] && !s.solution[it] }.let { rng.shuffled(it) }.take(rng.nextInt(10))) now = now.withMark(i, TentsLogic.GRASS)
            if (now.solved) continue
            val step = TentsTeacher.deduction(now)!!
            assertFalse(step.mistake)
            assertFalse("${d.name} day $day fell back from a correct partial board", step.fallback)
            walk(now)
        }
    }

    @Test
    fun `a wrong tent is addressed first and a right one is never called wrong`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val right = s.solution.indices.filter { s.solution[it] }
            val half = right.take(right.size / 2).fold(s) { a, i -> a.withMark(i, TentsLogic.TENT) }
            assertFalse(TentsTeacher.deduction(half)?.mistake ?: false)
            val at = rng.pick(s.trees.indices.filter { !s.trees[it] && !s.solution[it] })
            val bad = half.withMark(at, TentsLogic.TENT)
            val step = TentsTeacher.deduction(bad)!!
            assertTrue("${d.name} day $day did not flag the wrong tent", step.mistake)
            assertEquals(setOf(at), step.targets)
            assertFalse(step.isReached(bad))
            assertTrue(step.isReached(bad.withMark(at, 0)))
            assertEquals(0, (step.apply(bad) as TentsState).marks[at])
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = 0
        for (d in Difficulty.entries) for (day in 0 until 365) {
            val s = board(day, d)
            val at = s.trees.indices.first { !s.trees[it] && !s.solution[it] }
            for (state in listOf(s, s.withMark(at, TentsLogic.TENT))) {
                var cur = state
                var guard = 0
                while (!cur.solved && guard++ < 400) {
                    val step = TentsTeacher.teach(cur)!!
                    assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= TentsTeacher.MAX_NUDGE)
                    assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= TentsTeacher.MAX_EXPLANATION)
                    longest = maxOf(longest, step.explanation.length)
                    cur = TentsTeacher.deduction(cur)!!.apply(cur) as TentsState
                }
            }
        }
        println("longest explanation: $longest")
    }

    @Test
    fun `the reasoning cannot see the answer`() {
        val s = board(5, Difficulty.EXPERT)
        val blind = s.copy(solution = List(s.solution.size) { false })
        val a = TentsTeacher.teach(s)!!
        val b = TentsTeacher.teach(blind)!!
        assertEquals(a.explanation, b.explanation)
        assertEquals(a.targets, b.targets)
    }

    /** Boards with several answers: every non-fallback step must hold in every answer. */
    @Test
    fun `steps hold in every answer of boards that have several`() {
        val rng = Rng(3)
        val n = 6
        var boards = 0
        var steps = 0
        repeat(300) {
            val trees = BooleanArray(n * n)
            for (i in rng.shuffled((0 until n * n).toList()).take(6)) trees[i] = true
            val treeIdx = trees.indices.filter { trees[it] }
            val answers = HashSet<Set<Int>>()
            val chosen = ArrayList<Int>()
            fun go(k: Int) {
                if (k == treeIdx.size) { answers += chosen.toSet(); return }
                for (sq in TentsLogic.orth(n, treeIdx[k])) {
                    if (trees[sq] || sq in chosen || chosen.any { TentsLogic.around(n, sq).contains(it) }) continue
                    chosen += sq; go(k + 1); chosen.removeAt(chosen.size - 1)
                }
            }
            go(0)
            val pick = answers.firstOrNull() ?: return@repeat
            val rows = List(n) { r -> pick.count { it / n == r } }
            val cols = List(n) { c -> pick.count { it % n == c } }
            val all = answers.filter { a -> List(n) { r -> a.count { it / n == r } } == rows && List(n) { c -> a.count { it % n == c } } == cols }
            val s = TentsState(n, trees.toList(), rows, cols, List(n * n) { it in pick })
            boards++
            var now = s
            var guard = 0
            while (!now.solved && guard++ < 100) {
                val step = TentsTeacher.teach(now)!!
                if (step.technique == TentsTeacher.FALLBACK) break
                assertNotNull(step)
                for (a in all) for (t in step.targets) assertEquals("${step.technique} at $t", step.value == TentsLogic.TENT, t in a)
                steps++
                now = TentsTeacher.deduction(now)!!.apply(now) as TentsState
            }
        }
        println("multi-answer check: $boards boards, $steps steps")
        assertTrue(steps > 100)
    }
}
