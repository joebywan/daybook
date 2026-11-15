package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.CageState
import com.joebywan.daybook.puzzles.Cages
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.Look
import com.joebywan.daybook.ui.theme.useLook
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Cage Sudoku's cage colours in Arcade (issue #499): no two cages that share an edge are ever one colour, on any map, and
 * the colours are told apart, colour-blind vision included, with the digits on them readable.
 */
class CageColoursTest {

    /** Whether no two squares that share an edge, in different cages, have one colour. */
    private fun proper(cageOf: List<Int>, n: Int, colour: IntArray): Boolean = cageOf.indices.all { i ->
        listOf(if (i % n < n - 1) i + 1 else -1, if (i / n < n - 1) i + n else -1)
            .all { j -> j < 0 || cageOf[j] == cageOf[i] || colour[cageOf[j]] != colour[cageOf[i]] }
    }

    @Test
    fun `touching cages differ on every daily board of a year on every tier`() {
        for (d in Difficulty.entries) for (day in 0 until 365) {
            val s = Cages.generate(daySeed(day, Cages.id, d), d) as CageState
            val colour = Cages.cageColours(s.cageOf, 9)
            assertTrue("${d.name} day $day", proper(s.cageOf, 9, colour) && colour.all { it in 0..4 })
        }
    }

    /** A random map: regions of 1 to 8 squares grown from the first free square, each connected, so the map is planar. */
    private fun randomMap(n: Int, rng: Random): List<Int> {
        val region = IntArray(n * n) { -1 }
        var next = 0
        for (start in 0 until n * n) {
            if (region[start] >= 0) continue
            val size = rng.nextInt(1, 9)
            val grown = mutableListOf(start)
            region[start] = next
            while (grown.size < size) {
                val free = grown.flatMap { i ->
                    listOfNotNull(
                        (i - n).takeIf { it >= 0 }, (i + n).takeIf { it < n * n },
                        (i - 1).takeIf { i % n > 0 }, (i + 1).takeIf { i % n < n - 1 },
                    )
                }.filter { region[it] < 0 }
                if (free.isEmpty()) break
                val pick = free[rng.nextInt(free.size)]
                region[pick] = next
                grown += pick
            }
            next++
        }
        return region.toList()
    }

    private fun adjacency(cageOf: List<Int>, n: Int): List<BooleanArray> {
        val count = cageOf.max() + 1
        val adj = List(count) { BooleanArray(count) }
        for (i in cageOf.indices) for (j in listOf(if (i % n < n - 1) i + 1 else -1, if (i / n < n - 1) i + n else -1)) {
            if (j >= 0 && cageOf[i] != cageOf[j]) {
                adj[cageOf[i]][cageOf[j]] = true
                adj[cageOf[j]][cageOf[i]] = true
            }
        }
        return adj
    }

    /** The budget-free colouring alone, which [BoardHues.regionColours] falls back on, on maps it has never seen. */
    @Test
    fun `five colours always do, with no search`() {
        val rng = Random(499)
        repeat(4000) { k ->
            val n = 3 + k % 12
            val map = randomMap(n, rng)
            val colour = BoardHues.fiveColours(adjacency(map, n))
            assertTrue("map $k", proper(map, n, colour) && colour.all { it in 0..4 })
        }
    }

    /** A wheel: a ring of five regions round a middle one, each touching the next and the middle, the case Kempe's swap is for. */
    @Test
    fun `a region ringed by five takes a colour by swapping`() {
        val adj = List(6) { BooleanArray(6) }
        fun join(a: Int, b: Int) { adj[a][b] = true; adj[b][a] = true }
        for (r in 1..5) { join(0, r); join(r, r % 5 + 1) }
        val colour = BoardHues.fiveColours(adj)
        for (a in 0 until 6) for (b in 0 until 6) if (adj[a][b]) assertTrue("$a $b", colour[a] != colour[b])
    }

    @Test
    fun `cage colours are told apart and their digits read, in both schemes`() {
        useLook(Look.ARCADE)
        for (dark in listOf(false, true)) {
            val tints = (0 until 5).map { Cages.cageTint(it, dark) }
            var closest = Double.MAX_VALUE
            for (i in tints.indices) for (j in i + 1 until tints.size) closest = minOf(closest, ColourDistance.apart(tints[i], tints[j]))
            println("cage tints ${if (dark) "dark" else "light"}: closest pair $closest")
            assertTrue("closest pair $closest", closest >= MIN_APART)
            for (t in tints) assertTrue("digit on $t", BoardHues.contrast(BoardHues.onFill(t), t) >= 4.5f)
        }
    }

    private companion object {
        const val MIN_APART = 10.5 // measured 11.0 light, 12.5 dark (Regions' fills were 0.95 apart in dark)
    }
}
