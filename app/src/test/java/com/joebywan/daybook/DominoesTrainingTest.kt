package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.DominoesTraining
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoState
import com.joebywan.daybook.puzzles.Dominoes
import com.joebywan.daybook.puzzles.Placement
import com.joebywan.daybook.puzzles.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dominoes' course (docs/TRAINING_SPEC.md). Each practice is checked against [DominoOracle]'s count of answers and
 * against the ladder's levels re-derived here over a plain list of candidates (every unplaced domino on every free pair,
 * either way round), struck by each level's rule as the rules sheet states it; nothing is borrowed from the teacher.
 */
class DominoesTrainingTest {

    private class Cand(val d: Int, val s: Int, val t: Int, val vs: Int, val vt: Int) {
        /** How it settled: "square" with the settled square's free neighbours, or "domino". Set by [forced]. */
        var by = ""
        var freeNeighbours = 0
        val placement get() = Placement(s, t)
    }

    /**
     * Strikes candidates on [b] with the levels up to [level] (1 a square with one partner, 3 a rule broken whatever goes
     * on the region's other squares, 4 a rule the region's other squares' values can't keep, 5 a value's halves all
     * spoken for) until nothing changes, and says whether a placement is then forced: a free square or an unplaced domino
     * with one candidate left.
     */
    private fun forced(b: DominoState, level: Int): List<Cand> {
        val n = b.width * b.height
        val pips = IntArray(n) { -1 }
        b.placed.forEachIndexed { k, p -> if (p != null) { pips[p.first] = b.tray[k].a; pips[p.second] = b.tray[k].b } }
        val free = BooleanArray(n) { b.open[it] && pips[it] < 0 }
        fun adjacent(x: Int, y: Int) = (y == x + 1 && x % b.width != b.width - 1) || y == x + b.width
        val cands = ArrayList<Cand>()
        for (k in b.tray.indices) {
            if (b.placed[k] != null) continue
            val dom = b.tray[k]
            for (x in 0 until n) for (y in 0 until n) {
                if (!free[x] || !free[y] || !(adjacent(x, y) || adjacent(y, x))) continue
                if (dom.a == dom.b && y < x) continue
                cands += Cand(k, x, y, dom.a, dom.b)
            }
        }
        val alive = BooleanArray(cands.size) { true }
        val members = b.rules.indices.map { r -> (0 until n).filter { b.regionOf[it] == r } }
        fun mask(q: Int): Int {
            if (pips[q] >= 0) return 1 shl pips[q]
            var m = 0
            for ((i, c) in cands.withIndex()) if (alive[i]) { if (c.s == q) m = m or (1 shl c.vs); if (c.t == q) m = m or (1 shl c.vt) }
            return m
        }
        // Whether region r can keep its rule with these values fixed and the other squares free to take any of their masks.
        fun keeps(r: Int, fixed: List<Int>, masks: List<Int>): Boolean {
            val rule = b.rules[r]
            fun values(m: Int) = (0..6).filter { m shr it and 1 == 1 }
            return when (rule.kind) {
                RuleKind.SUM, RuleKind.LESS, RuleKind.MORE -> {
                    // Every total the region can reach, square by square.
                    var totals = setOf(fixed.sum())
                    for (m in masks) totals = totals.flatMap { s -> values(m).map { s + it } }.toSet()
                    when (rule.kind) {
                        RuleKind.SUM -> rule.n in totals
                        RuleKind.LESS -> totals.any { it < rule.n }
                        else -> totals.any { it > rule.n }
                    }
                }
                RuleKind.EQUAL -> (0..6).any { x -> fixed.all { it == x } && masks.all { m -> m shr x and 1 == 1 } }
                RuleKind.DIFFERENT -> {
                    fun place(i: Int, used: Set<Int>): Boolean =
                        i == masks.size || values(masks[i]).any { it !in used && place(i + 1, used + it) }
                    fixed.distinct().size == fixed.size && place(0, fixed.toSet())
                }
            }
        }
        fun view(c: Cand, r: Int): Pair<List<Int>, List<Int>> {
            val fixed = ArrayList<Int>()
            val others = ArrayList<Int>()
            for (q in members[r]) when {
                q == c.s -> fixed += c.vs
                q == c.t -> fixed += c.vt
                pips[q] >= 0 -> fixed += pips[q]
                else -> others += q
            }
            return fixed to others
        }
        fun regionsOf(c: Cand) = listOf(b.regionOf[c.s], b.regionOf[c.t]).filter { it >= 0 }.distinct()
        while (true) {
            var changed = false
            // A square whose live candidates all share one partner: the partner pairs with nothing else.
            for (q in 0 until n) {
                if (!free[q]) continue
                val partners = cands.indices.filter { alive[it] && (cands[it].s == q || cands[it].t == q) }
                    .map { if (cands[it].s == q) cands[it].t else cands[it].s }.distinct()
                if (partners.size != 1) continue
                for ((i, c) in cands.withIndex()) {
                    if (alive[i] && (c.s == partners[0] || c.t == partners[0]) && c.s != q && c.t != q) { alive[i] = false; changed = true }
                }
            }
            // A candidate after which a region's rule fails with every other square of it 0 to 6.
            if (level >= 3) for ((i, c) in cands.withIndex()) {
                if (alive[i] && regionsOf(c).any { r -> val (f, o) = view(c, r); !keeps(r, f, o.map { 0x7F }) }) { alive[i] = false; changed = true }
            }
            if (level >= 4 && !changed) {
                val masks = IntArray(n) { mask(it) }
                for ((i, c) in cands.withIndex()) {
                    if (alive[i] && regionsOf(c).any { r -> val (f, o) = view(c, r); !keeps(r, f, o.map { masks[it] }) }) { alive[i] = false; changed = true }
                }
            }
            if (level >= 5 && !changed) {
                val masks = IntArray(n) { mask(it) }
                for (v in 0..6) {
                    val only = (0 until n).filter { free[it] && masks[it] == 1 shl v }
                    val halves = b.tray.indices.filter { b.placed[it] == null }.sumOf { (if (b.tray[it].a == v) 1 else 0) + (if (b.tray[it].b == v) 1 else 0) }
                    if (only.isEmpty() || only.size != halves) continue
                    for ((i, c) in cands.withIndex()) {
                        if (alive[i] && ((c.vs == v && c.s !in only) || (c.vt == v && c.t !in only))) { alive[i] = false; changed = true }
                    }
                }
            }
            if (!changed) break
        }
        val live = cands.indices.filter { alive[it] }.map { cands[it] }
        fun copy(c: Cand, by: String, q: Int) = Cand(c.d, c.s, c.t, c.vs, c.vt).also {
            it.by = by
            it.freeNeighbours = if (q < 0) 0 else (0 until n).count { x -> free[x] && (adjacent(q, x) || adjacent(x, q)) }
        }
        return (0 until n).filter { free[it] }.mapNotNull { q -> live.filter { it.s == q || it.t == q }.singleOrNull()?.let { copy(it, "square", q) } } +
            b.tray.indices.filter { b.placed[it] == null }.mapNotNull { k -> live.filter { it.d == k }.singleOrNull()?.let { copy(it, "domino", -1) } }
    }

