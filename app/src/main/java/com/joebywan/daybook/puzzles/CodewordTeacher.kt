package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.bit
import com.joebywan.daybook.core.has
import com.joebywan.daybook.core.listed
import com.joebywan.daybook.puzzles.CodewordLogic.EMPTY

/**
 * Codeword, reasoned from the shapes of words.
 *
 * As in [CipherTeacher], the split is the point: [deduce] and [findSteps] are handed the grid's numbers and the letters
 * on the board (givens and the player's), nothing else, so a step cannot lean on the stored answer. The answer is read
 * only in [teach]: to decide which letters are mistakes (sound, because a shipped board has one answer), and for
 * [FALLBACK], which says openly that it points at the answer. Every step ends in one letter placed.
 *
 * Candidates are derived afresh on every call ([Sight]): a placed number has its letter; any other number every letter
 * no number holds. The words that *fit* a run are the list's words of its length with the run's shape (equal numbers
 * equal letters, different numbers different letters) whose every letter its number can still take.
 *
 * Techniques, simplest first:
 * 1. [ONE_WORD] — only one word fits a run: its first open number takes its letter.
 * 2. [RUN_AGREES] — every word that fits a run has the same letter for one of its numbers.
 * 3. [CROSSING] — a number's letters, cut by every run it is in, leave one.
 * 4. [ALL_WORDS] — every run and every number worked through together to a fixpoint, a letter pinned to one number
 *    taken from the rest. It cannot name one run, so it says so; it is counted per tier in `docs/TEACHING.md`.
 *
 * There is no "only number left for a letter": a grid uses only part of the alphabet, so a letter no number can take
 * may simply not be in it. A what-if (try each word a run allows) was built and dropped: from the givens a board keeps,
 * step 4 always finished it. The generator keeps a board only when these finish it from its givens, and its tier needs a
 * given step at least once (`CodewordGridsBuildTest`), so a board made here never reaches [FALLBACK]. Every loop walks
 * runs, numbers, letters and words in index order, so a board gets the same hint on every platform.
 *
 * Highlight indices: squares `0 until size * size`, number n's key [CodewordLogic.key], letter l's [CodewordLogic.letterKey].
 */
internal object CodewordTeacher {

    const val ONE_WORD = "one-word"
    const val RUN_AGREES = "run-agrees"
    const val CROSSING = "crossing"
    const val ALL_WORDS = "all-words"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(ONE_WORD, RUN_AGREES, CROSSING, ALL_WORDS, FALLBACK)

    private const val ALL = (1 shl 26) - 1

    private fun single(m: Int) = m != 0 && m and (m - 1) == 0

    /** The board's runs, as squares and as number indices (number - 1), and which runs each number is in. */
    internal class Shape(val size: Int, val numbers: List<Int>, val k: Int) {
        val squares: List<IntArray> = CodewordLogic.runs(size, numbers)
        val runs: List<IntArray> = squares.map { run -> IntArray(run.size) { numbers[run[it]] - 1 } }
        val shapes: IntArray = IntArray(runs.size) { CodewordWords.shape(runs[it]) }
        val runsOf: List<List<Int>> = List(k) { n -> runs.indices.filter { n in runs[it] } }
        fun squaresOf(n: Int): Set<Int> = numbers.indices.filter { numbers[it] == n + 1 }.toSet()
    }

    /** Candidates for every number and the words still fitting every run, derived from the letters placed. */
    internal class Sight private constructor(val shape: Shape, val cand: IntArray, val words: Array<IntArray>) {
        var consistent = true

        constructor(shape: Shape, letters: List<Int>) : this(shape, IntArray(shape.k), Array(shape.runs.size) { IntArray(0) }) {
            var placed = 0
            for (l in letters) if (l != EMPTY) placed = placed or bit(l)
            for (n in 0 until shape.k) cand[n] = if (letters[n] != EMPTY) bit(letters[n]) else ALL and placed.inv()
            for (r in shape.runs.indices) {
                val list = CodewordWords.of(shape.runs[r].size)
                words[r] = list.letters.indices.filter { list.shapes[it] == shape.shapes[r] }.toIntArray()
                filter(r)
            }
        }

        fun copy(): Sight = Sight(shape, cand.copyOf(), words.copyOf()).also { it.consistent = consistent }

        /** Drops from run [r] every word some letter of which its number can no longer take. */
        private fun filter(r: Int) {
            val run = shape.runs[r]
            val list = CodewordWords.of(run.size)
            words[r] = words[r].filter { w -> val ls = list.letters[w]; run.indices.all { has(cand[run[it]], ls[it]) } }.toIntArray()
            if (words[r].isEmpty()) consistent = false
        }

        /** The letters number [n] takes in some word fitting run [r]. */
        fun support(r: Int, n: Int): Int {
            val run = shape.runs[r]
            val at = run.indexOf(n)
            val list = CodewordWords.of(run.size)
            var m = 0
            for (w in words[r]) m = m or bit(list.letters[w][at])
            return m
        }

