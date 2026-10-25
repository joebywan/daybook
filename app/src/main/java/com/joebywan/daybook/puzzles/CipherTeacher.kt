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
         * Columns [c] until [c] + [span] as one: keeps only the digits and carries (the ones between them too) some
         * choice for the whole span supports (different letters different digits within it). -1 when nothing does,
         * 1 when something was cut.
         */
        fun revise(c: Int, span: Int = 1): Int {
            val vars = (c until c + span).flatMap { sum.columnLetters[it] }.distinct()
            val sup = IntArray(vars.size)
            val supCarry = IntArray(span + 1) // the carries into c .. c + span that some choice supports
            val cy = IntArray(span + 1)
            val adds = IntArray(span)
            val value = IntArray(n)
            fun go(k: Int, used: Int) {
                if (k == vars.size) {
                    for (j in 0 until span) adds[j] = sum.columnAddends[c + j].sumOf { value[it] }
                    cin@ for (cin in 0..sum.maxCarry) {
                        if (carry[c] and (1 shl cin) == 0) continue
                        cy[0] = cin
                        for (j in 0 until span) {
                            val s = adds[j] + cy[j]
                            if (s % 10 != value[sum.columnTotal[c + j]] || carry[c + j + 1] and (1 shl (s / 10)) == 0) continue@cin
                            cy[j + 1] = s / 10
                        }
                        for (i in vars.indices) sup[i] = sup[i] or (1 shl value[vars[i]])
                        for (j in 0..span) supCarry[j] = supCarry[j] or (1 shl cy[j])
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
            if (supCarry[0] == 0) return -1
            for (j in 0..span) if (supCarry[j] != carry[c + j]) { carry[c + j] = supCarry[j]; changed = 1 }
            return changed
        }

        /** A letter down to one digit takes it from every other; with ten letters, a digit only one letter can take is that letter's. */
        fun allDifferent(): Int {
            var changed = 0
            for (i in 0 until n) {
                if (cand[i].countOneBits() != 1) continue
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
                    if (holders.size == 1 && cand[holders[0]].countOneBits() != 1) { cand[holders[0]] = 1 shl d; changed = 1 }
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

    /** [carry]: the carries the technique settled, as [Sight.carry], for [CipherWorking] to show. */
    internal class Hit(val technique: String, val letter: Int, val digit: Int, val column: Int, val carry: IntArray)

    internal fun findStep(sum: CipherSum, digits: List<Int>): Hit? = findSteps(sum, digits).firstOrNull()

    /**
     * Every placement the first technique that finds any can make, in the order [findStep] has always taken them, so
     * the walk (and every tier) is unchanged; the hint picks among them the one it can explain best.
     */
    internal fun findSteps(sum: CipherSum, digits: List<Int>): List<Hit> {
        val base = Sight(sum, digits)
        if (!base.consistent) return emptyList()
        val hits = mutableListOf<Hit>()
        /** Every open letter of [letters] that [s] leaves one digit becomes a hit. */
        fun singles(technique: String, s: Sight, letters: Iterable<Int>, column: Int) {
            for (l in letters) {
                if (digits[l] == EMPTY && s.cand[l].countOneBits() == 1) hits += Hit(technique, l, s.cand[l].countTrailingZeroBits(), column, s.carry)
            }
        }
        val longest = sum.addends.maxOf { it.length }
        // 1. The total is longer than every addend: its first letter is what the column before it carries.
        if (sum.width > longest && sum.maxCarry == 1) {
            val t = sum.columnTotal[sum.width - 1]
            if (digits[t] == EMPTY && base.cand[t] and 2 != 0) return listOf(Hit(LEADING, t, 1, sum.width - 1, base.carry))
        }
        // 2. One column on its own, with the carry into it when everything to its right is placed.
        for (c in 0 until sum.width) {
            val s = base.copy()
            sum.knownCarry(digits, c)?.let { s.carry[c] = 1 shl it }
            if (s.revise(c) < 0) continue
            singles(COLUMN, s, sum.columnLetters[c], c)
        }
        if (hits.isNotEmpty()) return hits
        // 3. Ten letters use every digit: a digit only one letter can still take.
        if (base.n == 10) {
            for (d in 0..9) {
                if (digits.contains(d)) continue
                val holders = (0 until base.n).filter { digits[it] == EMPTY && base.cand[it] and (1 shl d) != 0 }
                if (holders.size == 1) hits += Hit(ONLY_LETTER, holders[0], d, -1, base.carry)
            }
        }
        if (hits.isNotEmpty()) return hits
        // 4. Two neighbouring columns worked together through the carry between them.
        for (c in 0 until sum.width - 1) {
            val s = base.copy()
            sum.knownCarry(digits, c)?.let { s.carry[c] = 1 shl it }
            if (!s.settle(c, c + 1)) continue
            singles(PAIR, s, (sum.columnLetters[c] + sum.columnLetters[c + 1]).distinct(), c)
        }
        if (hits.isNotEmpty()) return hits
        // 5. Every column and every carry worked through together.
        val all = base.copy()
        if (!all.settle()) return emptyList()
        singles(CHAIN, all, 0 until base.n, -1)
        if (hits.isNotEmpty()) return hits
        // 6. On top of that, every choice for two neighbouring columns tried at once.
        for (c in 0 until sum.width - 1) {
            val s = all.copy()
            if (s.revise(c, 2) < 0 || !s.settle()) continue
            singles(WHAT_IF, s, 0 until base.n, c)
        }
        return hits
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

    const val MAX_EXPLANATION = 260
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
        /** False when the explanation is a summary ("only I = 0 fits both") rather than the working itself. */
        val shown: Boolean = true,
    )

    fun teach(s: CipherState): Step? {
        if (s.solved) return null
        val sum = s.sum
        mistake(sum, s)?.let { return it }
        return deduce(sum, s.digits) ?: fallback(sum, s)
    }

    private fun fallback(sum: CipherSum, s: CipherState): Step? {
        val l = s.digits.indices.firstOrNull { s.digits[it] == EMPTY } ?: return null
        val d = s.solution[l]
        return Step(
            FALLBACK, l, d, sum.tilesOf(l), emptySet(),
            "Try ${sum.name(l)}.",
            "No short reason found: the stored answer has ${sum.name(l)} = $d.",
        )
    }

    private fun mistake(sum: CipherSum, s: CipherState): Step? {
        val wrong = s.digits.indices.filter { s.digits[it] != EMPTY && s.digits[it] != s.solution[it] }
        if (wrong.isEmpty()) return null
        val conflicts = CipherLogic.conflicts(sum, s.digits)
        val l = wrong.firstOrNull { it in conflicts.letters }
            ?: wrong.firstOrNull { conflicts.column != null && it in sum.columnLetters[conflicts.column] }
            ?: wrong.first()
        val d = s.digits[l]
        val x = sum.name(l)
        val clear = "Select $x and tap $d again to clear it."
        fun step(why: String, cited: Set<Int>) = Step(MISTAKE, l, EMPTY, sum.tilesOf(l), cited, "Check $x = $d.", "$why $clear")
        val twin = s.digits.indices.firstOrNull { it != l && s.digits[it] == d }
        if (twin != null) return step("$x and ${sum.name(twin)} can't both be $d: each letter has its own digit.", sum.tilesOf(twin))
        if (d == 0 && sum.leading and (1 shl l) != 0) return step("$x starts a word, so it can't be 0.", emptySet())
        val c = conflicts.column
        if (c != null && l in sum.columnLetters[c]) {
            return step("With these digits the ${sum.columnName(c)} doesn't add up.", sum.columnTiles(c))
        }
        return step("No way to finish the sum keeps $x = $d.", emptySet())
    }

    /** A letter as the player sees it: its digit once placed. */
    private fun term(sum: CipherSum, digits: List<Int>, l: Int) = if (digits[l] != EMPTY) digits[l].toString() else sum.name(l)

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

    /**
     * The step to hint: of the placements the first productive technique allows, one whose working can be shown in full
     * and briefly; the first ([findStep]'s) in its summary form when none can.
     */
    fun deduce(sum: CipherSum, digits: List<Int>): Step? {
        val hits = findSteps(sum, digits)
        if (hits.isEmpty()) return null
        return hits.asSequence().mapNotNull { hit ->
            val working = CipherWorking(sum, digits, hit.carry).explain(hit.letter, hit.digit)
            val step = step(sum, digits, hit, working)
            step.takeIf { it.shown && it.explanation.length <= MAX_EXPLANATION }
        }.minByOrNull { it.explanation.length } ?: step(sum, digits, hits[0], null)
    }

    private fun step(sum: CipherSum, digits: List<Int>, hit: Hit, working: CipherWorking.Working?): Step {
        val l = hit.letter
        val d = hit.digit
        val x = sum.name(l)
        val c = hit.column
        return when (hit.technique) {
            LEADING -> Step(
                LEADING, l, d, sum.tilesOf(l), sum.columnTiles(c - 1),
                "Look at the first letter of the total.",
                "The total's extra first letter, $x, is the carry out of the ${sum.columnName(c - 1)}. Two digits and a carry " +
                    "make at most 19, so $x is 1.",
            )
            COLUMN -> column(sum, digits, l, d, c, working)
            ONLY_LETTER -> Step(
                ONLY_LETTER, l, d, sum.tilesOf(l), emptySet(),
                if (d == 0) "Which letter can be 0?" else "One letter is left.",
                if (d == 0) "Ten letters share all ten digits, so one of them is 0. Every other open letter starts a word, so only $x can be 0."
                else "Ten letters share all ten digits, and $d is the only digit no letter has, so $x is $d.",
            )
            PAIR -> {
                val tiles = sum.columnTiles(c) + sum.columnTiles(c + 1)
                val carry = sum.knownCarry(digits, c)
                val next = if (sum.columnAddends[c + 1].isEmpty()) "it must carry ${term(sum, digits, sum.columnTotal[c + 1])} into the ${sum.columnName(c + 1)}"
                else "in the ${sum.columnName(c + 1)} ${columnText(sum, digits, c + 1, null)}"
                var text = "Take the ${sum.place(c)} and ${sum.place(c + 1)} columns together: ${columnText(sum, digits, c, carry)}, and $next. " +
                    "Only $x = $d fits both."
                if (text.length > MAX_EXPLANATION) {
                    text = "Take the ${sum.place(c)} and ${sum.place(c + 1)} columns together, through the carry between them: only $x = $d lets both add up."
                }
                val nudge = "Look at the ${sum.place(c)} and ${sum.place(c + 1)} columns together."
                working?.let { Step(PAIR, l, d, sum.tilesOf(l), cited(sum, it), nudge, it.text) } ?: Step(PAIR, l, d, tiles, tiles, nudge, text, shown = false)
            }
            CHAIN -> {
                val cols = (0 until sum.width).filter { l in sum.columnLetters[it] }
                val nudge = "Follow the carries through every column."
                working?.let { Step(CHAIN, l, d, sum.tilesOf(l), cited(sum, it), nudge, it.text) } ?: Step(
                    CHAIN, l, d, sum.tilesOf(l), cols.flatMap { sum.columnTiles(it) }.toSet(), nudge,
                    "Work every column through together, passing each carry on and giving no two letters the same digit. " +
                        "Once each column has ruled out what it can, $x can only be $d.",
                    shown = false,
                )
            }
            else -> {
                val tiles = sum.columnTiles(c) + sum.columnTiles(c + 1)
                val nudge = "Try the choices in the ${sum.place(c)} and ${sum.place(c + 1)} columns."
                working?.let { Step(WHAT_IF, l, d, sum.tilesOf(l), cited(sum, it), nudge, it.text) } ?: Step(
                    WHAT_IF, l, d, tiles, tiles, nudge,
                    "Try every choice for the ${sum.place(c)} and ${sum.place(c + 1)} columns at once, keeping what the other columns " +
                        "rule out: only the ones with $x = $d make both add up.",
                    shown = false,
                )
            }
        }
    }

    /** The tiles of the columns a working reads. */
    private fun cited(sum: CipherSum, w: CipherWorking.Working) = w.columns.flatMap { sum.columnTiles(it) }.toSet()

    private fun column(sum: CipherSum, digits: List<Int>, l: Int, d: Int, c: Int, working: CipherWorking.Working?): Step {
        val x = sum.name(l)
        val tiles = sum.columnTiles(c)
        val carry = if (c == 0) 0 else sum.knownCarry(digits, c)
        val adds = sum.columnAddends[c]
        val t = sum.columnTotal[c]
        val others = sum.columnLetters[c].filter { it != l }
        val nudge = "Look at the ${sum.columnName(c)}."
        // Everything else in the column is known: plain arithmetic.
        if (carry != null && others.all { digits[it] != EMPTY } && adds.count { it == l } <= 1) {
            val explanation = if (l == t && l !in adds) {
                val s = adds.sumOf { digits[it] } + carry
                val parts = adds.map { digits[it].toString() } + if (carry > 0) listOf("$carry carried") else emptyList()
                if (adds.isEmpty()) "The carry into the ${sum.columnName(c)} is $carry, so $x is $d."
                else "In the ${sum.columnName(c)}, ${parts.joinToString(" + ")} makes $s, so $x is $d."
            } else {
                "In the ${sum.columnName(c)}, ${columnText(sum, digits.toMutableList().also { it[l] = EMPTY }, c, carry)}, so $x is $d."
            }
            return Step(COLUMN, l, d, tiles, tiles, nudge, explanation)
        }
        // X + Y + carry ends in X: Y and the carry add nothing to X's last digit, so Y + carry is 0 or 10. Said
        // outright, because "only Y = 9 works" left a player asking why X could not be the 9 instead.
        if (l != t && t in adds && adds.size == 2 && adds.count { it == l } == 1 && (d == 0 || d == 9)) {
            val other = 9 - d
            val why = when {
                carry != null -> "With $carry carried, $x is $d."
                other == 0 && sum.leading and (1 shl l) != 0 -> "$x starts a word, so it isn't 0: $x is 9."
                digits.indexOf(other).let { it >= 0 && it != l } ->
                    "${sum.name(digits.indexOf(other))} already has $other, so $x is $d."
                else -> "Of the two, only $d fits the carries on either side."
            }
            return Step(
                COLUMN, l, d, tiles, tiles, nudge,
                "In the ${sum.columnName(c)}, ${columnText(sum, digits, c, carry)}, so $x + carry is 0 or 10: " +
                    "$x is 0 (nothing carried) or 9 (1 carried). $why",
            )
        }
        working?.let { return Step(COLUMN, l, d, sum.tilesOf(l), cited(sum, it), nudge, it.text) }
        return Step(
            COLUMN, l, d, tiles, tiles, nudge,
            "In the ${sum.columnName(c)}, ${columnText(sum, digits, c, carry)}. Of the digits still free, only $x = $d makes that work.",
            shown = false,
        )
    }
}
