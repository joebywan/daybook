package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.ReckonLogic
import com.joebywan.daybook.puzzles.ReckonState
import com.joebywan.daybook.puzzles.ReckonTeacher
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReckonTeachingTest {

    private fun daily(day: Int, d: Difficulty) = ReckonLogic.generateVerified(daySeed(day, "reckon", d), d)!!
    private fun random(k: Int, d: Difficulty) = ReckonLogic.generateVerified(daySeed(k, "reckon-teaching", d), d)!!

    /** The slowest single hint so far, in ms. */
    private var slowest = 0.0

    private fun apply(s: ReckonState, step: PlacementStep) = s.withCell(step.cell, step.digit).select(step.cell)

    /** Follows the hints from [s] to the end. Fails on a step that disagrees with the answer or changes nothing. */
    private fun walk(s: ReckonState, onStep: (String) -> Unit = {}): ReckonState {
        var now = s
        repeat(40) {
            if (now.solved) return now
            val t0 = System.nanoTime()
            val step = ReckonTeacher.teach(now) ?: error("no step on an unsolved board")
            slowest = maxOf(slowest, (System.nanoTime() - t0) / 1e6)
            onStep(step.technique)
            val next = apply(now, step)
            if (step.technique != ReckonTeacher.MISTAKE) {
                assertEquals("${step.technique} put the wrong digit in square ${step.cell}", s.solution[step.cell], next.cells[step.cell])
            }
            assertTrue("a step must change something", next.cells != now.cells)
            now = next
        }
        assertTrue("the walk did not finish", now.solved)
        return now
    }

    /**
     * From blank by hints alone, on 200 random boards and a year of daily boards a tier: never the fallback, and the tier's
     * gate seen from the hints. Counts per technique go to `build/reports/reckon-teaching-coverage.txt`.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val boards = List(200) { random(it, d) } + List(365) { daily(it, d) }
            val counts = linkedMapOf<String, Int>().apply { ReckonTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var fellBack = 0
            var steps = 0
            var needWhatIf = 0
            var needLineOrPair = 0
            slowest = 0.0
            for (s in boards) {
                val used = mutableSetOf<String>()
                walk(s) { t -> counts[t] = counts.getValue(t) + 1; steps++; used += t }
                if (ReckonTeacher.FALLBACK in used) fellBack++
                if (ReckonTeacher.WHAT_IF in used) needWhatIf++
                if (ReckonTeacher.CAGE_LINE in used || ReckonTeacher.NAKED_PAIR in used) needLineOrPair++
            }
            val perBoard = counts.entries.joinToString(", ") { "${it.key} %.2f".format(it.value / boards.size.toDouble()) }
            val line = "${d.name}: ${boards.size} boards, fell back on $fellBack, ${"%.1f".format(steps / boards.size.toDouble())} steps a board; " +
                "a board: $perBoard; slowest hint %.1f ms".format(slowest)
            println(line)
            report.appendLine(line)
            assertEquals("${d.name} fell back", 0, fellBack)
            when (d) {
                Difficulty.STANDARD -> assertEquals(0, counts.getValue(ReckonTeacher.CAGE_LINE) + counts.getValue(ReckonTeacher.NAKED_PAIR) + needWhatIf)
                Difficulty.HARD -> { assertEquals(0, needWhatIf); assertEquals(boards.size, needLineOrPair) }
                Difficulty.EXPERT -> assertEquals(boards.size, needWhatIf)
            }
        }
        writeReport("reckon-teaching-coverage.txt", report.toString())
    }

    @Test
    fun `steps stay sound from boards a player made, not only the solver's path`() {
        val rng = Rng(7)
        for (d in Difficulty.entries) for (day in 0 until 40) {
            val s = daily(day, d)
            val open = rng.shuffled(s.cells.indices.filter { s.cells[it] == 0 })
            var now = s
            for (i in open.take(rng.nextInt(open.size + 1))) now = now.withCell(i, s.solution[i])
            if (now.solved) continue
            val step = ReckonTeacher.teach(now)!!
            assertFalse(step.technique == ReckonTeacher.MISTAKE)
            assertFalse("${d.name} day $day fell back from a correct partial board", step.technique == ReckonTeacher.FALLBACK)
            walk(now)
        }
    }

    @Test
    fun `a wrong digit is addressed first, and a right one is never called wrong`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = daily(day, d)
            val open = s.cells.indices.filter { s.cells[it] == 0 }
            val allRight = open.fold(s) { acc, i -> acc.withCell(i, s.solution[i]) }
            assertTrue(allRight.solved)
            assertNull(ReckonTeacher.teach(allRight))
            val half = open.take(open.size / 2).fold(s) { acc, i -> acc.withCell(i, s.solution[i]) }
            assertFalse(ReckonTeacher.teach(half)?.technique == ReckonTeacher.MISTAKE)
            val at = rng.pick(open)
            val bad = half.withCell(at, rng.pick((1..6).filter { it != s.solution[at] }))
            val step = ReckonTeacher.teach(bad)!!
            assertEquals("${d.name} day $day", ReckonTeacher.MISTAKE, step.technique)
            assertEquals(at, step.cell)
            assertEquals(0, step.digit)
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = ""
        for (d in Difficulty.entries) for (day in 0 until 120) {
            val s = daily(day, d)
            val at = s.cells.indices.first { s.cells[it] == 0 }
            for (start in listOf(s, s.withCell(at, s.solution[at] % 6 + 1))) {
                var now = start
                while (!now.solved) {
                    val step = ReckonTeacher.teach(now)!!
                    assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= 70)
                    assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= ReckonTeacher.MAX_EXPLANATION)
                    if (step.explanation.length > longest.length) longest = step.explanation
                    now = apply(now, step)
                }
            }
        }
        println("longest explanation (${longest.length}): $longest")
    }

    @Test
    fun `cage hints show the combinations, and the shape's repeat rule where it bites`() {
        val seen = mutableMapOf<String, String>()
        for (d in Difficulty.entries) for (day in 0 until 60) {
            var now = daily(day, d)
            while (!now.solved) {
                val step = ReckonTeacher.teach(now)!!
                if (step.technique !in seen) seen[step.technique] = step.explanation
                if (step.explanation.contains("would repeat")) seen.putIfAbsent("repeat", step.explanation)
                now = apply(now, step)
            }
        }
        seen.forEach { (k, v) -> println("$k: $v") }
        assertTrue(seen.getValue(ReckonTeacher.CAGE_DIGIT).contains("·") || seen.getValue(ReckonTeacher.CAGE_DIGIT).contains("cage"))
        assertTrue("a hint names a set the shape rules out", "repeat" in seen)
        for (t in ReckonTeacher.TECHNIQUES - ReckonTeacher.FALLBACK) assertTrue("$t never seen", t in seen)
    }

    @Test
    fun `the teacher cannot see the answer`() {
        val s = daily(5, Difficulty.EXPERT)
        val a = ReckonTeacher.deduce(s.size, s.cells, s.cageOf, s.ops, s.targets)!!
        assertEquals(a.explanation, ReckonTeacher.teach(s.copy(solution = List(36) { 1 }))!!.explanation)
        assertEquals(s.solution[a.cell], a.digit)
    }

    /**
     * Uniqueness cannot tell reasoning from peeking, so: random 4x4 boards with several answers (by [ReckonOracle]),
     * walked by the teacher's reasoning alone, every step agreeing with every answer the board still allows.
     */
    @Test
    fun `steps hold for every answer still possible, on boards with several`() {
        var boards = 0
        var steps = 0
        for (seed in 1L..600L) {
            val rng = Rng(seed)
            val n = 4
            val solution = com.joebywan.daybook.puzzles.InequalityLogic.randomSquare(n, rng)
            val cageOf = ReckonLogic.carve(n, ReckonLogic.Spec(3, 0..1, 0, 0), rng) ?: continue
            val (ops, targets) = ReckonLogic.operations(n, cageOf, solution, rng) ?: continue
            var cells = List(n * n) { if (ops[cageOf[it]] == ReckonLogic.GIVEN) solution[it] else 0 }
            val answers = ReckonOracle.solutions(n, cells, cageOf, ops, targets, limit = 10_000)
            if (answers.size < 2) continue
            boards++
            while (true) {
                val step = ReckonTeacher.deduce(n, cells, cageOf, ops, targets) ?: break
                val still = answers.filter { a -> cells.indices.all { cells[it] == 0 || cells[it] == a[it] } }
                assertTrue("seed $seed ${step.technique}: square ${step.cell} = ${step.digit}", still.isNotEmpty() && still.all { it[step.cell] == step.digit })
                steps++
                cells = cells.toMutableList().also { it[step.cell] = step.digit }
            }
        }
        println("several-answer boards $boards, steps checked $steps")
        assertTrue("boards $boards, steps $steps", boards >= 50 && steps >= 100)
    }

    @Test
    fun `a part-played board survives a save, notes and selection included`() {
        val s = daily(3, Difficulty.HARD)
        val open = s.cells.indices.filter { s.cells[it] == 0 }
        val played = s.select(open[0]).withCell(open[0], s.solution[open[0]]).toggleNote(open[1], 2).toggleNote(open[1], 5)
        val json = Json.encodeToString(ReckonState.serializer(), played)
        assertEquals(played, Json.decodeFromString(ReckonState.serializer(), json))
        assertFalse("solved is computed, never written", json.contains("solved"))
    }
}
