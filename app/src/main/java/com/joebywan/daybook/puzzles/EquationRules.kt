package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.DailyCycle
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.SeedHash
import kotlinx.serialization.Serializable

/**
 * Equation's board: a hidden sum such as `12+35=47`, the guesses so far, and the row being typed. Symbols are stored as
 * typed: digits, `+ - * / =` (shown as + − × ÷ =).
 *
 * Saved games hold the sums themselves, so a state written today loads whatever the lists do later. [opener] picks the
 * first-guess hint from [EquationTeacher.openers], drawn from the seed apart from the answer, as Lexicon's is.
 */
@Serializable
data class EquationState(
    override val length: Int,
    override val maxGuesses: Int,
    override val answer: String,
    override val guesses: List<String> = emptyList(),
    override val current: String = "",
    override val moves: Int = 0,
    val opener: Int = 0,
) : PuzzleState, GuessRows<EquationState> {

    /** Every symbol of the last guess marked right: a guess is always a true sum, so this is the hidden sum found. */
    override val solved: Boolean get() = guesses.lastOrNull() == answer
    override val failed: Boolean get() = !solved && guesses.size >= maxGuesses

    override fun withRows(guesses: List<String>, current: String): EquationState =
        copy(guesses = guesses, current = current, moves = moves + 1)
}

/**
 * Equation's rules, with no board and no screen.
 *
 * A row is a true sum: a left side of two or more whole numbers joined by `+ - * /`, one `=`, and the left side's value
 * written out on the right. Decided (2026-11-08, for this puzzle):
 * - **Precedence is the usual one**: `*` and `/` before `+` and `-`, each worked left to right, so `2+3*4=14`.
 * - **Every `/` comes out whole**, worked left to right within its term: `8/4*3=6` is true, `3/2*4=6` is not (3/2 is not
 *   whole), so no fraction is ever needed. Dividing by 0 is never true.
 * - **No negatives written**: no minus sign before a number, and the right side is a plain number (so the value is at
 *   least 0). A running total may dip below 0 on the way (`1-5+6=2` is true).
 * - **No leading zeros** (`07`); a lone `0` is a number, so `5-5=0` and `0+7=7` are true and may be guessed.
 * - **Answers never hold a lone 0** (any number that is just 0, on either side), so a hidden sum is never `0*9=0`: the
 *   guesses are wider than the answers, as Lexicon's are.
 *
 * The lists are not stored: [valid] enumerates every true sum of a length on the device (sorted, so the daily answer is
 * an index into a fixed order and a guess is a binary search; no hash container is near the seed), and [problem] is the
 * same rule read off one row, which `EquationRulesTest` holds equal to the enumeration with an evaluator of its own.
 */
object EquationLogic {

    /** Every symbol a row can hold, in key order: what the hint indices number. */
    const val ALPHABET = "0123456789+-*/="

    /** A tier's board: symbols in a row and guesses allowed. */
    class Shape(val length: Int, val guesses: Int)

    /**
     * Standard six symbols, Hard seven, Expert eight, six guesses each. Measured by `EquationBalanceTest` (the answer
     * lists, and two modest players): see `CLAUDE.md`, Equation.
     */
    fun shape(difficulty: Difficulty): Shape = when (difficulty) {
        Difficulty.STANDARD -> Shape(6, 6)
        Difficulty.HARD -> Shape(7, 6)
        Difficulty.EXPERT -> Shape(8, 6)
    }

    /** Lengths [valid] can enumerate. */
    val LENGTHS = 5..8

    private val validCache = arrayOfNulls<List<String>>(LENGTHS.last + 1)
    private val answerCache = arrayOfNulls<List<String>>(LENGTHS.last + 1)

    /** Every true sum of [length] symbols, sorted. Enumerated once per length and kept. */
    fun valid(length: Int): List<String> {
        require(length in LENGTHS) { "no $length-symbol sums" }
        return validCache[length] ?: enumerate(length).also { validCache[length] = it }
    }

    /** The sums a board's answer can be: [valid] less any with a lone 0. Sorted. */
    fun answers(length: Int): List<String> =
        answerCache[length] ?: valid(length).filter { !hasLoneZero(it) }.also { answerCache[length] = it }

