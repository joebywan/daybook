package com.joebywan.daybook

import com.joebywan.daybook.core.CipherTraining
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Cipher
import com.joebywan.daybook.puzzles.CipherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cipher's course (docs/TRAINING_SPEC.md). Each practice is checked against [CipherOracle]'s count of answers, and the
 * ladder's lower rungs are re-derived here by brute force over one column, or two neighbouring columns, at a time: every
 * digit for the open letters in them, read straight off the words (nothing borrowed from the teacher's sight or carries).
 */
class CipherTrainingTest {

    /** The sum, read afresh from the words: letters in alphabetical order, column 0 the units. */
    private class Sum(val words: List<String>, val digits: List<Int>) {
        val letters = words.joinToString("").toSortedSet().toList()
        val width = words.last().length
        val firsts = words.map { letters.indexOf(it[0]) }.toSet()
        fun at(w: String, c: Int) = if (c < w.length) letters.indexOf(w[w.length - 1 - c]) else -1
        val addends = List(width) { c -> words.dropLast(1).map { at(it, c) }.filter { it >= 0 } }
        val totals = List(width) { c -> at(words.last(), c) }
        val open = letters.indices.filter { digits[it] < 0 }
        val unused = (0..9).filter { it !in digits }

        val leading = width > words.dropLast(1).maxOf { it.length } && digits[totals[width - 1]] < 0

        /** Ten letters, and some unused digit only one open letter may take (0 never starts a word). */
        val onlyLetter = letters.size == 10 && unused.any { d -> open.count { d != 0 || it !in firsts } == 1 }

        /** The carry into column [c] when every letter right of it is placed (0 into the units), else null. */
        fun knownCarry(c: Int): Int? {
            var carry = 0
            for (k in 0 until c) {
                if ((addends[k] + totals[k]).any { digits[it] < 0 }) return null
                carry = (addends[k].sumOf { digits[it] } + carry) / 10
            }
            return carry
        }

        /**
         * Columns [from]..[to], each on its own, cut down over and over (a letter alone on a digit takes it from the
         * others; with ten letters, a digit one letter alone can take is its) until nothing changes: whether some open
         * letter in them is left one digit. One column is a column step; two neighbours, through their carry, a pair.
         */
        fun forced(from: Int, to: Int): Boolean {
            val cand = letters.indices.map { l ->
                if (digits[l] >= 0) mutableSetOf(digits[l]) else unused.filter { it != 0 || l !in firsts }.toMutableSet()
            }
            // carries[k]: what may come into column k (nothing into the units, nothing out of the top).
            val carries = List(width + 1) { k -> if (k == 0 || k == width) mutableSetOf(0) else mutableSetOf(0, 1) }
            knownCarry(from)?.let { carries[from].retainAll(setOf(it)) }
            val mine = (from..to).flatMap { addends[it] + totals[it] }.distinct()
            var changed = true
            while (changed) {
                changed = false
                for (c in from..to) {
                    val vars = (addends[c] + totals[c]).distinct()
                    val ok = vars.map { HashSet<Int>() }
                    val okIn = HashSet<Int>()
                    val okOut = HashSet<Int>()
                    val v = IntArray(letters.size)
                    fun go(i: Int, used: Set<Int>) {
                        if (i == vars.size) {
                            for (cin in carries[c]) {
                                val sum = addends[c].sumOf { v[it] } + cin
                                if (sum % 10 == v[totals[c]] && sum / 10 in carries[c + 1]) {
                                    vars.indices.forEach { ok[it] += v[vars[it]] }; okIn += cin; okOut += sum / 10
                                }
                            }
                            return
                        }
                        for (x in cand[vars[i]]) if (x !in used) { v[vars[i]] = x; go(i + 1, used + x) }
                    }
                    go(0, emptySet())
                    for ((i, l) in vars.withIndex()) if (cand[l].retainAll(ok[i])) changed = true
                    if (carries[c].retainAll(okIn)) changed = true
                    if (carries[c + 1].retainAll(okOut)) changed = true
                }
                for (l in letters.indices) if (cand[l].size == 1) for (o in letters.indices) {
                    if (o != l && cand[o].removeAll(cand[l])) changed = true
                }
                if (letters.size == 10) for (d in 0..9) {
                    val holders = letters.indices.filter { d in cand[it] }
                    if (holders.size == 1 && cand[holders[0]].size > 1) { cand[holders[0]].retainAll(setOf(d)); changed = true }
                }
                if (cand.any { it.isEmpty() }) return false
            }
            return mine.any { it in open && cand[it].size == 1 }
        }

        val column get() = (0 until width).any { forced(it, it) }
        val pair get() = (0 until width - 1).any { forced(it, it + 1) }

        /** By the teacher's whole ladder's order: leading carry, column, only letter, column pair. */
        fun has(rung: Int) = when (rung) {
            0 -> leading
            1 -> column
            2 -> onlyLetter
            3 -> pair
            else -> false
        }
    }

    /** Each technique's place on the teacher's full ladder, the only letter included. */
    private val ladder = techniqueNamesOf("cipher").keys.toList()

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertTrue(CipherTraining.drill is MoveDrill)
        assertEquals(ladder - "only-letter", CipherTraining.techniques)
        for (t in CipherTraining.techniques) {
            val rung = ladder.indexOf(t)
            val list = CipherTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as CipherState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Cipher.teach(b)!!.technique)
                assertEquals(3, b.words.size)
                assertEquals("one answer", listOf(b.solution), CipherOracle.answers(b.words))
                assertTrue("placed digits are the answer's", b.digits.indices.all { b.digits[it] < 0 || b.digits[it] == b.solution[it] })
                val sum = Sum(b.words, b.digits)
                for (lower in 0 until rung) assertFalse("$t board has a rung-$lower step", sum.has(lower))
                if (rung < 4) assertTrue("$t board has its own step by counting", sum.has(rung))
                // The step gives one open letter the answer's digit.
                val after = (p.step as Deduction).apply(b) as CipherState
                val changed = b.digits.indices.filter { after.digits[it] != b.digits[it] }
                assertEquals(1, changed.size)
                assertEquals(b.solution[changed[0]], after.digits[changed[0]])
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when no answer keeps it, and otherwise plays on`() {
        for (t in CipherTraining.techniques) {
            for (p in CipherTraining.practices(t).take(3)) {
                val b = p.board as CipherState
                val d = p.step as Deduction
                assertEquals(true, CipherTraining.judge(p, d.apply(b)))
                val step = b.digits.indices.first { (d.apply(b) as CipherState).digits[it] != b.digits[it] }
                // Selecting a letter is on the way: neither.
                assertNull(CipherTraining.judge(p, b.select(step)))
                // Any other digit for it is wrong: the one answer does not keep it.
                val wrong = (0..9).first { it !in b.digits && it != b.solution[step] }
                assertEquals(false, CipherTraining.judge(p, b.withDigit(step, wrong)))
                // The right digit for another open letter is play on: only the step passes.
                b.digits.indices.firstOrNull { it != step && b.digits[it] < 0 }?.let {
                    assertNull(CipherTraining.judge(p, b.withDigit(it, b.solution[it])))
                }
            }
        }
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            CipherTraining.techniquesUsed(Cipher.generate(Training.seed(k), Difficulty.EXPERT)).containsAll(CipherTraining.techniques)
        }
        assertEquals(Training.seed(index), CipherTraining.examSeed)
        assertNotNull(Cipher.generateVerified(CipherTraining.examSeed!!, Difficulty.EXPERT))
        val exam = CipherTraining.exam() as CipherState
        assertEquals(listOf(exam.solution), CipherOracle.answers(exam.words))
        assertTrue(CipherTraining.techniquesUsed(exam).containsAll(CipherTraining.techniques))
    }
}
