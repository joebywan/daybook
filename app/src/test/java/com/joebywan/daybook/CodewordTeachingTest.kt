package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Codeword
import com.joebywan.daybook.puzzles.CodewordLogic
import com.joebywan.daybook.puzzles.CodewordLogic.EMPTY
import com.joebywan.daybook.puzzles.CodewordState
import com.joebywan.daybook.puzzles.CodewordTeacher
import com.joebywan.daybook.puzzles.WheelWords
import com.joebywan.daybook.puzzles.enter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodewordTeachingTest {


    private fun board(day: Int, d: Difficulty) =
        Codeword.generate(daySeed(day, Codeword.id, d), d) as CodewordState

    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = sortedMapOf<String, Int>()
            var fallbackBoards = 0
            val times = mutableListOf<Long>()
            for (day in 0 until 200) {
                var s = board(day, d)
                var fellBack = false
                while (!s.solved) {
                    val t0 = System.nanoTime()
                    val step = Codeword.teach(s)!!
                    times += System.nanoTime() - t0
                    assertFalse(step.technique, step.mistake)
                    counts.merge(step.technique, 1, Int::plus)
                    if (step.fallback) fellBack = true
                    s = step.apply(s) as CodewordState
                }
                assertEquals(s.solution, s.letters)
                if (fellBack) fallbackBoards++
            }
            times.sort()
            report.appendLine(
                "${d.name}: fallback on $fallbackBoards of 200 boards; steps per board " + counts.map { "${it.key} %.2f".format(it.value / 200.0) } +
                    "; hint median ${times[times.size / 2] / 1000} us, p95 ${times[times.size * 95 / 100] / 1000} us, max ${times.last() / 1000} us",
            )
            assertEquals("${d.name} never falls back", 0, fallbackBoards)
        }
        writeReport("codeword-teaching-coverage.txt", report.toString())
        println(report)
    }

    /** The squares of an L: a word across the top row and one down the first column, sharing their first square. */
    private val L = listOf(0, 1, 2, 3, 6)

    /** An L of two three-letter words sharing a first letter, numbered: many words fit it, so it has many answers. */
    private fun several(rng: Rng): CodewordState? {
        val threes = WheelWords.grid(3)
        val across = rng.pick(threes)
        val down = rng.pick(threes)
        if (across[0] != down[0] || across == down) return null
        val letters = (across + down.substring(1)).toList()
        val alphabet = letters.distinct()
        val numbers = MutableList(9) { 0 }
        L.forEachIndexed { i, sq -> numbers[sq] = alphabet.indexOf(letters[i]) + 1 }
        return CodewordState(3, numbers, alphabet.map { it - 'a' }, emptyList(), List(alphabet.size) { EMPTY })
    }

    @Test
    fun `on boards with several answers, every step holds for every answer still possible`() {
        // Soundness on a one-answer board cannot tell reasoning from peeking, so walk Ls of two words, which have many.
        val rng = Rng(3)
        var steps = 0
        var boards = 0
        var tries = 0
        while (boards < 60 && tries++ < 1_000_000) {
            val s = several(rng) ?: continue
            boards++
            val answers = answers(s)
            for (k in 0..3) {
                val a = answers[rng.nextInt(answers.size)]
                val letters = MutableList(s.letters.size) { EMPTY }
                for (n in rng.shuffled(letters.indices.toList()).take(k)) letters[n] = a[n]
                val step = CodewordTeacher.deduce(3, s.numbers, letters) ?: continue
                steps++
                for (b in answers.filter { b -> letters.indices.all { letters[it] == EMPTY || letters[it] == b[it] } }) {
                    assertEquals("${s.numbers} $letters ${step.technique}", b[step.number], step.letter)
                }
            }
        }
        assertTrue("the sample reached some steps: $steps of $boards boards", steps > 10)
    }

    /** Every answer of an L, by brute force over pairs of words. */
    private fun answers(s: CodewordState): List<List<Int>> {
        val threes = WheelWords.grid(3)
        val out = mutableListOf<List<Int>>()
        for (x in threes) for (y in threes) {
            if (x[0] != y[0]) continue
            val sq = x + y.substring(1)
            val letters = IntArray(s.letters.size) { -1 }
            var ok = true
            L.forEachIndexed { i, at ->
                val n = s.numbers[at] - 1
                if (letters[n] == -1) letters[n] = sq[i] - 'a' else if (letters[n] != sq[i] - 'a') ok = false
            }
            if (ok && letters.toList().distinct().size == letters.size) out += letters.toList()
        }
        return out
    }

    @Test
    fun `steps stay sound from boards a player made, not only the solver's path`() {
        val rng = Rng(5)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            // Any subset of the answer is a board a player could have made.
            val letters = s.letters.toMutableList()
            for (n in rng.shuffled(letters.indices.toList()).take(rng.nextInt(letters.size))) letters[n] = s.solution[n]
            val now = s.copy(letters = letters)
            if (now.solved) continue
            val step = CodewordTeacher.teach(now)!!
            assertTrue(step.technique, step.technique != CodewordTeacher.MISTAKE && step.technique != CodewordTeacher.FALLBACK)
            assertEquals(step.technique, s.solution[step.number], step.letter)
            assertEquals(EMPTY, letters[step.number])
        }
    }

    @Test
    fun `a wrong letter is addressed first, and a right one is never called wrong`() {
        val rng = Rng(9)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val open = (1..s.letters.size).filter { it !in s.given }
            val right = open[rng.nextInt(open.size)]
            val wrong = open.filter { it != right }[rng.nextInt(open.size - 1)]
            val bad = (s.solution[wrong - 1] + 1 + rng.nextInt(25)) % 26
            val now = s.withLetter(right, s.solution[right - 1]).withLetter(wrong, bad)
            val step = Codeword.teach(now)!!
            assertTrue(step.mistake)
            val squares = s.numbers.indices.filter { s.numbers[it] == wrong }.toSet()
            assertEquals(squares + CodewordLogic.key(wrong) + CodewordLogic.letterKey(bad), step.targets)
            // Taking it back clears the hint; the right letter alone is never a mistake.
            assertTrue(step.isReached(step.apply(now)))
            val fixed = step.apply(now) as CodewordState
            assertEquals(EMPTY, fixed.letters[wrong - 1])
            assertFalse(Codeword.teach(fixed)!!.mistake)
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
                val n = (1..s.letters.size).first { it !in s.given }
                s = s.withLetter(n, (s.solution[n - 1] + 1 + rng.nextInt(25)) % 26)
            }
            while (!s.solved) {
                val step = CodewordTeacher.teach(s)!!
                longest = maxOf(longest, step.explanation.length)
                assertTrue(step.nudge, step.nudge.length <= CodewordTeacher.MAX_NUDGE)
                assertTrue(step.explanation, step.explanation.length <= CodewordTeacher.MAX_EXPLANATION)
                s = s.withLetter(step.number + 1, step.letter)
            }
        }
        println("longest explanation $longest")
    }

    @Test
    fun `the teacher cannot see the answer`() {
        // deduce takes the numbers and the letters placed, nothing else; a changed stored answer changes no step.
        val s = board(0, Difficulty.EXPERT)
        val other = s.copy(solution = s.solution.map { (it + 1) % 26 })
        val a = CodewordTeacher.teach(s)!!
        val b = CodewordTeacher.teach(other.copy(letters = s.letters))!!
        assertEquals(a.explanation, b.explanation)
        assertEquals(a.number to a.letter, b.number to b.letter)
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Codeword.tutorial
        assertEquals(5, frames.size)
        assertEquals(listOf(true, false, false, false, false), frames.map { it.judge == null && !it.freePlay })
        val a = Codeword.tutorialNumber('a')
        val x = Codeword.tutorialNumber('x')
        val r = Codeword.tutorialNumber('r')
        val b0 = Codeword.tutorialBoard()
        // The board has one answer, and it is the stored one; the oracle agrees.
        assertEquals(1, CodewordOracle.count(5, b0.numbers, b0.letters))
        assertEquals(b0.solution, (CodewordLogic.solve(5, b0.numbers, b0.letters) as CodewordLogic.Verdict.ExactlyOne).letters)
        // The captions' claims: only ATE fits the top word from the givens, only EXTRA the middle row once A is in.
        val shape = CodewordTeacher.Shape(5, b0.numbers, b0.letters.size)
        fun onlyWord(letters: List<Int>, squares: Set<Int>): String? {
            val r0 = shape.squares.indexOfFirst { it.toSet() == squares }
            val hit = CodewordTeacher.findSteps(shape, letters).firstOrNull { it.run == r0 && it.technique == CodewordTeacher.ONE_WORD }
            return hit?.let { com.joebywan.daybook.puzzles.CodewordWords.of(squares.size).words[it.words[0]] }
        }
        assertEquals("ate", onlyWord(b0.letters, Codeword.TUTORIAL_ATE))
        // Frame 2: A on a square of ATE, then the A.
        val pa = frames[1].steps!!
        assertEquals(TutorialStep.REJECT, pa(b0))
        val pickA = b0.select(a)
        assertEquals(TutorialStep.PROGRESS, pa(pickA))
        assertEquals(TutorialStep.REJECT, pa(b0.select(x)))
        assertEquals(TutorialStep.REJECT, pa(pickA.enter(a, 4)))
        val one = pickA.enter(a, 0)
        assertEquals(TutorialStep.DONE, pa(one))
        assertEquals("extra", onlyWord(one.letters, Codeword.TUTORIAL_EXTRA))
        // Frame 3: X through the key.
        val px = frames[2].steps!!
        assertEquals(one.letters, (frames[2].state as CodewordState).letters)
        val pickX = one.select(x)
        assertEquals(TutorialStep.PROGRESS, px(pickX))
        assertEquals(TutorialStep.REJECT, px(pickX.enter(x, 1)))
        val two = pickX.enter(x, 'x' - 'a')
        assertEquals(TutorialStep.DONE, px(two))
        // Frame 4: R gets X (both turn red), then X again clears it.
        val clash = frames[3].steps!!
        val c0 = frames[3].state as CodewordState
        assertEquals(two.letters, c0.letters)
        assertEquals(TutorialStep.REJECT, clash(c0))
        val pickR = c0.select(r)
        assertEquals(TutorialStep.PROGRESS, clash(pickR))
        assertEquals(TutorialStep.REJECT, clash(pickR.enter(r, 2)))
        val rx = pickR.enter(r, 'x' - 'a')
        assertEquals(TutorialStep.PROGRESS, clash(rx))
        assertTrue(frames[3].glow!!(rx).warning)
        assertEquals(setOf(r, x), CodewordLogic.conflicts(5, rx.numbers, rx.letters).numbers)
        assertTrue(Codeword.teach(rx)!!.mistake)
        assertEquals(TutorialStep.DONE, clash(rx.enter(r, 'x' - 'a')))
        // Frame 5 is free play, and hints alone finish it with no fallback and no mistake.
        assertTrue(frames[4].freePlay)
        var now = frames[4].state as CodewordState
        assertEquals(two.letters, now.letters)
        while (!now.solved) {
            val d = Codeword.teach(now)!!
            assertFalse(d.fallback); assertFalse(d.mistake)
            now = d.apply(now) as CodewordState
        }
        assertEquals(b0.solution, now.letters)
        for (f in frames) assertTrue("caption ${f.caption.length}", f.caption.length <= 170)
    }
}
