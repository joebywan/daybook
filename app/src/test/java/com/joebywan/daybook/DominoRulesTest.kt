package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Domino
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoState
import com.joebywan.daybook.puzzles.DominoTeacher
import com.joebywan.daybook.puzzles.Placement
import com.joebywan.daybook.puzzles.RegionRule
import com.joebywan.daybook.puzzles.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Dominoes' rules, exhaustive count and generator, checked against [DominoOracle] (reading order,
 * no pruning, rules only on finished regions) and brute force over region completions, neither of
 * which shares anything with the code under test.
 */
class DominoRulesTest {

    companion object {
        fun seeds(count: Int, d: Difficulty, salt: String = "dominoes") = (0 until count).map {
            DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, d)
        }

        fun oracle(s: DominoState, cap: Int = 2, placed: List<Placement?> = List(s.tray.size) { null }) =
            DominoOracle.answers(s.width, s.height, s.open, s.regionOf, s.rules, s.tray, cap, placed)

        /**
         * A random small board with at least one answer: a random tiling of a random shape, distinct
         * dominoes laid on it, random regions whose rules are true of that answer (or, a third of the
         * time, loosened or made up), so the sample has boards with none, one and several answers.
         */
        fun planted(rng: Rng, w: Int, h: Int): DominoState? {
            val n = w * h
            val taken = BooleanArray(n)
            val tiles = ArrayList<Placement>()
            for (q in 0 until n) {
                if (taken[q] || rng.nextInt(5) == 0) continue
                val options = listOf(q + 1, q + w).filter { it < n && !taken[it] && !(it == q + 1 && q % w == w - 1) }
                if (options.isEmpty()) continue
                val p = rng.pick(options)
                taken[q] = true; taken[p] = true
                tiles.add(Placement(q, p))
            }
            if (tiles.size < 2) return null
            val doms = rng.shuffled(DominoLogic.SET).take(tiles.size)
            val pips = IntArray(n) { -1 }
            val laid = tiles.mapIndexed { i, t ->
                val p = if (rng.nextBoolean() || doms[i].isDouble) t else Placement(t.second, t.first)
                pips[p.first] = doms[i].a; pips[p.second] = doms[i].b
                p
            }
            val regionOf = MutableList(n) { -1 }
            val rules = ArrayList<RegionRule>()
            for (q in 0 until n) {
                if (!taken[q] || regionOf[q] >= 0 || rng.nextInt(4) == 0) continue
                val members = arrayListOf(q)
                regionOf[q] = rules.size
                val size = rng.nextInt(1, 4)
                while (members.size < size) {
                    val next = members.flatMap { m -> listOf(m - 1, m + 1, m - w, m + w).filter { it in 0 until n && taken[it] && regionOf[it] < 0 && DominoLogic.adjacent(w, m, it) } }
                    if (next.isEmpty()) break
                    val x = rng.pick(next.sorted())
                    regionOf[x] = rules.size
                    members.add(x)
                }
                val v = members.map { pips[it] }
                val rule = when (rng.nextInt(6)) {
                    0 -> if (v.all { it == v[0] }) RegionRule(RuleKind.EQUAL) else RegionRule(RuleKind.SUM, v.sum())
                    1 -> if (v.toSet().size == v.size) RegionRule(RuleKind.DIFFERENT) else RegionRule(RuleKind.SUM, v.sum())
                    2 -> RegionRule(RuleKind.LESS, v.sum() + 1 + rng.nextInt(3))
                    3 -> RegionRule(RuleKind.MORE, v.sum() - 1 - rng.nextInt(3))
                    4 -> RegionRule(RuleKind.SUM, rng.nextInt(0, 12)) // made up: maybe no answer
                    else -> RegionRule(RuleKind.SUM, v.sum())
                }
                rules.add(rule)
            }
            val tray = doms.sortedWith(compareBy({ it.a }, { it.b }))
            val answer = tray.map { d -> laid[doms.indexOf(d)].let { if (d.isDouble && it.first > it.second) Placement(it.second, it.first) else it } }
            return DominoState(w, h, taken.toList(), regionOf, rules, tray, List(tray.size) { null }, answer)
        }

