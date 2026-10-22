package com.joebywan.daybook.puzzles

import com.joebywan.daybook.puzzles.CipherLogic.EMPTY

/**
 * Cipher, reasoned a column at a time.
 *
 * As in [InequalityTeacher], the split is the point: [deduce] and [findStep] are handed the sum and the digits
 * the player has placed and nothing else, so a step cannot lean on the stored answer. The answer is read only
 * in [teach]: to decide which digits are mistakes (sound, because a shipped board has one answer), and for
 * [FALLBACK], which says openly that it points at the answer. Every step ends in one digit placed.
 *
 * Candidates are derived afresh on every call ([Sight]): a placed letter has its digit; any other letter
 * every digit no other letter holds, never 0 if it starts a word. Carries are candidates too: into the units
 * nothing, out of the top nothing, between columns 0 or 1 (with two addends).
 *
 * Techniques, simplest first:
 * 1. [LEADING] — the total is longer than the addends, so its first letter is the last carry: 1.
 * 2. [COLUMN] — one column on its own (with the carry into it once everything to its right is placed) leaves
 *    a letter one digit.
 * 3. [ONLY_LETTER] — ten letters use every digit, and only one letter can still take a digit (0 when every
 *    other open letter starts a word).
 * 4. [PAIR] — two neighbouring columns, each cutting what the other allows through the carry between them.
 * 5. [CHAIN] — every column and carry worked through together, letters taking digits from each other as they
 *    settle. It cannot name one column, so it says so; it is counted per tier in `docs/TEACHING.md`.
 * 6. [WHAT_IF] — on top of that, every choice for two neighbouring columns tried at once: a what-if confined
 *    to two columns, never a trial of the whole sum.
 *
 * The generator keeps a sum only when these finish it ([solvesByLogic]), and its tier is the hardest one it
 * needs (`CipherSumsBuildTest.tierOf`), so a board made here never reaches [FALLBACK]. Every loop walks
 * columns, letters and digits in index order, so a board gets the same hint on every platform.
 *
 * Highlight indices: a tile is `row * 10 + column` (row 0 the first addend, the total last; column 0 the
 * units), a letter's key [CipherLogic.key], a digit key [CipherLogic.pad].
 */
internal object CipherTeacher {

    const val LEADING = "leading-carry"
    const val COLUMN = "column"
    const val ONLY_LETTER = "only-letter"
    const val PAIR = "column-pair"
    const val CHAIN = "columns-together"
    const val WHAT_IF = "pair-what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(LEADING, COLUMN, ONLY_LETTER, PAIR, CHAIN, WHAT_IF, FALLBACK)

    private const val ALL = (1 shl 10) - 1


    private fun single(m: Int) = m != 0 && m and (m - 1) == 0

    /**
     * Candidates for every letter and every carry, derived from the digits placed. `carry[c]` is the carry
     * into column c, as a mask over 0..maxCarry; nothing comes into the units and nothing leaves the top.
     */
    internal class Sight(val sum: CipherSum, val digits: List<Int>) {
        val n = sum.letters.size
        val cand = IntArray(n)
        val carry = IntArray(sum.width + 1)
        var consistent = true

        init {
            var placed = 0
            for (d in digits) if (d != EMPTY) placed = placed or (1 shl d)
            for (i in 0 until n) {
                cand[i] = if (digits[i] != EMPTY) 1 shl digits[i] else ALL and placed.inv()
                if (sum.leading and (1 shl i) != 0) cand[i] = cand[i] and 1.inv()
                if (cand[i] == 0) consistent = false
            }
            for (c in carry.indices) carry[c] = if (c == 0 || c == sum.width) 1 else (1 shl (sum.maxCarry + 1)) - 1
        }

        fun copy(): Sight = Sight(sum, digits).also { s ->
            cand.copyInto(s.cand)
            carry.copyInto(s.carry)
            s.consistent = consistent
        }

