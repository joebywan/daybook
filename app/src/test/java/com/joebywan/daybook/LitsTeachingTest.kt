package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.LitsTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The LITS hint solver, checked against [LitsOracle] — an exhaustive enumerator written apart from
 * `Lits` — rather than against its own reasoning.
 *
 * Soundness is judged the way KingsTeachingTest judges it: a step must hold for *every* answer the
 * visible shading still allows, not just agree with the stored one. That matters more here than for
 * Kings. The generator proves uniqueness only among connected shadings while the win check no longer
 * asks for connectivity, so a good share of real boards have several legal answers — a technique that
 * quietly assumed "the shading joins up", or anything that leaked the stored answer, would shade a
 * square some legal answer leaves empty, and this is what would catch it.
 */
class LitsTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    private fun shadedOf(s: LitsState) = s.shaded.indices.filter { s.shaded[it] }.toSet()

    private fun alive(s: LitsState, cap: Int = 100_000) =
        LitsOracle.answers(s.width, s.height, s.region, shadedOf(s), cap)

    private fun withShading(s: LitsState, shaded: Set<Int>) = s.copy(shaded = List(s.width * s.height) { it in shaded })

    private val choiceText = "More than one finish"

    /**
     * The hint panel shows four lines of body text and ellipsises the rest, which on a phone is
     * about 170 characters. An explanation that runs past it loses its conclusion.
     */
    private fun assertFits(where: String, d: com.joebywan.daybook.core.Deduction) {
        assertTrue("$where: nudge too long: ${d.nudge}", d.nudge.length <= 70)
        assertTrue("$where: explanation too long (${d.explanation.length}): ${d.explanation}", d.explanation.length <= 170)
    }

    // ---- soundness on real boards ---------------------------------------------------------------

    @Test
    fun `every step on a real board holds for every answer the shading still allows`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(30, difficulty, "lits-teach")) {
                var s = Lits.generate(seed, difficulty) as LitsState
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 200)
                    val d = Lits.teach(s)
                    assertNotNull("$difficulty/$seed: no hint on an unsolved board", d)
                    d!!
                    assertFalse("$difficulty/$seed: a mistake on a board built from hints", d.mistake)
                    assertFits("$difficulty/$seed ${d.technique}", d)
                    val step = LitsTeacher.teach(s)!!
                    val answers = alive(s)
                    assertTrue("$difficulty/$seed: hints walked into a dead end", answers.isNotEmpty())
                    assertTrue("$difficulty/$seed ${d.technique}: a step with no move", step.shades.isNotEmpty())
                    val crossed = s.impossible()
                    for (c in step.shades) {
                        assertFalse("$difficulty/$seed ${d.technique}: shades a shaded square", s.shaded[c])
                        assertFalse("$difficulty/$seed ${d.technique}: shades a crossed square", c in crossed)
                    }
                    if (!d.fallback) {
                        for (c in step.shades) {
                            assertTrue(
                                "$difficulty/$seed ${d.technique}: shades $c, which a remaining answer leaves empty",
                                answers.all { c in it },
                            )
                        }
                    } else {
                        val c = step.shades.single()
                        assertTrue("$difficulty/$seed fallback: $c is in no answer", answers.any { c in it })
                        if (choiceText in d.explanation) {
                            assertTrue("$difficulty/$seed fallback claims a choice that isn't one", answers.any { c !in it })
                        } else {
                            assertTrue("$difficulty/$seed fallback claims every answer shades $c", answers.all { c in it })
                        }
                    }
                    if (d.technique == LitsTeacher.WHOLE_REGION) {
                        assertEquals("$difficulty/$seed: the whole region is not four squares", 4, d.focus.size)
                        assertTrue(d.focus.all { s.region[it] == s.region[d.focus.first()] })
                    }
                    val next = d.apply(s) as LitsState
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
            for (seed in seeds(30, difficulty, "lits-teach-played")) {
                val fresh = Lits.generate(seed, difficulty) as LitsState
                val some = fresh.solution.indices.filter { fresh.solution[it] && rng.nextInt(3) == 0 }.toSet()
                val s = withShading(fresh, some)
                if (s.solved) continue
                val step = LitsTeacher.teach(s)!!
                assertTrue("$difficulty/$seed: part of the answer was called a mistake", step.technique != LitsTeacher.MISTAKE)
                if (step.technique == LitsTeacher.FALLBACK) continue
                val answers = alive(s)
                for (c in step.shades) {
                    assertTrue("$difficulty/$seed ${step.technique}: shades $c against a remaining answer", answers.all { c in it })
                }
            }
        }
    }

    // ---- soundness from sight alone -------------------------------------------------------------

    /**
     * Regions of four to eight squares grown by flood from random seeds, with no care for uniqueness.
     * One region per six squares: at one per five the shading is too dense for any answer to exist.
     */
    private fun looseBoard(rng: java.util.Random, w: Int, h: Int): List<Int>? {
        val n = w * h
        val region = MutableList(n) { -1 }
        val count = n / 6
        (0 until n).shuffled(rng).take(count).forEachIndexed { id, cell -> region[cell] = id }
        fun around(c: Int) = listOfNotNull(
            (c - w).takeIf { it >= 0 }, (c + w).takeIf { it < n },
            (c - 1).takeIf { c % w > 0 }, (c + 1).takeIf { c % w < w - 1 },
        )
        val size = IntArray(count) { 1 }
        while (region.any { it == -1 }) {
            val open = (0 until n).filter { c -> region[c] == -1 && around(c).any { region[it] != -1 && size[region[it]] < 8 } }
            if (open.isEmpty()) return null
            val cell = open[rng.nextInt(open.size)]
            // The smallest neighbouring region takes it, so regions come out near the same size.
            val host = around(cell).map { region[it] }.filter { it != -1 && size[it] < 8 }.minBy { size[it] }
            region[cell] = host
            size[host]++
        }
        val sizes = (0 until count).map { id -> region.count { it == id } }
        return region.takeIf { sizes.all { it in 4..8 } }
    }

    @Test
    fun `on boards with several answers, every step holds for every answer still possible`() {
        val rng = java.util.Random(11)
        var boards = 0
        var steps = 0
        var choices = 0
        var attempts = 0
        val rejects = mutableListOf(0, 0, 0)
        while (boards < 150) {
            assertTrue("could not make enough loose boards: $boards; $rejects", attempts++ < 20_000)
            val w = 5 + rng.nextInt(2)
            val region = looseBoard(rng, w, w)
            if (region == null) { rejects[0]++; continue }
            val answers = LitsOracle.answers(w, w, region, cap = 5000)
            if (answers.size < 2) { rejects[minOf(answers.size, 1) + 1]++; continue }
            boards++
            var shaded = emptySet<Int>()
            while (true) {
                val remaining = answers.filter { it.containsAll(shaded) }
                assertTrue("a sound walk left no answer standing", remaining.isNotEmpty())
                if (remaining.any { it == shaded }) break
                // Deliberately no answer passed: this is the entry point that cannot see one.
                val step = LitsTeacher.deduce(w, w, region, List(w * w) { it in shaded })
                if (step == null) {
                    // Out of reasoning: make a choice the way a player would, from one answer.
                    val pick = remaining[rng.nextInt(remaining.size)]
                    shaded = shaded + (pick - shaded).sorted()[0]
                    choices++
                    continue
                }
                steps++
                for (c in step.shades) {
                    assertTrue("${step.technique} shaded $c, which a remaining answer leaves empty", remaining.all { c in it })
                }
                shaded = shaded + step.shades
            }
        }
        println("several-answer walk: $boards boards, $steps reasoned steps, $choices free choices")
        assertTrue("the walk barely stepped, so it proved little: $steps steps, $choices choices", steps > boards * 3)
    }

    // ---- coverage -----------------------------------------------------------------------------

    /**
     * Not a correctness check but a measurement, printed and written to
     * `app/build/reports/lits-teaching-coverage.txt`: how often each technique is what a player needs
     * next, and how often the fallback is reached — split into boards with one answer under the
     * rules and boards with several, since on the latter a choice is the honest end of reasoning.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 300
        val report = StringBuilder()
        report.appendLine("LITS teaching coverage: $perTier boards per tier, walked from empty by hints alone")
        for (difficulty in Difficulty.entries) {
            val stepCounts = LitsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = LitsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var totalSteps = 0
            var several = 0
            var uniqueFallback = 0
            var choiceSteps = 0
            var chainSteps = 0
            for (seed in seeds(perTier, difficulty, "lits-coverage")) {
                var s = Lits.generate(seed, difficulty) as LitsState
                val unique = LitsOracle.answers(s.width, s.height, s.region, cap = 2).size == 1
                if (!unique) several++
                val used = mutableSetOf<String>()
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 200)
                    val d = Lits.teach(s)!!
                    assertFalse("$difficulty/$seed: a mistake on a board built from hints", d.mistake)
                    assertFits("$difficulty/$seed ${d.technique}", d)
                    stepCounts[d.technique] = stepCounts.getValue(d.technique) + 1
                    if (d.fallback) {
                        if (choiceText in d.explanation) choiceSteps++ else chainSteps++
                    }
                    used += d.technique
                    totalSteps++
                    s = d.apply(s) as LitsState
                }
                if (unique && LitsTeacher.FALLBACK in used) uniqueFallback++
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine(
                "${difficulty.name} — $totalSteps steps, ${"%.1f".format(totalSteps / perTier.toDouble())} per board; " +
                    "$several/$perTier boards have several answers under the rules"
            )
            for (t in LitsTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-14s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / totalSteps,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
            report.appendLine(
                "  fallback: $choiceSteps steps a genuine choice, $chainSteps a longer chain; " +
                    "reached on $uniqueFallback of ${perTier - several} one-answer boards"
            )
            // Measured at zero on every tier when this was written: on a board with one answer the
            // techniques always finish. More than one in fifty would mean they had stopped covering
            // what the generator makes.
            assertTrue(
                "$difficulty: the fallback is reached on $uniqueFallback one-answer boards",
                uniqueFallback * 50 <= perTier - several,
            )
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/lits-teaching-coverage.txt").writeText(report.toString())
    }

    // ---- mistakes -------------------------------------------------------------------------------

    private fun tutorialBoard(shaded: Set<Int>) = LitsState(
        5, 5, Lits.TUTORIAL_REGIONS, List(25) { it in shaded }, List(25) { it in Lits.TUTORIAL_SOLUTION },
    )

    @Test
    fun `a shaded square that fills a 2x2 is addressed first, and said so`() {
        val s = tutorialBoard(Lits.TUTORIAL_TOP + 7)
        val d = Lits.teach(s)!!
        assertTrue(d.mistake)
        assertEquals(setOf(7), d.targets)
        assertTrue("should name the 2x2: ${d.explanation}", "2x2" in d.explanation)
        assertEquals(setOf(2, 3, 7, 8), d.cited)
        val fixed = d.apply(s) as LitsState
        assertFalse(fixed.shaded[7])
        assertTrue(d.isReached(fixed))
        assertFalse(d.isReached(s))
    }

    @Test
    fun `a fifth square in a region is flagged as one too many`() {
        // Five of the middle region's squares.
        val five = tutorialBoard(setOf(0, 5, 6, 7, 12))
        val d = Lits.teach(five)!!
        assertTrue(d.mistake)
        assertEquals(1, d.targets.size)
        assertTrue("should say too many: ${d.explanation}", "more than four" in d.explanation)
    }

    @Test
    fun `a square no answer shades outranks every step on a real board, and clearing it clears the hint`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(15, difficulty, "lits-mistake")) {
                val fresh = Lits.generate(seed, difficulty) as LitsState
                val answers = LitsOracle.answers(fresh.width, fresh.height, fresh.region)
                val wrong = fresh.shaded.indices.first { c -> answers.none { c in it } }
                val s = fresh.toggle(wrong)
                val d = Lits.teach(s)!!
                assertTrue("$difficulty/$seed: wrong square not flagged", d.mistake)
                assertFits("$difficulty/$seed mistake", d)
                assertEquals(setOf(wrong), d.targets)
                assertTrue("$difficulty/$seed: no explanation", d.explanation.isNotBlank())
                assertTrue(d.isReached(s.toggle(wrong)))
            }
        }
    }

    @Test
    fun `a square from a different legal answer is not called a mistake`() {
        var checked = 0
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "lits-other-answer")) {
                val fresh = Lits.generate(seed, difficulty) as LitsState
                val answers = LitsOracle.answers(fresh.width, fresh.height, fresh.region, cap = 50)
                val stored = shadedOf(fresh.copy(shaded = fresh.solution))
                val other = answers.firstOrNull { it != stored } ?: continue
                val cell = (other - stored).min()
                val s = fresh.toggle(cell)
                val d = Lits.teach(s)!!
                assertFalse("$difficulty/$seed: square $cell of a legal answer was called a mistake", d.mistake)
                // And the hints can walk that other answer through to a board the win check accepts.
                var t = s
                var guard = 0
                while (!t.solved) {
                    assertTrue(guard++ < 200)
                    val next = Lits.teach(t)!!
                    assertFalse("$difficulty/$seed: mistake while finishing the other answer", next.mistake)
                    t = next.apply(t) as LitsState
                }
                checked++
            }
        }
        assertTrue("too few boards with a second answer to test this: $checked", checked >= 10)
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val answers = LitsOracle.answers(5, 5, Lits.TUTORIAL_REGIONS)
        assertEquals(listOf(Lits.TUTORIAL_SOLUTION), answers)
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Lits.tutorial
        assertEquals(8, frames.size)
        fun board(i: Int) = frames[i].state as LitsState
        val top = Lits.TUTORIAL_TOP
        val middle = top + Lits.TUTORIAL_MIDDLE

        listOf(0, 1, 2).forEach { assertNull("frame ${it + 1} should be Next-only", frames[it].accepts) }

        // What the explanatory frames point at is true of the solved board.
        val letters = board(0).letters()
        assertTrue(listOf(10, 15, 16, 20).all { letters[it] == Lits.Piece.T })
        assertTrue(top.all { letters[it] == Lits.Piece.L } && Lits.TUTORIAL_MIDDLE.all { letters[it] == Lits.Piece.L })
        assertTrue("frame 2's block has three shaded", listOf(2, 3, 8).all { board(1).shaded[it] } && !board(1).shaded[7])

        // 4: one drag through the top region; a single tap is refused.
        val drag = frames[3].accepts!!
        assertTrue(drag(board(3).paint(top, true)))
        assertFalse("one tap of the four", drag(board(3).toggle(1)))
        assertFalse("a drag through five", drag(board(3).paint(top + 7, true)))
        assertEquals(4, Lits.TUTORIAL_REGIONS.count { it == Lits.TUTORIAL_REGIONS[1] })

        // 5: a tap on the stray square clears it.
        val clear = frames[4].accepts!!
        assertTrue(clear(board(4).toggle(7)))
        assertFalse("clearing the L instead", clear(board(4).toggle(2)))
        assertEquals(shadedOf(board(5)), shadedOf(board(4).toggle(7)))

        // 6: the middle region's four uncrossed squares, in one drag.
        val crossed = board(5).impossible()
        assertTrue("frame 6's soft squares are crossed", setOf(0, 5, 6, 7).all { it in crossed })
        val middleOpen = board(5).region.indices.filter {
            board(5).region[it] == board(5).region[12] && it !in crossed
        }.toSet()
        assertEquals(Lits.TUTORIAL_MIDDLE, middleOpen)
        val drag2 = frames[5].accepts!!
        assertTrue(drag2(board(5).paint(Lits.TUTORIAL_MIDDLE, true)))
        assertFalse("one of the four", drag2(board(5).toggle(12)))
        assertEquals(middle, shadedOf(board(6)))

        // 7: the T's bump. The two squares that would make an L are crossed, and the region has
        // exactly the T left.
        val crossed7 = board(6).impossible()
        assertTrue(11 in crossed7 && 21 in crossed7)
        val leftOpen = board(6).region.indices.filter { board(6).region[it] == board(6).region[10] && it !in crossed7 }.toSet()
        assertEquals(setOf(10, 15, 16, 20), leftOpen)
        val tap = frames[6].accepts!!
        assertTrue(tap(board(6).toggle(Lits.TUTORIAL_BUMP)))
        assertFalse("a crossed square", tap(board(6).toggle(11)))
        assertFalse("a tap elsewhere", tap(board(6).toggle(10)))
        assertEquals(middle + Lits.TUTORIAL_BUMP, shadedOf(board(7)))

        // Every move the frames ask for is part of the answer.
        assertTrue((middle + Lits.TUTORIAL_BUMP).all { it in Lits.TUTORIAL_SOLUTION })

        // 8: free play, finishable by hints alone, with no fallback and no mistakes.
        assertTrue(frames[7].freePlay)
        var s = board(7)
        while (!s.solved) {
            val d = Lits.teach(s)!!
            assertFalse("the last frame should not need the fallback", d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as LitsState
        }
        assertEquals(Lits.TUTORIAL_SOLUTION, shadedOf(s))
    }

    @Test
    fun `the walkthrough's hints teach the same moves its frames do`() {
        // From empty, the first hint is the top region, the whole of it.
        val first = LitsTeacher.deduce(5, 5, Lits.TUTORIAL_REGIONS, List(25) { false })!!
        assertEquals(LitsTeacher.WHOLE_REGION, first.technique)
        assertEquals(Lits.TUTORIAL_TOP, first.shades.toSet())
    }
}
