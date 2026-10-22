package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.SandLogic
import com.joebywan.daybook.puzzles.SandState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * `SandLogic` against [SandOracle], which shares nothing with it but the spec's prose (breadth-first over raw positions,
 * no canonical sort, no pruning, no budget). Written from `docs/SAND_SPEC.md` without reading the implementation.
 */
class SandDifferentialTest {

    private class Board(val tubes: List<List<Int>>, val height: Int)

    /** A random deal of [colours] x [height] into full tubes plus [spares] empty ones; the generator's rejection rules are not applied. */
    private fun deal(rng: Rng, colours: Int, height: Int, spares: Int): Board {
        val layers = rng.shuffled((0 until colours).flatMap { c -> List(height) { c } })
        return Board((0 until colours).map { layers.subList(it * height, (it + 1) * height) } + List(spares) { emptyList() }, height)
    }

    /**
     * Positions from a random walk of legal pours (the oracle's rule) from [b]: the start, the middle and the end. A
     * walk wanders into unsortable positions often enough to exercise `Unsolvable`, which a fresh deal with two spares
     * almost never is. One-spare deals are mixed in for the same reason; the rules do not depend on the spare count.
     */
    private fun positions(rng: Rng, b: Board): List<List<List<Int>>> {
        val out = mutableListOf(b.tubes)
        var p = b.tubes
        val steps = rng.nextInt(4, 24)
        for (i in 1..steps) {
            val moves = p.indices.flatMap { f -> p.indices.map { t -> f to t } }.filter { (f, t) -> SandOracle.canPour(p, b.height, f, t) }
            if (moves.isEmpty()) break
            val (f, t) = rng.pick(moves)
            p = SandOracle.pour(p, b.height, f, t)!!
            if (i == steps / 2) out += p
        }
        out += p
        return out
    }

    private fun boards(): List<Board> {
        val rng = Rng(0x5A4DL)
        return (0 until 300).map {
            val colours = rng.nextInt(3, 5)
            val height = rng.nextInt(3, 4)
            deal(rng, colours, height, if (it % 3 == 2) 1 else 2)
        }
    }

    @Test
    fun `pour, solved and stuck agree with the oracle on every move from sampled positions`() {
        val rng = Rng(11)
        var pours = 0
        for (b in boards()) for (p in positions(rng, b)) {
            val h = b.height
            assertEquals("isSolved $p", SandOracle.isSolved(p, h), SandLogic.isSolved(p, h))
            assertEquals("isStuck $p", SandOracle.isStuck(p, h), SandLogic.isStuck(p, h))
            for (f in p.indices) for (t in p.indices) {
                val legal = SandOracle.canPour(p, h, f, t)
                assertEquals("canPour $f->$t on $p", legal, SandLogic.canPour(p, h, f, t))
                if (legal) {
                    assertEquals("pour $f->$t on $p", SandOracle.pour(p, h, f, t), SandLogic.pour(p, h, f, t))
                    pours++
                }
            }
        }
        assertTrue("too few legal pours compared: $pours", pours > 3000)
    }

    @Test
    fun `solve is Solvable exactly when the oracle finds a road, and Unsolvable only when it exhausts`() {
        val rng = Rng(23)
        var solvable = 0
        var unsolvable = 0
        for (b in boards()) for (p in positions(rng, b)) {
            val h = b.height
            val oracle = SandOracle.solve(p, h)
            when (val v = SandLogic.solve(p, h, 50_000_000)) {
                is SandLogic.Verdict.Solvable -> {
                    assertTrue("solve found a road the oracle says cannot exist on $p", oracle.solvable)
                    val end = SandOracle.replay(p, h, v.path)
                    assertNotNull("solve's road has an illegal pour on $p: ${v.path}", end)
                    assertTrue("solve's road does not finish $p: ${v.path}", SandOracle.isSolved(end!!, h))
                    assertTrue("shorter than the oracle's shortest on $p", v.path.size >= oracle.shortest!!.size)
                    solvable++
                }
                is SandLogic.Verdict.Unsolvable -> {
                    assertFalse("solve said Unsolvable but the oracle sorts $p by ${oracle.shortest}", oracle.solvable)
                    unsolvable++
                }
                is SandLogic.Verdict.Truncated -> fail("Truncated on a small board with a huge budget: $p")
            }
        }
        // Both verdicts must actually be exercised, or the comparison proves one direction only.
        assertTrue("solvable cases: $solvable", solvable > 300)
        assertTrue("unsolvable cases: $unsolvable", unsolvable > 30)
    }