        /**
         * Column [c] on its own: keeps only the digits and carries some choice for the whole column supports
         * (different letters different digits within it). -1 when nothing does, 1 when something was cut.
         */
        fun revise(c: Int): Int {
            val adds = sum.columnAddends[c]
            val t = sum.columnTotal[c]
            val vars = (adds + t).distinct()
            val sup = IntArray(vars.size)
            var supIn = 0
            var supOut = 0
            val value = IntArray(n)
            fun go(k: Int, used: Int) {
                if (k == vars.size) {
                    val s0 = adds.sumOf { value[it] }
                    for (cin in 0..sum.maxCarry) {
                        if (carry[c] and (1 shl cin) == 0) continue
                        val s = s0 + cin
                        if (s % 10 != value[t] || carry[c + 1] and (1 shl (s / 10)) == 0) continue
                        for (i in vars.indices) sup[i] = sup[i] or (1 shl value[vars[i]])
                        supIn = supIn or (1 shl cin)
                        supOut = supOut or (1 shl (s / 10))
                    }
                    return
                }
                val l = vars[k]
                var m = cand[l] and used.inv()
                while (m != 0) {
                    val d = m.countTrailingZeroBits()
                    m = m and (m - 1)
                    value[l] = d
                    go(k + 1, used or (1 shl d))
                }
            }
            go(0, 0)
            var changed = 0
            for (i in vars.indices) {
                val next = cand[vars[i]] and sup[i]
                if (next == 0) return -1
                if (next != cand[vars[i]]) { cand[vars[i]] = next; changed = 1 }
            }
            if (supIn == 0) return -1
            if (supIn != carry[c]) { carry[c] = supIn; changed = 1 }
            if (supOut != carry[c + 1]) { carry[c + 1] = supOut; changed = 1 }
            return changed
        }

        /** Columns [c] and [c] + 1 as one: every choice for both, through the carry between them. */
        fun reviseJoint(c: Int): Int {
            val addsA = sum.columnAddends[c]
            val addsB = sum.columnAddends[c + 1]
            val tA = sum.columnTotal[c]
            val tB = sum.columnTotal[c + 1]
            val vars = (addsA + tA + addsB + tB).distinct()
            val sup = IntArray(vars.size)
            var supIn = 0
            var supMid = 0
            var supOut = 0
            val value = IntArray(n)
            fun go(k: Int, used: Int) {
                if (k == vars.size) {
                    val a0 = addsA.sumOf { value[it] }
                    val b0 = addsB.sumOf { value[it] }
                    for (cin in 0..sum.maxCarry) {
                        if (carry[c] and (1 shl cin) == 0) continue
                        val s = a0 + cin
                        if (s % 10 != value[tA] || carry[c + 1] and (1 shl (s / 10)) == 0) continue
                        val s2 = b0 + s / 10
                        if (s2 % 10 != value[tB] || carry[c + 2] and (1 shl (s2 / 10)) == 0) continue
                        for (i in vars.indices) sup[i] = sup[i] or (1 shl value[vars[i]])
                        supIn = supIn or (1 shl cin)
                        supMid = supMid or (1 shl (s / 10))
                        supOut = supOut or (1 shl (s2 / 10))
                    }
                    return
                }
                val l = vars[k]
                var m = cand[l] and used.inv()
                while (m != 0) {
                    val d = m.countTrailingZeroBits()
                    m = m and (m - 1)
                    value[l] = d
                    go(k + 1, used or (1 shl d))
                }
            }
            go(0, 0)
            var changed = 0
            for (i in vars.indices) {
                val next = cand[vars[i]] and sup[i]
                if (next == 0) return -1
                if (next != cand[vars[i]]) { cand[vars[i]] = next; changed = 1 }
            }
            if (supIn == 0) return -1
            if (supIn != carry[c]) { carry[c] = supIn; changed = 1 }
            if (supMid != carry[c + 1]) { carry[c + 1] = supMid; changed = 1 }
            if (supOut != carry[c + 2]) { carry[c + 2] = supOut; changed = 1 }
            return changed
        }

        /** A letter down to one digit takes it from every other; with ten letters, a digit only one letter can take is that letter's. */
        fun allDifferent(): Int {
            var changed = 0
            for (i in 0 until n) {
                if (!single(cand[i])) continue
                for (j in 0 until n) {
                    if (j == i || cand[j] and cand[i] == 0) continue
                    cand[j] = cand[j] and cand[i].inv()
                    if (cand[j] == 0) return -1
                    changed = 1
                }
            }
            if (n == 10) {
                for (d in 0..9) {
                    val holders = (0 until n).filter { cand[it] and (1 shl d) != 0 }
                    if (holders.isEmpty()) return -1
                    if (holders.size == 1 && !single(cand[holders[0]])) { cand[holders[0]] = 1 shl d; changed = 1 }
                }
            }
            return changed
        }