    /** Whether [row] holds a number that is just 0 (on either side). */
    fun hasLoneZero(row: String): Boolean = row.indices.any { i ->
        row[i] == '0' && (i == 0 || !row[i - 1].isDigit()) && (i == row.lastIndex || !row[i + 1].isDigit())
    }

    /** Whether [row] is a true sum. */
    fun isTrue(row: String): Boolean = problem(row, row.length) == null

    private fun isOp(c: Char) = c == '+' || c == '-' || c == '*' || c == '/'

    /**
     * Why [row] cannot be submitted on a board of [length] symbols, or null if it can: short, not one `=`, a malformed
     * side, or not true. Short enough for the header line; nothing is spent.
     */
    fun problem(row: String, length: Int): String? {
        if (row.length < length) return "Not enough symbols"
        val eq = row.indexOf('=')
        if (eq < 0) return "A sum needs ="
        if (row.indexOf('=', eq + 1) >= 0) return "Only one = in a sum"
        val left = row.substring(0, eq)
        val right = row.substring(eq + 1)
        if (right.isEmpty() || right.any { !it.isDigit() }) return "Only a number after ="
        if (left.isEmpty() || isOp(left[0])) return "Start with a number"
        if (isOp(left.last())) return "A number goes before ="
        if (left.none(::isOp)) return "Use + − × or ÷ before ="
        for (i in 1 until left.length) if (isOp(left[i]) && isOp(left[i - 1])) return "A number goes between signs"
        if (leadingZero(left) || leadingZero(right)) return "No number starts with 0"
        // Worked as the rules say: terms of * and / left to right, each / whole, then the terms added and taken away.
        var total = 0L
        var term = 0L
        var sign = 1L
        var op = '+'
        var i = 0
        while (i < left.length) {
            var j = i
            while (j < left.length && left[j].isDigit()) j++
            val n = left.substring(i, j).toLong()
            when (op) {
                '+', '-' -> {
                    total += sign * term
                    term = n
                    sign = if (op == '-') -1L else 1L
                }
                '*' -> term *= n
                else -> {
                    if (n == 0L) return "Nothing divides by 0"
                    if (term % n != 0L) return "Each ÷ must come out whole"
                    term /= n
                }
            }
            if (j < left.length) op = left[j]
            i = j + 1
        }
        total += sign * term
        if (total < 0) return "The left side is below 0"
        if (total.toString() != right) return "Not true: the left makes $total"
        return null
    }

    private fun leadingZero(side: String): Boolean = side.indices.any { i ->
        side[i] == '0' && (i == 0 || !side[i - 1].isDigit()) && i < side.lastIndex && side[i + 1].isDigit()
    }

    // ---- the enumeration --------------------------------------------------------------------------

    private fun pow10(k: Int): Long {
        var p = 1L
        repeat(k) { p *= 10 }
        return p
    }

