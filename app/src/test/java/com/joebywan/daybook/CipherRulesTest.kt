package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Cipher
import com.joebywan.daybook.puzzles.CipherLogic
import com.joebywan.daybook.puzzles.CipherLogic.EMPTY
import com.joebywan.daybook.puzzles.CipherState
import com.joebywan.daybook.puzzles.CipherSum
import com.joebywan.daybook.puzzles.CipherTeacher
import com.joebywan.daybook.puzzles.WordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CipherRulesTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun board(day: Int, d: Difficulty) =
        Cipher.generate(DailySeed.seedFor(start.plusDays(day.toLong()), Cipher.id, d), d) as CipherState

    private fun state(words: List<String>, digits: List<Int>) =
        CipherState(words = words, solution = digits, digits = digits)

    @Test
    fun `the list is sorted, distinct, well formed and the words are answers`() {
        val fours = WordList.answers(4).toSet()
        val fives = WordList.answers(5).toSet()
        val seen = mutableSetOf<String>()
        for (d in Difficulty.entries) {
            val list = CipherLogic.sums(d)
            assertEquals("${d.name} is sorted", list.sorted(), list)
            assertEquals(1200, list.size)
            for (entry in list) {
                assertTrue("$entry appears once", seen.add(entry))
                val sum = CipherLogic.parse(entry)
                assertTrue(entry, sum.wellFormed)
                assertTrue(entry, sum.addends.all { it in fours } && sum.total in fives)
            }
        }
    }

    @Test
    fun `every sum in the list is proved, finished by the ladder, and in its own tier`() {
        for (d in Difficulty.entries) {
            for (entry in CipherLogic.sums(d)) {
                val sum = CipherLogic.parse(entry)
                assertTrue(entry, CipherLogic.prove(sum) is CipherLogic.Verdict.ExactlyOne)
                assertEquals(entry, d, CipherSumsBuildTest.tierOf(sum))
            }
        }
    }

    @Test
    fun `the exhaustive proof agrees with a solver that tries every digit for every letter`() {
        val rng = Rng(7)
        var several = 0
        var none = 0
        // Listed sums, which have one answer...
        for (d in Difficulty.entries) {
            repeat(12) {
                val entry = rng.pick(CipherLogic.sums(d))
                val sum = CipherLogic.parse(entry)
                val answers = CipherOracle.answers(sum.words)
                assertEquals(entry, 1, answers.size)
                assertEquals(entry, answers[0], (CipherLogic.prove(sum) as CipherLogic.Verdict.ExactlyOne).digits)
            }
        }
        // ...and random ones, which mostly have none or several.
        repeat(150) {
            val words = listOf(rng.pick(WordList.answers(4)), rng.pick(WordList.answers(4)), rng.pick(WordList.answers(5)))
            val sum = CipherSum(words)
            if (!sum.wellFormed) return@repeat
            val answers = CipherOracle.answers(words)
            when (val v = CipherLogic.prove(sum)) {
                CipherLogic.Verdict.None -> { none++; assertEquals(words.toString(), 0, answers.size) }
                CipherLogic.Verdict.Several -> { several++; assertTrue(words.toString(), answers.size >= 2) }
                is CipherLogic.Verdict.ExactlyOne -> assertEquals(words.toString(), listOf(v.digits), answers)
            }
        }
        assertTrue("the sample reached sums with several answers ($several) and none ($none)", several > 5 && none > 5)
    }

    @Test
    fun `the stored answer obeys the rules`() {
        for (d in Difficulty.entries) for (day in 0 until 30) {
            val s = board(day, d)
            assertFalse(s.solved)
            assertTrue(s.digits.all { it == EMPTY })
            assertTrue(state(s.words, s.solution).solved)
        }
    }

    @Test
    fun `each rule refuses on its own`() {
        // TO + GO = OUT is 21 + 81 = 102 (letters G O T U).
        val words = listOf("to", "go", "out")
        assertTrue(state(words, listOf(8, 1, 2, 0)).solved)
        assertFalse("a letter with no digit", state(words, listOf(8, 1, 2, EMPTY)).solved)
        assertFalse("a sum that does not add up", state(words, listOf(7, 1, 2, 0)).solved)
        // AA + BB = CC: 11 + 22 = 33 wins; 11 + 11 = 22 adds up but A and B share a digit.
        val twins = CipherSum(listOf("aa", "bb", "cc"))
        assertTrue(CipherLogic.isSolved(twins, listOf(1, 2, 3)))
        assertFalse("two letters on one digit", CipherLogic.isSolved(twins, listOf(1, 1, 2)))
        // AB + CD = CE: 05 + 14 = 19 adds up with every digit different, but AB starts with 0.
        assertFalse("a word starting with 0", CipherLogic.isSolved(CipherSum(listOf("ab", "cd", "ce")), listOf(0, 5, 1, 4, 9)))
    }

    @Test
    fun `the win check reads the rules, so any legal answer wins`() {
        // A sum with several answers: the stored one is not the only way to win.
        val rng = Rng(11)
        var checked = 0
        while (checked < 20) {
            val words = listOf(rng.pick(WordList.answers(4)), rng.pick(WordList.answers(4)), rng.pick(WordList.answers(5)))
            if (!CipherSum(words).wellFormed) continue
            val answers = CipherOracle.answers(words)
            if (answers.size < 2) continue
            val s = CipherState(words = words, solution = answers[0], digits = answers[1])
            assertTrue(words.toString(), s.solved)
            checked++
        }
    }

    @Test
    fun `conflicts name shared digits, leading zeros and the first column that does not add up`() {
        val sum = CipherSum(listOf("to", "go", "out")) // G O T U
        val share = CipherLogic.conflicts(sum, listOf(EMPTY, 1, EMPTY, 1))
        assertEquals(setOf(1, 3), share.letters)
        assertNull(share.column)
        val lead = CipherLogic.conflicts(sum, listOf(0, EMPTY, EMPTY, EMPTY))
        assertEquals(setOf(0), lead.letters)
        // Units: O + O ends in T. 1 + 1 = 2, so T = 3 is wrong; with T empty nothing is known yet.
        assertEquals(0, CipherLogic.conflicts(sum, listOf(EMPTY, 1, 3, EMPTY)).column)
        assertNull(CipherLogic.conflicts(sum, listOf(EMPTY, 1, EMPTY, EMPTY)).column)
        // 21 + 71 = 92: the tens column adds up (2 + 7 = 9 = U) but carries nothing, so the hundreds' O = 1 is wrong.
        assertEquals(2, CipherLogic.conflicts(sum, listOf(7, 1, 2, 9)).column)
        // 21 + 81 = 102 with U = 1: the tens column says U is 0.
        assertEquals(1, CipherLogic.conflicts(sum, listOf(8, 1, 2, 1)).column)
        assertNull(CipherLogic.conflicts(sum, listOf(8, 1, 2, 0)).column)
    }

    @Test
    fun `the last resort is a proved board on every tier`() {
        for (d in Difficulty.entries) {
            val s = CipherLogic.lastResort(d)
            assertFalse(s.solved)
            assertEquals(listOf(s.solution), CipherOracle.answers(s.words))
            assertEquals(d, CipherSumsBuildTest.tierOf(s.sum))
        }
    }

    @Test
    fun `the walkthrough sum has exactly one answer, and it is the stored one`() {
        assertEquals(listOf(Cipher.TUTORIAL_SOLUTION), CipherOracle.answers(Cipher.TUTORIAL_WORDS))
        assertNotNull(CipherTeacher.walk(CipherSum(Cipher.TUTORIAL_WORDS)))
    }

    @Test
    fun `a year of daily boards - the tiers get harder in the order they are offered`() {
        val report = StringBuilder()
        val means = Difficulty.entries.map { d ->
            val counts = mutableMapOf<String, Int>()
            val times = mutableListOf<Double>()
            for (day in 0 until 365) {
                val seed = DailySeed.seedFor(start.plusDays(day.toLong()), Cipher.id, d)
                val t0 = System.nanoTime()
                val s = Cipher.generate(seed, d) as CipherState
                times += (System.nanoTime() - t0) / 1e6
                for (h in CipherTeacher.walk(s.sum)!!) counts.merge(h.technique, 1, Int::plus)
            }
            times.sort()
            report.appendLine(
                "${d.name}: steps per board " + counts.toSortedMap().map { "${it.key} %.2f".format(it.value / 365.0) } +
                    " ms median %.1f p95 %.1f max %.1f".format(times[182], times[346], times.last()),
            )
            // What a tier asks: the share of steps that need more than one column on its own.
            (counts[CipherTeacher.PAIR] ?: 0) + 2 * (counts[CipherTeacher.CHAIN] ?: 0) + 4 * (counts[CipherTeacher.WHAT_IF] ?: 0)
        }
        File("build/reports").mkdirs()
        File("build/reports/cipher-tiers.txt").writeText(report.toString())
        println(report)
        assertTrue("tiers ask more in order: $means", means[0] < means[1] && means[1] < means[2])
    }
}
