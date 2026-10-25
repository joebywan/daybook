package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Cipher
import com.joebywan.daybook.puzzles.CipherLogic
import com.joebywan.daybook.puzzles.CipherLogic.EMPTY
import com.joebywan.daybook.puzzles.CipherState
import com.joebywan.daybook.puzzles.CipherSum
import com.joebywan.daybook.puzzles.CipherTeacher
import com.joebywan.daybook.puzzles.WordList
import com.joebywan.daybook.puzzles.enter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CipherTeachingTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun board(day: Int, d: Difficulty) =
        Cipher.generate(DailySeed.seedFor(start.plusDays(day.toLong()), Cipher.id, d), d) as CipherState

    private val minShown = mapOf(Difficulty.STANDARD to 0.88, Difficulty.HARD to 0.77, Difficulty.EXPERT to 0.72)

    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = sortedMapOf<String, Int>()
            var fallbackBoards = 0
            var steps = 0
            var shown = 0
            for (day in 0 until 200) {
                var s = board(day, d)
                var fellBack = false
                while (!s.solved) {
                    steps++
                    if (CipherTeacher.teach(s)!!.shown) shown++
                    val step = Cipher.teach(s)!!
                    assertFalse("${s.words} ${step.technique}", step.mistake)
                    counts.merge(step.technique, 1, Int::plus)
                    if (step.fallback) fellBack = true
                    s = step.apply(s) as CipherState
                }
                assertEquals(s.solution, s.digits)
                if (fellBack) fallbackBoards++
            }
            report.appendLine("${d.name}: fallback on $fallbackBoards of 200 boards; working shown on %.0f%% of steps; steps per board ".format(100.0 * shown / steps) +
                counts.map { "${it.key} %.2f".format(it.value / 200.0) })
            assertEquals("${d.name} never falls back", 0, fallbackBoards)
            // Measured 89 / 80 / 75%: the rest are the all-columns and what-if steps, still given as a summary.
            assertTrue("${d.name}: working shown on $shown of $steps steps", shown >= steps * minShown.getValue(d))
        }
        File("build/reports").mkdirs()
        File("build/reports/cipher-teaching-coverage.txt").writeText(report.toString())
        println(report)
    }

    /** Every answer of [words] that keeps the digits already placed. */
    private fun answersKeeping(words: List<String>, digits: List<Int>) =
        CipherOracle.answers(words).filter { a -> digits.indices.all { digits[it] == EMPTY || digits[it] == a[it] } }

    @Test
    fun `on sums with several answers, every step holds for every answer still possible`() {
        // Soundness on a one-answer board cannot tell reasoning from peeking, so walk sums with several.
        val rng = Rng(3)
        var steps = 0
        var sums = 0
        while (sums < 40) {
            val words = listOf(rng.pick(WordList.answers(4)), rng.pick(WordList.answers(4)), rng.pick(WordList.answers(5)))
            val sum = CipherSum(words)
            if (!sum.wellFormed) continue
            val all = CipherOracle.answers(words)
            if (all.size < 2 || all.size > 40) continue
            sums++
            // From the empty board, and from a few boards part-filled from one of the answers.
            for (k in 0..3) {
                val a = all[rng.nextInt(all.size)]
                val digits = MutableList(sum.letters.size) { EMPTY }
                for (l in rng.shuffled(sum.letters.indices.toList()).take(k)) digits[l] = a[l]
                val step = CipherTeacher.deduce(sum, digits) ?: continue
                steps++
                for (b in answersKeeping(words, digits)) {
                    assertEquals("$words $digits ${step.technique}", b[step.letter], step.digit)
                }
            }
        }
        assertTrue("the sample reached some steps: $steps", steps > 20)
    }

    @Test
    fun `steps stay sound from boards a player made, not only the solver's path`() {
        val rng = Rng(5)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            // Any subset of the answer is a board a player could have made.
            val digits = MutableList(s.digits.size) { EMPTY }
            for (l in rng.shuffled(s.digits.indices.toList()).take(rng.nextInt(s.digits.size))) digits[l] = s.solution[l]
            val now = s.copy(digits = digits)
            if (now.solved) continue
            val step = CipherTeacher.teach(now)!!
            assertTrue("${s.words} ${step.technique}", step.technique != CipherTeacher.MISTAKE && step.technique != CipherTeacher.FALLBACK)
            assertEquals("${s.words} ${step.technique}", s.solution[step.letter], step.digit)
            assertEquals(EMPTY, digits[step.letter])
        }
    }

    @Test
    fun `a wrong digit is addressed first, and a right one is never called wrong`() {
        val rng = Rng(9)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val right = rng.nextInt(s.digits.size)
            var wrong = rng.nextInt(s.digits.size)
            if (wrong == right) wrong = (wrong + 1) % s.digits.size
            val bad = (s.solution[wrong] + 1 + rng.nextInt(9)) % 10
            val now = s.copy(digits = s.digits.toMutableList().also { it[right] = s.solution[right]; it[wrong] = bad })
            val step = Cipher.teach(now)!!
            assertTrue(step.mistake)
            assertEquals(s.sum.tilesOf(wrong) + CipherLogic.key(wrong), step.targets)
            // Taking it back clears the hint; the right digit alone is never a mistake.
            assertTrue(step.isReached(step.apply(now)))
            val fixed = step.apply(now) as CipherState
            assertEquals(EMPTY, fixed.digits[wrong])
            assertFalse(Cipher.teach(fixed)!!.mistake)
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        val rng = Rng(13)
        var longest = 0
        for (d in Difficulty.entries) for (day in 0 until 365) {
            var s = board(day, d)
            // A mistake planted on some boards, so its text is measured too.
            if (day % 3 == 0) {
                val l = rng.nextInt(s.digits.size)
                s = s.copy(digits = s.digits.toMutableList().also { it[l] = (s.solution[l] + 1) % 10 })
            }
            while (!s.solved) {
                val step = CipherTeacher.teach(s)!!
                longest = maxOf(longest, step.explanation.length)
                assertTrue("${s.words}: ${step.nudge}", step.nudge.length <= CipherTeacher.MAX_NUDGE)
                assertTrue("${s.words}: ${step.explanation}", step.explanation.length <= CipherTeacher.MAX_EXPLANATION)
                s = s.withDigit(step.letter, step.digit)
            }
        }
        println("longest explanation $longest")
    }

    @Test
    fun `a letter that adds nothing to its column says why it is 9, not just that it is`() {
        // The owner's board: DEED + EASE = LEGAL, L placed. "Only D = 9 works" left them asking why E could not be 9.
        val sum = CipherSum(listOf("deed", "ease", "legal"))
        val digits = MutableList(sum.letters.size) { EMPTY }.also { it[sum.letters.indexOf('l')] = 1 }
        val step = CipherTeacher.deduce(sum, digits)!!
        assertEquals('d', sum.letters[step.letter])
        assertEquals(9, step.digit)
        assertTrue(step.explanation, "D + carry is 0 or 10" in step.explanation && "D starts a word" in step.explanation)
        assertTrue(step.explanation, step.explanation.length <= CipherTeacher.MAX_EXPLANATION)
    }

    @Test
    fun `a forced carry is worked through, not asserted`() {
        // The owner's board (24 Oct 2026, Standard): TRIP + RUMP = TIARA with T = 1. "Only I = 0 fits both" left them
        // asking why not another digit. The thousands must carry, 1 + R + carry is at most 11, and T has the 1.
        val sum = CipherSum(listOf("trip", "rump", "tiara"))
        val digits = MutableList(sum.letters.size) { EMPTY }.also { it[sum.letters.indexOf('t')] = 1 }
        val step = CipherTeacher.deduce(sum, digits)!!
        assertEquals('i', sum.letters[step.letter])
        assertEquals(0, step.digit)
        assertTrue(step.explanation, step.shown)
        assertTrue(step.explanation, "at most 1 + 9 + 1 = 11" in step.explanation && "T has 1" in step.explanation)
    }

    @Test
    fun `the teacher cannot see the answer`() {
        // deduce takes the sum and the digits placed, nothing else; a changed stored answer changes no step.
        val s = board(0, Difficulty.EXPERT)
        val other = s.copy(solution = s.solution.map { (it + 1) % 10 })
        val a = CipherTeacher.deduce(s.sum, s.digits)!!
        val b = CipherTeacher.deduce(other.sum, other.digits)!!
        assertEquals(a.explanation, b.explanation)
        assertEquals(a.letter to a.digit, b.letter to b.digit)
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Cipher.tutorial
        assertEquals(5, frames.size)
        assertEquals(listOf(true, false, false, false, false), frames.map { it.judge == null && !it.freePlay })
        // Letters G O T U. Each move is one frame: pick the letter (the digit glows next), then the digit.
        val b0 = Cipher.tutorialBoard()
        val o = frames[1].steps!!
        assertEquals(TutorialStep.REJECT, o(b0))
        val pickO = b0.select(1)
        assertEquals(TutorialStep.PROGRESS, o(pickO))
        assertEquals(setOf(CipherLogic.pad(1)), frames[1].glow!!(pickO).strong)
        assertEquals(TutorialStep.REJECT, o(b0.select(2)))
        assertEquals(TutorialStep.REJECT, o(pickO.enter(1, 2)))
        val one = pickO.enter(1, 1)
        assertEquals(TutorialStep.DONE, o(one))
        // Frame 3: T = 2.
        val t = frames[2].steps!!
        assertEquals(one.digits, (frames[2].state as CipherState).digits)
        val pickT = one.select(2)
        assertEquals(TutorialStep.PROGRESS, t(pickT))
        assertEquals(TutorialStep.REJECT, t(one.select(0)))
        assertEquals(TutorialStep.REJECT, t(pickT.enter(2, 3)))
        val two = pickT.enter(2, 2)
        assertEquals(TutorialStep.DONE, t(two))
        // Frame 4: the player gives U the 1 (it and O turn red), then taps 1 again to clear it.
        val clash = frames[3].steps!!
        val c0 = frames[3].state as CipherState
        assertEquals(two.digits, c0.digits)
        assertEquals(TutorialStep.REJECT, clash(c0))
        val pickU = c0.select(3)
        assertEquals(TutorialStep.PROGRESS, clash(pickU))
        assertEquals(TutorialStep.REJECT, clash(pickU.enter(3, 0)))
        val u1 = pickU.enter(3, 1)
        assertEquals(TutorialStep.PROGRESS, clash(u1))
        assertTrue(frames[3].glow!!(u1).warning)
        assertEquals(setOf(1, 3), CipherLogic.conflicts(u1.sum, u1.digits).letters)
        assertTrue(Cipher.teach(u1)!!.mistake)
        assertEquals(TutorialStep.DONE, clash(u1.enter(3, 1)))
        // Frame 5 is free play from T = 2, and hints alone finish it with no fallback and no mistake.
        assertTrue(frames[4].freePlay)
        var now = frames[4].state as CipherState
        assertEquals(two.digits, now.digits)
        while (!now.solved) {
            val d = Cipher.teach(now)!!
            assertFalse(d.fallback); assertFalse(d.mistake)
            now = d.apply(now) as CipherState
        }
        assertEquals(Cipher.TUTORIAL_SOLUTION, now.digits)
        for (f in frames) assertTrue("caption ${f.caption.length}", f.caption.length <= 170)
        // The captions' claims: O is the total's extra letter, O + O makes T in the units, and the tens
        // column (two digits and a carry of at most 1) makes at most 9 + 9 + 1 = 19, so it carries 1.
        val sum = CipherSum(Cipher.TUTORIAL_WORDS)
        assertEquals(1, sum.columnTotal[2]); assertEquals(listOf(1, 1), sum.columnAddends[0]); assertEquals(2, sum.columnTotal[0])
        assertEquals(2, sum.columnAddends[1].size)
        assertEquals(3, sum.columnTotal.size)
    }
}