        /** Brute force: some way of giving the [open] squares 0..6 keeps the rule. */
        fun someCompletion(rule: RegionRule, pips: List<Int>, open: Int): Boolean {
            if (open == 0) return holdsByHand(rule, pips)
            return (0..6).any { someCompletion(rule, pips + it, open - 1) }
        }

        fun holdsByHand(rule: RegionRule, v: List<Int>): Boolean = when (rule.kind) {
            RuleKind.SUM -> v.sum() == rule.n
            RuleKind.LESS -> v.sum() < rule.n
            RuleKind.MORE -> v.sum() > rule.n
            RuleKind.EQUAL -> v.distinct().size <= 1
            RuleKind.DIFFERENT -> v.distinct().size == v.size
        }
    }

    // ---- the rules ------------------------------------------------------------------------------------

    @Test
    fun `each rule kind holds exactly when its arithmetic says so`() {
        assertTrue(DominoLogic.ruleHolds(RegionRule(RuleKind.SUM, 9), listOf(6, 3)))
        assertFalse(DominoLogic.ruleHolds(RegionRule(RuleKind.SUM, 9), listOf(6, 2)))
        assertTrue(DominoLogic.ruleHolds(RegionRule(RuleKind.LESS, 4), listOf(1, 2)))
        assertFalse("less is strict", DominoLogic.ruleHolds(RegionRule(RuleKind.LESS, 3), listOf(1, 2)))
        assertTrue(DominoLogic.ruleHolds(RegionRule(RuleKind.MORE, 9), listOf(5, 5)))
        assertFalse("more is strict", DominoLogic.ruleHolds(RegionRule(RuleKind.MORE, 10), listOf(5, 5)))
        assertTrue(DominoLogic.ruleHolds(RegionRule(RuleKind.EQUAL), listOf(4, 4, 4)))
        assertFalse(DominoLogic.ruleHolds(RegionRule(RuleKind.EQUAL), listOf(4, 4, 3)))
        assertTrue(DominoLogic.ruleHolds(RegionRule(RuleKind.DIFFERENT), listOf(0, 6, 3)))
        assertFalse(DominoLogic.ruleHolds(RegionRule(RuleKind.DIFFERENT), listOf(0, 6, 0)))
    }

    @Test
    fun `a rule is broken exactly when no completion of its empty squares keeps it`() {
        val rng = Rng(31)
        repeat(3000) {
            val kind = RuleKind.entries[rng.nextInt(RuleKind.entries.size)]
            val rule = RegionRule(kind, rng.nextInt(0, 20))
            val covered = List(rng.nextInt(0, 3)) { rng.nextInt(0, 6) }
            val open = rng.nextInt(0, 3)
            assertEquals("$rule $covered +$open", !someCompletion(rule, covered, open), DominoLogic.ruleBroken(rule, covered, open))
            if (open == 0 && covered.isNotEmpty()) assertEquals(holdsByHand(rule, covered), DominoLogic.ruleHolds(rule, covered))
        }
    }

    /** Two squares side by side and two below: a 2x2 box, region 0 the left column, region 1 the right. */
    private fun square2x2(rules: List<RegionRule>, tray: List<Domino>, answer: List<Placement>) =
        DominoState(2, 2, List(4) { true }, listOf(0, 1, 0, 1), rules, tray, List(tray.size) { null }, answer)

    @Test
    fun `a domino may straddle two regions, and the win checks every region`() {
        // Tray 1-2 and 3-4 laid across the rows: the left column shows 1 and 3, the right 2 and 4.
        val s = square2x2(listOf(RegionRule(RuleKind.SUM, 4), RegionRule(RuleKind.SUM, 6)), listOf(Domino(1, 2), Domino(3, 4)), listOf(Placement(0, 1), Placement(2, 3)))
        val done = DominoLogic.place(DominoLogic.place(s, 0, Placement(0, 1)), 1, Placement(2, 3))
        assertTrue(done.solved)
        val flipped = DominoLogic.flip(done, 1)
        assertFalse("turning the 3-4 round breaks both columns", flipped.solved)
        assertTrue(DominoLogic.regionBroken(flipped, 0) && DominoLogic.regionBroken(flipped, 1))
        assertFalse("not every domino down is not solved", DominoLogic.place(s, 0, Placement(0, 1)).solved)
    }

    @Test
    fun `solved checks the rules, so a legal answer that is not the stored one wins`() {
        // Both rows must total 3, and the 0-3 and the 1-2 each make 3 in either row, either way round.
        val s = DominoState(
            2, 2, List(4) { true }, listOf(0, 0, 1, 1),
            listOf(RegionRule(RuleKind.SUM, 3), RegionRule(RuleKind.SUM, 3)),
            listOf(Domino(0, 3), Domino(1, 2)), List(2) { null },
            answer = listOf(Placement(0, 1), Placement(2, 3)),
        )
        val all = oracle(s, cap = 10)
        assertTrue("the board has several answers: $all", all.size > 1)
        assertTrue(DominoLogic.solutions(s) is DominoLogic.Verdict.Several)
        val other = all.first { it != s.answer }
        var b: DominoState = s
        for (k in other.indices) b = DominoLogic.place(b, k, other[k])
        assertTrue("the other answer $other wins too", b.solved)
    }

    @Test
    fun `placing, lifting and flipping are one move each and refuse what cannot be done`() {
        val s = square2x2(listOf(RegionRule(RuleKind.SUM, 4), RegionRule(RuleKind.SUM, 6)), listOf(Domino(1, 2), Domino(3, 3)), listOf(Placement(0, 1), Placement(2, 3)))
        val one = DominoLogic.place(s, 0, Placement(0, 2))
        assertEquals(1, one.moves)
        assertEquals(listOf(1, -1, 2, -1), DominoLogic.pipsOf(one).toList())
        assertSame("a square already covered", one, DominoLogic.place(one, 1, Placement(2, 3)))
        assertSame("not neighbours", s, DominoLogic.place(s, 0, Placement(0, 3)))
        assertSame("a domino already down", one, DominoLogic.place(one, 0, Placement(1, 3)))
        assertEquals(Placement(2, 0), DominoLogic.flip(one, 0).placed[0])
        assertEquals(2, DominoLogic.flip(one, 0).moves)
        val double = DominoLogic.place(one, 1, Placement(1, 3))
        assertSame("a double turned round is the same board", double, DominoLogic.flip(double, 1))
        val lifted = DominoLogic.lift(double, 0)
        assertEquals(null, lifted.placed[0])
        assertEquals(3, lifted.moves)
        assertSame(lifted, DominoLogic.lift(lifted, 0))
        assertEquals(1, DominoLogic.dominoAt(double, 3))
        assertEquals(-1, DominoLogic.dominoAt(lifted, 0))
    }

    // ---- the count against the oracle ------------------------------------------------------------------

    @Test
    fun `the count agrees with the oracle on small boards with no answer, one, and several`() {
        val rng = Rng(4242)
        var none = 0
        var one = 0
        var several = 0
        repeat(6000) {
            val s = planted(rng, rng.nextInt(2, 5), rng.nextInt(2, 4)) ?: return@repeat
            val want = oracle(s)
            when (val v = DominoLogic.solutions(s)) {
                DominoLogic.Verdict.None -> { assertTrue(want.isEmpty()); none++ }
                is DominoLogic.Verdict.ExactlyOne -> { assertEquals(want, listOf(v.placements)); one++ }
                DominoLogic.Verdict.Several -> { assertEquals(2, want.size); several++ }
            }
        }
        println("oracle sample: $none none, $one one, $several several")
        assertTrue("the sample must hold boards with no answer: $none", none > 50)
        assertTrue("and with one: $one", one > 50)
        assertTrue("and with several: $several", several > 50)
    }

    // ---- generated boards --------------------------------------------------------------------------------

    @Test
    fun `every generated board has exactly one answer by the oracle, and it is the stored one`() {
        for (d in Difficulty.entries) {
            val count = if (d == Difficulty.EXPERT) 12 else 40
            var ms = 0L
            for (seed in seeds(count, d, "dominoes-oracle")) {
                val s = DominoLogic.generateVerified(seed, d)!!
                val t0 = System.nanoTime()
                assertEquals("$d/$seed", listOf(s.answer), oracle(s))
                ms += (System.nanoTime() - t0) / 1_000_000
            }
            println("oracle on $count $d boards: ${ms / count} ms a board")
        }
    }

    @Test
    fun `generated boards keep the shape, tray, region and rule rules of their tier`() {
        for (d in Difficulty.entries) {
            val spec = DominoLogic.specFor(d)
            for (seed in seeds(60, d, "dominoes-shape")) {
                val s = DominoLogic.generateVerified(seed, d)!!
                val n = s.width * s.height
                assertEquals(spec.width to spec.height, s.width to s.height)
                assertTrue("$d/$seed tray ${s.tray.size}", s.tray.size in spec.minDominoes..spec.maxDominoes)
                assertEquals(2 * s.tray.size, s.open.count { it })
                assertEquals("distinct and sorted", s.tray.distinct().sortedWith(compareBy({ it.a }, { it.b })), s.tray)
                assertTrue(s.tray.all { it.a in 0..it.b && it.b <= 6 })
                assertTrue("shape is 4-connected", connected(s, (0 until n).filter { s.open[it] }))
                for (r in s.rules.indices) {
                    val members = (0 until n).filter { s.regionOf[it] == r }
                    assertTrue("$d/$seed region $r size ${members.size}", members.size in 1..6)
                    assertTrue(members.all { s.open[it] })
                    assertTrue("$d/$seed region $r is connected", connected(s, members))
                    val kind = s.rules[r].kind
                    if (!spec.bounds) assertTrue("$d has no bounds", kind != RuleKind.LESS && kind != RuleKind.MORE)
                    if (!spec.different) assertTrue("$d has no all-different", kind != RuleKind.DIFFERENT)
                    if (kind == RuleKind.EQUAL || kind == RuleKind.DIFFERENT) assertTrue("no one-square $kind", members.size > 1)
                }
                assertTrue("the answer keeps the rules", DominoLogic.isSolved(s.copy(placed = s.answer)))
                assertFalse(s.solved)
            }
        }
    }

    private fun connected(s: DominoState, squares: List<Int>): Boolean {
        val seen = mutableSetOf(squares.first())
        val todo = ArrayDeque(listOf(squares.first()))
        while (todo.isNotEmpty()) {
            val q = todo.removeFirst()
            for (t in squares) if (t !in seen && DominoLogic.adjacent(s.width, q, t)) { seen += t; todo += t }
        }
        return seen.size == squares.size
    }

    @Test
    fun `the tiers get harder in the order they are offered`() {
        // Each tier needs a deeper rung of the ladder than the one before (Standard placement steps only,
        // Hard a region-value or counting step, Expert a what-if), and lays more dominoes.
        var lastSteps = 0.0
        for (d in Difficulty.entries) {
            val spec = DominoLogic.specFor(d)
            var dominoes = 0
            for (seed in seeds(60, d, "dominoes-depth")) {
                val s = DominoLogic.generateVerified(seed, d)!!
                val level = DominoTeacher.solveLevel(s.layout, DominoTeacher.LEVEL_WHAT_IF)
                assertNotNull(level)
                assertTrue("$d/$seed needs $level", level!! in spec.minLevel..spec.needs)
                dominoes += s.tray.size
            }
            val mean = dominoes / 60.0
            assertTrue("$d lays $mean dominoes, no more than the tier before ($lastSteps)", mean > lastSteps)
            lastSteps = mean
        }
        val levels = Difficulty.entries.map { DominoLogic.specFor(it) }
        assertTrue(levels.zipWithNext().all { (a, b) -> a.needs < b.needs && a.minLevel < b.minLevel })
    }

    /**
     * A measurement over a year of daily boards per tier, printed and written to
     * `app/build/reports/dominoes-generation.txt`, with budgets it must stay inside: how much of the
     * board the rules give away (squares in a ruled region; squares whose value a one-square sum states
     * outright; rules that are exact sums), the ladder level needed, and generation time.
     */
    @Test
    fun `a year of daily boards - what the generator gives away, and what it costs`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            var squares = 0
            var ruled = 0
            var stated = 0
            var rules = 0
            var sums = 0
            val kinds = IntArray(RuleKind.entries.size)
            val levels = IntArray(DominoTeacher.LEVEL_WHAT_IF + 1)
            val ms = ArrayList<Double>()
            for (seed in seeds(365, d)) {
                val t0 = System.nanoTime()
                val s = DominoLogic.generateVerified(seed, d)!!
                ms += (System.nanoTime() - t0) / 1e6
                squares += s.open.count { it }
                ruled += s.regionOf.count { it >= 0 }
                for (r in s.rules.indices) {
                    val size = s.regionOf.count { it == r }
                    if (s.rules[r].kind == RuleKind.SUM) { sums++; if (size == 1) stated++ }
                    kinds[s.rules[r].kind.ordinal]++
                }
                rules += s.rules.size
                levels[DominoTeacher.solveLevel(s.layout, DominoTeacher.LEVEL_WHAT_IF)!!]++
            }
            ms.sort()
            val ruledShare = 100.0 * ruled / squares
            val statedShare = 100.0 * stated / squares
            report.appendLine(
                "${d.name}: squares in a ruled region %.0f%%, stated by a one-square sum %.0f%%, exact sums %.0f%% of %.1f rules a board; ".format(
                    ruledShare, statedShare, 100.0 * sums / rules, rules / 365.0,
                ) + "kinds ${RuleKind.entries.zip(kinds.toList())}; levels needed ${levels.toList()}; " +
                    "ms median %.1f p95 %.1f max %.1f".format(ms[ms.size / 2], ms[ms.size * 95 / 100], ms.last()),
            )
            // Budgets: a board must leave work to do (the Snap lesson), and stay cheap for the browser.
            assertTrue("$d rules cover $ruledShare% of squares", ruledShare < 85)
            assertTrue("$d states $statedShare% of squares outright", statedShare < 40)
            assertTrue("$d median ${ms[ms.size / 2]} ms", ms[ms.size / 2] < 40)
        }
        println(report)
        java.io.File("build/reports").mkdirs()
        java.io.File("build/reports/dominoes-generation.txt").writeText(report.toString())
    }

    @Test
    fun `the last resort of every tier is a board that tier would accept, with one answer`() {
        for (d in Difficulty.entries) {
            val s = DominoLogic.lastResort(d)
            val spec = DominoLogic.specFor(d)
            assertEquals(spec.width, s.width)
            assertTrue(DominoTeacher.solveLevel(s.layout, spec.needs)!! >= spec.minLevel)
            assertEquals(listOf(s.answer), oracle(s))
        }
    }

    @Test
    fun `generation is pure`() {
        for (d in Difficulty.entries) for (seed in seeds(5, d, "dominoes-pure")) {
            assertEquals(DominoLogic.generateVerified(seed, d), DominoLogic.generateVerified(seed, d))
        }
    }
}
