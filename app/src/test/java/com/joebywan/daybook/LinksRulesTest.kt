package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.Links
import com.joebywan.daybook.puzzles.LinksLogic
import com.joebywan.daybook.puzzles.LinksState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.time.TimeSource

class LinksRulesTest {

    private fun masks(n: Int, vararg paths: List<Int>): IntArray {
        val m = IntArray(n * n)
        for (p in paths) for (i in 1 until p.size) LinksLogic.setLine(n, m, p[i - 1], p[i], true)
        return m
    }

    private fun state(n: Int, ends: String, answer: IntArray, lines: IntArray = IntArray(n * n)) =
        LinksState(n, ends, LinksLogic.maskString(answer), LinksLogic.maskString(lines))

    // A 3x3 with one pair in two corners: a path of all nine squares, which can go two ways round.
    private val twoWay = "a.......a"
    private val snakeA = masks(3, listOf(0, 1, 2, 5, 4, 3, 6, 7, 8))
    private val snakeB = masks(3, listOf(0, 3, 6, 7, 4, 1, 2, 5, 8))

    @Test
    fun `a legal answer wins whether or not it is the stored one`() {
        assertTrue(LinksOracle.obeys(3, twoWay, snakeA))
        assertTrue(LinksOracle.obeys(3, twoWay, snakeB))
        assertTrue(state(3, twoWay, snakeA, snakeA).solved)
        assertTrue("the other answer is a win too", state(3, twoWay, snakeA, snakeB).solved)
        assertEquals(2L, LinksOracle.count(3, twoWay))
        assertEquals(Verdict.Several, LinksLogic.solutions(LinksLogic.Board.of(3, twoWay)))
    }

    @Test
    fun `each rule on its own refuses a board`() {
        // A square left out: a 2x3 with the pair joined the short way.
        val ends = "a.a..."
        val short = masks(3, listOf(0, 1, 2))
        assertFalse(state(3, ends + "...", short, short).solved)
        // A loop of loose line beside a joined pair: every square has its lines, but one piece holds no end.
        val ends2 = "a.a" + "..." + "..."
        val loop = masks(3, listOf(0, 1, 2), listOf(3, 4, 5, 8, 7, 6, 3))
        assertFalse(state(3, ends2, loop, loop).solved)
        assertFalse(LinksOracle.obeys(3, ends2, loop))
        // Two pairs joined to each other.
        val ends3 = "ab" + "ba"
        val crossed = masks(2, listOf(0, 1), listOf(2, 3))
        assertFalse(state(2, ends3, crossed, crossed).solved)
        assertFalse(LinksOracle.obeys(2, ends3, crossed))
        val right = masks(2, listOf(0, 2), listOf(1, 3))
        assertFalse("a 2x2 with crossed pairs cannot be joined", LinksOracle.obeys(2, ends3, right))
        assertEquals(0L, LinksOracle.count(2, ends3))
        assertEquals(Verdict.None, LinksLogic.solutions(LinksLogic.Board.of(2, ends3)))
    }

    @Test
    fun `the count and the oracle agree on small boards`() {
        // Every 3x3 with two pairs, ends placed every way: the two counts agree to two.
        var checked = 0
        val cells = (0 until 9).toList()
        for (a in cells) for (b in cells) for (c in cells) for (d in cells) {
            if (setOf(a, b, c, d).size != 4 || a > b || c > d || a > c) continue
            val ends = CharArray(9) { '.' }
            ends[a] = 'a'; ends[b] = 'a'; ends[c] = 'b'; ends[d] = 'b'
            val text = ends.concatToString()
            val oracle = LinksOracle.count(3, text)
            val mine = LinksLogic.solutions(LinksLogic.Board.of(3, text))
            val want: Verdict<IntArray> = when (oracle) { 0L -> Verdict.None; 1L -> mine; else -> Verdict.Several }
            if (oracle == 1L) {
                assertTrue(text, mine is Verdict.ExactlyOne)
                assertTrue(text, LinksOracle.obeys(3, text, (mine as Verdict.ExactlyOne).value))
            } else {
                assertEquals(text, want, mine)
            }
            checked++
        }
        assertTrue(checked > 100)
    }

    private val start = LocalDate(2026, 11, 1)

    private fun daily(day: Int, d: Difficulty) = Links.generate(DailySeed.seedFor(start.plus(DatePeriod(days = day)), Links.id, d), d) as LinksState

    @Test
    fun `generated boards have one answer by the oracle, the stored one, and the tier's rung`() {
        val sample = mapOf(Difficulty.STANDARD to 40, Difficulty.HARD to 20, Difficulty.EXPERT to 8)
        for ((d, days) in sample) for (day in 0 until days) {
            val s = daily(day, d)
            val spec = LinksLogic.specFor(d)
            assertEquals(spec.side, s.size)
            val pairs = s.ends.filter { it != '.' }.toSet().size
            assertTrue("$d day $day: $pairs pairs", pairs in spec.minPairs..spec.maxPairs)
            assertEquals("$d day $day", 1L, LinksOracle.count(s.size, s.ends))
            assertTrue("$d day $day", LinksOracle.obeys(s.size, s.ends, LinksLogic.masksOf(s.answer)))
            assertTrue(LinksLogic.accepts(s, spec))
        }
    }