        /** Run [r]: cuts its numbers to the letters its words allow. -1 when nothing fits, 1 when something was cut. */
        fun revise(r: Int): Int {
            filter(r)
            if (!consistent) return -1
            var changed = 0
            for (n in shape.runs[r].distinct()) {
                val next = cand[n] and support(r, n)
                if (next == 0) { consistent = false; return -1 }
                if (next != cand[n]) { cand[n] = next; changed = 1 }
            }
            return changed
        }

        /** A number down to one letter takes it from every other. */
        fun allDifferent(): Int {
            var changed = 0
            for (i in 0 until shape.k) {
                if (!single(cand[i])) continue
                for (j in 0 until shape.k) {
                    if (j == i || cand[j] and cand[i] == 0) continue
                    cand[j] = cand[j] and cand[i].inv()
                    if (cand[j] == 0) { consistent = false; return -1 }
                    changed = 1
                }
            }
            return changed
        }

        /** Every run and the all-different rule, over and over until nothing changes. */
        fun settle(): Boolean {
            if (!consistent) return false
            do {
                var changed = false
                for (r in shape.runs.indices) when (revise(r)) {
                    -1 -> return false
                    1 -> changed = true
                }
                when (allDifferent()) {
                    -1 -> return false
                    1 -> changed = true
                }
            } while (changed)
            return true
        }
    }

    /** A placement: number index [number] (0-based) takes [letter]. [run] is the run it was read from, or -1; [words] the words weighed. */
    internal class Hit(val technique: String, val number: Int, val letter: Int, val run: Int, val words: IntArray = IntArray(0))

    /** The first open number of run [r], in the run's order. */
    private fun firstOpen(shape: Shape, letters: List<Int>, r: Int): Int? = shape.runs[r].firstOrNull { letters[it] == EMPTY }

    /**
     * Every placement the first technique (up to [upTo], an index into [TECHNIQUES]) that finds any can make, in a
     * fixed order. [letters] has one letter per number index, [EMPTY] for open; the stored answer is not a parameter.
     */
    internal fun findSteps(shape: Shape, letters: List<Int>, upTo: Int = TECHNIQUES.indexOf(ALL_WORDS)): List<Hit> {
        val base = Sight(shape, letters)
        if (!base.consistent) return emptyList()
        val hits = mutableListOf<Hit>()
        val lettersOf = { r: Int -> CodewordWords.of(shape.runs[r].size).letters }
        // 1. A run only one word fits.
        for (r in shape.runs.indices) {
            if (base.words[r].size != 1) continue
            val n = firstOpen(shape, letters, r) ?: continue
            hits += Hit(ONE_WORD, n, lettersOf(r)[base.words[r][0]][shape.runs[r].indexOf(n)], r, base.words[r])
        }
        if (hits.isNotEmpty() || upTo < 1) return hits
        // 2. Every word that fits a run agrees on one of its numbers.
        for (r in shape.runs.indices) {
            if (base.words[r].size < 2) continue
            for (n in shape.runs[r].distinct()) {
                if (letters[n] != EMPTY) continue
                val m = base.support(r, n)
                if (single(m)) hits += Hit(RUN_AGREES, n, m.countTrailingZeroBits(), r, base.words[r])
            }
        }
        if (hits.isNotEmpty() || upTo < 2) return hits
        // 3. A number's letters, cut by every run it is in.
        for (n in 0 until shape.k) {
            if (letters[n] != EMPTY) continue
            var m = base.cand[n]
            for (r in shape.runsOf[n]) m = m and base.support(r, n)
            if (single(m)) hits += Hit(CROSSING, n, m.countTrailingZeroBits(), -1)
        }
        if (hits.isNotEmpty() || upTo < 3) return hits
        // 4. Everything worked through together.
        val all = base.copy()
        if (!all.settle()) return emptyList()
        for (n in 0 until shape.k) {
            if (letters[n] == EMPTY && single(all.cand[n])) hits += Hit(ALL_WORDS, n, all.cand[n].countTrailingZeroBits(), -1)
        }
        return hits
    }

    /** The ladder's steps from [letters] to the answer, or null when it gets stuck or ends somewhere wrong. */
    fun walk(size: Int, numbers: List<Int>, letters: List<Int>, upTo: Int = TECHNIQUES.indexOf(ALL_WORDS)): List<Hit>? {
        val shape = Shape(size, numbers, letters.size)
        val now = letters.toMutableList()
        val steps = mutableListOf<Hit>()
        while (now.any { it == EMPTY }) {
            val hit = findSteps(shape, now, upTo).firstOrNull() ?: return null
            now[hit.number] = hit.letter
            steps += hit
        }
        return if (CodewordLogic.isSolved(size, numbers, now)) steps else null
    }

    /** Whether the ladder alone finishes the board from [letters]: what the generator asks of every board. */
    fun solvesByLogic(size: Int, numbers: List<Int>, letters: List<Int>): Boolean = walk(size, numbers, letters) != null

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ------------------------

    const val MAX_EXPLANATION = 200
    const val MAX_NUDGE = 70

    /** One step: the letter for number index [number] ([EMPTY] to take a mistake back), where to look and why. */
    class Step(
        val technique: String,
        val number: Int,
        val letter: Int,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    fun teach(s: CodewordState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.numbers, s.letters) ?: fallback(s)
    }

