package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.WhosWhoTraining
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who's Who's course (docs/TRAINING_SPEC.md). Each practice is checked against [WhosWhoOracle]'s count of answers and
 * against the ladder's rungs re-read here from the clue kinds and the marks by plain counting (nothing borrowed from
 * the teacher). The words never matter: every rung reads clue kinds and indices only, so no board leans on knowledge.
 */
class WhosWhoTrainingTest {

    /** One instance of a rung: the ticks and crosses it makes. */
    private data class Marks(val ticks: Set<Int>, val crosses: Set<Int>)

    /** The marks as possibilities, in this test's own words: per person and column, a mask of slots still open. */
    private class Table(val s: WhosWhoState) {
        val n = s.n
        val cols = s.columns
        private val cand = Array(n) { IntArray(cols) { (1 shl n) - 1 } }
        private val placed = Array(n) { IntArray(cols) { -1 } }

        init {
            for (p in 0 until n) for (c in 0 until cols) for (slot in 0 until n) {
                if (s.marks[s.markIndex(p, c, slot)] == 'x') cand[p][c] = cand[p][c] and (1 shl slot).inv()
            }
            for (p in 0 until n) for (c in 0 until cols) for (slot in 0 until n) {
                if (s.marks[s.markIndex(p, c, slot)] != 'o') continue
                placed[p][c] = slot
                cand[p][c] = 1 shl slot
                for (q in 0 until n) if (q != p) cand[q][c] = cand[q][c] and (1 shl slot).inv()
            }
        }

        private fun bits(m: Int) = (0 until n).filter { m shr it and 1 == 1 }
        fun isName(ref: Int) = ref / n == 0
        fun can(p: Int, ref: Int) = if (isName(ref)) p == ref % n else cand[p][ref / n - 1] shr (ref % n) and 1 == 1
        fun has(p: Int, ref: Int) = if (isName(ref)) p == ref % n else placed[p][ref / n - 1] == ref % n
        private fun category(ref: Int) = (0 until n).map { ref / n * n + it }

        /** The mark index of [p] holding [ref] (not a name). */
        fun at(p: Int, ref: Int) = s.markIndex(p, ref / n - 1, ref % n)

        /** Every square a clue naming a person settles. */
        val clues: List<Marks> get() = s.clues.mapNotNull { k ->
            when (k.kind) {
                0, 1 -> {
                    val (name, other) = when {
                        isName(k.a) -> k.a to k.b
                        isName(k.b) -> k.b to k.a
                        else -> return@mapNotNull null
                    }
                    val who = name % n
                    when {
                        k.kind == 0 && can(who, other) && !has(who, other) -> Marks(setOf(at(who, other)), emptySet())
                        k.kind == 1 && can(who, other) -> Marks(emptySet(), setOf(at(who, other)))
                        else -> null
                    }
                }
                4 -> when {
                    isName(k.a) -> category(k.b).filter { it != k.b && it != k.c && can(k.a % n, it) }.map { at(k.a % n, it) }
                    isName(k.b) -> (0 until n).filter { it != k.b % n && it != k.c % n && can(it, k.a) }.map { at(it, k.a) }
                    else -> emptyList()
                }.takeIf { it.isNotEmpty() }?.let { Marks(emptySet(), it.toSet()) }
                else -> null
            }
        }
        val clue: Boolean get() = clues.isNotEmpty()

        /** Every tick a cell with one slot open and no tick, or a column's slot only one person can still take, makes. */
        val onlys: List<Marks> get() =
            (0 until n).flatMap { p -> (0 until cols).filter { c -> placed[p][c] < 0 && bits(cand[p][c]).size == 1 }
                .map { c -> Marks(setOf(s.markIndex(p, c, bits(cand[p][c])[0])), emptySet()) } } +
                (0 until cols).flatMap { c -> (0 until n).mapNotNull { slot ->
                    val who = (0 until n).filter { cand[it][c] shr slot and 1 == 1 }
                    if (who.size == 1 && placed[who[0]][c] != slot) Marks(setOf(s.markIndex(who[0], c, slot)), emptySet()) else null
                } }
        val only: Boolean get() = onlys.isNotEmpty()

        /** Same / not / either between two things neither of which is a name, carried through what is known. */
        val link: Boolean get() = s.clues.any { k ->
            if (k.kind !in listOf(0, 1, 4) || isName(k.a) || isName(k.b)) return@any false
            val people = 0 until n
            when (k.kind) {
                0 -> listOf(k.a to k.b, k.b to k.a).any { (x, y) ->
                    people.any { has(it, x) && can(it, y) && !has(it, y) } || people.any { can(it, x) && !can(it, y) }
                }
                1 -> listOf(k.a to k.b, k.b to k.a).any { (x, y) -> people.any { has(it, x) && can(it, y) } }
                else -> people.any { p -> has(p, k.a) && category(k.b).any { it != k.b && it != k.c && can(p, it) } } ||
                    people.any { can(it, k.a) && !can(it, k.b) && !can(it, k.c) }
            }
        }

        private fun related(kind: Int, ha: Int, hb: Int) = if (kind == 2) kotlin.math.abs(ha - hb) == 1 else ha < hb

        /** Houses of [p] (holding one end of a next-to / left-of clue) beside which someone who can hold [other] could live. */
        private fun backed(kind: Int, other: Int, p: Int, aSide: Boolean) = bits(cand[p][0]).filter { h ->
            (0 until n).any { q -> q != p && can(q, other) && bits(cand[q][0]).any { h2 -> if (aSide) related(kind, h, h2) else related(kind, h2, h) } }
        }

