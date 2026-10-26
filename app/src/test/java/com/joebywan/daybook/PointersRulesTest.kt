package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.puzzles.Pointers
import com.joebywan.daybook.puzzles.PointersLogic
import com.joebywan.daybook.puzzles.PointersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pointers' rules and generator, checked by code that shares nothing with [PointersLogic]: the arrows' heading and
 * their way to the edge are worked out here from row and column arithmetic, and "in the way of" is a graph built
 * here and searched for a cycle by depth-first colouring, not by the generator's reachability walk.
 */
class PointersRulesTest {

    private fun board(day: Int, tier: Difficulty): PointersState {
        val seed = daySeed(day, Pointers.id, tier)
        return Pointers.generate(seed, tier) as PointersState
    }

    /** The squares between [arrow]'s head and the edge, from coordinates. */
    private fun way(s: PointersState, arrow: List<Int>): List<Int> {
        val (hr, hc) = arrow.last() / s.width to arrow.last() % s.width
        val (pr, pc) = arrow[arrow.size - 2] / s.width to arrow[arrow.size - 2] % s.width
        val dr = hr - pr
        val dc = hc - pc
        return generateSequence(hr + dr to hc + dc) { (r, c) -> r + dr to c + dc }
            .takeWhile { (r, c) -> r in 0 until s.height && c in 0 until s.width }
            .map { (r, c) -> r * s.width + c }
            .toList()
    }

    private fun free(s: PointersState, gone: Set<Int>, i: Int): Boolean {
        val taken = s.arrows.indices.filter { it !in gone }.flatMap { s.arrows[it] }.toSet()
        return way(s, s.arrows[i]).none { it in taken }
    }

    /** Takes any free arrow until none is: the greedy player. */
    private fun greedyClears(s: PointersState): Boolean {
        val gone = mutableSetOf<Int>()
        while (gone.size < s.arrows.size) {
            val next = s.arrows.indices.firstOrNull { it !in gone && free(s, gone, it) } ?: return false
            gone += next
        }
        return true
    }

    /** x -> y when x has a square on y's way. True if that graph has a cycle (white/grey/black DFS). */
    private fun hasCycle(s: PointersState): Boolean {
        val n = s.arrows.size
        val ownerOf = HashMap<Int, Int>()
        s.arrows.forEachIndexed { i, a -> a.forEach { ownerOf[it] = i } }
        val out = List(n) { mutableListOf<Int>() }
        for (y in 0 until n) for (cell in way(s, s.arrows[y])) ownerOf[cell]?.let { x -> out[x] += y }
        val colour = IntArray(n)
        fun visit(x: Int): Boolean {
            colour[x] = 1
            for (y in out[x]) {
                if (colour[y] == 1) return true
                if (colour[y] == 0 && visit(y)) return true
            }
            colour[x] = 2
            return false
        }
        return (0 until n).any { colour[it] == 0 && visit(it) }
    }

    private fun assertWellFormed(s: PointersState, label: String) {
        val all = s.arrows.flatten()
        assertEquals("$label: arrows share a square", all.size, all.toSet().size)
        assertTrue("$label: square off the board", all.all { it in 0 until s.width * s.height })
        for (a in s.arrows) {
            assertTrue("$label: arrow shorter than two squares", a.size >= 2)
            for (k in 0 until a.size - 1) {
                val dr = kotlin.math.abs(a[k] / s.width - a[k + 1] / s.width)
                val dc = kotlin.math.abs(a[k] % s.width - a[k + 1] % s.width)
                assertEquals("$label: arrow $a is not a path", 1, dr + dc)
            }
            assertTrue("$label: arrow $a points into itself", way(s, a).none { it in a })
        }
    }

    @Test
    fun `a year of boards per tier is well formed, acyclic and cleared by the greedy player`() {
        for (tier in Difficulty.entries) {
            for (day in 0 until 365) {
                val s = board(day, tier)
                val label = "${tier.name} day $day"
                assertWellFormed(s, label)
                assertFalse("$label: a cycle of arrows in each other's way", hasCycle(s))
                assertTrue("$label: the greedy player is stranded", greedyClears(s))
                assertTrue("$label: every arrow is free from the start", s.arrows.indices.any { !free(s, emptySet(), it) })
            }
        }
    }

    @Test
    fun `taking an arrow away never blocks another`() {
        for (tier in Difficulty.entries) for (day in 0 until 20) {
            val s = board(day, tier)
            val gone = mutableSetOf<Int>()
            // Along a greedy clearing, every arrow free before a removal is free after it.
            while (gone.size < s.arrows.size) {
                val before = s.arrows.indices.filter { it !in gone && free(s, gone, it) }
                val take = before.last()
                gone += take
                for (i in before) if (i != take) assertTrue("${tier.name} day $day: removing $take blocked $i", free(s, gone, i))
            }
        }
    }

    @Test
    fun `the tiers get bigger and more tangled in the order they are offered`() {
        var lastCells = 0
        var lastDepth = -1.0
        for (tier in Difficulty.entries) {
            val boards = (0 until 100).map { board(it, tier) }
            val cells = boards[0].width * boards[0].height
            val depth = boards.map { PointersLogic.depths(it).average() }.average()
            val bare = boards.map { s -> s.width * s.height - s.arrows.sumOf { it.size } }.average() / cells
            println("pointers ${tier.name}: ${boards[0].width}x${boards[0].height}, arrows ${boards.map { it.arrows.size }.average()}, mean depth $depth, bare ${"%.1f".format(bare * 100)}%")
            assertTrue("${tier.name} is no bigger than the tier before", cells > lastCells)
            assertTrue("${tier.name} is no more tangled than the tier before", depth > lastDepth)
            assertTrue("${tier.name} leaves over 6% of the board bare", bare < 0.06)
            lastCells = cells
            lastDepth = depth
        }
    }

