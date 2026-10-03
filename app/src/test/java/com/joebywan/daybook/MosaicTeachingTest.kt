package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.MosaicTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * Mosaic's teacher, checked against a search written again here on a different principle.
 *
 * Mosaic has no stored answer to agree with — the board is all there is — so "sound" means the
 * one thing that matters at zero slack: **every fill the teacher teaches still finishes inside the
 * limit**, and every board it calls lost really is. Both are checked by [winsWithin], which moves
 * only through [MosaicState.flood] and reads areas only through [MosaicState.area]: no bitmasks, no
 * area graph, no distance bound, nothing shared with [MosaicTeacher.Search] or [Mosaic.solve]. On
 * small boards that search is pinned to breadth-first truth, so it cannot pass everything by
 * answering "yes" to everything.
 *
 * Each technique's stated reason is re-checked too, since a true move with a false reason still
 * teaches the wrong thing: a "count" step must really have as many fills as colours to wipe out and
 * flood a colour's last patch, a "biggest swallow" must really swallow the most, and so on.
 */
class MosaicTeachingTest {

    private fun seeds(count: Int, tier: Difficulty, salt: String) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 3, 1).plusDays(it.toLong()), salt, tier)
    }

    private fun board(seed: Long, tier: Difficulty) = Mosaic.generate(seed, tier) as MosaicState

    // ---- an independent engine ------------------------------------------------------------------

    private fun areaCells(b: MosaicState): List<Int> {
        val seen = BooleanArray(b.cells.size)
        val out = mutableListOf<Int>()
        for (cell in b.cells.indices) {
            if (seen[cell]) continue
            out += cell
            for (m in b.area(cell)) seen[m] = true
        }
        return out
    }

    /** Every fill, as the board it leaves: one per area and colour, nothing filtered. */
    private fun everyFill(b: MosaicState): List<Pair<Int, Int>> =
        areaCells(b).flatMap { rep -> (0 until b.colours).filter { it != b.cells[rep] }.map { rep to it } }

    /** Whether [b] can be finished in at most [fills], by plain memoised depth-first over [everyFill]. */
    private fun winsWithin(b: MosaicState, fills: Int): Boolean {
        val memo = HashMap<List<Int>, Int>()
        var budget = 6_000_000
        fun search(s: MosaicState, left: Int): Boolean {
            if (s.solved) return true
            if (left <= 0) return false
            if (s.cells.distinct().size - 1 > left) return false
            if (--budget < 0) throw AssertionError("the independent search ran out of budget")
            if ((memo[s.cells] ?: -1) >= left) return false
            val tried = everyFill(s).map { s.flood(it.first, it.second) }
                .sortedBy { it.areaCount() }
            for (next in tried) if (search(next, left - 1)) return true
            memo[s.cells] = left
            return false
        }
        return search(b, fills)
    }

    private fun breadthFirst(b: MosaicState): Int {
        if (b.solved) return 0
        var frontier = listOf(b)
        val seen = hashSetOf(b.cells)
        var depth = 0
        while (true) {
            depth++
            val next = mutableListOf<MosaicState>()
            for (s in frontier) for ((cell, colour) in everyFill(s)) {
                val m = s.flood(cell, colour)
                if (m.solved) return depth
                if (seen.add(m.cells)) next += m
            }
            frontier = next
        }
    }

    private fun left(s: MosaicState) = s.limit - s.moves

    /** Neighbouring areas of [cell]'s area that wear [colour], counted independently. */
    private fun gain(s: MosaicState, cell: Int, colour: Int): Int {
        val mine = s.area(cell).toSet()
        val reps = mutableSetOf<Int>()
        for (x in mine) {
            val r = x / s.width
            val c = x % s.width
            val around = listOfNotNull(
                (x - s.width).takeIf { r > 0 }, (x + s.width).takeIf { r < s.height - 1 },
                (x - 1).takeIf { c > 0 }, (x + 1).takeIf { c < s.width - 1 },
            )
            for (y in around) if (y !in mine && s.cells[y] == colour) reps += s.area(y).min()
        }
        return reps.size
    }

    // ---- the search itself, against breadth-first and against the generator ------------------------

    @Test
    fun `the teacher's search agrees with breadth-first on small boards, and so does the checker`() {
        val rng = Random(20260930)
        var checked = 0
        repeat(150) {
            val w = 3 + rng.nextInt(2)
            val h = 3 + rng.nextInt(2)
            val colours = 2 + rng.nextInt(2)
            val b = MosaicState(w, h, colours, w * h, List(w * h) { rng.nextInt(colours) })
            if (b.solved) return@repeat
            val truth = breadthFirst(b)
            val a = MosaicTeacher.Areas.of(w, h, b.cells)!!
            val got = MosaicTeacher.Search(colours).optimum(a.root, w * h)
            assertEquals("${b.cells}: the teacher's search disagrees with breadth-first", truth, got)
            assertTrue("${b.cells}: the checker misses a $truth-fill win", winsWithin(b, truth))
            assertFalse("${b.cells}: the checker invents a ${truth - 1}-fill win", winsWithin(b, truth - 1))
            checked++
        }
        assertTrue(checked > 100)
    }

    @Test
    fun `on generated boards the teacher's search finds the generator's optimum, which is the limit`() {
        for (tier in Difficulty.entries) {
            for (seed in seeds(if (tier == Difficulty.EXPERT) 10 else 25, tier, "mosaic-teach-opt")) {
                val b = board(seed, tier)
                val a = MosaicTeacher.Areas.of(b.width, b.height, b.cells)!!
                val search = MosaicTeacher.Search(b.colours)
                assertEquals("$tier/$seed", Mosaic.solve(b)!!.size, search.optimum(a.root, b.limit + 1))
                assertFalse("$tier/$seed ran out of budget", search.exhausted)
            }
        }
    }

    // ---- soundness ------------------------------------------------------------------------------

    /** Re-checks a fill step's move and its stated reason, independently. */
    private fun checkFill(label: String, s: MosaicState, step: MosaicTeacher.Step) {
        val r = left(s)
        assertTrue("$label: a fill step with no fill", step.cell in s.cells.indices && step.colour in 0 until s.colours)
        assertTrue("$label: pours an area its own colour", s.cells[step.cell] != step.colour)
        val next = s.flood(step.cell, step.colour)
        assertTrue("$label ${step.technique}: the taught fill cannot finish in ${r - 1}", winsWithin(next, r - 1))
        assertEquals("$label: targets are not the flooded area", s.area(step.cell).toSet(), step.targets)
        assertTrue("$label: explanation too long for the panel (${step.explanation.length})", step.explanation.length <= 180)
        assertTrue("$label: nudge too long", step.nudge.length <= 60)
        when (step.technique) {
            MosaicTeacher.FINISH -> assertTrue("$label: finish does not finish", next.solved)
            MosaicTeacher.COUNT -> {
                val k = s.cells.distinct().size
                assertEquals("$label: count step without the count being tight", k - 1, r)
                assertEquals("$label: count step floods a colour with other patches", 1,
                    areaCells(s).count { s.cells[it] == s.cells[step.cell] })
                assertEquals("$label: count step did not wipe a colour out", k - 1, next.cells.distinct().size)
            }
            MosaicTeacher.BIGGEST -> {
                val best = everyFill(s).maxOf { gain(s, it.first, it.second) }
                assertEquals("$label: not the biggest swallow", best, gain(s, step.cell, step.colour))
                assertTrue("$label: biggest swallow of one area", best >= 2)
            }
            MosaicTeacher.CENTRE -> {
                val ecc = eccentricities(s)
                assertEquals("$label: not the most central area", ecc.values.min(), ecc.getValue(s.area(step.cell).min()))
            }
            MosaicTeacher.KEEPS -> assertTrue("$label: the fallback must say so", "No rule of thumb" in step.explanation)
            else -> throw AssertionError("$label: unexpected technique ${step.technique}")
        }
    }

    /** Each area's eccentricity (by its least cell) in the area graph, by plain BFS over cells. */
    private fun eccentricities(s: MosaicState): Map<Int, Int> {
        val reps = areaCells(s).map { s.area(it).min() }
        val repOf = IntArray(s.cells.size)
        for (rep in reps) for (x in s.area(rep)) repOf[x] = rep
        val adj = reps.associateWith { mutableSetOf<Int>() }
        for (x in s.cells.indices) {
            if (x % s.width < s.width - 1 && repOf[x] != repOf[x + 1]) {
                adj.getValue(repOf[x]) += repOf[x + 1]; adj.getValue(repOf[x + 1]) += repOf[x]
            }
            if (x / s.width < s.height - 1 && repOf[x] != repOf[x + s.width]) {
                adj.getValue(repOf[x]) += repOf[x + s.width]; adj.getValue(repOf[x + s.width]) += repOf[x]
            }
        }
        return reps.associateWith { from ->
            val d = mutableMapOf(from to 0)
            val q = ArrayDeque(listOf(from))
            while (q.isNotEmpty()) {
                val u = q.removeFirst()
                for (v in adj.getValue(u)) if (v !in d) { d[v] = d.getValue(u) + 1; q += v }
            }
            d.values.max()
        }
    }

    @Test
    fun `every taught fill on a hint-walked board still finishes in the limit, for the reason given`() {
        for (tier in Difficulty.entries) {
            for (seed in seeds(if (tier == Difficulty.EXPERT) 12 else 30, tier, "mosaic-teach-sound")) {
                var s = board(seed, tier)
                while (!s.solved) {
                    val label = "$tier/$seed@${s.moves}"
                    val step = MosaicTeacher.teach(s)
                    assertNotNull("$label: no hint on a live board", step)
                    assertFalse("$label: a board built from hints called lost", step!!.technique == MosaicTeacher.MISTAKE)
                    checkFill(label, s, step)
                    val d = Mosaic.teach(s)!!
                    val next = d.apply(s) as MosaicState
                    assertTrue("$label: applying it did not reach it", d.isReached(next))
                    assertFalse("$label: reached before it was made", d.isReached(s))
                    assertEquals("$label: Show me made a different fill", s.flood(step.cell, step.colour).cells, next.cells)
                    s = next
                }
                assertTrue("$tier/$seed: hints overran the limit", s.moves <= s.limit)
            }
        }
    }

    @Test
    fun `from boards a player made, hints stay sound and a lost board is always caught`() {
        val rng = Random(31)
        for (tier in Difficulty.entries) {
            for (seed in seeds(if (tier == Difficulty.EXPERT) 6 else 16, tier, "mosaic-teach-played")) {
                var s = board(seed, tier)
                // Random fills, each checked independently for whether the board is still winnable.
                while (!s.solved && left(s) > 0) {
                    val (cell, colour) = everyFill(s).let { it[rng.nextInt(it.size)] }
                    s = s.flood(cell, colour)
                    val alive = !s.solved && winsWithin(s, left(s))
                    if (s.solved) break
                    val label = "$tier/$seed@${s.moves}"
                    val step = MosaicTeacher.teach(s)!!
                    if (alive) {
                        assertFalse("$label: a winnable board called lost", step.technique == MosaicTeacher.MISTAKE)
                        checkFill(label, s, step)
                    } else {
                        assertEquals("$label: a lost board was not caught", MosaicTeacher.MISTAKE, step.technique)
                        checkLost(label, s, step)
                        break
                    }
                }
            }
        }
    }

    /** The rewind point is the last one that could still finish: it can, and one fill later cannot. */
    private fun checkLost(label: String, s: MosaicState, step: MosaicTeacher.Step) {
        val j = step.rewindTo
        val back = step.rewind
        assertNotNull("$label: the fills are on record, so the rewind should be known", back)
        back!!
        assertEquals(j, back.moves)
        assertTrue("$label: rewinds to a board that cannot finish either", winsWithin(back, back.limit - j))
        val slipped = back.flood(s.trail[j] / 8, s.trail[j] % 8)
        assertEquals("$label: the named fill is not the player's", s.trail.take(j + 1), slipped.trail)
        assertFalse("$label: the named fill did not lose it", winsWithin(slipped, slipped.limit - slipped.moves))
        assertTrue("$label: should tell them how far to undo: ${step.explanation}", "Undo" in step.explanation)
        assertTrue("$label: explanation too long (${step.explanation.length})", step.explanation.length <= 180)
        assertEquals("$label: glow is not the cells the slip filled", back.area(s.trail[j] / 8).toSet(), step.focus)

        val d = Mosaic.teach(s)!!
        assertTrue(d.mistake)
        val rewound = d.apply(s) as MosaicState
        assertEquals(back.cells, rewound.cells)
        assertTrue("$label: Show me's rewind does not count as fixed", d.isReached(rewound))
        assertFalse("$label: the lost board counts as fixed", d.isReached(s))
    }

    @Test
    fun `a wasted first fill is named, with undo back to the start`() {
        // The walkthrough board: its one good first fill is violet-corner-to-amber. Teal into the
        // corner instead wastes a fill that zero slack cannot spare.
        val start = MosaicState(5, 5, 3, 3, Mosaic.TUTORIAL_CELLS)
        val s = start.flood(17, 1)
        val step = MosaicTeacher.teach(s)!!
        assertEquals(MosaicTeacher.MISTAKE, step.technique)
        assertEquals(0, step.rewindTo)
        assertTrue(step.explanation, "first fill" in step.explanation && "teal" in step.explanation)
        assertEquals(start.cells, step.rewind!!.cells)

        // Out of fills entirely: still says where it slipped, not merely that it is over.
        val spent = s.flood(0, 0).flood(0, 2)
        assertTrue(spent.failed)
        val over = MosaicTeacher.teach(spent)!!
        assertEquals(MosaicTeacher.MISTAKE, over.technique)
        assertEquals(0, over.rewindTo)
        assertTrue(over.explanation, over.explanation.startsWith("You're out of fills."))
    }

    @Test
    fun `a lost board with no record of its fills still says so, without naming a fill`() {
        val start = MosaicState(5, 5, 3, 3, Mosaic.TUTORIAL_CELLS)
        val played = start.flood(17, 1)
        // As a game saved before fills were recorded would load.
        val s = played.copy(start = emptyList(), trail = emptyList())
        val step = MosaicTeacher.teach(s)!!
        assertEquals(MosaicTeacher.MISTAKE, step.technique)
        assertNull(step.rewind)
        assertTrue(step.explanation, "Undo back" in step.explanation)
    }

    @Test
    fun `deduce reasons from the visible board alone`() {
        // The entry point that takes no record: the same board with and without one gets the same fill.
        for (tier in Difficulty.entries) {
            for (seed in seeds(4, tier, "mosaic-teach-visible")) {
                val b = board(seed, tier)
                val a = MosaicTeacher.deduce(b.width, b.height, b.colours, b.cells, b.limit)!!
                val t = MosaicTeacher.teach(b)!!
                assertEquals(a.technique, t.technique)
                assertEquals(a.cell to a.colour, t.cell to t.colour)
            }
        }
    }

    // ---- coverage -------------------------------------------------------------------------------

    /**
     * A measurement, printed and written to `app/build/reports/mosaic-teaching-coverage.txt`: how
     * often each technique is what the next fill needs, walked from the start by hints alone, and
     * how often only the honest fallback applies.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = mapOf(Difficulty.STANDARD to 200, Difficulty.HARD to 200, Difficulty.EXPERT to 120)
        val report = StringBuilder()
        report.appendLine("Mosaic teaching coverage, walked from the start by hints alone")
        for (tier in Difficulty.entries) {
            val n = perTier.getValue(tier)
            val steps = MosaicTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boards = MosaicTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var total = 0
            var slowest = 0L
            for (seed in seeds(n, tier, "mosaic-coverage")) {
                var s = board(seed, tier)
                val used = mutableSetOf<String>()
                while (!s.solved) {
                    val t0 = System.nanoTime()
                    val step = MosaicTeacher.teach(s)
                    slowest = maxOf(slowest, System.nanoTime() - t0)
                    assertNotNull("$tier/$seed: no hint", step)
                    steps[step!!.technique] = steps.getValue(step.technique) + 1
                    used += step.technique
                    total++
                    s = s.flood(step.cell, step.colour)
                }
                used.forEach { boards[it] = boards.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${tier.name}: $n boards, $total steps; slowest hint ${slowest / 1_000_000} ms")
            for (t in MosaicTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-16s %5d steps (%5.1f%%)   on %3d/%d boards (%5.1f%%)".format(
                        t, steps.getValue(t), 100.0 * steps.getValue(t) / total,
                        boards.getValue(t), n, 100.0 * boards.getValue(t) / n,
                    )
                )
            }
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/mosaic-teaching-coverage.txt").writeText(report.toString())
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    /** Every fill that keeps [s] finishable in its limit, as the boards they leave. */
    private fun keepingFills(s: MosaicState): Set<List<Int>> =
        everyFill(s).map { s.flood(it.first, it.second) }
            .filter { winsWithin(it, left(it)) }
            .map { it.cells }
            .toSet()

    @Test
    fun `the walkthrough board needs exactly its limit, and each taught fill is the only one that keeps it`() {
        val start = MosaicState(5, 5, 3, 3, Mosaic.TUTORIAL_CELLS)
        assertEquals("the walkthrough's limit is not its optimum", 3, breadthFirst(start))
        var s = start
        val techniques = listOf(MosaicTeacher.BIGGEST, MosaicTeacher.COUNT, MosaicTeacher.FINISH)
        for ((i, fill) in Mosaic.TUTORIAL_FILLS.withIndex()) {
            val next = s.flood(fill.first, fill.second)
            assertEquals("fill ${i + 1} is not the one fill that keeps the board", setOf(next.cells), keepingFills(s))
            val step = MosaicTeacher.teach(s)!!
            assertEquals("fill ${i + 1}: the teacher gives another reason", techniques[i], step.technique)
            assertEquals("fill ${i + 1}: the teacher picks another fill", next.cells, s.flood(step.cell, step.colour).cells)
            s = next
        }
        assertTrue(s.solved)
    }

    @Test
    fun `each walkthrough frame accepts its fill, from any cell of the area, and rejects a wrong one`() {
        val frames = Mosaic.tutorial
        assertEquals(6, frames.size)
        fun board(i: Int) = frames[i].state as MosaicState
        listOf(0, 1).forEach { assertNull("frame ${it + 1} should be Next-only", frames[it].accepts) }

        for ((offset, fill) in Mosaic.TUTORIAL_FILLS.withIndex()) {
            val i = offset + 2
            val b = board(i)
            val accepts = frames[i].accepts!!
            val (cell, colour) = fill
            for (other in b.area(cell)) assertTrue("frame ${i + 1} from cell $other", accepts(b.flood(other, colour)))
            val wrongColour = (0 until b.colours).first { it != colour && it != b.cells[cell] }
            assertFalse("frame ${i + 1}: the wrong colour", accepts(b.flood(cell, wrongColour)))
            // The last frame's board has only the one area not already that colour.
            areaCells(b).firstOrNull { it !in b.area(cell) && b.cells[it] != colour }?.let { elsewhere ->
                assertFalse("frame ${i + 1}: the right colour, wrong area", accepts(b.flood(elsewhere, colour)))
            }
            val otherArea = areaCells(b).first { it !in b.area(cell) }
            val otherColour = (0 until b.colours).first { it != b.cells[otherArea] }
            assertFalse("frame ${i + 1}: another area", accepts(b.flood(otherArea, otherColour)))
            if (i + 1 < 5) assertEquals("frame ${i + 2} continues from frame ${i + 1}", board(i + 1).cells, b.flood(cell, colour).cells)
            assertTrue("frame ${i + 1}'s glow misses the area to tap", frames[i].highlight.strong.containsAll(b.area(cell).toList()))
        }
        assertTrue("the last taught fill finishes the board", board(4).flood(Mosaic.TUTORIAL_FILLS[2].first, Mosaic.TUTORIAL_FILLS[2].second).solved)
    }

    @Test
    fun `the your-turn board is exactly its limit and hints finish it without the fallback`() {
        val frame = Mosaic.tutorial.last()
        assertTrue(frame.freePlay)
        val start = frame.state as MosaicState
        assertEquals("the practice board's limit is not its optimum", start.limit, Mosaic.solve(start)!!.size)
        assertFalse(winsWithin(start, start.limit - 1))
        var s = start
        while (!s.solved) {
            val d = Mosaic.teach(s)!!
            assertFalse("the your-turn board should not need the fallback (${d.technique})", d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as MosaicState
        }
        assertEquals(start.limit, s.moves)
    }
}
