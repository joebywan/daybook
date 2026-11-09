package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.GuessMarks
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Equation
import com.joebywan.daybook.puzzles.EquationLogic
import com.joebywan.daybook.puzzles.EquationState
import com.joebywan.daybook.puzzles.GuessKeyAction
import com.joebywan.daybook.puzzles.equationKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.TimeSource

/**
 * Equation's rules against [EquationOracle], which shares nothing with them: the lists, what a row may be, the marks, the
 * boards and the daily cycle.
 */
class EquationRulesTest {

    @Test
    fun `the enumerated lists are exactly every true sum the oracle finds by trying every left side`() {
        for (length in EquationLogic.LENGTHS) {
            val oracle = EquationOracle.lists.getValue(length)
            assertEquals("$length symbols", oracle, EquationLogic.valid(length))
            assertEquals(
                "$length-symbol answers are the true sums with no lone 0 and no × or ÷ by 1",
                oracle.filter { !EquationOracle.loneZero(it) && !EquationOracle.timesOne(it) },
                EquationLogic.answers(length),
            )
        }
        // Pinned: a change to the rules or the enumeration moves every board, so it should be seen.
        assertEquals(listOf(476, 11705, 65704), (6..8).map { EquationLogic.valid(it).size })
        assertEquals(listOf(206, 5431, 15963), (6..8).map { EquationLogic.answers(it).size })
    }

    @Test
    fun `a row may be submitted exactly when the oracle calls it a true sum`() {
        val rng = Rng(7)
        val alphabet = EquationLogic.ALPHABET
        for (length in 6..8) {
            val valid = EquationLogic.valid(length)
            for (row in valid) assertNull(row, EquationLogic.problem(row, length))
            // Every one-symbol change of a sample of true sums, and random rows heavy in = and digits.
            for (row in valid.filterIndexed { i, _ -> i % 37 == 0 }) for (i in row.indices) for (c in alphabet) {
                val near = row.substring(0, i) + c + row.substring(i + 1)
                assertEquals(near, EquationOracle.isTrue(near), EquationLogic.problem(near, length) == null)
            }
            repeat(20_000) {
                val row = String(CharArray(length) { alphabet[rng.nextInt(alphabet.length)] })
                assertEquals(row, EquationOracle.isTrue(row), EquationLogic.problem(row, length) == null)
            }
        }
        // Each rule on its own, with the line it shows.
        assertEquals("Not enough symbols", EquationLogic.problem("1+2=3", 6))
        assertEquals("A sum needs =", EquationLogic.problem("12+345", 6))
        assertEquals("Only one = in a sum", EquationLogic.problem("1=1=11", 6))
        assertEquals("Only a number after =", EquationLogic.problem("12=3+9", 6))
        assertEquals("Start with a number", EquationLogic.problem("-1+2=1", 6))
        assertEquals("A number goes before =", EquationLogic.problem("12+3+=7", 7))
        assertEquals("Use + − × or ÷ before =", EquationLogic.problem("1234=5", 6))
        assertEquals("A number goes between signs", EquationLogic.problem("1+*2=3", 6))
        assertEquals("No number starts with 0", EquationLogic.problem("01+2=3", 6))
        assertEquals("Nothing divides by 0", EquationLogic.problem("10/0=1", 6))
        assertEquals("Each ÷ must come out whole", EquationLogic.problem("3/2*4=6", 7))
        assertEquals("The left side is below 0", EquationLogic.problem("1-20=19", 7))
        assertEquals("Not true: the left makes 15", EquationLogic.problem("12+3=14", 7))
        // Precedence, left-to-right division, and a running total that dips below 0.
        assertTrue(EquationLogic.isTrue("2+3*4=14"))
        assertFalse(EquationLogic.isTrue("2+3*4=20"))
        assertTrue(EquationLogic.isTrue("8/4*3=6"))
        assertTrue(EquationLogic.isTrue("1-5+6=2"))
        assertTrue(EquationLogic.isTrue("5-5=0"))
    }

    @Test
    fun `every refusal fits the header line`() {
        val rng = Rng(11)
        val alphabet = EquationLogic.ALPHABET
        val seen = HashSet<String>()
        repeat(50_000) {
            val row = String(CharArray(8) { alphabet[rng.nextInt(alphabet.length)] })
            EquationLogic.problem(row, 8)?.let { seen += it }
        }
        for (line in seen) assertTrue(line, line.length <= 30)
    }

    @Test
    fun `marks agree with the reference marker on sums, repeated symbols included`() {
        for (length in 6..8) {
            val valid = EquationLogic.valid(length)
            val rng = Rng(length.toLong())
            repeat(20_000) {
                val g = valid[rng.nextInt(valid.size)]
                val a = valid[rng.nextInt(valid.size)]
                assertEquals("$g against $a", LexiconSupport.refMark(g, a), GuessMarks.mark(g, a))
            }
        }
        // A repeated symbol is marked only as many times as the answer holds it: against 9+11=20 the 1 in place is
        // green, the first spare 1 yellow and the others grey.
        assertEquals(listOf(1, 0, 1, 2, 2, 0, 1), GuessMarks.mark("11+1=12", "9+11=20"))
    }