    /**
     * Every true sum of [length] symbols, sorted. For each right side of r digits (values [lo]..[hi]), left sides are
     * built number by number, keeping the running total, the term being multiplied out and its sign; the last number
     * is not tried one by one but solved for (a range for + − ×, the divisors of the term for ÷), so even eight symbols
     * walk only the left sides before their last number.
     */
    private fun enumerate(length: Int): List<String> {
        val out = ArrayList<String>()
        val buf = CharArray(length)
        for (r in 1..length - 4) {
            val lhs = length - 1 - r
            val lo = if (r == 1) 0L else pow10(r - 1)
            val hi = pow10(r) - 1

            fun emit(k: Int, b: Long, value: Long) {
                if (value < lo || value > hi) return
                writeNumber(buf, lhs - k, k, b)
                buf[lhs] = '='
                writeNumber(buf, lhs + 1, r, value)
                out += buf.concatToString()
            }

            fun last(pos: Int, total: Long, term: Long, sign: Long, op: Char) {
                val k = lhs - pos
                val bMin = if (k == 1) 0L else pow10(k - 1)
                val bMax = pow10(k) - 1
                val base = total + sign * term
                when (op) {
                    '+' -> for (b in maxOf(bMin, lo - base)..minOf(bMax, hi - base)) emit(k, b, base + b)
                    '-' -> for (b in maxOf(bMin, base - hi)..minOf(bMax, base - lo)) emit(k, b, base - b)
                    '*' -> {
                        val c = sign * term
                        if (c == 0L) {
                            if (total in lo..hi) for (b in bMin..bMax) emit(k, b, total)
                        } else {
                            val from = if (c > 0) ceilDiv(lo - total, c) else ceilDiv(hi - total, c)
                            val to = if (c > 0) (hi - total).floorDiv(c) else (lo - total).floorDiv(c)
                            for (b in maxOf(bMin, from)..minOf(bMax, to)) emit(k, b, total + c * b)
                        }
                    }
                    else -> {
                        if (term == 0L) {
                            if (total in lo..hi) for (b in maxOf(1L, bMin)..bMax) emit(k, b, total)
                        } else {
                            val found = ArrayList<Long>()
                            var d = 1L
                            while (d * d <= term) {
                                if (term % d == 0L) {
                                    found += d
                                    if (d != term / d) found += term / d
                                }
                                d++
                            }
                            for (b in found) if (b in bMin..bMax) emit(k, b, total + sign * (term / b))
                        }
                    }
                }
            }

            fun build(pos: Int, total: Long, term: Long, sign: Long, op: Char) {
                for (k in 1..lhs - pos) {
                    val end = pos + k
                    if (end == lhs) {
                        if (op != ' ') last(pos, total, term, sign, op)
                        continue
                    }
                    if (end == lhs - 1) continue
                    val from = if (k == 1) 0L else pow10(k - 1)
                    for (n in from until pow10(k)) {
                        var t = total
                        var m = term
                        var g = sign
                        when (op) {
                            ' ' -> m = n
                            '+', '-' -> {
                                t += sign * term
                                m = n
                                g = if (op == '-') -1L else 1L
                            }
                            '*' -> m = term * n
                            else -> {
                                if (n == 0L || term % n != 0L) continue
                                m = term / n
                            }
                        }
                        writeNumber(buf, pos, k, n)
                        for (next in OPS) {
                            buf[end] = next
                            build(end + 1, t, m, g, next)
                        }
                    }
                }
            }
            build(0, 0L, 0L, 1L, ' ')
        }
        return out.sorted()
    }

    private const val OPS = "+-*/"

    private fun ceilDiv(a: Long, b: Long): Long = -((-a).floorDiv(b))

    /** [value] in [k] digits at [at]. */
    private fun writeNumber(buf: CharArray, at: Int, k: Int, value: Long) {
        var v = value
        for (i in at + k - 1 downTo at) {
            buf[i] = '0' + (v % 10).toInt()
            v /= 10
        }
    }

    // ---- boards -----------------------------------------------------------------------------------

    /**
     * The board for [seed]: the answer is one index into the tier's [answers]. A daily seed carries its day
     * ([SeedHash.cycleDay]) and walks [cycle]; any other seed (Random, Levels) draws the index.
     */
    fun newBoard(seed: Long, difficulty: Difficulty): EquationState {
        val shape = shape(difficulty)
        val answers = answers(shape.length)
        val day = SeedHash.cycleDay(seed, Equation.id)
        val index = if (day == null) Rng(seed).nextInt(answers.size) else cycle(day, difficulty)
        val opener = Rng(seed xor OPENER_SALT).nextInt(EquationTeacher.openers(shape.length).size)
        return EquationState(shape.length, shape.guesses, answers[index], opener = opener)
    }

    private const val OPENER_SALT = 0x4571704F70656EL // "EqpOpen"

    /** The answer index of cycle day [day] on [difficulty]: Lexicon's cycle ([DailyCycle]) over the tier's answers. */
    fun cycle(day: Long, difficulty: Difficulty): Int =
        DailyCycle.pick(day, answers(shape(difficulty).length).size, CYCLE_SALT, difficulty)

    private const val CYCLE_SALT = 0x4571437963L // "EqCyc"
}
