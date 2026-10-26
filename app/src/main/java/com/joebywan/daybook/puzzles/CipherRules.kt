package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng

/**
 * A sum of words laid out in right-aligned columns: [words] is the addends, then the total, lowercase.
 *
 * Letters are numbered in alphabetical order ([letters]); a digit assignment is a list indexed that way,
 * [CipherLogic.EMPTY] for a letter with no digit yet. Columns are numbered from the right: column 0 is the
 * units. A tile is (row, column) with row 0 the first addend and the last row the total; its highlight
 * index is [tile].
 */
private val PLACES = listOf("units", "tens", "hundreds", "thousands", "ten-thousands", "hundred-thousands")

internal class CipherSum(val words: List<String>) {
    val addends: List<String> = words.dropLast(1)
    val total: String = words.last()
    val letters: List<Char> = words.flatMap { it.toList() }.distinct().sorted()
    val width: Int = total.length
    val rows: Int = words.size

    /** The letter at [col] (from the right) of word [row], or -1 where the word is too short. */
    fun letterAt(row: Int, col: Int): Int {
        val w = words[row]
        return if (col < w.length) letters.indexOf(w[w.length - 1 - col]) else -1
    }

    /** Column [col]'s addend letters, top to bottom (a letter may repeat). */
    val columnAddends: List<List<Int>> = List(width) { c -> (0 until rows - 1).map { letterAt(it, c) }.filter { it >= 0 } }

    /** Column [col]'s total letter. */
    val columnTotal: List<Int> = List(width) { letterAt(rows - 1, it) }

    /** Column [col]'s letters, addends then total, each once. */
    val columnLetters: List<List<Int>> = List(width) { c -> (columnAddends[c] + columnTotal[c]).distinct() }

    /** A letter as the player sees it: "T". */
    fun name(l: Int): String = letters[l].uppercaseChar().toString()

    /** Column [c]'s place: "units", "tens" and so on. */
    fun place(c: Int): String = PLACES[c]

    fun columnName(c: Int): String = "${place(c)} column"

    /**
     * The carry into column [c] once every letter to its right is placed, else null. Nothing comes into the units
     * and nothing leaves the top.
     */
    fun knownCarry(digits: List<Int>, c: Int): Int? {
        if (c == width) return 0
        var carry = 0
        for (k in 0 until c) {
            if (columnLetters[k].any { digits[it] == CipherLogic.EMPTY }) return null
            carry = (columnAddends[k].sumOf { digits[it] } + carry) / 10
        }
        return carry
    }

    /** Letters that start a word, as a mask over letter indices: none of them may be 0. */
    val leading: Int = words.fold(0) { m, w -> m or (1 shl letters.indexOf(w[0])) }

    /** The highest carry a column can pass on: one less than the number of addends. */
    val maxCarry: Int = addends.size - 1

    /** Highlight index of the tile at word [row], column [col] from the right. */
    fun tile(row: Int, col: Int): Int = row * CipherLogic.TILE_ROW + col

    /** Every tile showing letter [letter]. */
    fun tilesOf(letter: Int): Set<Int> = buildSet {
        for (r in 0 until rows) for (c in 0 until words[r].length) if (letterAt(r, c) == letter) add(tile(r, c))
    }

    /** Every tile of column [col]. */
    fun columnTiles(col: Int): Set<Int> = (0 until rows).filter { col < words[it].length }.map { tile(it, col) }.toSet()

    /** Whether this is a sum the puzzle can pose: at most ten letters, and a total that can be the right length. */
    val wellFormed: Boolean
        get() = addends.size >= 2 && letters.size <= 10 && addends.all { it.length <= width } &&
            width <= addends.maxOf { it.length } + 1 && words.all { w -> w.isNotEmpty() && w.all { it in 'a'..'z' } }
}

/**
 * The rules of Cipher, its proof and its generator.
 *
 * Each letter stands for one digit, different letters for different digits, no word starts with 0, and the
 * sum is true. [isSolved] checks exactly that and never looks at a stored answer.
 *
 * Boards come from [CipherSums]: sums of real words (the addends *and* the total), found offline by
 * `CipherSumsBuildTest` because a sum of real words with one answer is rare (about one addend pair in
 * four has one that the ladder finishes, out of 1,768 totals tried, which is far too slow to search for on a phone). The
 * seed picks an entry; the device then proves it again before shipping it: the teacher's ladder must
 * finish it ([CipherTeacher.solvesByLogic]) and an exhaustive search ([prove]) must find exactly one
 * answer, which becomes the stored solution. `CipherRulesTest` checks both against a third, naive solver
 * that tries every digit for every letter.
 */
internal object CipherLogic {

    const val EMPTY = -1

    /** Tile highlight indices are `row * TILE_ROW + column`; letter keys are [key], digit keys [pad]. */
    const val TILE_ROW = 10

    fun key(letter: Int): Int = 100 + letter

    fun pad(digit: Int): Int = 1000 + digit

    fun value(word: String, sum: CipherSum, digits: List<Int>): Long =
        word.fold(0L) { v, ch -> v * 10 + digits[sum.letters.indexOf(ch)] }

    /** The rules and nothing else: every letter a digit, all different, no leading zero, and the sum true. */
    fun isSolved(sum: CipherSum, digits: List<Int>): Boolean {
        if (digits.size != sum.letters.size || digits.any { it !in 0..9 }) return false
        if (digits.distinct().size != digits.size) return false
        if (digits.indices.any { sum.leading and (1 shl it) != 0 && digits[it] == 0 }) return false
        return sum.addends.sumOf { value(it, sum, digits) } == value(sum.total, sum, digits)
    }

