package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.KingsTeacher
import com.joebywan.daybook.puzzles.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The hint solver, checked against rules and a brute-force enumerator written out again here.
 *
 * Soundness is checked two ways. On real boards, every step must agree with the stored answer. That
 * alone cannot tell reasoning from peeking, because a unique board makes every true fact derivable
 * — so the solver is also walked over boards with *several* answers, where a step is only sound if
 * it holds for every answer the visible board still allows. A technique that quietly assumed
 * uniqueness, or anything that leaked the stored answer, would cross out a square some other answer
 * needs.
 */
class KingsTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String = "kings-teach") = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    // ---- independent rules --------------------------------------------------------------------

    private fun touching(n: Int, a: Int, b: Int) =
        kotlin.math.abs(a / n - b / n) <= 1 && kotlin.math.abs(a % n - b % n) <= 1

    /** Every legal placement, by plain enumeration of one column per row. */
    private fun allSolutions(n: Int, region: List<Int>, cap: Int = 5000): List<Set<Int>> {
        val out = mutableListOf<Set<Int>>()
        val cols = IntArray(n)
        fun walk(row: Int) {
            if (out.size >= cap) return
            if (row == n) {
                val kings = (0 until n).map { it * n + cols[it] }
                if (kings.map { region[it] }.toSet().size == n) out += kings.toSet()
                return
            }
            for (c in 0 until n) {
                if ((0 until row).any { cols[it] == c }) continue
                if (row > 0 && kotlin.math.abs(cols[row - 1] - c) <= 1) continue
                cols[row] = c
                walk(row + 1)
            }
        }
        walk(0)
        return out
    }

    /** Squares a king on [k] rules out, re-derived rather than borrowed from KingsState. */
    private fun ruledOutBy(n: Int, region: List<Int>, k: Int): Set<Int> =
        (0 until n * n).filter { it / n == k / n || it % n == k % n || region[it] == region[k] || touching(n, it, k) }
            .toSet()

    private fun openCells(s: KingsState): Set<Int> {
        val kings = s.marks.indices.filter { s.marks[it] == Mark.KING }
        val out = kings.flatMap { ruledOutBy(s.size, s.region, it) }.toSet()
        return s.marks.indices.filter { s.marks[it] == Mark.EMPTY && it !in out }.toSet()
    }

    private fun kingsOf(s: KingsState) = s.marks.indices.filter { s.marks[it] == Mark.KING }.toSet()

    // ---- soundness on real boards ---------------------------------------------------------------

    @Test
    fun `every step on a real board agrees with the answer and changes something open`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty)) {
                var s = Kings.generate(seed, difficulty) as KingsState
                var guard = 0
                while (!s.solved) {
                    assertTrue("kings/$difficulty/$seed: walk did not finish", guard++ < 400)
                    val d = Kings.teach(s)
                    assertNotNull("kings/$difficulty/$seed: no hint on an unsolved board", d)
                    d!!
                    assertFalse("kings/$difficulty/$seed: a mistake on a board built from hints", d.mistake)
                    val step = KingsTeacher.teach(s)!!
                    val open = openCells(s)
                    assertTrue("$difficulty/$seed ${d.technique}: crowned outside the answer", s.solution.containsAll(step.kings))
                    assertTrue("$difficulty/$seed ${d.technique}: crossed an answer square", step.crosses.none { it in s.solution })
                    assertTrue("$difficulty/$seed ${d.technique}: changed a closed square", (step.kings + step.crosses).all { it in open })
                    assertTrue("$difficulty/$seed ${d.technique}: a step with no move", step.targets.isNotEmpty())
                    if (d.technique == KingsTeacher.LAST_SQUARE) {
                        // The claim, re-checked: the house it points at has exactly that one square open.
                        val houseOpen = d.focus.filter { it in open }
                        assertEquals("$difficulty/$seed last square is not the last", step.kings, houseOpen)
                        assertTrue("$difficulty/$seed last square in a finished house", d.focus.none { s.marks[it] == Mark.KING })
                    }
                    val next = d.apply(s) as KingsState
                    assertTrue("$difficulty/$seed ${d.technique}: applying it did not reach it", d.isReached(next))
                    assertFalse("$difficulty/$seed ${d.technique}: reached before it was made", d.isReached(s))
                    s = next
                }
            }
        }
    }

    @Test
    fun `hints stay sound from boards a player made, not just from the solver's own path`() {
        val rng = java.util.Random(7)
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "kings-teach-played")) {
                val fresh = Kings.generate(seed, difficulty) as KingsState
                val n = fresh.size
                // Some of the answer's kings, and some crosses the answer agrees with.
                val kings = fresh.solution.filter { rng.nextInt(3) == 0 }.toSet()
                val crosses = (0 until n * n).filter { it !in fresh.solution && rng.nextInt(4) == 0 }.toSet()
                val s = fresh.copy(marks = List(n * n) {
                    when (it) { in kings -> Mark.KING; in crosses -> Mark.BLOCKED; else -> Mark.EMPTY }
                })
                if (s.solved) continue
                val step = KingsTeacher.teach(s)!!
                assertTrue("$difficulty/$seed: a correct board was called a mistake", step.technique != KingsTeacher.MISTAKE)
                assertTrue("$difficulty/$seed ${step.technique}: crowned outside the answer", s.solution.containsAll(step.kings))
                assertTrue("$difficulty/$seed ${step.technique}: crossed an answer square", step.crosses.none { it in s.solution })
            }
        }
    }

    // ---- soundness from sight alone -------------------------------------------------------------

    /** Regions grown by flood from random seeds, with no attempt at a unique answer. */
    private fun looseBoard(rng: java.util.Random, n: Int): List<Int> {
        val region = MutableList(n * n) { -1 }
        val starts = (0 until n * n).shuffled(rng).take(n)
        starts.forEachIndexed { id, cell -> region[cell] = id }
        while (region.any { it == -1 }) {
            val cell = (0 until n * n).filter { region[it] == -1 }.shuffled(rng).first { c ->
                listOf(c - n, c + n, c - 1, c + 1).any { m ->
                    m in 0 until n * n && region[m] != -1 && (m / n == c / n || m % n == c % n)
                }
            }
            val hosts = listOf(cell - n, cell + n, cell - 1, cell + 1).filter { m ->
                m in 0 until n * n && region[m] != -1 && (m / n == cell / n || m % n == cell % n)
            }
            region[cell] = region[hosts[rng.nextInt(hosts.size)]]
        }
        return region
    }

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        val rng = java.util.Random(11)
        var boards = 0
        var steps = 0
        while (boards < 150) {
            val n = 5 + rng.nextInt(3)
            val region = looseBoard(rng, n)
            val answers = allSolutions(n, region)
            if (answers.size < 2) continue
            boards++
            var marks = List(n * n) { Mark.EMPTY }
            while (true) {
                val kings = marks.indices.filter { marks[it] == Mark.KING }.toSet()
                val crosses = marks.indices.filter { marks[it] == Mark.BLOCKED }.toSet()
                val alive = answers.filter { it.containsAll(kings) && it.none { c -> c in crosses } }
                assertTrue("a sound walk left no answer standing", alive.isNotEmpty())
                // Deliberately no answer passed: this is the entry point that cannot see one.
                val step = KingsTeacher.deduce(n, region, marks) ?: break
                steps++
                for (k in step.kings) {
                    assertTrue("${step.technique} crowned $k, which some remaining answer leaves empty", alive.all { k in it })
                }
                for (x in step.crosses) {
                    assertTrue("${step.technique} crossed $x, which some remaining answer crowns", alive.none { x in it })
                }
                marks = marks.mapIndexed { i, m ->
                    when (i) { in step.kings -> Mark.KING; in step.crosses -> Mark.BLOCKED; else -> m }
                }
            }
        }
        assertTrue("the walk barely stepped, so it proved little: $steps", steps > boards)
    }

    // ---- coverage -----------------------------------------------------------------------------

    /**
     * Not a correctness check but a measurement, printed and written to
     * `app/build/reports/kings-teaching-coverage.txt`: how often each technique is what a player
     * needs next, and how often the what-if and the fallback are reached, per tier. The assertions
     * only pin the walk finishing and the fallback staying under a tenth of boards; the numbers are the point.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 500
        val report = StringBuilder()
        report.appendLine("Kings teaching coverage: $perTier boards per tier, walked from empty by hints alone")
        for (difficulty in Difficulty.entries) {
            val stepCounts = KingsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = KingsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var totalSteps = 0
            for (seed in seeds(perTier, difficulty, "kings-coverage")) {
                var s = Kings.generate(seed, difficulty) as KingsState
                val used = mutableSetOf<String>()
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 400)
                    val d = Kings.teach(s)!!
                    stepCounts[d.technique] = stepCounts.getValue(d.technique) + 1
                    used += d.technique
                    totalSteps++
                    s = d.apply(s) as KingsState
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${difficulty.name} — $totalSteps steps, ${"%.1f".format(totalSteps / perTier.toDouble())} per board")
            for (t in KingsTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-18s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / totalSteps,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
            val fallbackBoards = boardCounts.getValue(KingsTeacher.FALLBACK)
            // Measured at 0% / 0% / ~1% of boards when this was written. A tenth would mean the
            // techniques had stopped covering the boards the generator makes.
            assertTrue(
                "$difficulty: the fallback is reached on $fallbackBoards/$perTier boards",
                fallbackBoards * 10 < perTier,
            )
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/kings-teaching-coverage.txt").writeText(report.toString())
    }

    // ---- mistakes -------------------------------------------------------------------------------

    private fun tutorialBoard(kings: Set<Int> = emptySet(), crosses: Set<Int> = emptySet()) = KingsState(
        5, Kings.TUTORIAL_REGIONS,
        List(25) { when (it) { in kings -> Mark.KING; in crosses -> Mark.BLOCKED; else -> Mark.EMPTY } },
        Kings.TUTORIAL_SOLUTION,
    )

    @Test
    fun `a wrong king is addressed first, and why it is wrong is explained when it can be`() {
        // r0c1 rules out both blue squares: one shares its row, the other touches it.
        val s = tutorialBoard(kings = setOf(1))
        val d = Kings.teach(s)!!
        assertTrue(d.mistake)
        assertEquals(setOf(1), d.targets)
        assertTrue("explanation should name blue: ${d.explanation}", "blue" in d.explanation)
        assertTrue("the cited squares should include blue", d.cited.containsAll(setOf(2, 7)))
        val fixed = d.apply(s) as KingsState
        assertEquals(Mark.EMPTY, fixed.marks[1])
        assertTrue(d.isReached(fixed))
        assertFalse(d.isReached(s))
    }

    @Test
    fun `a wrong king on a real board outranks every step, and taking it back clears it`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(20, difficulty, "kings-mistake")) {
                val fresh = Kings.generate(seed, difficulty) as KingsState
                val wrong = fresh.marks.indices.first { it !in fresh.solution }
                val s = fresh.toggleKing(wrong)
                val d = Kings.teach(s)!!
                assertTrue("$difficulty/$seed: wrong king not flagged", d.mistake)
                assertEquals(setOf(wrong), d.targets)
                assertTrue("$difficulty/$seed: no explanation", d.explanation.isNotBlank())
                assertTrue(d.isReached(s.toggleKing(wrong)))
            }
        }
    }

    @Test
    fun `a cross on an answer square is flagged, with the last-square reason when it is one`() {
        // Purple is {0, 1}; with 1 crossed, crossing 0 as well takes purple's last square.
        val s = tutorialBoard(crosses = setOf(0, 1))
        val d = Kings.teach(s)!!
        assertTrue(d.mistake)
        assertEquals(setOf(0), d.targets)
        assertTrue("should say it is purple's last square: ${d.explanation}", "purple" in d.explanation)
        assertTrue(d.isReached(s.toggleMark(0)))

        for (difficulty in Difficulty.entries) {
            for (seed in seeds(20, difficulty, "kings-mistake-cross")) {
                val fresh = Kings.generate(seed, difficulty) as KingsState
                val target = fresh.solution.min()
                val crossed = fresh.toggleMark(target)
                val m = Kings.teach(crossed)!!
                assertTrue("$difficulty/$seed: wrong cross not flagged", m.mistake)
                assertEquals(setOf(target), m.targets)
                assertEquals(Mark.EMPTY, (m.apply(crossed) as KingsState).marks[target])
            }
        }
    }

    @Test
    fun `a cross the answer agrees with is never called a mistake`() {
        val s = tutorialBoard(crosses = setOf(1, 12))
        assertFalse(Kings.teach(s)!!.mistake)
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val answers = allSolutions(5, Kings.TUTORIAL_REGIONS)
        assertEquals(listOf(Kings.TUTORIAL_SOLUTION), answers)
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Kings.tutorial
        assertEquals(7, frames.size)
        fun board(i: Int) = frames[i].state as KingsState

        // Explanatory frames take no move.
        listOf(0, 1, 4).forEach { assertEquals("frame ${it + 1} should be Next-only", null, frames[it].accepts) }

        // 3: a single tap on r0c1.
        val tap = frames[2].accepts!!
        assertTrue(tap(board(2).toggleMark(1)))
        assertFalse("a tap elsewhere", tap(board(2).toggleMark(3)))
        assertFalse("a crown instead of a cross", tap(board(2).toggleKing(1)))
        assertEquals("frame 4 continues from frame 3's move", board(3).marks, board(2).toggleMark(1).marks)

        // 4: a double tap on r0c0 (the board emits one toggleKing for the pair).
        val crown = frames[3].accepts!!
        assertTrue(crown(board(3).toggleKing(0)))
        assertFalse("a single tap", crown(board(3).toggleMark(0)))
        assertFalse("a crown elsewhere", crown(board(3).toggleKing(7)))
        assertEquals(board(4).marks, board(3).toggleKing(0).marks)

        // 6: one sweep down column 3.
        val sweep = frames[5].accepts!!
        assertTrue(sweep(board(5).paint(listOf(12, 17, 22), Mark.BLOCKED)))
        assertFalse("one square of the three", sweep(board(5).toggleMark(12)))
        assertFalse("a sweep through blue", sweep(board(5).paint(listOf(7, 12, 17, 22), Mark.BLOCKED)))
        assertEquals(board(6).marks, board(5).paint(listOf(12, 17, 22), Mark.BLOCKED).marks)

        // Each explained move is actually true on its board.
        assertTrue(1 !in Kings.TUTORIAL_SOLUTION)
        assertEquals("purple has one open square in frame 4", setOf(0), openCells(board(3)).filter { Kings.TUTORIAL_REGIONS[it] == 0 }.toSet())
        assertTrue(listOf(12, 17, 22).none { it in Kings.TUTORIAL_SOLUTION })

        // 7: free play, finishable by hints without the fallback.
        assertTrue(frames[6].freePlay)
        var s = board(6)
        while (!s.solved) {
            val d = Kings.teach(s)!!
            assertFalse("the last frame should not need the fallback", d.fallback)
            s = d.apply(s) as KingsState
        }
        assertEquals(Kings.TUTORIAL_SOLUTION, kingsOf(s))
    }
}
