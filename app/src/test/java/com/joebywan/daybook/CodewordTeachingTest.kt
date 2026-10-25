package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Codeword
import com.joebywan.daybook.puzzles.CodewordLogic
import com.joebywan.daybook.puzzles.CodewordLogic.EMPTY
import com.joebywan.daybook.puzzles.CodewordState
import com.joebywan.daybook.puzzles.CodewordTeacher
import com.joebywan.daybook.puzzles.WheelWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CodewordTeachingTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun board(day: Int, d: Difficulty) =
        Codeword.generate(DailySeed.seedFor(start.plusDays(day.toLong()), Codeword.id, d), d) as CodewordState

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
                    assertFalse("${step.technique}", step.mistake)
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
        File("build/reports").mkdirs()
        File("build/reports/codeword-teaching-coverage.txt").writeText(report.toString())
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
}