    /** The level each rung's step needs: the three "only" steps up to the rule fit, then 4, 5 and the what-if. */
    private val levelOf = listOf(3, 3, 3, 4, 5, 6)

    private fun same(b: DominoState, k: Int, p: Placement?, q: Placement) =
        p == q || (b.tray[k].a == b.tray[k].b && p == Placement(q.second, q.first))

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertTrue(DominoesTraining.drill is MoveDrill)
        for ((rung, t) in DominoesTraining.techniques.withIndex()) {
            val list = DominoesTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as DominoState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Dominoes.teach(b)!!.technique)
                assertEquals("a tray of different dominoes", b.tray.size, b.tray.distinct().size)
                val answers = DominoOracle.answers(b.width, b.height, b.open, b.regionOf, b.rules, b.tray, cap = 2)
                assertEquals("one answer", 1, answers.size)
                assertTrue("the answer is the stored one", b.tray.indices.all { same(b, it, answers[0][it], b.answer[it]) })
                assertTrue("walked dominoes are the answer's", b.placed.indices.all { b.placed[it] == null || same(b, it, b.placed[it], b.answer[it]) })
                // Nothing is forced with the levels below this rung's; with its own level, something is (bar the what-if).
                val level = levelOf[rung]
                if (level > 3) assertTrue("$t board has a step below level $level", forced(b, level - 1).isEmpty())
                if (level < 6) assertTrue("$t board has its own step by counting", forced(b, level).isNotEmpty())
                // The step lays one domino where the answer has it.
                val d = p.step as Deduction
                val after = d.apply(b) as DominoState
                val laid = b.tray.indices.single { b.placed[it] == null && after.placed[it] != null }
                assertTrue(same(b, laid, after.placed[laid], b.answer[laid]))
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when the teacher calls it a mistake, and otherwise plays on`() {
        for (t in DominoesTraining.techniques) {
            for (p in DominoesTraining.practices(t).take(3)) {
                val b = p.board as DominoState
                val d = p.step as Deduction
                val after = d.apply(b) as DominoState
                val laid = b.tray.indices.single { b.placed[it] == null && after.placed[it] != null }
                assertEquals(true, DominoesTraining.judge(p, after))
                // The step's domino on two free squares the answer doesn't give it: wrong.
                val free = (0 until b.width * b.height).filter { b.open[it] && b.placed.none { q -> q != null && (q.first == it || q.second == it) } }
                val wrong = free.flatMap { x -> listOf(x + 1, x + b.width).filter { it in free && (it != x + 1 || x % b.width != b.width - 1) }.map { Placement(x, it) } }
                    .first { !same(b, laid, it, b.answer[laid]) && !same(b, laid, Placement(it.second, it.first), b.answer[laid]) }
                assertEquals(false, DominoesTraining.judge(p, DominoLogic.place(b, laid, wrong)))
                // The same domino turned the wrong way round, when it isn't a double: wrong too.
                val way = b.answer[laid]
                if (b.tray[laid].a != b.tray[laid].b) assertEquals(false, DominoesTraining.judge(p, DominoLogic.place(b, laid, Placement(way.second, way.first))))
            }
        }
    }

    /** The instances of rung [rung] on [b] by the counting above, as placements: the three "only" steps told apart by how they settle. */
    private fun instances(b: DominoState, rung: Int): Set<Pair<Int, Placement>> = forced(b, levelOf[rung]).filter { c ->
        when (rung) {
            0 -> c.by == "square" && c.freeNeighbours == 1
            1 -> c.by == "domino"
            2 -> c.by == "square" && c.freeNeighbours != 1
            else -> true
        }
    }.map { it.d to it.placement }.toSet()

    @Test fun `another instance of the same technique is right, and a true domino no instance justifies plays on`() {
        var others = 0
        var unjustified = 0
        for ((rung, t) in DominoesTraining.techniques.take(5).withIndex()) {
            for (p in DominoesTraining.practices(t).take(12)) {
                val b = p.board as DominoState
                val mine = (p.step as Deduction).apply(b) as DominoState
                val laid = b.tray.indices.single { b.placed[it] == null && mine.placed[it] != null }
                val found = instances(b, rung)
                assertTrue("$t: the step is an instance by counting", found.any { (k, q) -> k == laid && same(b, k, q, mine.placed[k]!!) })
                for ((k, q) in found) {
                    if (k == laid) continue
                    others++
                    assertEquals("$t: domino $k at $q", true, DominoesTraining.judge(p, DominoLogic.place(b, k, q)))
                }
                // A domino laid where the answer has it, that no instance of the technique settles: no verdict.
                b.tray.indices.firstOrNull { k -> b.placed[k] == null && found.none { it.first == k } }?.let { k ->
                    unjustified++
                    assertNull("$t: domino $k", DominoesTraining.judge(p, DominoLogic.place(b, k, b.answer[k])))
                }
            }
        }
        assertTrue("some practice has another instance ($others)", others > 0)
        assertTrue("some practice has an unjustified true domino ($unjustified)", unjustified > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("dominoes").keys.toList(), DominoesTraining.techniques)
        val index = (0 until 60).first { k ->
            DominoesTraining.board(k, Difficulty.EXPERT)?.let { DominoesTraining.techniquesUsed(it) } == DominoesTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), DominoesTraining.examSeed)
        val exam = DominoLogic.generateVerified(DominoesTraining.examSeed!!, Difficulty.EXPERT)
        assertNotNull(exam)
        assertEquals(exam, DominoesTraining.exam())
        assertEquals(DominoesTraining.techniques.toSet(), DominoesTraining.techniquesUsed(exam!!))
        assertFalse(DominoesTraining.techniques.isEmpty())
    }
}