    @Test
    fun `the last resort is the tier's own size and clears`() {
        for (tier in Difficulty.entries) {
            val s = PointersLogic.lastResort(tier)
            val spec = PointersLogic.specFor(tier)
            assertEquals(spec.width to spec.height, s.width to s.height)
            assertWellFormed(s, "lastResort ${tier.name}")
            assertTrue(greedyClears(s))
            assertEquals(s.width * s.height, s.arrows.sumOf { it.size })
        }
    }

    @Test
    fun `generation time on the JVM`() {
        for (tier in Difficulty.entries) {
            val ms = (0 until 200).map { day ->
                val seed = daySeed(day, Pointers.id, tier, LocalDate.of(2027, 1, 1))
                val t = System.nanoTime()
                Pointers.generate(seed, tier)
                (System.nanoTime() - t) / 1e6
            }.sorted()
            println("pointers ${tier.name} generation ms: median ${"%.2f".format(ms[100])}, p95 ${"%.2f".format(ms[190])}, max ${"%.2f".format(ms.last())}")
            assertTrue("${tier.name} p95 over 50 ms", ms[190] < 50)
        }
    }

    // ---- the rules of a tap -----------------------------------------------------------------

    private val tut = Pointers.tutorialBoard()
    private fun cellOf(i: Int) = Pointers.TUTORIAL_ARROWS[i][0]

    @Test
    fun `a free arrow leaves, a blocked one bumps and costs a life, a bare square does nothing`() {
        val gone = tut.tap(cellOf(Pointers.TOP))!!
        assertEquals(listOf(Pointers.TOP), gone.removed)
        assertEquals(PointersLogic.LIVES, gone.lives)
        assertEquals(1, gone.moves)

        val bumped = gone.tap(cellOf(Pointers.ROW))!!
        assertEquals(listOf(Pointers.TOP), bumped.removed)
        assertEquals(PointersLogic.LIVES - 1, bumped.lives)
        assertEquals("the bump names the arrow that bumped", listOf(Pointers.ROW), bumped.bumps)
        assertEquals(2, bumped.moves)

        assertNull("a cleared arrow's square is bare", bumped.tap(cellOf(Pointers.TOP)))
        assertNull(PointersState(3, 3, listOf(listOf(0, 1))).tap(8))
    }

    @Test
    fun `three bumps lose the board, which then takes no taps until Try again restores it`() {
        var s = tut
        repeat(3) { s = s.tap(cellOf(Pointers.ROW))!! }
        assertTrue(s.failed)
        assertFalse(s.solved)
        assertEquals(0, s.lives)
        assertNull("a lost board takes no taps", s.tap(cellOf(Pointers.TOP)))
        val again = s.retry()
        assertFalse(again.failed)
        assertEquals(PointersLogic.LIVES, again.lives)
        assertTrue(again.removed.isEmpty())
        assertEquals(s.moves + 1, again.moves)
        assertEquals(tut.arrows, again.arrows)
    }

    @Test
    fun `clearing every arrow wins, in any order that works, and a win is never also a loss`() {
        val order = listOf(Pointers.SIDE, Pointers.ROW, Pointers.HOOK, Pointers.TOP, Pointers.SHORT)
        var s = tut
        for (i in order) s = s.tap(cellOf(i))!!
        assertTrue(s.solved)
        assertFalse(s.failed)
        assertNull(s.tap(0))
        // Down to one life and then cleared: still a win.
        var t = tut.tap(cellOf(Pointers.ROW))!!.tap(cellOf(Pointers.ROW))!!
        assertEquals(1, t.lives)
        for (i in order) t = t.tap(cellOf(i))!!
        assertTrue(t.solved && !t.failed)
    }

    @Test
    fun `a save without lives or removals loads as a fresh board`() {
        val json = """{"state":{"type":"com.joebywan.daybook.puzzles.PointersState","width":4,"height":4,""" +
            """"arrows":[[2,1,0],[4,5,6],[3,7,11,15],[8,12,13,14],[10,9]]}}"""
        val game = SavedGame.decode(json)
        assertNotNull(game)
        assertEquals(tut, game!!.state)
        assertEquals(PointersLogic.LIVES, (game.state as PointersState).lives)
    }

    // ---- hit testing --------------------------------------------------------------------------

    @Test
    fun `a tap takes the arrow under it, or the nearest line within reach of a bare square`() {
        // A 3x3 with one arrow along the middle row; every other square bare.
        val s = PointersState(3, 3, listOf(listOf(3, 4, 5)))
        assertEquals(4, Pointers.hit(s, 1.5f, 1.5f))
        assertEquals(3, Pointers.hit(s, 0.1f, 1.9f))
        // Bare squares above and below, within reach of the line.
        assertTrue(Pointers.hit(s, 1.5f, 0.9f) in listOf(3, 4))
        assertTrue(Pointers.hit(s, 1.5f, 2.2f) in listOf(3, 4))
        // A corner is beyond reach.
        assertEquals(-1, Pointers.hit(s, 0.1f, 0.1f))
        // A cleared arrow is not hit.
        assertEquals(-1, Pointers.hit(s.copy(removed = listOf(0)), 1.5f, 1.5f))
    }
}