    @Test
    fun `no path in a generated board is shorter than three squares or runs beside itself`() {
        for (d in Difficulty.entries) for (day in 0 until 30) {
            val s = daily(day, d)
            val n = s.size
            val v = LinksLogic.View(LinksLogic.Board.of(n, s.ends), LinksLogic.masksOf(s.answer))
            for (p in 0 until v.b.pairs) {
                val path = Links.pathOf(v, v.b.endA[p]).toList()
                assertTrue(path.size >= 3)
                for (i in path.indices) for (j in i + 2 until path.size) {
                    assertFalse("$d day $day pair $p touches itself", LinksLogic.dirTo(n, path[i], path[j]) >= 0)
                }
            }
        }
    }

    @Test
    fun `the tiers get bigger and need more, in the order they are offered`() {
        val report = StringBuilder()
        var last = 0.0
        for (d in Difficulty.entries) {
            val boards = (0 until 40).map { daily(it, d) }
            val depths = boards.map { LinksLogic.depth(LinksLogic.Board.of(it.size, it.ends))!! }
            val mean = depths.average()
            val pairs = boards.map { b -> b.ends.filter { it != '.' }.toSet().size }.average()
            report.append("${d.name}: pairs mean ${"%.2f".format(pairs)}, depth mean ${"%.2f".format(mean)} ${depths.groupingBy { it }.eachCount().toSortedMap()}\n")
            assertTrue("$d $mean", mean > last)
            last = mean
        }
        println(report)
        assertEquals(LinksLogic.LOCAL, (0 until 20).map { LinksLogic.depth(LinksLogic.Board.of(6, daily(it, Difficulty.STANDARD).ends)) }.maxOf { it!! })
    }

    @Test
    fun `the last resort is accepted at its tier`() {
        for (d in Difficulty.entries) {
            val s = LinksLogic.lastResort(d)
            assertTrue("$d", LinksLogic.accepts(s, LinksLogic.specFor(d)))
            assertEquals(1L, LinksOracle.count(s.size, s.ends))
        }
    }

    @Test
    fun `a year of daily boards - draws and generation time`() {
        val out = StringBuilder()
        for (d in Difficulty.entries) {
            val times = mutableListOf<Long>()
            val draws = mutableListOf<Int>()
            for (day in 0 until 365) {
                val seed = DailySeed.seedFor(start.plus(DatePeriod(days = day)), Links.id, d)
                val t0 = TimeSource.Monotonic.markNow()
                val s = LinksLogic.generateVerified(seed, d)
                times += t0.elapsedNow().inWholeMicroseconds
                assertNotNull("$d day $day fell back", s)
                draws += 1 + (0 until LinksLogic.ATTEMPTS).first { LinksLogic.attempt(seed, d, it) != null }
            }
            times.sort()
            out.append(
                "${d.name}: draws mean ${"%.2f".format(draws.average())} max ${draws.max()}; ms median ${times[times.size / 2] / 1000.0} " +
                    "p95 ${times[times.size * 95 / 100] / 1000.0} max ${times.last() / 1000.0}\n",
            )
        }
        println(out)
        File("build/reports").mkdirs()
        File("build/reports/links-generation.txt").writeText(out.toString())
    }

    // ---- the drag ---------------------------------------------------------------------------------

    private val tut = Links.tutorialBoard()

    @Test
    fun `a drag from an end draws, and a drag back along it rubs out`() {
        val drawn = tut.drag(listOf(21, 20, 15))
        val m = LinksLogic.masksOf(drawn.links)
        assertTrue(LinksLogic.hasLine(5, m, 21, 20) && LinksLogic.hasLine(5, m, 20, 15))
        assertEquals(1, drawn.moves)
        val back = drawn.drag(listOf(15, 20, 21))
        assertEquals(tut.links, back.links)
        assertSame("a tap is no drag", tut, tut.drag(listOf(21)))
    }

    @Test
    fun `backing onto its own line cuts it back there, and an end of another pair stops it`() {
        val a = tut.drag(listOf(0, 5, 10, 11, 6, 5))
        val m = LinksLogic.masksOf(a.links)
        assertTrue(LinksLogic.hasLine(5, m, 0, 5))
        assertFalse(LinksLogic.hasLine(5, m, 5, 10))
        // 0 then 1, then 2 is pair 3's end: the drag stops at 1.
        val b = tut.drag(listOf(0, 1, 2, 3))
        val mb = LinksLogic.masksOf(b.links)
        assertTrue(LinksLogic.hasLine(5, mb, 0, 1))
        assertFalse(LinksLogic.hasLine(5, mb, 1, 2))
    }

    @Test
    fun `drawing across another path cuts it, and the piece left without an end goes`() {
        val one = tut.drag(listOf(21, 20, 15, 10, 5))
        // The 2 at 16 drawn left onto 15, across path 1.
        val cut = one.drag(listOf(16, 15))
        val m = LinksLogic.masksOf(cut.links)
        assertTrue(LinksLogic.hasLine(5, m, 16, 15))
        assertTrue("the side with the 1 stays", LinksLogic.hasLine(5, m, 21, 20))
        assertFalse(LinksLogic.hasLine(5, m, 20, 15))
        assertFalse("the piece cut off from the 1 goes", LinksLogic.hasLine(5, m, 10, 5))
        assertFalse(LinksLogic.View(LinksLogic.Board.of(5, cut.ends), m).bad)
    }

    @Test
    fun `a line can start on an empty square, and joins an end`() {
        val loose = tut.drag(listOf(12, 17))
        val v = LinksLogic.View(LinksLogic.Board.of(5, loose.ends), LinksLogic.masksOf(loose.links))
        assertEquals(-1, v.colour[12])
        val joined = loose.drag(listOf(17, 16))
        val w = LinksLogic.View(LinksLogic.Board.of(5, joined.ends), LinksLogic.masksOf(joined.links))
        assertEquals(1, w.colour[12])
    }
}