    /** What breaks a rule so far: letters sharing a digit or starting a word with 0, and the first column that is wrong. */
    class Conflicts(val letters: Set<Int>, val column: Int?)

    fun conflicts(sum: CipherSum, digits: List<Int>): Conflicts {
        val bad = mutableSetOf<Int>()
        for (i in digits.indices) {
            if (digits[i] == EMPTY) continue
            if (digits[i] == 0 && sum.leading and (1 shl i) != 0) bad += i
            for (j in digits.indices) if (j != i && digits[j] == digits[i]) bad += i
        }
        // A column is wrong only once it and everything to its right is filled in: then its carry is known.
        var carry = 0
        var column: Int? = null
        for (c in 0 until sum.width) {
            val letters = sum.columnAddends[c] + sum.columnTotal[c]
            if (letters.any { digits[it] == EMPTY }) break
            val s = sum.columnAddends[c].sumOf { digits[it] } + carry
            if (s % 10 != digits[sum.columnTotal[c]] || (c == sum.width - 1 && s >= 10)) {
                column = c
                break
            }
            carry = s / 10
        }
        return Conflicts(bad, column)
    }

    // ---- proof -------------------------------------------------------------------------------------

    /** What an exhaustive search says. Only [ExactlyOne] carries an answer, so "gave up" cannot pass for one. */
    sealed interface Verdict {
        data object None : Verdict
        class ExactlyOne(val digits: List<Int>) : Verdict
        data object Several : Verdict
    }

    /**
     * Every answer of [sum], counted to two. Column by column from the units, a digit for each new letter in
     * the column, the total's letter then forced by the column's sum. No node budget, so it always ends with
     * the true count.
     */
    fun prove(sum: CipherSum): Verdict {
        if (!sum.wellFormed) return Verdict.None
        val d = IntArray(sum.letters.size) { EMPTY }
        var used = 0
        var found = 0
        var first: List<Int>? = null

        fun column(c: Int, carry: Int) {
            if (found >= 2) return
            if (c == sum.width) {
                if (carry == 0) {
                    found++
                    if (first == null) first = d.toList()
                }
                return
            }
            val adds = sum.columnAddends[c]
            val t = sum.columnTotal[c]

            fun assign(k: Int) {
                if (found >= 2) return
                if (k == adds.size) {
                    val s = adds.sumOf { d[it] } + carry
                    val digit = s % 10
                    if (d[t] != EMPTY) {
                        if (d[t] == digit) column(c + 1, s / 10)
                    } else if (used and (1 shl digit) == 0 && !(digit == 0 && sum.leading and (1 shl t) != 0)) {
                        d[t] = digit
                        used = used or (1 shl digit)
                        column(c + 1, s / 10)
                        used = used and (1 shl digit).inv()
                        d[t] = EMPTY
                    }
                    return
                }
                val l = adds[k]
                if (d[l] != EMPTY) {
                    assign(k + 1)
                    return
                }
                for (digit in 0..9) {
                    if (used and (1 shl digit) != 0 || (digit == 0 && sum.leading and (1 shl l) != 0)) continue
                    d[l] = digit
                    used = used or (1 shl digit)
                    assign(k + 1)
                    used = used and (1 shl digit).inv()
                    d[l] = EMPTY
                }
            }
            assign(0)
        }
        column(0, 0)
        return when (found) {
            0 -> Verdict.None
            1 -> Verdict.ExactlyOne(first!!)
            else -> Verdict.Several
        }
    }

    // ---- generator ---------------------------------------------------------------------------------

    /** Each attempt draws another entry with `Rng(seed + attempt)`; every entry is proved in tests, so one is enough. */
    const val ATTEMPTS = 8

    fun sums(difficulty: Difficulty): List<String> = when (difficulty) {
        Difficulty.STANDARD -> CipherSums.STANDARD
        Difficulty.HARD -> CipherSums.HARD
        Difficulty.EXPERT -> CipherSums.EXPERT
    }

    /** "abc+de=fgh" into its words. */
    fun parse(entry: String): CipherSum {
        val (left, total) = entry.split("=")
        return CipherSum(left.split("+") + total)
    }

    /** The board for [entry] when the ladder finishes it and the search proves one answer, else null. */
    fun board(entry: String): CipherState? {
        val sum = parse(entry)
        if (!sum.wellFormed || !CipherTeacher.solvesByLogic(sum)) return null
        val verdict = prove(sum) as? Verdict.ExactlyOne ?: return null
        return CipherState(words = sum.words, solution = verdict.digits, digits = List(sum.letters.size) { EMPTY })
    }

    /** Null when nothing was proved. Pure in (seed, difficulty): only the list's own order reaches the `Rng`. */
    fun generateVerified(seed: Long, difficulty: Difficulty): CipherState? {
        val list = sums(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            board(list[Rng(seed + attempt).nextInt(list.size)])?.let { return it }
        }
        return null
    }

    /** A fixed board per tier for a seed nothing proved (none ever has: `FallbackTest`); `CipherRulesTest` proves each. */
    val LAST_RESORT = mapOf(
        Difficulty.STANDARD to "abet+pale=allow",
        Difficulty.HARD to "abet+beat=alibi",
        Difficulty.EXPERT to "able+boil=amass",
    )

    fun lastResort(difficulty: Difficulty): CipherState = board(LAST_RESORT.getValue(difficulty))!!
}