        /** Next to and left of against the houses still open. */
        val house: Boolean get() = s.clues.any { k ->
            if (k.kind != 2 && k.kind != 3) return@any false
            listOf(true, false).any { aSide ->
                val x = if (aSide) k.a else k.b
                val y = if (aSide) k.b else k.a
                (0 until n).any { p -> has(p, x) && backed(k.kind, y, p, aSide).size.let { it in 1 until bits(cand[p][0]).size } } ||
                    !isName(x) && (0 until n).any { p -> can(p, x) && backed(k.kind, y, p, aSide).isEmpty() }
            }
        }

        fun has(rung: Int) = when (rung) {
            0 -> clue
            1 -> only
            2 -> link
            3 -> house
            else -> false
        }
    }

    private fun isAnswer(s: WhosWhoState, mark: Int): Boolean {
        val cell = mark / s.n
        return s.answer[cell % s.columns][cell / s.columns] == mark % s.n
    }

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertTrue(WhosWhoTraining.drill is MoveDrill)
        assertEquals(techniqueNamesOf("whoswho").keys.toList(), WhosWhoTraining.techniques)
        for ((rung, t) in WhosWhoTraining.techniques.withIndex()) {
            val list = WhosWhoTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as WhosWhoState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, WhosWho.teach(b)!!.technique)
                assertEquals("one answer", listOf(b.answer), WhosWhoOracle.answers(b.n, b.columns, b.clues, limit = 2))
                assertTrue("walked marks agree with the answer", b.marks.indices.all { m ->
                    when (b.marks[m]) { 'o' -> isAnswer(b, m); 'x' -> !isAnswer(b, m); else -> true }
                })
                val table = Table(b)
                for (lower in 0 until rung) assertFalse("$t board has a rung-$lower step", table.has(lower))
                if (rung < 4) assertTrue("$t board has its own step by counting", table.has(rung))
                // What the step ticks is the answer's; what it crosses is not.
                val after = (p.step as Deduction).apply(b) as WhosWhoState
                assertTrue(after.marks != b.marks)
                for (m in after.marks.indices) if (after.marks[m] != b.marks[m]) assertEquals(isAnswer(b, m), after.marks[m] == 'o')
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when no answer keeps it, and otherwise plays on`() {
        for (t in WhosWhoTraining.techniques) {
            for (p in WhosWhoTraining.practices(t).take(3)) {
                val b = p.board as WhosWhoState
                val d = p.step as Deduction
                assertEquals(true, WhosWhoTraining.judge(p, d.apply(b)))
                val open = b.marks.indices.filter { b.marks[it] == '.' }
                fun at(m: Int, tick: Boolean) = b.mark(m / b.n / b.columns, m / b.n % b.columns, m % b.n, tick)
                // A tick off the answer, or a cross on it, is wrong: the one answer does not keep it.
                assertEquals(false, WhosWhoTraining.judge(p, at(open.first { !isAnswer(b, it) }, tick = true)))
                open.firstOrNull { isAnswer(b, it) }?.let { assertEquals(false, WhosWhoTraining.judge(p, at(it, tick = false))) }
                // Another true mark: right or play on by whether an instance makes it (the test below).
            }
        }
    }

    @Test fun `another instance of the same technique is right, and a true mark no instance justifies plays on`() {
        var others = 0
        var unjustified = 0
        for ((rung, t) in WhosWhoTraining.techniques.take(2).withIndex()) {
            for (p in WhosWhoTraining.practices(t).take(12)) {
                val b = p.board as WhosWhoState
                val table = Table(b)
                val instances = if (rung == 0) table.clues else table.onlys
                val after = (p.step as Deduction).apply(b) as WhosWhoState
                val mine = b.marks.indices.filter { after.marks[it] != b.marks[it] }.toSet()
                fun with(m: Marks): WhosWhoState {
                    var x = b
                    for (c in m.crosses) if (x.marks[c] == '.') x = x.mark(c / b.n / b.columns, c / b.n % b.columns, c % b.n, tick = false)
                    for (c in m.ticks) x = x.mark(c / b.n / b.columns, c / b.n % b.columns, c % b.n, tick = true)
                    return x
                }
                for (m in instances) {
                    if (m.ticks + m.crosses == mine) continue
                    others++
                    assertEquals("$t instance $m", true, WhosWhoTraining.judge(p, with(m)))
                }
                // A true cross that is no instance's: no verdict.
                val justified = instances.flatMap { it.ticks + it.crosses }.toSet() + mine
                b.marks.indices.firstOrNull { b.marks[it] == '.' && !isAnswer(b, it) && it !in justified && after.marks[it] == '.' }?.let { c ->
                    unjustified++
                    assertNull("true cross $c", WhosWhoTraining.judge(p, with(Marks(emptySet(), setOf(c)))))
                }
            }
        }
        assertTrue("some practice has another instance ($others)", others > 0)
        assertTrue("some practice has an unjustified true cross ($unjustified)", unjustified > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            WhosWhoTraining.techniquesUsed(WhosWho.generate(Training.seed(k), Difficulty.EXPERT)).containsAll(WhosWhoTraining.techniques)
        }
        assertEquals(Training.seed(index), WhosWhoTraining.examSeed)
        val exam = WhosWhoTraining.exam() as WhosWhoState
        assertEquals(listOf(exam.answer), WhosWhoOracle.answers(exam.n, exam.columns, exam.clues, limit = 2))
        assertTrue(WhosWhoTraining.techniquesUsed(exam).containsAll(WhosWhoTraining.techniques))
    }
}
