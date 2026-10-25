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

class CodewordRulesTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun board(day: Int, d: Difficulty) =
        Codeword.generate(DailySeed.seedFor(start.plusDays(day.toLong()), Codeword.id, d), d) as CodewordState

    private fun solved(s: CodewordState) = s.copy(letters = s.solution)

    /** A grid read as rows of letters (`#` black); numbers in order of the letters' first appearance. */
    private fun grid(vararg rows: String): CodewordState {
        val squares = rows.joinToString("")
        val alphabet = squares.filter { it != '#' }.toList().distinct()
        val numbers = squares.map { if (it == '#') 0 else alphabet.indexOf(it) + 1 }
        val solution = alphabet.map { it - 'a' }
        return CodewordState(rows.size, numbers, solution, emptyList(), List(alphabet.size) { EMPTY })
    }

    /** Every across and down run of white squares, my own reading of the grid: maximal, of any length. */
    private fun lines(e: CodewordLogic.Entry): List<String> {
        val n = e.size
        val rows = (0 until n).map { r -> e.squares.substring(r * n, r * n + n) }
        val cols = (0 until n).map { c -> String(CharArray(n) { e.squares[it * n + c] }) }
        return (rows + cols).flatMap { it.split('#') }.filter { it.isNotEmpty() }
    }

    @Test
    fun `every listed grid is square, symmetric, connected, fully checked and spells listed words once each`() {
        for (d in Difficulty.entries) {
            val list = CodewordLogic.entries(d)
            assertEquals("${d.name} is sorted", list.sorted(), list)
            assertEquals("${d.name} has no repeat", list.size, list.map { it.substringBefore(':') }.distinct().size)
            assertTrue(list.size >= 365)
            for (entry in list) {
                val e = CodewordLogic.parse(entry)
                assertEquals(entry, CodewordLogic.specFor(d).size, e.size)
                val n = e.size
                for (i in e.squares.indices) assertEquals("$entry symmetric", e.squares[i] == '#', e.squares[n * n - 1 - i] == '#')
                val words = lines(e)
                assertTrue("$entry: every run 3 to 7 letters", words.all { it.length in 3..7 })
                assertTrue("$entry: every run a grid word", words.all { WheelWords.isGridWord(it) })
                assertEquals("$entry: no word twice", words.size, words.distinct().size)
                assertTrue("$entry: no skipped word", words.none { it in CodewordGridsBuildTest.SKIP })
                assertTrue("$entry: givens", e.givens.isNotEmpty() && e.givens.all { it - 'a' in e.alphabet })
                // Connected: flood from the first white square.
                val seen = mutableSetOf(e.squares.indexOfFirst { it != '#' })
                val stack = ArrayDeque(seen.toList())
                while (stack.isNotEmpty()) {
                    val s = stack.removeLast()
                    for (t in listOf(s - n, s + n, if (s % n > 0) s - 1 else -1, if (s % n < n - 1) s + 1 else -1)) {
                        if (t in e.squares.indices && e.squares[t] != '#' && seen.add(t)) stack.addLast(t)
                    }
                }
                assertEquals("$entry connected", e.squares.count { it != '#' }, seen.size)
            }
        }
    }

    @Test
    fun `a sample of listed grids is proved by the ladder and the search, and the tiers need what they say`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) {
            val list = CodewordLogic.entries(d)
            repeat(40) {
                val entry = list[rng.nextInt(list.size)]
                val s = CodewordLogic.board(CodewordLogic.parse(entry), Rng(rng.nextLong()))
                assertTrue(entry, s != null)
                assertEquals(entry, CodewordLogic.parse(entry).squares, String(CharArray(s!!.numbers.size) { if (s.numbers[it] == 0) '#' else 'a' + s.solution[s.numbers[it] - 1] }))
                val needs = Codeword.neededTechnique(s)
                val want = CodewordLogic.specFor(d).needs
                if (d == Difficulty.STANDARD) assertTrue("$entry needs $needs", CodewordTeacher.TECHNIQUES.indexOf(needs!!) <= CodewordTeacher.TECHNIQUES.indexOf(want))
                else assertEquals(entry, want, needs)
            }
        }
    }

    @Test
    fun `the search agrees with an oracle that walks the alphabet letter by letter`() {
        val rng = Rng(5)
        val samples = mapOf(Difficulty.STANDARD to 12, Difficulty.HARD to 4, Difficulty.EXPERT to 2)
        for ((d, count) in samples) repeat(count) {
            val s = board(rng.nextInt(365), d)
            val known = s.letters
            assertEquals(1, CodewordOracle.count(s.size, s.numbers, known))
            val v = CodewordLogic.solve(s.size, s.numbers, known)
            assertTrue(v is CodewordLogic.Verdict.ExactlyOne)
            assertEquals(s.solution, (v as CodewordLogic.Verdict.ExactlyOne).letters)
        }
        // Small boards with several answers or none: the two must agree there too.
        val cat = grid("cat", "###", "###")
        assertTrue(CodewordLogic.solve(3, cat.numbers, cat.letters) is CodewordLogic.Verdict.Several)
        assertEquals(2, CodewordOracle.count(3, cat.numbers, cat.letters))
        val noWord = CodewordState(3, listOf(1, 1, 1, 0, 0, 0, 0, 0, 0), listOf(0), emptyList(), listOf(EMPTY))
        assertTrue(CodewordLogic.solve(3, noWord.numbers, noWord.letters) is CodewordLogic.Verdict.None)
        assertEquals(0, CodewordOracle.count(3, noWord.numbers, noWord.letters))
    }

    @Test
    fun `the rules decide a win, not the stored answer`() {
        val cat = grid("cat", "###", "###")
        val b = 'b' - 'a'
        assertTrue("the stored word wins", solved(cat).solved)
        assertTrue("another word with the same shape wins too", cat.copy(letters = listOf(b, 0, 19)).solved)
        assertFalse("not a word", cat.copy(letters = listOf(25, 25 - 1, 19)).solved)
        assertFalse("a letter missing", cat.copy(letters = listOf(2, 0, EMPTY)).solved)
        val tot = grid("tot", "###", "###")
        assertFalse("two numbers can't share a letter", tot.copy(letters = listOf(19, 19)).solved)
        for (d in Difficulty.entries) {
            val s = board(3, d)
            assertFalse(s.solved)
            assertTrue(solved(s).solved)
        }
    }

    @Test
    fun `conflicts name twin numbers and runs no word fits, and nothing on a correct board`() {
        val s = board(9, Difficulty.HARD)
        assertTrue(CodewordLogic.conflicts(s.size, s.numbers, s.solution).let { it.numbers.isEmpty() && it.runs.isEmpty() })
        assertTrue(CodewordLogic.conflicts(s.size, s.numbers, s.letters).let { it.numbers.isEmpty() && it.runs.isEmpty() })
        val open = (1..s.letters.size).first { it !in s.given }
        val twin = s.withLetter(open, s.letters[s.given[0] - 1])
        assertEquals(setOf(open, s.given[0]), CodewordLogic.conflicts(s.size, s.numbers, twin.letters).numbers)
        val cat = grid("cat", "###", "###")
        assertEquals(setOf(0), CodewordLogic.conflicts(3, cat.numbers, listOf('x' - 'a', 'q' - 'a', EMPTY)).runs)
        assertTrue(CodewordLogic.conflicts(3, cat.numbers, listOf(2, 0, EMPTY)).runs.isEmpty())
    }

    @Test
    fun `a given cannot change or be selected, and the moves count only changes`() {
        val s = board(2, Difficulty.STANDARD)
        val g = s.given[0]
        assertTrue(s.withLetter(g, EMPTY) === s)
        assertTrue(s.select(g) === s)
        val open = (1..s.letters.size).first { it !in s.given }
        assertEquals(1, s.withLetter(open, 4).moves)
        assertTrue(s.withLetter(open, 4).let { it.withLetter(open, 4) === it })
    }

    @Test
    fun `every tier's last resort is proved and matches its tier`() {
        for (d in Difficulty.entries) {
            val s = CodewordLogic.lastResort(d)
            assertEquals(CodewordLogic.specFor(d).size, s.size)
            assertTrue(CodewordLogic.solve(s.size, s.numbers, s.letters) is CodewordLogic.Verdict.ExactlyOne)
            assertTrue(CodewordTeacher.solvesByLogic(s.size, s.numbers, s.letters))
        }
    }

    @Test
    fun `a year of daily boards - the tiers get bigger and need more, generation is quick`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val needs = mutableMapOf<String?, Int>()
            val times = mutableListOf<Long>()
            var givens = 0
            for (day in 0 until 365) {
                val seed = DailySeed.seedFor(start.plusDays(day.toLong()), Codeword.id, d)
                val t0 = System.nanoTime()
                val s = Codeword.generateVerified(seed, d)!!
                times += System.nanoTime() - t0
                givens += s.given.size
                val need = Codeword.neededTechnique(s)
                needs.merge(need, 1, Int::plus)
                assertEquals(CodewordLogic.specFor(d).size, s.size)
                if (d == Difficulty.STANDARD) assertTrue("$need", need in setOf(CodewordTeacher.ONE_WORD, CodewordTeacher.RUN_AGREES))
                else assertEquals(CodewordLogic.specFor(d).needs, need)
            }
            times.sort()
            report.appendLine(
                "$d: needs $needs, givens mean ${"%.2f".format(givens / 365.0)}, generation median ${times[182] / 1_000_000} ms, " +
                    "p95 ${times[346] / 1_000_000} ms, max ${times.last() / 1_000_000} ms",
            )
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/codeword-tiers.txt").writeText(report.toString())
    }
}
