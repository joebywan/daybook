package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Target
import com.joebywan.daybook.puzzles.TargetLogic
import com.joebywan.daybook.puzzles.TargetOp
import com.joebywan.daybook.puzzles.TargetState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class TargetRulesTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun board(day: Int, d: Difficulty) =
        Target.generateVerified(DailySeed.seedFor(start.plusDays(day.toLong()), Target.id, d), d)
            ?: error("no proved board for day $day ${d.name}")

    @Test
    fun `each rule refuses on its own`() {
        // Subtraction and division put the bigger tile first, so order never matters.
        assertEquals(3, TargetLogic.combine(5, 8, TargetOp.Sub))
        assertEquals(3, TargetLogic.combine(8, 5, TargetOp.Sub))
        assertNull("zero", TargetLogic.combine(5, 5, TargetOp.Sub))
        assertEquals(4, TargetLogic.combine(2, 8, TargetOp.Div))
        assertEquals(4, TargetLogic.combine(8, 2, TargetOp.Div))
        assertNull("a fraction", TargetLogic.combine(7, 2, TargetOp.Div))
        assertEquals(1, TargetLogic.combine(6, 6, TargetOp.Div))
        assertEquals(9999, TargetLogic.combine(9990, 9, TargetOp.Add))
        assertNull("over the cap", TargetLogic.combine(9990, 10, TargetOp.Add))
        assertNull("over the cap", TargetLogic.combine(101, 100, TargetOp.Mul))
        assertEquals(9999, TargetLogic.combine(101, 99, TargetOp.Mul))
    }

    @Test
    fun `an operation uses two different live tiles and leaves the answer in the first`() {
        val s = Target.tutorialBoard()
        assertNull("a tile with itself", s.combine(0, TargetOp.Add, 0))
        val next = s.combine(0, TargetOp.Mul, 1)!!
        assertEquals(listOf(100, 0, 3, 2, 7, 1), next.slots)
        assertEquals(1, next.moves)
        assertNull("a used tile", next.combine(1, TargetOp.Add, 2))
        assertNull("a used tile as the first", next.combine(2, TargetOp.Add, 1))
        assertEquals("25 × 4 = 100", next.log.single().text())
        assertEquals("7 − 2 = 5", s.combine(3, TargetOp.Sub, 4)!!.log.single().text())
    }

    @Test
    fun `a legal answer that is not the one the search finds wins, and a near miss does not`() {
        val s = Target.tutorialBoard() // 25 4 3 2 7 1 -> 106
        // 7 - 1 = 6, 25 x 4 = 100, 100 + 6: not the order or the route the teacher would pick.
        val other = s.combine(4, TargetOp.Sub, 5)!!.combine(0, TargetOp.Mul, 1)!!
        assertFalse(other.solved)
        val won = other.combine(0, TargetOp.Add, 4)!!
        assertTrue(won.solved)
        assertEquals(106, won.tiles.single { it == 106 })
        // 105 is close, and not it.
        val near = s.combine(0, TargetOp.Mul, 1)!!.combine(2, TargetOp.Add, 3)!!.combine(0, TargetOp.Add, 2)!!
        assertEquals(105, near.slots[0])
        assertFalse(near.solved)
    }

    @Test
    fun `the search agrees with an independent breadth-first oracle, for every value, reachable or not`() {
        var checked = 0
        for ((d, days) in listOf(Difficulty.STANDARD to 25, Difficulty.HARD to 25, Difficulty.EXPERT to 25)) {
            for (day in 0 until days) {
                val s = board(day, d)
                val mine = TargetLogic.table(s.start)
                val theirs = TargetOracle.minOps(s.start)
                for (v in 1..999) assertEquals("${d.name} day $day tiles ${s.start} value $v", theirs[v], mine[v])
                checked++
            }
        }
        // And on awkward boards: repeated numbers, ones, a lone big tile.
        for (tiles in listOf(listOf(1, 1, 1, 1, 1, 1), listOf(100, 100, 100, 1, 1, 2), listOf(7, 7, 7, 7, 7, 7), listOf(25, 50, 75, 100, 10, 10))) {
            val mine = TargetLogic.table(tiles)
            val theirs = TargetOracle.minOps(tiles)
            for (v in 1..999) assertEquals("tiles $tiles value $v", theirs[v], mine[v])
        }
        assertEquals(75, checked)
    }

    @Test
    fun `every sampled board is as claimed, by the oracle`() {
        for (d in Difficulty.entries) {
            val spec = TargetLogic.specFor(d)
            for (day in 0 until 30) {
                val s = board(day, d)
                assertEquals(6, s.start.size)
                assertEquals(s.start.sortedDescending(), s.start)
                assertEquals(spec.large, s.start.count { it >= 25 && it % 25 == 0 })
                assertTrue("target ${s.target}", s.target in TargetLogic.MIN_TARGET..TargetLogic.MAX_TARGET)
                assertFalse("the target is a starting tile", s.target in s.start)
                assertEquals(TargetOracle.minOps(s.start)[s.target], s.par)
                assertTrue("par ${s.par} in ${spec.parLo}..${spec.parHi}", s.par in spec.parLo..spec.parHi)
                assertFalse(s.solved)
            }
        }
    }

    @Test
    fun `a year of daily boards on every tier is proved, and the shortest-answer lengths are measured`() {
        val report = StringBuilder()
        val means = mutableListOf<Double>()
        for (d in Difficulty.entries) {
            val spec = TargetLogic.specFor(d)
            val hist = IntArray(7)
            var gaveUp = 0
            val t0 = System.nanoTime()
            for (day in 0 until 365) {
                val seed = DailySeed.seedFor(start.plusDays(day.toLong()), Target.id, d)
                val s = Target.generateVerified(seed, d)
                if (s == null) { gaveUp++; continue }
                assertEquals(s, Target.generate(seed, d))
                assertTrue("${d.name} day $day par ${s.par}", s.par in spec.parLo..spec.parHi)
                hist[s.par]++
            }
            val ms = (System.nanoTime() - t0) / 1_000_000 / 365
            val total = hist.sum()
            val mean = hist.indices.sumOf { it * hist[it] }.toDouble() / total
            means += mean
            val line = "${d.name}: fell back on $gaveUp/365, shortest answer length histogram (0..6) ${hist.toList()}, mean %.2f, %d ms a board".format(mean, ms)
            println(line)
            report.appendLine(line)
            assertEquals("${d.name} shipped its last resort", 0, gaveUp)
        }
        File("build/reports").mkdirs()
        File("build/reports/target-par-distribution.txt").writeText(report.toString())
        assertTrue("the tiers get harder in the order they are offered: $means", means[0] < means[1] && means[1] < means[2])
    }

    @Test
    fun `the last resorts are themselves proved, and in their tier's range`() {
        for (d in Difficulty.entries) {
            val s = TargetLogic.lastResort(d)
            val spec = TargetLogic.specFor(d)
            assertEquals(TargetOracle.minOps(s.start)[s.target], s.par)
            assertTrue(s.par in spec.parLo..spec.parHi)
            assertEquals(spec.large, s.start.count { it >= 25 && it % 25 == 0 })
            assertFalse(s.solved)
        }
    }

    @Test
    fun `the walkthrough board needs three steps`() {
        val s = Target.tutorialBoard()
        assertEquals(3, TargetOracle.minOps(s.start)[s.target])
        assertEquals(s.par, TargetOracle.minOps(s.start)[s.target])
    }

    @Test
    fun `undoing a step from the log replays exactly`() {
        val s = Target.tutorialBoard()
        val three = s.combine(0, TargetOp.Mul, 1)!!.combine(2, TargetOp.Mul, 3)!!.combine(4, TargetOp.Sub, 5)!!
        assertEquals(3, three.log.size)
        val back = three.withoutFrom(1)
        assertEquals(s.combine(0, TargetOp.Mul, 1)!!.slots, back.slots)
        assertEquals(1, back.log.size)
        assertEquals(three.moves + 1, back.moves)
        assertEquals(s.slots, three.withoutFrom(0).slots)
    }

    @Test
    fun `a save written without the log still loads, with the tiles as started`() {
        val json = Json { ignoreUnknownKeys = true }
        val s = json.decodeFromString(TargetState.serializer(), """{"start":[25,4,3,2,7,1],"target":106,"par":3}""")
        assertEquals(s.start, s.slots)
        assertTrue(s.log.isEmpty())
        assertEquals(0, s.moves)
    }
}