        /** Columns [from] to [to] and the all-different rule, over and over until nothing changes. */
        fun settle(from: Int = 0, to: Int = sum.width - 1): Boolean {
            if (!consistent) return false
            do {
                var changed = false
                for (c in from..to) {
                    when (revise(c)) {
                        -1 -> { consistent = false; return false }
                        1 -> changed = true
                    }
                }
                when (allDifferent()) {
                    -1 -> { consistent = false; return false }
                    1 -> changed = true
                }
            } while (changed)
            return true
        }
    }

    /** The carry into column [c] when every letter to its right is placed, else null. */
    private fun knownCarry(sum: CipherSum, digits: List<Int>, c: Int): Int? {
        var carry = 0
        for (k in 0 until c) {
            if (sum.columnAddends[k].any { digits[it] == EMPTY } || digits[sum.columnTotal[k]] == EMPTY) return null
            carry = (sum.columnAddends[k].sumOf { digits[it] } + carry) / 10
        }
        return carry
    }

    internal class Hit(val technique: String, val letter: Int, val digit: Int, val column: Int)

    internal fun findStep(sum: CipherSum, digits: List<Int>): Hit? {
        val base = Sight(sum, digits)
        if (!base.consistent) return null
        val longest = sum.addends.maxOf { it.length }
        // 1. The total is longer than every addend: its first letter is what the column before it carries.
        if (sum.width > longest && sum.maxCarry == 1) {
            val t = sum.columnTotal[sum.width - 1]
            if (digits[t] == EMPTY && base.cand[t] and 2 != 0) return Hit(LEADING, t, 1, sum.width - 1)
        }
        // 2. One column on its own, with the carry into it when everything to its right is placed.
        for (c in 0 until sum.width) {
            val s = base.copy()
            knownCarry(sum, digits, c)?.let { s.carry[c] = 1 shl it }
            if (s.revise(c) < 0) continue
            for (l in (sum.columnAddends[c] + sum.columnTotal[c]).distinct()) {
                if (digits[l] == EMPTY && single(s.cand[l])) return Hit(COLUMN, l, s.cand[l].countTrailingZeroBits(), c)
            }
        }
        // 3. Ten letters use every digit: a digit only one letter can still take.
        if (base.n == 10) {
            for (d in 0..9) {
                if (digits.contains(d)) continue
                val holders = (0 until base.n).filter { digits[it] == EMPTY && base.cand[it] and (1 shl d) != 0 }
                if (holders.size == 1) return Hit(ONLY_LETTER, holders[0], d, -1)
            }
        }
        // 4. Two neighbouring columns worked together through the carry between them.
        for (c in 0 until sum.width - 1) {
            val s = base.copy()
            knownCarry(sum, digits, c)?.let { s.carry[c] = 1 shl it }
            if (!s.settle(c, c + 1)) continue
            for (l in (sum.columnAddends[c] + sum.columnTotal[c] + sum.columnAddends[c + 1] + sum.columnTotal[c + 1]).distinct()) {
                if (digits[l] == EMPTY && single(s.cand[l])) return Hit(PAIR, l, s.cand[l].countTrailingZeroBits(), c)
            }
        }
        // 5. Every column and every carry worked through together.
        val all = base.copy()
        if (!all.settle()) return null
        for (l in 0 until base.n) {
            if (digits[l] == EMPTY && single(all.cand[l])) return Hit(CHAIN, l, all.cand[l].countTrailingZeroBits(), -1)
        }
        // 6. On top of that, every choice for two neighbouring columns tried at once.
        for (c in 0 until sum.width - 1) {
            val s = all.copy()
            if (s.reviseJoint(c) < 0 || !s.settle()) continue
            for (l in 0 until base.n) {
                if (digits[l] == EMPTY && single(s.cand[l])) return Hit(WHAT_IF, l, s.cand[l].countTrailingZeroBits(), c)
            }
        }
        return null
    }

    /** Whether the ladder alone finishes [sum] from an empty board: what the generator asks of every board. */
    fun solvesByLogic(sum: CipherSum): Boolean = walk(sum) != null

