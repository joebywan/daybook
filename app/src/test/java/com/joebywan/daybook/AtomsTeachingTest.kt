package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Atom
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.AtomsTeacher
import com.joebywan.daybook.puzzles.Pair2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The Atoms hint solver, checked against an enumerator written out again here on a different
 * principle from both the generator's (line by line) and the teacher's (bounds per line): it walks
 * atom by atom, handing each atom's still-owed bonds out among its lines to later atoms, and only
 * checks crossings as it goes and connection at the end.
 *
 * As for Kings, soundness on a unique board cannot tell reasoning from peeking, so the solver is also
 * walked over boards with several answers, where a bond is only sound if every answer still standing
 * has it.
 */
class AtomsTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String = "atoms-teach") = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    // ---- independent rules --------------------------------------------------------------------

    private fun linesCross(atoms: List<Atom>, p: Pair2, q: Pair2): Boolean {
        if (p.horizontal == q.horizontal) return false
        val h = if (p.horizontal) p else q
        val v = if (p.horizontal) q else p
        val y = atoms[h.a].row
        val x = atoms[v.a].col
        val x0 = minOf(atoms[h.a].col, atoms[h.b].col)
        val x1 = maxOf(atoms[h.a].col, atoms[h.b].col)
        val y0 = minOf(atoms[v.a].row, atoms[v.b].row)
        val y1 = maxOf(atoms[v.a].row, atoms[v.b].row)
        return x > x0 && x < x1 && y > y0 && y < y1
    }

    private fun linesFor(atoms: List<Atom>): List<Pair2> {
        val out = mutableListOf<Pair2>()
        for (i in atoms.indices) for (j in i + 1 until atoms.size) {
            val a = atoms[i]
            val b = atoms[j]
            if (a.row != b.row && a.col != b.col) continue
            val between = atoms.any { c ->
                if (a.row == b.row) c.row == a.row && c.col > minOf(a.col, b.col) && c.col < maxOf(a.col, b.col)
                else c.col == a.col && c.row > minOf(a.row, b.row) && c.row < maxOf(a.row, b.row)
            }
            if (!between) out += Pair2(i, j, a.row == b.row)
        }
        return out
    }

    private fun connected(atoms: List<Atom>, pairs: List<Pair2>, counts: List<Int>): Boolean {
        val parent = IntArray(atoms.size) { it }
        fun find(x: Int): Int = if (parent[x] == x) x else find(parent[x]).also { parent[x] = it }
        pairs.indices.filter { counts[it] > 0 }.forEach { parent[find(pairs[it].a)] = find(pairs[it].b) }
        return atoms.indices.all { find(it) == find(0) }
    }

    /** Every legal bonding, atom by atom, up to [cap] of them. */
    private fun allSolutions(atoms: List<Atom>, pairs: List<Pair2>, cap: Int = 3000): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        val counts = IntArray(pairs.size) { -1 }
        val forward = atoms.indices.map { a -> pairs.indices.filter { pairs[it].a == a } } // a < b always
        fun ok(p: Int, v: Int) = v == 0 || pairs.indices.none { q -> counts[q] > 0 && linesCross(atoms, pairs[p], pairs[q]) }
        fun owed(a: Int) = atoms[a].bonds - pairs.indices.sumOf { if ((pairs[it].a == a || pairs[it].b == a) && counts[it] > 0) counts[it] else 0 }

        fun atom(a: Int) {
            if (out.size >= cap) return
            if (a == atoms.size) {
                val c = counts.toList()
                if (connected(atoms, pairs, c)) out += c
                return
            }
            val lines = forward[a]
            fun give(i: Int, left: Int) {
                if (out.size >= cap) return
                if (i == lines.size) {
                    if (left == 0) atom(a + 1)
                    return
                }
                val p = lines[i]
                for (v in 0..minOf(2, left)) {
                    if (!ok(p, v)) continue
                    // The far atom must not be overfilled.
                    counts[p] = v
                    if (owed(pairs[p].b) >= 0) give(i + 1, left - v)
                    counts[p] = -1
                }
            }
            val left = owed(a)
            if (left < 0) return
            give(0, left)
        }
        atom(0)
        return out
    }

    // ---- the answers the teacher is checked against --------------------------------------------

    /**
     * Mistakes are judged against the stored answer, so it had better be one. It was not on 4% of
     * Standard, 14% of Hard and 35% of Expert boards until the generator learned to check.
     */
    @Test
    fun `every stored answer obeys the rules, and is the only one`() {
        for (difficulty in Difficulty.entries) {
            val count = if (difficulty == Difficulty.EXPERT) 60 else 150
            for (seed in seeds(count, difficulty, "atoms-answers")) {
                val s = Atoms.generate(seed, difficulty) as AtomsState
                assertTrue("$difficulty/$seed: fell back to the three-atom chain", s.atoms.size > 3)
                for (a in s.atoms.indices) {
                    val carried = s.pairs.indices.sumOf { if (s.pairs[it].a == a || s.pairs[it].b == a) s.solution[it] else 0 }
                    assertEquals("$difficulty/$seed: atom $a", s.atoms[a].bonds, carried)
                }
                for (p in s.pairs.indices) for (q in s.pairs.indices) {
                    assertFalse("$difficulty/$seed: answer crosses", s.solution[p] > 0 && s.solution[q] > 0 && linesCross(s.atoms, s.pairs[p], s.pairs[q]))
                }
                assertTrue("$difficulty/$seed: answer not connected", connected(s.atoms, s.pairs, s.solution))
                assertTrue("$difficulty/$seed: solved rejects the answer", s.copy(counts = s.solution).solved)
                if (difficulty != Difficulty.EXPERT) {
                    assertEquals("$difficulty/$seed", listOf(s.solution), allSolutions(s.atoms, s.pairs, cap = 2))
                }
            }
        }
    }

    // ---- soundness on real boards ---------------------------------------------------------------

    @Test
    fun `every step on a real board agrees with the answer and adds a bond`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty)) {
                var s = Atoms.generate(seed, difficulty) as AtomsState
                var guard = 0
                while (!s.solved) {
                    assertTrue("atoms/$difficulty/$seed: walk did not finish", guard++ < 300)
                    val d = Atoms.teach(s)
                    assertNotNull("atoms/$difficulty/$seed: no hint on an unsolved board", d)
                    d!!
                    assertFalse("$difficulty/$seed: a mistake on a board built from hints", d.mistake)
                    val step = AtomsTeacher.teach(s)!!
                    assertTrue("$difficulty/$seed ${d.technique}: a step with no move", step.raise.isNotEmpty())
                    for ((p, to) in step.raise) {
                        assertTrue("$difficulty/$seed ${d.technique}: raised line $p past the answer", to <= s.solution[p])
                        assertTrue("$difficulty/$seed ${d.technique}: raised nothing", to > s.counts[p])
                    }
                    assertEquals(step.raise.keys.map { s.atoms.size + it }.toSet(), d.targets)
                    assertTrue("$difficulty/$seed ${d.technique}: no explanation", d.explanation.isNotBlank())
                    val next = d.apply(s) as AtomsState
                    assertTrue("$difficulty/$seed ${d.technique}: applying it did not reach it", d.isReached(next))
                    assertFalse("$difficulty/$seed ${d.technique}: reached before it was made", d.isReached(s))
                    s = next
                }
                assertEquals(s.solution, s.counts)
            }
        }
    }

    @Test
    fun `hints stay sound from boards a player made, not just from the solver's own path`() {
        val rng = java.util.Random(7)
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "atoms-teach-played")) {
                val fresh = Atoms.generate(seed, difficulty) as AtomsState
                // Some of the answer's bonds, some only half laid.
                val counts = fresh.solution.map { v -> if (v == 0) 0 else rng.nextInt(v + 1) }
                val s = fresh.copy(counts = counts)
                if (s.solved) continue
                val step = AtomsTeacher.teach(s)!!
                assertTrue("$difficulty/$seed: a correct board was called a mistake", step.technique != AtomsTeacher.MISTAKE)
                for ((p, to) in step.raise) assertTrue("$difficulty/$seed ${step.technique}: past the answer", to <= s.solution[p])
            }
        }
    }

    // ---- soundness from sight alone -------------------------------------------------------------

    /**
     * A random molecule with no attempt at a unique answer: atoms scattered, lines between them
     * joined in a random order that keeps them uncrossed and connects everything, and each atom's
     * number read off the result.
     */
    private fun looseBoard(rng: java.util.Random): kotlin.Pair<List<Atom>, List<Pair2>>? {
        val n = 5 + rng.nextInt(3)
        val cells = (0 until n * n).shuffled(rng).take(5 + rng.nextInt(5))
        val bare = cells.map { Atom(it / n, it % n, 0) }
        val pairs = linesFor(bare)
        val counts = IntArray(pairs.size)
        val parent = IntArray(bare.size) { it }
        fun find(x: Int): Int = if (parent[x] == x) x else find(parent[x]).also { parent[x] = it }
        for (p in pairs.indices.shuffled(rng)) {
            if (pairs.indices.any { q -> counts[q] > 0 && linesCross(bare, pairs[p], pairs[q]) }) continue
            val joins = find(pairs[p].a) != find(pairs[p].b)
            if (joins || rng.nextInt(3) == 0) {
                counts[p] = 1 + rng.nextInt(2)
                parent[find(pairs[p].a)] = find(pairs[p].b)
            }
        }
        if (!connected(bare, pairs, counts.toList())) return null
        val atoms = bare.mapIndexed { i, a ->
            a.copy(bonds = pairs.indices.sumOf { if (pairs[it].a == i || pairs[it].b == i) counts[it] else 0 })
        }
        return atoms to pairs
    }

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        val rng = java.util.Random(11)
        var boards = 0
        var steps = 0
        val seen = mutableMapOf<String, Int>()
        while (boards < 200) {
            val (atoms, pairs) = looseBoard(rng) ?: continue
            val answers = allSolutions(atoms, pairs)
            if (answers.size < 2) continue
            boards++
            var counts = List(pairs.size) { 0 }
            while (true) {
                val alive = answers.filter { ans -> ans.indices.all { ans[it] >= counts[it] } }
                assertTrue("a sound walk left no answer standing", alive.isNotEmpty())
                // Deliberately no answer passed: this is the entry point that cannot see one.
                val step = AtomsTeacher.deduce(atoms, pairs, counts) ?: break
                steps++
                seen[step.technique] = (seen[step.technique] ?: 0) + 1
                for ((p, to) in step.raise) {
                    assertTrue(
                        "${step.technique} raised line $p to $to, which some remaining answer does not reach: ${step.explanation}",
                        alive.all { it[p] >= to },
                    )
                }
                counts = counts.mapIndexed { i, v -> step.raise[i] ?: v }
            }
        }
        assertTrue("the walk barely stepped, so it proved little: $steps", steps > boards)
        println("several-answer walk: $boards boards, $steps steps, $seen")
    }

    // ---- coverage -----------------------------------------------------------------------------

    /**
     * Not a correctness check but a measurement, printed and written to
     * `app/build/reports/atoms-teaching-coverage.txt`: how often each technique is what a player
     * needs next, and how often the what-if and the fallback are reached, per tier.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 500
        val report = StringBuilder()
        report.appendLine("Atoms teaching coverage: $perTier boards per tier, walked from empty by hints alone")
        for (difficulty in Difficulty.entries) {
            val stepCounts = AtomsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = AtomsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var totalSteps = 0
            var longest = ""
            for (seed in seeds(perTier, difficulty, "atoms-coverage")) {
                var s = Atoms.generate(seed, difficulty) as AtomsState
                val used = mutableSetOf<String>()
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 300)
                    val d = Atoms.teach(s)!!
                    if (d.explanation.length > longest.length) longest = d.explanation
                    stepCounts[d.technique] = stepCounts.getValue(d.technique) + 1
                    used += d.technique
                    totalSteps++
                    s = d.apply(s) as AtomsState
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${difficulty.name} — $totalSteps steps, ${"%.1f".format(totalSteps / perTier.toDouble())} per board")
            for (t in AtomsTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-14s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / totalSteps,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
            report.appendLine("  longest explanation, ${longest.length} characters: $longest")
            // The panel shows four lines and ellipsizes the rest; about 200 characters fit.
            assertTrue("$difficulty: an explanation too long for the panel: $longest", longest.length <= 230)
            val fallbackBoards = boardCounts.getValue(AtomsTeacher.FALLBACK)
            assertTrue(
                "$difficulty: the fallback is reached on $fallbackBoards/$perTier boards",
                fallbackBoards * 10 < perTier,
            )
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/atoms-teaching-coverage.txt").writeText(report.toString())
    }

    // ---- mistakes -------------------------------------------------------------------------------

    private fun tutorialBoard(vararg counts: Int) = AtomsState(
        5, Atoms.TUTORIAL_ATOMS, Atoms.TUTORIAL_PAIRS, counts.toList(), Atoms.TUTORIAL_SOLUTION,
    )

    @Test
    fun `a closed-off pair is addressed first, and why it is wrong is explained`() {
        // The bottom two 2s doubled: both full, and cut off from the rest.
        val s = tutorialBoard(0, 0, 0, 0, 0, 2)
        val d = Atoms.teach(s)!!
        assertTrue(d.mistake)
        assertEquals(setOf(6 + 5), d.targets)
        assertTrue("should say it closes them off: ${d.explanation}", "closes" in d.explanation)
        assertTrue("should cite both 2s", d.cited.containsAll(setOf(4, 5)))
        val fixed = d.apply(s) as AtomsState
        assertEquals(0, fixed.counts[5])
        assertTrue(d.isReached(fixed))
        assertFalse(d.isReached(s))
        // Tapping once more cycles the double away, which is what the explanation asks for.
        assertTrue(d.isReached(s.cycle(5)))
    }

    @Test
    fun `an overfilled atom is named as the reason`() {
        // The top 2 doubled down, and the 3 given two more to the right: five on a 3.
        val s = tutorialBoard(2, 2, 0, 0, 0, 0)
        val d = Atoms.teach(s)!!
        assertTrue(d.mistake)
        assertTrue("should say more bonds than its number: ${d.explanation}", "more bonds than its number" in d.explanation)
    }

    @Test
    fun `a wrong bond on a real board outranks every step, and taking it back clears it`() {
        var checked = 0
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(20, difficulty, "atoms-mistake")) {
                val fresh = Atoms.generate(seed, difficulty) as AtomsState
                // A line the answer leaves empty, laid by a drag.
                val wrong = fresh.pairs.indices.firstOrNull { fresh.solution[it] == 0 && fresh.link(it) != null } ?: continue
                val s = fresh.link(wrong)!!
                val d = Atoms.teach(s)!!
                assertTrue("$difficulty/$seed: wrong bond not flagged", d.mistake)
                assertEquals(setOf(fresh.atoms.size + wrong), d.targets)
                assertTrue("$difficulty/$seed: no explanation", d.explanation.isNotBlank())
                assertFalse(d.isReached(s))
                assertFalse("one tap only doubles it", d.isReached(s.cycle(wrong)))
                assertTrue(d.isReached(s.cycle(wrong).cycle(wrong)))
                checked++

                // A double where the answer has one.
                val single = fresh.pairs.indices.first { fresh.solution[it] == 1 }
                val doubled = fresh.copy(counts = fresh.counts.toMutableList().also { it[single] = 2 })
                val m = Atoms.teach(doubled)!!
                assertTrue("$difficulty/$seed: an extra bond not flagged", m.mistake)
                assertEquals(setOf(fresh.atoms.size + single), m.targets)
                assertTrue(m.isReached(doubled.cycle(single)))
            }
        }
        assertTrue("too few boards had a free line to test: $checked", checked > 30)
    }

    @Test
    fun `a bond the answer agrees with is never called a mistake`() {
        assertFalse(Atoms.teach(tutorialBoard(1, 0, 0, 1, 0, 0))!!.mistake)
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        assertEquals(Atoms.TUTORIAL_PAIRS, linesFor(Atoms.TUTORIAL_ATOMS))
        assertEquals(listOf(Atoms.TUTORIAL_SOLUTION), allSolutions(Atoms.TUTORIAL_ATOMS, Atoms.TUTORIAL_PAIRS))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Atoms.tutorial
        assertEquals(7, frames.size)
        fun board(i: Int) = frames[i].state as AtomsState

        listOf(0, 1).forEach { assertNull("frame ${it + 1} should be Next-only", frames[it].accepts) }
        assertTrue(board(0).solved)

        // 3: a drag from the top 2 lays the first bond (a tap would too, and is fine).
        val drag = frames[2].accepts!!
        assertTrue(drag(board(2).link(0)!!))
        assertTrue(drag(board(2).cycle(0)))
        assertFalse("a bond elsewhere", drag(board(2).link(1)!!))
        assertEquals(board(3).counts, board(2).link(0)!!.counts)

        // 4: a tap on the bond doubles it.
        val double = frames[3].accepts!!
        assertTrue(double(board(3).cycle(0)))
        assertNull("a drag cannot double a bond", board(3).link(0))
        assertFalse("a tap elsewhere", double(board(3).cycle(1)))
        assertEquals(board(4).counts.take(5), board(3).cycle(0).counts.take(5))

        // 5: a tap on the wrong double clears it.
        val clear = frames[4].accepts!!
        assertTrue(clear(board(4).cycle(5)))
        assertFalse("a tap elsewhere", clear(board(4).cycle(4)))
        assertEquals(board(5).counts, board(4).cycle(5).counts)
        assertTrue("frame 5's double really is a mistake", Atoms.teach(board(4))!!.mistake)

        // 6: a tap in the gap above the corner 2.
        val gap = frames[5].accepts!!
        assertTrue(gap(board(5).cycle(4)))
        assertFalse("the closed-off pair again", gap(board(5).cycle(5)))
        assertEquals(board(6).counts, board(5).cycle(4).counts)
        // The claim, re-checked by enumeration: every answer has a bond on line 4.
        assertTrue(allSolutions(Atoms.TUTORIAL_ATOMS, Atoms.TUTORIAL_PAIRS).all { it[4] >= 1 })

        // 7: free play, finishable by hints without the fallback.
        assertTrue(frames[6].freePlay)
        var s = board(6)
        while (!s.solved) {
            val d = Atoms.teach(s)!!
            assertFalse("the last frame should not need the fallback", d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as AtomsState
        }
        assertEquals(Atoms.TUTORIAL_SOLUTION, s.counts)
    }
}