    private fun up(l: Int) = ('A' + l).toString()

    /** Run [r] as the player reads it: placed letters as letters, open squares as their numbers. */
    private fun read(shape: Shape, letters: List<Int>, r: Int): String =
        shape.runs[r].joinToString(" ") { n -> if (letters[n] != EMPTY) up(letters[n]) else "${n + 1}" }

    private fun word(shape: Shape, r: Int, w: Int) = CodewordWords.of(shape.runs[r].size).words[w].uppercase()

    private fun letterList(m: Int): String = listed((0 until 26).filter { has(m, it) }.map { up(it) }, and = "or")

    private fun fallback(s: CodewordState): Step? {
        val n = s.letters.indices.firstOrNull { s.letters[it] == EMPTY } ?: return null
        val l = s.solution[n]
        val shape = Shape(s.size, s.numbers, s.letters.size)
        return Step(FALLBACK, n, l, shape.squaresOf(n), emptySet(), "This one takes a longer chain. Look at ${n + 1}.", "This one needs a longer chain than a hint can walk through, so here's the answer for ${n + 1}: it's ${up(l)}.")
    }

    private fun mistake(s: CodewordState): Step? {
        val wrong = s.letters.indices.filter { it + 1 !in s.given && s.letters[it] != EMPTY && s.letters[it] != s.solution[it] }
        if (wrong.isEmpty()) return null
        val shape = Shape(s.size, s.numbers, s.letters.size)
        val conflicts = CodewordLogic.conflicts(s.size, s.numbers, s.letters)
        val deadRuns = conflicts.runs.sorted()
        val n = wrong.firstOrNull { it + 1 in conflicts.numbers }
            ?: wrong.firstOrNull { w -> deadRuns.any { w in shape.runs[it] } }
            ?: wrong.first()
        val l = s.letters[n]
        val x = n + 1
        val clear = "Select $x and tap ${up(l)} again to clear it."
        fun step(why: String, cited: Set<Int>) = Step(MISTAKE, n, EMPTY, shape.squaresOf(n), cited, "Check $x = ${up(l)}.", "$why $clear")
        val twin = s.letters.indices.firstOrNull { it != n && s.letters[it] == l }
        if (twin != null) return step("$x and ${twin + 1} can't both be ${up(l)}: each number has its own letter.", shape.squaresOf(twin))
        val dead = deadRuns.firstOrNull { n in shape.runs[it] }
        if (dead != null) return step("With $x as ${up(l)}, no word in the list fits ${read(shape, s.letters, dead)}.", shape.squares[dead].toSet())
        return step("No way to finish the grid keeps $x as ${up(l)}.", emptySet())
    }

    /** The step to hint: the first placement of the first technique that finds one. */
    fun deduce(size: Int, numbers: List<Int>, letters: List<Int>): Step? {
        val shape = Shape(size, numbers, letters.size)
        val hit = findSteps(shape, letters).firstOrNull() ?: return null
        return step(shape, letters, hit)
    }

    private fun step(shape: Shape, letters: List<Int>, hit: Hit): Step {
        val n = hit.number
        val x = n + 1
        val l = up(hit.letter)
        val r = hit.run
        val mine = shape.squaresOf(n)
        val runSquares = if (r >= 0) shape.squares[r].toSet() else emptySet()
        fun fit(text: String) = if (text.length <= MAX_EXPLANATION) text else null
        return when (hit.technique) {
            ONE_WORD -> Step(
                ONE_WORD, n, hit.letter, runSquares, runSquares, "One word fits the glowing word.",
                "Only ${word(shape, r, hit.words[0])} fits ${read(shape, letters, r)}, so $x is $l.",
            )
            RUN_AGREES -> {
                val ws = hit.words.map { word(shape, r, it) }
                val text = fit("${listed(ws)} all fit ${read(shape, letters, r)}, and every one has $l for $x.")
                    ?: "${ws.size} words fit ${read(shape, letters, r)} (${ws.take(3).joinToString(", ")}, ...), and every one has $l for $x."
                Step(RUN_AGREES, n, hit.letter, runSquares, runSquares, "Look at the words that fit the glowing word.", text)
            }
            CROSSING -> {
                val sight = Sight(shape, letters)
                val parts = shape.runsOf[n].map { "${read(shape, letters, it)} (${letterList(sight.support(it, n))})" }
                val cited = shape.runsOf[n].flatMap { shape.squares[it].toList() }.toSet()
                val text = fit("$x is in ${listed(parts)}. Only $l fits them all, so $x is $l.")
                    ?: "$x is in ${parts.size} words, and each allows only some letters for it. Only $l fits them all, so $x is $l."
                Step(CROSSING, n, hit.letter, mine, cited, "Look at every word $x is in.", text)
            }
            else -> Step(
                ALL_WORDS, n, hit.letter, mine, shape.runsOf[n].flatMap { shape.squares[it].toList() }.toSet(), "Work the words through together.",
                "Work every word through together: each rules out letters for its numbers, and a letter pinned to one number " +
                    "is taken from the rest. That leaves $x only $l.",
            )
        }
    }
}