    /** The ladder's steps from an empty board to the answer, or null when it gets stuck or ends somewhere wrong. */
    fun walk(sum: CipherSum): List<Hit>? {
        val digits = MutableList(sum.letters.size) { EMPTY }
        val steps = mutableListOf<Hit>()
        while (digits.any { it == EMPTY }) {
            val hit = findStep(sum, digits) ?: return null
            digits[hit.letter] = hit.digit
            steps += hit
        }
        return if (CipherLogic.isSolved(sum, digits)) steps else null
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ------------------------

    const val MAX_EXPLANATION = 200
    const val MAX_NUDGE = 70

    /** One step: the digit for [letter] ([EMPTY] to take a mistake back), where to look and why. */
    class Step(
        val technique: String,
        val letter: Int,
        val digit: Int,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    fun teach(s: CipherState): Step? {
        if (s.solved) return null
        val sum = s.sum
        mistake(sum, s)?.let { return it }
        return deduce(sum, s.digits) ?: fallback(sum, s)
    }

    private fun name(sum: CipherSum, l: Int) = sum.letters[l].uppercaseChar().toString()

    private fun fallback(sum: CipherSum, s: CipherState): Step? {
        val l = s.digits.indices.firstOrNull { s.digits[it] == EMPTY } ?: return null
        val d = s.solution[l]
        return Step(
            FALLBACK, l, d, sum.tilesOf(l), emptySet(),
            "Try ${name(sum, l)}.",
            "No short reason found: the stored answer has ${name(sum, l)} = $d.",
        )
    }

    private fun mistake(sum: CipherSum, s: CipherState): Step? {
        val wrong = s.digits.indices.filter { s.digits[it] != EMPTY && s.digits[it] != s.solution[it] }
        if (wrong.isEmpty()) return null
        val conflicts = CipherLogic.conflicts(sum, s.digits)
        val l = wrong.firstOrNull { it in conflicts.letters }
            ?: wrong.firstOrNull { conflicts.column != null && it in columnLetters(sum, conflicts.column) }
            ?: wrong.first()
        val d = s.digits[l]
        val x = name(sum, l)
        val clear = "Select $x and tap $d again to clear it."
        fun step(why: String, cited: Set<Int>) = Step(MISTAKE, l, EMPTY, sum.tilesOf(l), cited, "Check $x = $d.", "$why $clear")
        val twin = s.digits.indices.firstOrNull { it != l && s.digits[it] == d }
        if (twin != null) return step("$x and ${name(sum, twin)} can't both be $d: each letter has its own digit.", sum.tilesOf(twin))
        if (d == 0 && sum.leading and (1 shl l) != 0) return step("$x starts a word, so it can't be 0.", emptySet())
        val c = conflicts.column
        if (c != null && l in columnLetters(sum, c)) {
            return step("With these digits the ${columnName(c)} doesn't add up.", sum.columnTiles(c))
        }
        return step("No way to finish the sum keeps $x = $d.", emptySet())
    }

    private fun columnLetters(sum: CipherSum, c: Int) = (sum.columnAddends[c] + sum.columnTotal[c]).distinct()

    private val PLACES = listOf("units", "tens", "hundreds", "thousands", "ten-thousands", "hundred-thousands")

    private fun columnName(c: Int) = "${PLACES[c]} column"

    /** A letter as the player sees it: its digit once placed. */
    private fun term(sum: CipherSum, digits: List<Int>, l: Int) = if (digits[l] != EMPTY) digits[l].toString() else name(sum, l)

    /** Column [c] as a sum in words: "E + 1 + carry ends in O". [carry] is the carry into it when known. */
    private fun columnText(sum: CipherSum, digits: List<Int>, c: Int, carry: Int?): String {
        val parts = sum.columnAddends[c].map { term(sum, digits, it) } + when {
            c == 0 || carry == 0 -> emptyList()
            carry != null -> listOf("$carry carried")
            else -> listOf("carry")
        }
        val t = term(sum, digits, sum.columnTotal[c])
        return if (sum.columnAddends[c].isEmpty()) "the carry into it is $t" else "${parts.joinToString(" + ")} ends in $t"
    }

    fun deduce(sum: CipherSum, digits: List<Int>): Step? {
        val hit = findStep(sum, digits) ?: return null
        val l = hit.letter
        val d = hit.digit
        val x = name(sum, l)
        val c = hit.column
        return when (hit.technique) {
            LEADING -> Step(
                LEADING, l, d, sum.tilesOf(l), sum.columnTiles(c - 1),
                "Look at the first letter of the total.",
                "The total is a letter longer than the addends, so its first letter, $x, is what the ${columnName(c - 1)} " +
                    "carries. Two digits and a carry make at most 19, so $x is 1.",
            )
            COLUMN -> column(sum, digits, l, d, c)
            ONLY_LETTER -> Step(
                ONLY_LETTER, l, d, sum.tilesOf(l), emptySet(),
                if (d == 0) "Which letter can be 0?" else "One letter is left.",
                if (d == 0) "Ten letters share all ten digits, so one of them is 0. Every other open letter starts a word, so only $x can be 0."
                else "Ten letters share all ten digits, and $d is the only digit no letter has, so $x is $d.",
            )
            PAIR -> {
                val tiles = sum.columnTiles(c) + sum.columnTiles(c + 1)
                val carry = knownCarry(sum, digits, c)
                val next = if (sum.columnAddends[c + 1].isEmpty()) "it must carry ${term(sum, digits, sum.columnTotal[c + 1])} into the ${columnName(c + 1)}"
                else "in the ${columnName(c + 1)} ${columnText(sum, digits, c + 1, null)}"
                var text = "Take the ${PLACES[c]} and ${PLACES[c + 1]} columns together: ${columnText(sum, digits, c, carry)}, and $next. " +
                    "Only $x = $d fits both."
                if (text.length > MAX_EXPLANATION) {
                    text = "Take the ${PLACES[c]} and ${PLACES[c + 1]} columns together, through the carry between them: only $x = $d lets both add up."
                }
                Step(PAIR, l, d, tiles, tiles, "Look at the ${PLACES[c]} and ${PLACES[c + 1]} columns together.", text)
            }
            CHAIN -> {
                val cols = (0 until sum.width).filter { l in columnLetters(sum, it) }
                Step(
                    CHAIN, l, d, sum.tilesOf(l), cols.flatMap { sum.columnTiles(it) }.toSet(),
                    "Follow the carries through every column.",
                    "Work every column through together, passing each carry on and giving no two letters the same digit. " +
                        "Once each column has ruled out what it can, $x can only be $d.",
                )
            }
            else -> {
                val tiles = sum.columnTiles(c) + sum.columnTiles(c + 1)
                Step(
                    WHAT_IF, l, d, tiles, tiles,
                    "Try the choices in the ${PLACES[c]} and ${PLACES[c + 1]} columns.",
                    "Try every choice for the ${PLACES[c]} and ${PLACES[c + 1]} columns at once, keeping what the other columns " +
                        "rule out: only the ones with $x = $d make both add up.",
                )
            }
        }
    }

    private fun column(sum: CipherSum, digits: List<Int>, l: Int, d: Int, c: Int): Step {
        val x = name(sum, l)
        val tiles = sum.columnTiles(c)
        val carry = if (c == 0) 0 else knownCarry(sum, digits, c)
        val adds = sum.columnAddends[c]
        val t = sum.columnTotal[c]
        val others = columnLetters(sum, c).filter { it != l }
        val nudge = "Look at the ${columnName(c)}."
        // Everything else in the column is known: plain arithmetic.
        if (carry != null && others.all { digits[it] != EMPTY } && adds.count { it == l } <= 1) {
            val explanation = if (l == t && l !in adds) {
                val s = adds.sumOf { digits[it] } + carry
                val parts = adds.map { digits[it].toString() } + if (carry > 0) listOf("$carry carried") else emptyList()
                if (adds.isEmpty()) "The carry into the ${columnName(c)} is $carry, so $x is $d."
                else "In the ${columnName(c)}, ${parts.joinToString(" + ")} makes $s, so $x is $d."
            } else {
                "In the ${columnName(c)}, ${columnText(sum, digits.toMutableList().also { it[l] = EMPTY }, c, carry)}, so $x is $d."
            }
            return Step(COLUMN, l, d, tiles, tiles, nudge, explanation)
        }
        return Step(
            COLUMN, l, d, tiles, tiles, nudge,
            "In the ${columnName(c)}, ${columnText(sum, digits, c, carry)}. Of the digits still free, only $x = $d makes that work.",
        )
    }
}
