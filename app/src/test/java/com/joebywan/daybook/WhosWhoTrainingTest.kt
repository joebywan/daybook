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

        /** A clue naming a person settles a square. */
        val clue: Boolean get() = s.clues.any { k ->
            when (k.kind) {
                0, 1 -> {
                    val (name, other) = when {
                        isName(k.a) -> k.a to k.b
                        isName(k.b) -> k.b to k.a
                        else -> return@any false
                    }
                    val who = name % n
                    if (k.kind == 0) can(who, other) && !has(who, other) else can(who, other)
                }
                4 -> when {
                    isName(k.a) -> category(k.b).any { it != k.b && it != k.c && can(k.a % n, it) }
                    isName(k.b) -> (0 until n).any { it != k.b % n && it != k.c % n && can(it, k.a) }
                    else -> false
                }
                else -> false
            }
        }

        /** A cell with one slot open and no tick, or a column's slot only one person can still take, untaken. */
        val only: Boolean get() =
            (0 until n).any { p -> (0 until cols).any { c -> placed[p][c] < 0 && bits(cand[p][c]).size == 1 } } ||
                (0 until cols).any { c -> (0 until n).any { slot ->
                    val who = (0 until n).filter { cand[it][c] shr slot and 1 == 1 }
                    who.size == 1 && placed[who[0]][c] != slot
                } }

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
                // A true cross the step does not make is play on.
                val after = d.apply(b) as WhosWhoState
                open.firstOrNull { !isAnswer(b, it) && after.marks[it] == '.' }?.let { assertNull(WhosWhoTraining.judge(p, at(it, tick = false))) }
            }
        }
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
