package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Feedback
import com.joebywan.daybook.puzzles.Tower
import com.joebywan.daybook.puzzles.TowerState
import com.joebywan.daybook.puzzles.TowerTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Tower's hint solver, checked against a scorer and a code enumerator written out again here.
 *
 * Mastermind boards always have several answers until late in the game, so soundness here is the
 * strong form throughout: a slot fact must hold for *every* code the scores still allow, not just
 * for the stored one. The scorer below works on a different principle from the game's (colour
 * histograms: total matches minus exact, rather than counting leftovers), so the two cannot share
 * a blind spot.
 */
class TowerTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    // ---- independent rules --------------------------------------------------------------------

    private fun scoreOf(guess: List<Int>, code: List<Int>, colours: Int): Feedback {
        val exact = guess.indices.count { guess[it] == code[it] }
        val total = (0 until colours).sumOf { c -> minOf(guess.count { it == c }, code.count { it == c }) }
        return Feedback(exact, total - exact)
    }

    /** Every code the visible scores allow, by plain enumeration. */
    private fun alive(s: TowerState): List<List<Int>> {
        val feedback = s.guesses.map { scoreOf(it, s.secret, s.colours) }
        val out = mutableListOf<List<Int>>()
        val code = IntArray(s.slots)
        fun walk(i: Int) {
            if (i == s.slots) {
                val c = code.toList()
                if (s.guesses.indices.all { scoreOf(s.guesses[it], c, s.colours) == feedback[it] }) out += c
                return
            }
            for (colour in 0 until s.colours) {
                code[i] = colour
                walk(i + 1)
            }
        }
        walk(0)
        return out
    }

    private fun feedbackOf(s: TowerState) = s.guesses.map { scoreOf(it, s.secret, s.colours) }

    private fun step(s: TowerState) =
        TowerTeacher.deduce(s.slots, s.colours, s.guesses, feedbackOf(s), s.current)

    /** Checks one step against every code still possible. */
    private fun assertSound(where: String, s: TowerState, step: TowerTeacher.Step, codes: List<List<Int>>) {
        assertTrue("$where: the secret itself was ruled out", s.secret in codes)
        val placed = s.current.indices.filter { s.current[it] >= 0 }
        val fits = codes.any { code -> placed.all { code[it] == s.current[it] } }
        when (val m = step.move) {
            is TowerTeacher.Move.Place -> {
                assertTrue("$where ${step.technique}: slot ${m.slot} = ${m.colour} fails for some code", codes.all { it[m.slot] == m.colour })
                assertTrue("$where ${step.technique}: the row it builds on was wrong", fits)
                assertEquals("$where: placed a peg that was already there", -1, s.current[m.slot])
                assertTrue("$where: fact too long to fit the panel", step.explanation.length <= TowerTeacher.MAX_EXPLANATION)
            }
            is TowerTeacher.Move.Fill -> {
                assertTrue("$where ${step.technique}: suggested ${m.code}, which no score allows", m.code in codes)
                assertTrue("$where: the fill ignored placed pegs", placed.all { m.code[it] == s.current[it] })
            }
            TowerTeacher.Move.Submit -> assertTrue("$where: offered to submit a row that cannot be the code", s.current in codes)
            is TowerTeacher.Move.Clear -> {
                assertEquals(TowerTeacher.MISTAKE, step.technique)
                assertFalse("$where: called a row a mistake when some code fits it: ${s.current}", fits)
                // Either the flagged peg is impossible on its own (other pegs may be wrong too, and
                // the next hint will get to them), or taking the flagged pegs out leaves a row that fits.
                val kept = placed - m.slots.toSet()
                val pegImpossible = m.slots.size == 1 && codes.none { it[m.slots[0]] == s.current[m.slots[0]] }
                assertTrue(
                    "$where: flagged ${m.slots}, but that peg is possible and clearing it still leaves an impossible row",
                    pegImpossible || codes.any { code -> kept.all { code[it] == s.current[it] } },
                )
            }
        }
        if (step.technique != TowerTeacher.MISTAKE) assertTrue("$where: a fitting row was not a mistake", fits)
    }

    /** [turns] guesses made by following hints, stopping early if the code is cracked. */
    private fun playByHints(start: TowerState, turns: Int): TowerState {
        var s = start
        repeat(turns) {
            if (s.solved) return s
            while (!s.ready) s = Tower.teach(s)!!.apply(s) as TowerState
            s = s.submit()
        }
        return s
    }

    // ---- soundness ------------------------------------------------------------------------------

    @Test
    fun `every step on the hint-following path holds for every code the scores allow`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(25, difficulty, "tower-teach")) {
                var s = Tower.generate(seed, difficulty) as TowerState
                var guard = 0
                while (!s.solved && !s.failed) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 200)
                    val d = Tower.teach(s)!!
                    val st = step(s)!!
                    assertEquals(d.technique, st.technique)
                    assertSound("$difficulty/$seed", s, st, alive(s))
                    val next = d.apply(s) as TowerState
                    assertTrue("$difficulty/$seed ${d.technique}: applying it did not reach it", d.isReached(next))
                    assertFalse("$difficulty/$seed ${d.technique}: reached before it was made", d.isReached(s))
                    s = next
                }
                assertTrue("$difficulty/$seed: hints alone ran out of guesses", s.solved)
            }
        }
    }

    /**
     * A person doesn't play the hint's game: they guess what they like, and half-build rows that may
     * or may not fit. Every step from those boards must still hold.
     */
    @Test
    fun `hints stay sound on boards a careless player made`() {
        val rng = java.util.Random(7)
        var mistakes = 0
        var facts = 0
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "tower-teach-played")) {
                val fresh = Tower.generate(seed, difficulty) as TowerState
                val n = 1 + rng.nextInt(fresh.maxGuesses - 3)
                val guesses = List(n) { List(fresh.slots) { rng.nextInt(fresh.colours) } }
                    .filter { it != fresh.secret }
                val current = List(fresh.slots) { if (rng.nextInt(2) == 0) rng.nextInt(fresh.colours) else -1 }
                val s = fresh.copy(guesses = guesses, current = current)
                val st = step(s) ?: continue
                if (st.technique == TowerTeacher.MISTAKE) mistakes++
                if (st.technique in TowerTeacher.FACTS) facts++
                assertSound("$difficulty/$seed", s, st, alive(s))
                // Take the row away and ask again, so the facts get tested, not just the mistakes.
                val empty = s.copy(current = List(s.slots) { -1 })
                val again = step(empty)!!
                if (again.technique in TowerTeacher.FACTS) facts++
                assertSound("$difficulty/$seed (empty row)", empty, again, alive(empty))
            }
        }
        assertTrue("random rows never produced a mistake, so nothing was tested: $mistakes", mistakes > 10)
        assertTrue("random boards never produced a fact, so nothing was tested: $facts", facts > 10)
    }

    /**
     * The teacher's entry point takes no code, and [Tower.teach] only hands it the scores. Swapping
     * the stored code for another the scores allow must not change a single word of the hint.
     */
    @Test
    fun `a hint does not change when the hidden code is swapped for another that fits`() {
        var compared = 0
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(15, difficulty, "tower-teach-swap")) {
                var s = Tower.generate(seed, difficulty) as TowerState
                s = playByHints(s, 3)
                if (s.solved) continue
                val others = alive(s).filter { it != s.secret }
                if (others.isEmpty()) continue
                val a = Tower.teach(s)!!
                val b = Tower.teach(s.copy(secret = others.last()))!!
                assertEquals(a.technique, b.technique)
                assertEquals(a.explanation, b.explanation)
                assertEquals(a.targets, b.targets)
                compared++
            }
        }
        assertTrue(compared > 20)
    }

    @Test
    fun `the teacher's scorer is the one the board shows`() {
        val rng = java.util.Random(3)
        repeat(2000) {
            val colours = 4 + rng.nextInt(5)
            val a = List(5) { rng.nextInt(colours) }
            val b = List(5) { rng.nextInt(colours) }
            assertEquals(scoreOf(a, b, colours), TowerTeacher.score(a, b, colours))
        }
    }

    // ---- coverage -------------------------------------------------------------------------------

    /**
     * A measurement, printed and written to `app/build/reports/tower-teaching-coverage.txt`. Boards
     * are walked by hints alone ("Show me" every time, Submit when the row is full). Per tier:
     *
     * - how often each technique is the step shown;
     * - per turn (one guess), whether a plain one-slot fact was taught at all, or the whole row came
     *   from the fallback;
     * - the ceiling: how often *some* slot was pinned down by the scores at the start of a turn,
     *   whether or not a one-line rule could say why. The gap between that and the facts taught is
     *   the honest measure of what the named rules miss.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 120
        val report = StringBuilder()
        report.appendLine("Tower teaching coverage: $perTier boards per tier, walked from empty by hints alone")
        for (difficulty in Difficulty.entries) {
            val stepCounts = TowerTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var totalSteps = 0
            var turns = 0
            var turnsWithFact = 0
            var turnsFallbackOnly = 0
            var turnsSlotPinned = 0
            var turnsPinnedTaught = 0
            var slotsPinned = 0
            var slotsTaught = 0
            var guessesUsed = 0
            var worst = 0
            for (seed in seeds(perTier, difficulty, "tower-coverage")) {
                var s = Tower.generate(seed, difficulty) as TowerState
                var guard = 0
                while (!s.solved && !s.failed) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 200)
                    val turnTechniques = mutableListOf<String>()
                    if (s.guesses.isNotEmpty()) {
                        val codes = alive(s)
                        val pinned = (0 until s.slots).count { i -> codes.all { it[i] == codes[0][i] } }
                        slotsPinned += pinned
                        if (pinned > 0) turnsSlotPinned++
                    }
                    val turn = s.guesses.size
                    while (s.guesses.size == turn && !s.solved) {
                        val d = Tower.teach(s)!!
                        stepCounts[d.technique] = stepCounts.getValue(d.technique) + 1
                        totalSteps++
                        turnTechniques += d.technique
                        s = d.apply(s) as TowerState
                    }
                    if (turn > 0) {
                        turns++
                        val taught = turnTechniques.count { it in TowerTeacher.FACTS }
                        slotsTaught += taught
                        if (taught > 0) turnsWithFact++ else if (
                            turnTechniques.any { it == TowerTeacher.CONSISTENT || it == TowerTeacher.ONLY_CODE }
                        ) turnsFallbackOnly++
                        if (taught > 0) turnsPinnedTaught++
                    }
                }
                assertTrue("$difficulty/$seed: hints alone lost the game", s.solved)
                guessesUsed += s.guesses.size
                worst = maxOf(worst, s.guesses.size)
            }
            report.appendLine()
            report.appendLine(
                "${difficulty.name} — $totalSteps steps; guesses avg %.2f, worst %d".format(
                    guessesUsed / perTier.toDouble(), worst,
                )
            )
            for (t in TowerTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-18s %5d steps (%5.1f%%)".format(t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / totalSteps)
                )
            }
            report.appendLine(
                "  turns after the first: %d; a one-slot fact taught on %.1f%%, whole row from the fallback on %.1f%%".format(
                    turns, 100.0 * turnsWithFact / turns, 100.0 * turnsFallbackOnly / turns,
                )
            )
            report.appendLine(
                "  ceiling: some slot pinned by the scores on %.1f%% of turns; %d slots pinned, %d taught as facts (%.1f%%)".format(
                    100.0 * turnsSlotPinned / turns, slotsPinned, slotsTaught, 100.0 * slotsTaught / maxOf(1, slotsPinned),
                )
            )
            assertTrue("$difficulty: the facts never fire", turnsPinnedTaught > 0)
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/tower-teaching-coverage.txt").writeText(report.toString())
    }

    // ---- mistakes -------------------------------------------------------------------------------

    private fun board(guesses: List<List<Int>>, current: List<Int>, secret: List<Int> = listOf(2, 0, 1, 3)) =
        TowerState(4, 5, 10, secret, guesses, current)

    @Test
    fun `a colour a nothing-scoring guess ruled out is flagged, with that guess named`() {
        // Guess 1 (purple x4) scores nothing against green red blue yellow.
        val s = board(listOf(listOf(4, 4, 4, 4)), listOf(-1, 4, -1, -1))
        val d = Tower.teach(s)!!
        assertTrue(d.mistake)
        assertEquals(setOf(TowerTeacher.CURRENT + 1), d.targets)
        assertTrue(d.explanation, d.explanation.startsWith("Guess 1 scored nothing, so purple isn't in the code."))
        assertFalse(d.isReached(s))
        assertTrue("overwriting the peg fixes it", d.isReached(s.withPeg(1, 0)))
        assertEquals(-1, (d.apply(s) as TowerState).current[1])
    }

    @Test
    fun `a peg against a no-filled-pip guess is flagged in that slot`() {
        // red blue green yellow against green red blue yellow is 1 filled, so use a 0-filled one.
        val g = listOf(0, 2, 3, 1) // vs 2 0 1 3: no exact, all four colours present
        val s = board(listOf(g), listOf(0, -1, -1, -1))
        val d = Tower.teach(s)!!
        assertTrue(d.mistake)
        assertTrue(d.explanation, d.explanation.startsWith("Guess 1 had red in slot 1 and no filled pips"))
    }

    @Test
    fun `a full row that contradicts a score says which guess and what it would have scored`() {
        val g = listOf(2, 0, 4, 4) // vs 2 0 1 3: two filled, nothing hollow
        val row = listOf(0, 2, 1, 3) // fits no simple rule, but would score 0 filled 2 hollow on guess 1
        val s = board(listOf(g), row)
        val d = Tower.teach(s)!!
        assertTrue(d.mistake)
        assertTrue(d.explanation, "guess 1 would have scored 0 filled and 2 hollow, not 2 filled and 0 hollow" in d.explanation)
        assertTrue(d.isReached(s.withPeg(0, 2)))
    }

    @Test
    fun `a row that fits the scores is never called a mistake`() {
        val rng = java.util.Random(19)
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(30, difficulty, "tower-teach-fits")) {
                var s = Tower.generate(seed, difficulty) as TowerState
                s = playByHints(s, 2)
                if (s.solved) continue
                val codes = alive(s)
                val code = codes[rng.nextInt(codes.size)]
                // Any subset of a fitting code's pegs fits.
                val row = code.map { if (rng.nextBoolean()) it else -1 }
                val d = Tower.teach(s.copy(current = row))!!
                assertFalse("$difficulty/$seed: $row fits but was called a mistake: ${d.explanation}", d.mistake)
            }
        }
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    private fun frameBoard(i: Int) = Tower.tutorial[i].state as TowerState

    @Test
    fun `the walkthrough's scores allow exactly one code, and it is the stored one`() {
        val s = frameBoard(6)
        assertEquals(3, s.guesses.size)
        assertEquals(listOf(Tower.TUTORIAL_CODE), alive(s))
        // Before guess 3 there is more than one, so guess 3 is doing the work the frames say it does.
        assertTrue(alive(frameBoard(0)).size > 1)
        // And the scores the captions describe are the scores the board shows.
        assertEquals(listOf(Feedback(0, 0), Feedback(2, 0), Feedback(2, 0)), feedbackOf(s))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real controls, and rejects a wrong one`() {
        val frames = Tower.tutorial
        assertEquals(7, frames.size)
        listOf(0, 1, 4).forEach { assertEquals("frame ${it + 1} should be Next-only", null, frames[it].accepts) }

        // 3: blue swatch, then slot 1 — the board emits one withPeg.
        val change = frames[2].accepts!!
        assertTrue(change(frameBoard(2).withPeg(0, 1)))
        assertFalse("red is selected by default; tapping without choosing blue", change(frameBoard(2).withPeg(0, 0)))
        assertFalse("blue in the wrong slot", change(frameBoard(2).withPeg(1, 1)))
        assertEquals(frameBoard(3).current, frameBoard(2).withPeg(0, 1).current)

        // 4: Submit.
        val submit = frames[3].accepts!!
        assertTrue(submit(frameBoard(3).submit()))
        assertFalse("changing a peg instead", submit(frameBoard(3).withPeg(2, 2)))
        assertEquals(frameBoard(4).guesses, frameBoard(3).submit().guesses)
        assertEquals(Feedback(2, 0), frameBoard(4).score(frameBoard(4).guesses[2]))

        // 6: green swatch, then slot 1.
        val green = frames[5].accepts!!
        assertTrue(green(frameBoard(5).withPeg(0, 2)))
        assertFalse(green(frameBoard(5).withPeg(0, 1)))
        assertFalse(green(frameBoard(5).withPeg(1, 2)))
        assertEquals(frameBoard(6).current, frameBoard(5).withPeg(0, 2).current)

        // Frame 5's claim, checked: every code the scores allow has green in slot 1.
        assertTrue(alive(frameBoard(4)).all { it[0] == 2 })

        // 7: free play, finishable by hints without the fallback, and the hints are the named facts.
        assertTrue(frames[6].freePlay)
        var s = frameBoard(6)
        val techniques = mutableListOf<String>()
        while (!s.solved) {
            val d = Tower.teach(s)
            assertNotNull(d)
            assertFalse("the last frame should not need the fallback: ${d!!.explanation}", d.fallback)
            techniques += d.technique
            s = d.apply(s) as TowerState
        }
        assertEquals(listOf(TowerTeacher.ACCOUNTED, TowerTeacher.ACCOUNTED, TowerTeacher.SUBMIT), techniques)
        assertEquals(4, s.guesses.size)
    }

    @Test
    fun `walkthrough captions fit the four lines the runner gives them`() {
        for ((i, f) in Tower.tutorial.withIndex()) {
            assertTrue("frame ${i + 1} caption is ${f.caption.length} chars", f.caption.length <= 170)
        }
    }
}
