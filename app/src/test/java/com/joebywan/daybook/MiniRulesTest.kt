package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.Mini
import com.joebywan.daybook.puzzles.MiniLogic
import com.joebywan.daybook.puzzles.MiniLogic.BLACK
import com.joebywan.daybook.puzzles.MiniLogic.OPEN
import com.joebywan.daybook.puzzles.MiniState
import com.joebywan.daybook.puzzles.MiniTeacher
import com.joebywan.daybook.puzzles.WheelWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniRulesTest {


    private fun board(day: Int, d: Difficulty) = Mini.generate(daySeed(day, Mini.id, d), d) as MiniState

    private fun solved(s: MiniState) = s.copy(letters = s.answer)

    /** The letters the hints' walk reads from the clues, placed on an empty grid. */
    private fun clueLetters(size: Int, answer: String): String {
        val read = CharArray(answer.length) { if (answer[it] == BLACK) BLACK else OPEN }
        for (hit in MiniTeacher.walk(size, answer)!!) if (hit.technique == MiniTeacher.CLUE) read[hit.square] = hit.letter
        return read.concatToString()
    }

    @Test
    fun `every listed grid is square, symmetric, connected, fully checked and spells clued listed words once each`() {
        for (d in Difficulty.entries) {
            val list = MiniLogic.entries(d)
            assertEquals("${d.name} is sorted", list.sorted(), list)
            assertEquals("${d.name} has no repeat", list.size, list.distinct().size)
            assertTrue("${d.name} has ${list.size}", list.size >= 365)
            val n = MiniLogic.specFor(d).size
            for (e in list) {
                assertEquals(e, n * n, e.length)
                for (i in e.indices) assertEquals("$e symmetric", e[i] == '#', e[n * n - 1 - i] == '#')
                val words = MiniOracle.runs(n, e)
                assertTrue("$e: every run 3 to $n letters", words.all { it.length in 3..n })
                assertTrue("$e: every run a grid word", words.all { WheelWords.isGridWord(it) })
                assertEquals("$e: no word twice", words.size, words.distinct().size)
                assertTrue("$e: nothing off the breakfast table", words.none { it in MiniGridsBuildTest.SKIP })
                // Fully checked: every white square is in a run both ways (a run of one would be a lone square).
                for (i in e.indices) if (e[i] != '#') {
                    val r = i / n
                    val c = i % n
                    assertTrue("$e checked across at $i", (c > 0 && e[i - 1] != '#') || (c < n - 1 && e[i + 1] != '#'))
                    assertTrue("$e checked down at $i", (r > 0 && e[i - n] != '#') || (r < n - 1 && e[i + n] != '#'))
                }
                val seen = mutableSetOf(e.indexOfFirst { it != '#' })
                val stack = ArrayDeque(seen.toList())
                while (stack.isNotEmpty()) {
                    val s = stack.removeLast()
                    for (t in listOf(s - n, s + n, if (s % n > 0) s - 1 else -1, if (s % n < n - 1) s + 1 else -1)) {
                        if (t in e.indices && e[t] != '#' && seen.add(t)) stack.addLast(t)
                    }
                }
                assertEquals("$e connected", e.count { it != '#' }, seen.size)
            }
        }
    }

    @Test
    fun `slots are numbered as a printed crossword is and agree with an independent reading`() {
        // Letters need not spell words for the numbering: a 4x4 with two corners black.
        val grid = "#ace" + "dove" + "agar" + "mid#"
        val slots = MiniLogic.slots(4, grid)
        assertEquals(listOf("1A", "4A", "5A", "6A", "1D", "2D", "3D", "4D"), slots.map { "${it.number}${if (it.across) "A" else "D"}" })
        assertEquals(MiniOracle.runs(4, grid).sorted(), slots.map { MiniLogic.spelled(grid, it) }.sorted())
        for (d in Difficulty.entries) {
            val s = board(4, d)
            assertEquals(MiniOracle.runs(s.size, s.answer).sorted(), MiniLogic.slots(s.size, s.answer).map { MiniLogic.spelled(s.answer, it) }.sorted())
        }
    }

    @Test
    fun `a sample of listed grids is proved on the device, with the clue share in the tier's band`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) {
            val list = MiniLogic.entries(d)
            val spec = MiniLogic.specFor(d)
            repeat(40) {
                val e = list[rng.nextInt(list.size)]
                val s = MiniLogic.board(e, d)
                assertNotNull(e, s)
                assertEquals(e, s!!.answer)
                val read = clueLetters(spec.size, e)
                assertTrue(e, spec.accepts(read.count { it in 'a'..'z' }, e.count { it != BLACK }))
                assertEquals("$e: the clue letters and the crossings leave one grid", 1, MiniOracle.count(spec.size, read))
            }
        }
    }

    @Test
    fun `the search agrees with an oracle that walks the alphabet square by square`() {
        val rng = Rng(5)
        for (d in Difficulty.entries) repeat(if (d == Difficulty.EXPERT) 4 else 10) {
            val s = board(rng.nextInt(365), d)
            val read = clueLetters(s.size, s.answer)
            val v = MiniLogic.solve(s.size, read)
            assertTrue(v is Verdict.ExactlyOne)
            assertEquals(s.answer, (v as Verdict.ExactlyOne).value)
            assertEquals(1, MiniOracle.count(s.size, read))
            // One clue letter fewer: both see several grids, or both still one.
            val drop = read.indexOfFirst { it in 'a'..'z' }
            val fewer = read.substring(0, drop) + OPEN + read.substring(drop + 1)
            val many = MiniLogic.solve(s.size, fewer) is Verdict.Several
            assertEquals(if (many) 2 else 1, MiniOracle.count(s.size, fewer))
        }
        // No word fits: both say none.
        val none = "###" + "qqq" + "###"
        assertTrue(MiniLogic.solve(3, none) is Verdict.None)
        assertEquals(0, MiniOracle.count(3, none))
    }

    @Test
    fun `the rules decide a win, not the stored answer`() {
        for (d in Difficulty.entries) {
            val s = board(3, d)
            assertFalse(s.solved)
            assertTrue(solved(s).solved)
            assertTrue("the stored answer is not read", solved(s).copy(answer = s.answer.map { if (it == BLACK) BLACK else 'q' }.joinToString("")).solved)
            val sq = s.answer.indexOfFirst { it != BLACK }
            val other = if (s.answer[sq] == 'e') 'a' else 'e'
            assertFalse("one letter off", solved(s).withLetter(sq, other).solved)
            assertFalse("one letter missing", solved(s).withLetter(sq, OPEN).solved)
            // A real word under another clue is not a win: the clue is the rule.
            val swapped = s.clues.toMutableList().also { val t = it[0]; it[0] = it[1]; it[1] = t }
            assertFalse(solved(s).copy(clues = swapped).solved)
        }
    }

    @Test
    fun `a slot is dead when it is full and no word of the list, never for a word under another clue`() {
        val s = board(7, Difficulty.STANDARD)
        val slots = MiniLogic.slots(s.size, s.answer)
        assertTrue(MiniLogic.deadSlots(s.size, s.answer).isEmpty())
        assertTrue(MiniLogic.deadSlots(s.size, s.letters).isEmpty())
        val first = slots[0]
        var t = s
        for (sq in first.squares) t = t.withLetter(sq, 'q')
        assertTrue(0 in MiniLogic.deadSlots(s.size, t.letters))
        val partial = t.withLetter(first.squares[0], OPEN)
        assertTrue(0 !in MiniLogic.deadSlots(s.size, partial.letters))
        val other = WheelWords.grid(first.length).first { it != MiniLogic.spelled(s.answer, first) }
        assertTrue(0 !in MiniLogic.deadSlots(s.size, s.withWord(0, other).letters))
    }

    @Test
    fun `typing walks the word, a second tap turns, Backspace steps back, and each is one state`() {
        val s = board(1, Difficulty.HARD)
        val slots = MiniLogic.slots(s.size, s.answer)
        val a = slots.first { it.across && it.length >= 3 }
        val at = s.select(a.squares[0])
        assertEquals(a.squares[0], at.cursor)
        assertTrue(at.across)
        assertEquals(0, at.moves)
        val one = at.type('x')
        assertEquals('x', one.letters[a.squares[0]])
        assertEquals(a.squares[1], one.cursor)
        assertEquals(1, one.moves)
        assertEquals(a.squares[0], one.erase().cursor)
        assertEquals(OPEN, one.erase().letters[a.squares[0]])
        assertFalse(at.select(a.squares[0]).across)
        assertTrue(at.select(a.squares[0]).select(a.squares[0]).across)
        val black = s.answer.indexOf(BLACK)
        if (black >= 0) assertTrue(s.select(black) === s)
        assertEquals("the same letter again changes nothing but the cursor", one.moves, one.copy(cursor = a.squares[0]).type('x').moves)
        assertNull(Mini.withoutSelection(one).let { (it as MiniState).cursor })
    }

    @Test
    fun `every tier's last resort is proved and matches its tier`() {
        for (d in Difficulty.entries) {
            val s = MiniLogic.lastResort(d)
            assertEquals(MiniLogic.specFor(d).size, s.size)
            assertEquals(1, MiniOracle.count(s.size, clueLetters(s.size, s.answer)))
        }
    }

    @Test
    fun `a year of daily boards - the tiers get bigger and leave more to the clues, generation is quick`() {
        val report = StringBuilder()
        val means = mutableListOf<Double>()
        for (d in Difficulty.entries) {
            val times = mutableListOf<Long>()
            var read = 0
            var whites = 0
            val needs = mutableMapOf<String?, Int>()
            for (day in 0 until 365) {
                val seed = daySeed(day, Mini.id, d)
                val t0 = System.nanoTime()
                val s = MiniLogic.generateVerified(seed, d)!!
                times += System.nanoTime() - t0
                assertEquals(MiniLogic.specFor(d).size, s.size)
                read += MiniTeacher.walk(s.size, s.answer)!!.count { it.technique == MiniTeacher.CLUE }
                whites += s.answer.count { it != BLACK }
                needs.merge(MiniTeacher.depthOf(MiniTeacher.walk(s.size, s.answer)!!), 1, Int::plus)
            }
            times.sort()
            means += read / 365.0
            report.appendLine(
                "$d: clue letters mean ${"%.2f".format(read / 365.0)}, share ${"%.3f".format(read.toDouble() / whites)}, deepest step $needs, " +
                    "generation median ${times[182] / 100_000 / 10.0} ms, p95 ${times[346] / 100_000 / 10.0} ms, max ${times.last() / 100_000 / 10.0} ms",
            )
        }
        println(report)
        writeReport("mini-tiers.txt", report.toString())
        assertTrue("clue letters rise with the tier: $means", means[0] < means[1] && means[1] < means[2])
    }
}