    @Test
    fun `boards are one pick from the tier's answers, the same for a seed, and solved only by the answer`() {
        assertEquals(Equation, PuzzleRegistry.byId("equation"))
        for (tier in Difficulty.entries) {
            val shape = EquationLogic.shape(tier)
            for (day in 0 until 50) {
                val s = EquationSupport.board(day, tier)
                assertEquals(shape.length, s.length)
                assertEquals(shape.guesses, s.maxGuesses)
                assertTrue(s.answer in EquationLogic.answers(s.length))
                assertEquals(s, EquationSupport.board(day, tier))
                assertFalse(s.solved)
            }
        }
        val s = EquationState(6, 2, "5+8=13")
        val probe = "9+9=18".fold(s) { acc, c -> acc.withLetter(c) }.submit()
        assertFalse(probe.solved)
        assertFalse(probe.failed)
        val won = "5+8=13".fold(probe) { acc, c -> acc.withLetter(c) }.submit()
        assertTrue(won.solved)
        val lost = "8+5=13".fold(probe) { acc, c -> acc.withLetter(c) }.submit()
        assertTrue(lost.failed && !lost.solved && !lost.open)
        // A full row takes no more symbols, and an empty one gives none back.
        assertEquals(probe.copy(current = "5+8=13"), "5+8=13".fold(probe) { acc, c -> acc.withLetter(c) }.withLetter('1').copy(moves = probe.moves))
        assertEquals(s, s.withoutLetter())
    }

    @Test
    fun `daily sums go round a cycle, every answer once a round, and random seeds draw`() {
        val from = SeedHash.EQUATION_CYCLE_FROM
        for (tier in Difficulty.entries) {
            val answers = EquationLogic.answers(EquationLogic.shape(tier).length)
            val n = answers.size
            val rounds = if (n < 1000) 2 else 1
            for (round in 0 until rounds) {
                val days = (0 until n).map { from + round.toLong() * n + it }
                val seen = days.map { d ->
                    val seed = SeedHash.daily(d, "equation", tier)
                    assertEquals(d - from, SeedHash.cycleDay(seed, "equation"))
                    EquationLogic.newBoard(seed, tier).answer
                }
                assertEquals("${tier.name} round $round", answers.toSet(), seen.toSet())
            }
            for (nonce in 0L until 500L) assertNull(SeedHash.cycleDay(SeedHash.random("equation", tier, nonce), "equation"))
        }
        // The tiers' cycle seeds differ, and Lexicon's tag is not Equation's.
        assertEquals(3, Difficulty.entries.map { SeedHash.daily(from + 5, "equation", it) }.toSet().size)
        assertNull(SeedHash.cycleDay(SeedHash.daily(SeedHash.LEXICON_CYCLE_FROM + 3, "words", Difficulty.HARD), "equation"))
    }

    @Test
    fun `enumeration is cheap`() {
        // A fresh enumeration, not the cache: the JVM's figure; the web's is in CLAUDE.md.
        val times = (6..8).map { length ->
            val t = TimeSource.Monotonic.markNow()
            val method = EquationLogic::class.java.getDeclaredMethod("enumerate", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(EquationLogic, length)
            t.elapsedNow().inWholeMilliseconds
        }
        println("enumeration ms, 6 / 7 / 8 symbols: $times")
        assertTrue("$times", times.all { it < 1000 })
    }

    @Test
    fun `keys type digits, signs and =, with Shift where a keyboard needs it`() {
        assertEquals(GuessKeyAction.Letter('7'), equationKeyAction(Key.Seven))
        assertEquals(GuessKeyAction.Letter('7'), equationKeyAction(Key.NumPad7))
        assertEquals(GuessKeyAction.Letter('+'), equationKeyAction(Key.Equals, shift = true))
        assertEquals(GuessKeyAction.Letter('='), equationKeyAction(Key.Equals))
        assertEquals(GuessKeyAction.Letter('*'), equationKeyAction(Key.Eight, shift = true))
        assertEquals(GuessKeyAction.Letter('*'), equationKeyAction(Key.X))
        assertEquals(GuessKeyAction.Letter('-'), equationKeyAction(Key.Minus))
        assertEquals(GuessKeyAction.Letter('/'), equationKeyAction(Key.Slash))
        assertEquals(GuessKeyAction.Letter('+'), equationKeyAction(Key.NumPadAdd))
        assertEquals(GuessKeyAction.Enter, equationKeyAction(Key.Enter))
        assertEquals(GuessKeyAction.Backspace, equationKeyAction(Key.Backspace))
        assertNull(equationKeyAction(Key.Seven, shift = true))
        assertNull(equationKeyAction(Key.A))
        assertNotNull(equationKeyAction(Key.NumPadEquals))
    }
}
