package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.HexBest
import com.joebywan.daybook.puzzles.HexGeometry
import com.joebywan.daybook.puzzles.HexLogic
import com.joebywan.daybook.puzzles.HexReach
import com.joebywan.daybook.puzzles.HexSearch
import com.joebywan.daybook.puzzles.HexTake
import com.joebywan.daybook.puzzles.Hexwords
import com.joebywan.daybook.puzzles.HexwordsState
import com.joebywan.daybook.puzzles.swipeHex
import com.joebywan.daybook.puzzles.tapHex
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HexwordsRulesTest {

    private val start = LocalDate(2026, 1, 1)
    private fun seed(day: Int, d: Difficulty) = DailySeed.seedFor(start.plus(DatePeriod(days = day)), "hexwords", d)
    private fun all(s: HexwordsState) = (1L shl s.letters.length) - 1

    @Test
    fun `a longer word always scores more than one extra point a letter`() {
        for (n in 3..6) assertTrue(HexLogic.points(n + 1) - HexLogic.points(n) > 1)
        for (n in 3..7) assertEquals(HexwordsOracle.worth(n), HexLogic.points(n))
    }

    @Test
    fun `touching hexes agree with an independent reading of the block`() {
        for (d in Difficulty.entries) {
            val rows = HexLogic.specFor(d).rows
            val oracle = HexwordsOracle(rows, "a".repeat(rows.sum()), false)
            val nb = HexLogic.neighbours(rows)
            for (h in nb.indices) assertEquals(oracle.neighbours(h).sorted(), nb[h].sorted())
        }
    }

    @Test
    fun `solved is the score rule, whichever words made it`() {
        val base = HexwordsState(listOf(3, 4, 3), "dhoacrince", 11)
        val eight = base.take("dance", listOf(0, 3, 7, 8, 9))
        assertFalse(eight.solved)
        assertEquals(3, eight.left)
        // Not the words the board was built from, not on any stored list: a bonus word scores the same.
        assertTrue(eight.take("rho", listOf(5, 1, 2)).solved)
        assertTrue(HexwordsState(listOf(3, 4, 3), "dhoacrince", 8).take("nacho", listOf(7, 3, 4, 1, 2)).solved)
    }

    @Test
    fun `letters go, and undoing to a word puts them back as one move`() {
        val s = HexwordsState(listOf(3, 4, 3), "dhoacrince", 16)
        val t = s.take("dance", listOf(0, 3, 7, 8, 9))
        assertEquals(0L, t.free and 1L)
        assertEquals(1, t.moves)
        assertEquals(s.free, t.withoutFrom(0).free)
        assertEquals(2, t.withoutFrom(0).moves)
    }

    @Test
    fun `a search out of budget says so and never says No`() {
        val s = Hexwords.generate(seed(0, Difficulty.EXPERT), Difficulty.EXPERT) as HexwordsState
        val nb = HexLogic.neighbours(s.rows)
        val roads = HexLogic.roads(s.letters, nb, all(s), HexLogic.acceptedWords)
        assertEquals(HexReach.Truncated, HexSearch(roads, s.letters.length, 3).reach(all(s), s.target))
        assertEquals(HexBest.Truncated, HexSearch(roads, s.letters.length, 3).best(all(s)))
    }

    @Test
    fun `the exact search agrees with the oracle on every position it can read`() {
        for ((d, count) in listOf(Difficulty.STANDARD to 40, Difficulty.HARD to 8, Difficulty.EXPERT to 1)) repeat(count) { day ->
            val s = Hexwords.generate(seed(day, d), d) as HexwordsState
            val nb = HexLogic.neighbours(s.rows)
            for (accepted in listOf(false, true)) {
                if (accepted && d == Difficulty.EXPERT) continue
                val words = if (accepted) HexLogic.acceptedWords else HexLogic.gridWords
                val oracle = HexwordsOracle(s.rows, s.letters, accepted)
                val roads = HexLogic.roads(s.letters, nb, all(s), words)
                val mine = roads.map { it.word + it.path.toList() }.sorted()
                assertEquals(oracle.found.map { it.word + it.path }.sorted(), mine)
                val best = HexSearch(roads, s.letters.length, 5_000_000).best(all(s)) as HexBest.Proved
                assertEquals("day $day $d accepted=$accepted", oracle.best(all(s)), best.points)
            }
        }
    }

    @Test
    fun `a year of boards - common words alone reach the target, by a road the oracle checks`() {
        for (d in Difficulty.entries) repeat(365) { day ->
            val s = HexLogic.generateVerified(seed(day, d), d)!!
            assertEquals(HexLogic.specFor(d).rows.sum(), s.letters.length)
            assertTrue(s.letters.all { it in 'a'..'z' })
            val nb = HexLogic.neighbours(s.rows)
            val roads = HexLogic.roads(s.letters, nb, all(s), HexLogic.gridWords)
            val road = (HexSearch(roads, s.letters.length, HexLogic.BUDGET).reach(all(s), s.target) as HexReach.Yes).road
            val oracle = if (day % 30 == 0) HexwordsOracle(s.rows, s.letters, false) else null
            if (oracle != null) {
                val total = HexwordsOracle.score(oracle, s.letters, road.map { it.word to it.path.toList() })
                assertTrue(total >= s.target)
            }
            assertTrue(road.sumOf { it.points } >= s.target)
        }
    }

    /** The balance: each tier sets out to beat a simple player, so measure that player rather than trust the constants. */
    @Test
    fun `the tiers beat a greedy player and then a careful one, in the order they are offered`() {
        val report = StringBuilder()
        val greedyShare = ArrayList<Double>()
        val carefulShare = ArrayList<Double>()
        for (d in Difficulty.entries) {
            var greedy = 0
            var wide = 0
            var careful = 0
            var carefulWide = 0
            var random = 0
            var atBest = 0
            var hexes = 0
            var targets = 0L
            val days = 120
            val tries = 20
            val times = ArrayList<Double>()
            repeat(days) { day ->
                val t0 = System.nanoTime()
                val s = HexLogic.generateVerified(seed(day, d), d)!!
                times += (System.nanoTime() - t0) / 1e6
                val nb = HexLogic.neighbours(s.rows)
                val roads = HexLogic.roads(s.letters, nb, all(s), HexLogic.gridWords)
                val acc = HexLogic.roads(s.letters, nb, all(s), HexLogic.acceptedWords)
                hexes = s.letters.length
                targets += s.target
                repeat(tries) { i ->
                    if (biggestFirst(roads, all(s), Rng(i * 31L + day)) >= s.target) greedy++
                    if (biggestFirst(acc, all(s), Rng(i * 31L + day)) >= s.target) wide++
                    if (anyWord(roads, all(s), Rng(i * 37L + day)) >= s.target) random++
                }
                if (HexLogic.careful(roads, all(s)) >= s.target) careful++
                if (HexLogic.careful(acc, all(s)) >= s.target) carefulWide++
                if ((HexSearch(roads, s.letters.length, 5_000_000).best(all(s)) as HexBest.Proved).points == s.target) atBest++
            }
            times.sort()
            greedyShare += greedy.toDouble() / (days * tries)
            carefulShare += careful.toDouble() / days
            report.appendLine(
                "$d: $hexes hexes, mean target ${targets / days}; reach the target: biggest-first ${greedy * 100 / (days * tries)}% " +
                    "(any accepted word ${wide * 100 / (days * tries)}%), one-word lookahead ${careful * 100 / days}% (any accepted word ${carefulWide * 100 / days}%), " +
                    "any word at random ${random * 100 / (days * tries)}%; target is the board's best ${atBest * 100 / days}%; " +
                    "generate ms median ${times[days / 2]} p95 ${times[days * 95 / 100]} max ${times.last()}",
            )
        }
        File("build/reports").mkdirs()
        File("build/reports/hexwords-balance.txt").writeText(report.toString())
        print(report)
        assertTrue("Standard must be reachable greedily: $greedyShare", greedyShare[0] > 0.5)
        assertTrue("Hard must not fall to the biggest word first: $greedyShare", greedyShare[1] < 0.1)
        assertTrue("Expert must not fall to the biggest word first: $greedyShare", greedyShare[2] <= greedyShare[1])
        assertEquals("Expert's target is above the careful player by construction", 0.0, carefulShare[2], 0.0)
        assertTrue("A careful player must find Hard easier than Expert", carefulShare[1] > carefulShare[2])
    }

    private fun biggestFirst(roads: List<com.joebywan.daybook.puzzles.HexRoad>, all: Long, r: Rng): Int {
        var free = all
        var total = 0
        var live = roads
        while (true) {
            live = live.filter { it.mask and free == it.mask }
            if (live.isEmpty()) return total
            val top = live.maxOf { it.word.length }
            val pick = live.filter { it.word.length == top }.let { it[r.nextInt(it.size)] }
            total += pick.points
            free = free xor pick.mask
        }
    }

    private fun anyWord(roads: List<com.joebywan.daybook.puzzles.HexRoad>, all: Long, r: Rng): Int {
        var free = all
        var total = 0
        var live = roads
        while (true) {
            live = live.filter { it.mask and free == it.mask }
            if (live.isEmpty()) return total
            val pick = live[r.nextInt(live.size)]
            total += pick.points
            free = free xor pick.mask
        }
    }

    @Test
    fun `a hex is hit only inside its six sides, and the block stays inside its box`() {
        for ((w, h) in listOf(359f to 280f, 375f to 300f, 390f to 620f, 800f to 300f)) for (d in Difficulty.entries) {
            val g = HexGeometry(HexLogic.specFor(d).rows, w, h)
            for (i in g.xs.indices) {
                assertTrue(g.xs[i] - g.apothem >= -0.01f && g.xs[i] + g.apothem <= w + 0.01f)
                assertTrue(g.ys[i] - g.radius >= -0.01f && g.ys[i] + g.radius <= h + 0.01f)
                assertEquals(i, g.hit(g.xs[i], g.ys[i]))
                assertEquals(i, g.hitCore(g.xs[i], g.ys[i]))
            }
            // Every point: the hex hit is the nearest centre, and off the block there is none.
            var y = 0f
            while (y < h) {
                var x = 0f
                while (x < w) {
                    val hit = g.hit(x, y)
                    if (hit != null) {
                        val near = g.xs.indices.minByOrNull { (g.xs[it] - x) * (g.xs[it] - x) + (g.ys[it] - y) * (g.ys[it] - y) }!!
                        val dHit = (g.xs[hit] - x) * (g.xs[hit] - x) + (g.ys[hit] - y) * (g.ys[hit] - y)
                        val dNear = (g.xs[near] - x) * (g.xs[near] - x) + (g.ys[near] - y) * (g.ys[near] - y)
                        assertEquals(dNear, dHit, 1f) // equal, or on a shared side
                    }
                    x += 7f
                }
                y += 7f
            }
        }
        // A corner of a pointy hex's box is outside it.
        val g = HexGeometry(listOf(1), 100f, 100f)
        assertEquals(null, g.hit(g.xs[0] + g.apothem * 0.95f, g.ys[0] + g.radius * 0.95f))
    }

    @Test
    fun `a corner between two hexes takes neither while the finger is moving`() {
        val g = HexGeometry(listOf(3, 4, 3), 400f, 400f)
        val nb = HexLogic.neighbours(listOf(3, 4, 3))
        val b = nb[0].first()
        val midX = (g.xs[0] + g.xs[b]) / 2
        val midY = (g.ys[0] + g.ys[b]) / 2
        assertEquals(null, g.hitCore(midX, midY))
    }

    @Test
    fun `swipe and tap models`() {
        val nb = HexLogic.neighbours(listOf(3, 4, 3))
        assertEquals(listOf(0, 1), swipeHex(listOf(0), 1, nb))
        assertEquals(listOf(0), swipeHex(listOf(0), 9, nb)) // does not touch
        assertEquals(listOf(0), swipeHex(listOf(0, 1), 0, nb)) // back up
        assertEquals(listOf(0, 1, 2), swipeHex(listOf(0, 1, 2), 0, nb)) // not touching the last, already used
        assertEquals(emptyList<Int>(), tapHex(listOf(0), 0, nb))
        assertEquals(listOf(0, 1), tapHex(listOf(0), 1, nb))
        assertEquals(listOf(9), tapHex(listOf(0), 9, nb))
    }

    @Test
    fun `a save from before any later field still loads`() {
        val j = """{"rows":[3,4,3],"letters":"dhoacrince","target":16,"taken":[{"word":"dance","path":[0,3,7,8,9]}],"moves":1}"""
        val s = kotlinx.serialization.json.Json.decodeFromString(HexwordsState.serializer(), j)
        assertEquals(listOf(HexTake("dance", listOf(0, 3, 7, 8, 9))), s.taken)
    }
}