    @Test
    fun `a solved position is Solvable with an empty road`() {
        val sorted = listOf(listOf(1, 1, 1), emptyList(), listOf(0, 0, 0), emptyList())
        val v = SandLogic.solve(sorted, 3, 1_000)
        assertTrue("$v", v is SandLogic.Verdict.Solvable && v.path.isEmpty())
    }

    @Test
    fun `a budget too small to finish is Truncated, never Unsolvable`() {
        // Fresh two-spare deals the oracle sorts, with a budget of one: whatever solve can see in one step, it cannot
        // have exhausted the space, so the only honest answers are Solvable (with a real road) or Truncated.
        val rng = Rng(31)
        repeat(40) {
            val b = deal(rng, 5, 4, 2)
            if (!SandOracle.solve(b.tubes, 4).solvable) return@repeat
            when (val v = SandLogic.solve(b.tubes, 4, 1)) {
                is SandLogic.Verdict.Unsolvable -> fail("a budget of one called a sortable board Unsolvable: ${b.tubes}")
                is SandLogic.Verdict.Solvable -> assertTrue(SandOracle.isSolved(SandOracle.replay(b.tubes, 4, v.path)!!, 4))
                is SandLogic.Verdict.Truncated -> Unit
            }
        }
    }

    /** The oracle when the board is small enough for raw breadth-first, else solve's own road replayed by the oracle's rule. */
    private fun assertSortable(s: SandState, what: String) {
        if (s.colours <= 5) {
            assertTrue("$what: the oracle cannot sort ${s.tubes}", SandOracle.solve(s.tubes, s.height).solvable)
        } else {
            val v = SandLogic.solve(s.tubes, s.height, 50_000_000)
            assertTrue("$what: solve did not prove ${s.tubes}: $v", v is SandLogic.Verdict.Solvable)
            val end = SandOracle.replay(s.tubes, s.height, (v as SandLogic.Verdict.Solvable).path)
            assertTrue("$what: solve's road does not replay to solved by the oracle's rule", end != null && SandOracle.isSolved(end, s.height))
        }
    }

    @Test
    fun `generated daily boards follow the dealing rules and can be sorted`() {
        val start = LocalDate.of(2026, 1, 1)
        for (difficulty in Difficulty.entries) for (day in 0L until 365L step 7) {
            val seed = DailySeed.seedFor(start.plusDays(day), "sand", difficulty)
            val s = checkNotNull(SandLogic.generateVerified(seed, difficulty)) { "generateVerified gave up: $difficulty day $day" }
            val what = "$difficulty day $day ${s.tubes}"
            val h = s.height
            val c = s.colours
            assertEquals("$what: C + 2 tubes", c + 2, s.tubes.size)
            assertEquals("$what: exactly two empty tubes", 2, s.tubes.count { it.isEmpty() })
            for (t in s.tubes) {
                assertTrue("$what: a dealt tube is full", t.isEmpty() || t.size == h)
                assertTrue("$what: colour out of range", t.all { it in 0 until c })
                assertTrue("$what: a tube already one colour", t.isEmpty() || t.toSet().size > 1)
                var run = 0
                for (i in t.indices) {
                    run = if (i > 0 && t[i] == t[i - 1]) run + 1 else 1
                    assertTrue("$what: a run longer than H - 2", run <= h - 2)
                }
            }
            for (colour in 0 until c) assertEquals("$what: colour $colour layers", h, s.tubes.sumOf { t -> t.count { it == colour } })
            assertEquals(0, s.moves)
            assertFalse(what, SandOracle.isSolved(s.tubes, h))
            assertSortable(s, what)
        }
    }

    @Test
    fun `each tier's last resort can be sorted`() {
        for (difficulty in Difficulty.entries) {
            val s = SandLogic.lastResort(difficulty)
            assertFalse("$difficulty last resort is already solved", SandOracle.isSolved(s.tubes, s.height))
            for (colour in 0 until s.colours) assertEquals(s.height, s.tubes.sumOf { t -> t.count { it == colour } })
            assertSortable(s, "$difficulty last resort")
        }
    }
}
