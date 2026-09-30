package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Link
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.MamboTeacher
import com.joebywan.daybook.puzzles.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The Mambo hint solver, checked against an enumerator written out again here on a different
 * principle from both the generator's (square by square, backtracking) and the teacher's (rules
 * applied locally): it lists every legal row up front and stacks whole rows, checking columns and
 * links only as rows land.
 *
 * As for Kings, soundness on a unique board cannot tell reasoning from peeking, so the solver is also
 * walked over boards with several answers, where a symbol is only sound if every answer still
 * standing has it.
 */
class MamboTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String = "mambo-teach") = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    // ---- independent rules --------------------------------------------------------------------

    /** Every row of length [n] with n/2 of each symbol and no three alike together. */
    private fun legalRows(n: Int): List<List<Sym>> = (0 until (1 shl n)).mapNotNull { bits ->
        if (bits.countOneBits() != n / 2) return@mapNotNull null
        val row = (0 until n).map { if (bits shr it and 1 == 1) Sym.SUN else Sym.MOON }
        if ((0 until n - 2).any { row[it] == row[it + 1] && row[it] == row[it + 2] }) null else row
    }

    /** Every answer consistent with [cells] and [links], up to [cap]. */
    private fun allSolutions(n: Int, cells: List<Sym>, links: List<Link>, cap: Int = 500): List<List<Sym>> {
        val rows = legalRows(n)
        val out = mutableListOf<List<Sym>>()
        val stack = mutableListOf<List<Sym>>()
        fun columnsOk(): Boolean {
            val depth = stack.size
            for (c in 0 until n) {
                val col = stack.map { it[c] }
                if (col.count { it == Sym.SUN } > n / 2 || col.count { it == Sym.MOON } > n / 2) return false
                if (depth >= 3 && col[depth - 1] == col[depth - 2] && col[depth - 1] == col[depth - 3]) return false
            }
            val flat = stack.flatten()
            return links.all { l -> l.a >= flat.size || l.b >= flat.size || (flat[l.a] == flat[l.b]) == l.same }
        }
        fun place(r: Int) {
            if (out.size >= cap) return
            if (r == n) {
                out += stack.flatten()
                return
            }
            for (row in rows) {
                if ((0 until n).any { c -> cells[r * n + c] != Sym.NONE && cells[r * n + c] != row[c] }) continue
                stack += row
                if (columnsOk()) place(r + 1)
                stack.removeAt(stack.lastIndex)
            }
        }
        place(0)
        return out
    }

    // ---- soundness on real boards ---------------------------------------------------------------

    @Test
    fun `every step on a real board agrees with the answer and fills a square`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty)) {
                var s = Mambo.generate(seed, difficulty) as MamboState
                var guard = 0
                while (!s.solved) {
                    assertTrue("mambo/$difficulty/$seed: walk did not finish", guard++ < 200)
                    val d = Mambo.teach(s)
                    assertNotNull("mambo/$difficulty/$seed: no hint on an unsolved board", d)
                    d!!
                    assertFalse("$difficulty/$seed: a mistake on a board built from hints", d.mistake)
                    val step = MamboTeacher.teach(s)!!
                    assertTrue("$difficulty/$seed ${d.technique}: a step with no move", step.places.isNotEmpty())
                    for ((i, sym) in step.places) {
                        assertEquals("$difficulty/$seed ${d.technique}: against the answer", s.solution[i], sym)
                        assertEquals("$difficulty/$seed ${d.technique}: a filled square", Sym.NONE, s.cells[i])
                    }
                    assertEquals(step.places.keys, d.targets)
                    assertTrue("$difficulty/$seed ${d.technique}: no explanation", d.explanation.isNotBlank())
                    val next = d.apply(s) as MamboState
                    assertTrue("$difficulty/$seed ${d.technique}: applying it did not reach it", d.isReached(next))
                    assertFalse("$difficulty/$seed ${d.technique}: reached before it was made", d.isReached(s))
                    s = next
                }
                assertEquals(s.solution, s.cells)
            }
        }
    }

    @Test
    fun `hints stay sound from boards a player made, not just from the solver's own path`() {
        val rng = java.util.Random(7)
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "mambo-teach-played")) {
                val fresh = Mambo.generate(seed, difficulty) as MamboState
                // A scatter of the answer's own symbols, in no order a solver would take.
                val cells = fresh.cells.indices.map { if (fresh.givens[it] || rng.nextInt(3) == 0) fresh.solution[it] else Sym.NONE }
                val s = fresh.copy(cells = cells)
                if (s.solved) continue
                val step = MamboTeacher.teach(s)!!
                assertTrue("$difficulty/$seed: a correct board was called a mistake", step.technique != MamboTeacher.MISTAKE)
                for ((i, sym) in step.places) assertEquals("$difficulty/$seed ${step.technique}", fresh.solution[i], sym)
            }
        }
    }

    // ---- soundness from sight alone -------------------------------------------------------------

    /**
     * A 6x6 board with no attempt at a unique answer: one legal grid, a random handful of its
     * squares shown, and a few of its links printed.
     */
    private fun looseBoard(rng: java.util.Random): Pair<List<Sym>, List<Link>>? {
        val n = 6
        // A random legal grid: a few random symbols pinned, then one of the grids that fit them.
        val pinned = MutableList(n * n) { Sym.NONE }
        repeat(4) { pinned[rng.nextInt(n * n)] = if (rng.nextBoolean()) Sym.SUN else Sym.MOON }
        val grid = allSolutions(n, pinned, emptyList(), cap = 20).shuffled(rng).firstOrNull() ?: return null
        val cells = grid.indices.map { if (rng.nextInt(100) < 18 + rng.nextInt(12)) grid[it] else Sym.NONE }
        val allLinks = buildList {
            for (r in 0 until n) for (c in 0 until n) {
                val i = r * n + c
                if (c + 1 < n) add(Link(i, i + 1, grid[i] == grid[i + 1]))
                if (r + 1 < n) add(Link(i, i + n, grid[i] == grid[i + n]))
            }
        }
        val links = allLinks.shuffled(rng).take(rng.nextInt(5))
        return cells to links
    }

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        val rng = java.util.Random(11)
        var boards = 0
        var steps = 0
        val seen = sortedMapOf<String, Int>()
        var longest = ""
        while (boards < 200) {
            val (start, links) = looseBoard(rng) ?: continue
            val answers = allSolutions(6, start, links)
            if (answers.size < 2) continue
            boards++
            var cells = start
            while (true) {
                val alive = answers.filter { ans -> ans.indices.all { cells[it] == Sym.NONE || cells[it] == ans[it] } }
                assertTrue("a sound walk left no answer standing", alive.isNotEmpty())
                // Deliberately no answer passed: this is the entry point that cannot see one.
                val step = MamboTeacher.deduce(6, cells, links) ?: break
                steps++
                seen[step.technique] = (seen[step.technique] ?: 0) + 1
                if (step.explanation.length > longest.length) longest = step.explanation
                for ((i, sym) in step.places) {
                    assertTrue(
                        "${step.technique} put a ${sym.name} on $i, which some remaining answer does not: ${step.explanation}",
                        alive.all { it[i] == sym },
                    )
                }
                cells = cells.mapIndexed { i, v -> step.places[i] ?: v }
            }
        }
        assertTrue("the walk barely stepped, so it proved little: $steps", steps > boards)
        assertTrue("the harder techniques were never exercised: $seen", (seen[MamboTeacher.ALMOST] ?: 0) > 0)
        assertTrue("the what-if was never exercised: $seen", (seen[MamboTeacher.WHAT_IF] ?: 0) > 0)
        assertTrue("an explanation too long for the panel: $longest", longest.length <= MamboTeacher.MAX_EXPLANATION)
        println("several-answer walk: $boards boards, $steps steps, $seen; longest ${longest.length}: $longest")
    }

    // ---- coverage -----------------------------------------------------------------------------

    /**
     * Not a correctness check but a measurement, printed and written to
     * `app/build/reports/mambo-teaching-coverage.txt`: how often each technique is what a player
     * needs next, and how often the what-if and the fallback are reached, per tier.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 500
        val report = StringBuilder()
        report.appendLine("Mambo teaching coverage: $perTier boards per tier, walked from the givens by hints alone")
        for (difficulty in Difficulty.entries) {
            val stepCounts = MamboTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = MamboTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var totalSteps = 0
            var longest = ""
            for (seed in seeds(perTier, difficulty, "mambo-coverage")) {
                var s = Mambo.generate(seed, difficulty) as MamboState
                val used = mutableSetOf<String>()
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 200)
                    val d = Mambo.teach(s)!!
                    if (d.explanation.length > longest.length) longest = d.explanation
                    stepCounts[d.technique] = stepCounts.getValue(d.technique) + 1
                    used += d.technique
                    totalSteps++
                    s = d.apply(s) as MamboState
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${difficulty.name} — $totalSteps steps, ${"%.1f".format(totalSteps / perTier.toDouble())} per board")
            for (t in MamboTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-17s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / totalSteps,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
            report.appendLine("  longest explanation, ${longest.length} characters: $longest")
            assertTrue("$difficulty: an explanation too long for the panel: $longest", longest.length <= MamboTeacher.MAX_EXPLANATION)
            // Every board is carved to fall to the simple rules, so the fallback should never show.
            assertEquals("$difficulty: the fallback was reached", 0, boardCounts.getValue(MamboTeacher.FALLBACK))
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/mambo-teaching-coverage.txt").writeText(report.toString())
    }

    // ---- mistakes -------------------------------------------------------------------------------

    private fun tutorialBoard(vararg placed: Pair<Int, Sym>): MamboState {
        val walk = Mambo.tutorial[2].state as MamboState
        return walk.copy(cells = walk.cells.toMutableList().also { c -> placed.forEach { (i, s) -> c[i] = s } })
    }

    @Test
    fun `a wrong moon that makes three is named, and one tap fixes it`() {
        // Row 4 holds given moons at columns 3, 4 and 6; a moon on column 5 makes four together.
        val s = tutorialBoard(22 to Sym.MOON)
        val d = Mambo.teach(s)!!
        assertTrue(d.mistake)
        assertEquals(setOf(22), d.targets)
        assertTrue("should name the rule: ${d.explanation}", "three moons together" in d.explanation)
        assertTrue("should say one tap: ${d.explanation}", "once" in d.explanation)
        assertTrue("should cite the run it makes: ${d.cited}", d.cited.isNotEmpty() && setOf(20, 21, 23).containsAll(d.cited))
        assertFalse(d.isReached(s))
        assertTrue("one tap turns it to the sun", d.isReached(s.withCell(22, Sym.SUN)))
        assertEquals(Sym.NONE, (d.apply(s) as MamboState).cells[22])
    }

    @Test
    fun `a wrong sun that breaks a link is named, and clearing it fixes it`() {
        // The x joins squares 7 and 8; 8 is a given sun, so a sun on 7 breaks it.
        val s = tutorialBoard(7 to Sym.SUN)
        val d = Mambo.teach(s)!!
        assertTrue(d.mistake)
        assertTrue("should name the link: ${d.explanation}", "The x says" in d.explanation)
        assertTrue("should say clear: ${d.explanation}", "clear" in d.explanation)
        assertTrue(d.isReached(s.withCell(7, Sym.NONE)))
    }

    @Test
    fun `a wrong symbol that breaks nothing yet is still caught first`() {
        var checked = 0
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(20, difficulty, "mambo-mistake")) {
                val fresh = Mambo.generate(seed, difficulty) as MamboState
                val i = fresh.cells.indices.first { !fresh.givens[it] }
                val s = fresh.withCell(i, fresh.solution[i].other())
                val d = Mambo.teach(s)!!
                assertTrue("$difficulty/$seed: wrong symbol not flagged", d.mistake)
                assertEquals(setOf(i), d.targets)
                assertTrue(d.explanation.length <= MamboTeacher.MAX_EXPLANATION)
                assertFalse(d.isReached(s))
                assertTrue(d.isReached(d.apply(s)))
                checked++
            }
        }
        assertEquals(60, checked)
    }

    @Test
    fun `a symbol the answer agrees with is never called a mistake`() {
        assertFalse(Mambo.teach(tutorialBoard(2 to Sym.MOON, 22 to Sym.SUN))!!.mistake)
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val start = Mambo.tutorial[2].state as MamboState
        assertEquals(listOf(Mambo.TUTORIAL_SOLUTION), allSolutions(6, start.cells, start.links))
        assertTrue(start.copy(cells = Mambo.TUTORIAL_SOLUTION).violations().isEmpty())
    }

    /** One tap on [i], through the same cycle the board's tap handler runs. */
    private fun MamboState.tap(i: Int): MamboState = withCell(
        i,
        when (cells[i]) {
            Sym.NONE -> Sym.MOON
            Sym.MOON -> Sym.SUN
            Sym.SUN -> Sym.NONE
        },
    )

    @Test
    fun `each walkthrough frame accepts its move, made by real taps, and rejects a wrong one`() {
        val frames = Mambo.tutorial
        assertEquals(8, frames.size)
        fun board(i: Int) = frames[i].state as MamboState
        listOf(0, 1).forEach { assertNull("frame ${it + 1} should be Next-only", frames[it].accepts) }
        assertTrue(board(0).solved)

        // Each interactive frame: the tap it asks for, the board the next frame starts from, and a
        // tap somewhere else that it must turn down.
        for ((frame, cell) in listOf(2 to 2, 3 to 7, 4 to 22, 5 to 22, 6 to 32)) {
            val accepts = frames[frame].accepts!!
            val tapped = board(frame).tap(cell)
            assertTrue("frame ${frame + 1} should accept a tap on $cell", accepts(tapped))
            assertEquals("frame ${frame + 2} should start where frame ${frame + 1} ends", board(frame + 1).cells, tapped.cells)
            val elsewhere = board(frame).cells.indices.first { it != cell && !board(frame).givens[it] }
            assertFalse("frame ${frame + 1} should turn down a tap on $elsewhere", accepts(board(frame).tap(elsewhere)))
            assertTrue(frames[frame].caption.length <= MamboTeacher.MAX_EXPLANATION)
        }

        // The claims the captions make, checked by the teacher's own rules and by enumeration.
        val answers = allSolutions(6, board(2).cells, board(2).links)
        assertEquals(Sym.MOON, answers.single()[2])
        assertTrue("frame 6's passing moon really is three together", board(5).violations().isNotEmpty())
        assertTrue("frame 7's board breaks nothing", board(6).violations().isEmpty())
        assertEquals(3, listOf(2, 8, 14, 20, 26).count { board(6).cells[it] == Sym.SUN })

        // 8: free play, finishable by hints without the fallback.
        assertTrue(frames[7].freePlay)
        var s = board(7)
        while (!s.solved) {
            val d = Mambo.teach(s)!!
            assertFalse("the last frame should not need the fallback", d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as MamboState
        }
        assertEquals(Mambo.TUTORIAL_SOLUTION, s.cells)
    }
}
