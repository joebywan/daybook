package com.joebywan.daybook.puzzles

import com.joebywan.daybook.puzzles.CipherLogic.EMPTY

/**
 * The working behind a Cipher hint, written out so a player can follow it: which carries are settled and why, then
 * the column that gives the letter, as a sum, with every other digit it could have ruled out by name. "Only I = 0
 * fits both" told a player the answer and nothing they could use on the next board.
 *
 * It may lean only on what the player can see: the digits placed. The step's own technique proves which carries are
 * settled ([carry], masks as [CipherTeacher.Sight.carry]); a carry is used only once a sentence here says why, from
 * one neighbouring column and the placed digits (chained outward a few columns at most). Anything it cannot say that
 * way gives null, and the teacher keeps its shorter summary.
 */
internal class CipherWorking(private val sum: CipherSum, private val digits: List<Int>, private val carry: IntArray) {

    private val places = listOf("units", "tens", "hundreds", "thousands", "ten-thousands", "hundred-thousands")

    private fun name(l: Int) = sum.letters[l].uppercaseChar().toString()
    private fun leads(l: Int) = sum.leading and (1 shl l) != 0
    private fun columnLetters(c: Int) = (sum.columnAddends[c] + sum.columnTotal[c]).distinct()

    /** Who holds digit [d] already, as "T has 1"; null when nobody does. */
    private fun holder(d: Int): Int? = digits.indexOf(d).takeIf { it >= 0 }

    /** Every way column [c] adds up with carry [a] in and [b] out, from [v]. Strict: free digits, all different, no leading 0. */
    private fun fills(c: Int, a: Int, b: Int, v: List<Int>, strict: Boolean): List<List<Int>> {
        val open = columnLetters(c).filter { v[it] == EMPTY }
        val out = mutableListOf<List<Int>>()
        val w = v.toMutableList()
        fun go(k: Int) {
            if (k == open.size) {
                if (sum.columnAddends[c].sumOf { w[it] } + a == w[sum.columnTotal[c]] + 10 * b) out += w.toList()
                return
            }
            val l = open[k]
            for (d in 0..9) {
                if (d == 0 && leads(l)) continue
                if (strict && w.contains(d)) continue
                w[l] = d
                go(k + 1)
                w[l] = EMPTY
            }
        }
        go(0)
        return out
    }

    /** A carry the player can work out without thinking about it: none into the units or out of the top, or all placed to its right. */
    private fun plainCarry(k: Int): Int? {
        if (k == 0 || k == sum.width) return 0
        var cy = 0
        for (c in 0 until k) {
            if (columnLetters(c).any { digits[it] == EMPTY }) return null
            cy = (sum.columnAddends[c].sumOf { digits[it] } + cy) / 10
        }
        return cy
    }

    private class Carry(val values: List<Int>, val why: List<String> = emptyList(), val columns: Set<Int> = emptySet())

    /** The working as said, and the columns it reads. */
    class Working(val text: String, val columns: Set<Int>)

    /** Carry [k]: plain, explained (with the sentences that explain it), or either value. [away] is the direction to look for a reason. */
    private fun carryAt(k: Int, away: Int, depth: Int): Carry {
        plainCarry(k)?.let { return Carry(listOf(it)) }
        val m = carry[k]
        if (depth > 0 && m != 0 && m and (m - 1) == 0) {
            val v = m.countTrailingZeroBits()
            listOfNotNull(explainCarry(k, v, away, depth), supposeCarry(k, v))
                .minByOrNull { it.why.sumOf { w -> w.length } }?.let { return it }
        }
        return Carry(listOf(0, 1))
    }

    /** Carry [k] is [v] because the other value, followed column by column, gives some letter a digit it cannot have. */
    private fun supposeCarry(k: Int, v: Int): Carry? {
        val cy = IntArray(sum.width + 1) { -1 }.also { it[0] = 0; it[sum.width] = 0; it[k] = 1 - v }
        val (trace, columns) = follow(digits.toMutableList(), cy) ?: return null
        val who = "the ${places[k - 1]}"
        val conclusion = if (v == 1) "so they carry 1" else "so they carry nothing"
        val hypothesis = if (v == 1) "If $who carried nothing" else "If $who carried 1"
        return Carry(listOf(v), listOf("$hypothesis, $trace, $conclusion."), columns + (k - 1) + minOf(k, sum.width - 1))
    }

    /**
     * Follows [v] and the carries [cy] (-1 unknown) column by column, as a player would with pencil: a column with one
     * letter left to find gives it, a column with every letter known gives its carries. Returns how it breaks ("T would
     * be 4 and H 9, which A has") and the columns it read, or null if nothing breaks within [LIMIT] letters.
     */
    private fun follow(v: MutableList<Int>, cy: IntArray): Pair<String, Set<Int>>? {
        val found = mutableListOf<String>()
        val read = mutableSetOf<Int>()
        fun told(clash: String): Pair<String, Set<Int>> {
            val head = when (found.size) {
                0 -> ""
                1 -> "${found[0]} and "
                else -> found.joinToString(", ") + " and "
            }
            return "$head$clash" to read
        }
        fun place(l: Int, d: Int, c: Int): Pair<String, Set<Int>>? {
            read += c
            if (d !in 0..9) return told("${name(l)} would have to be $d")
            if (d == 0 && leads(l)) return told("${name(l)} would be 0, which can't start a word")
            val who = v.indexOf(d)
            if (who >= 0) {
                val by = if (digits[who] != EMPTY) "which ${name(who)} has" else "the same as ${name(who)}"
                return told("${name(l)} would be $d, $by")
            }
            v[l] = d
            found += "${name(l)} would be $d"
            return null
        }
        var changed = true
        while (changed && found.size < LIMIT) {
            changed = false
            for (c in 0 until sum.width) {
                val adds = sum.columnAddends[c]
                val t = sum.columnTotal[c]
                val open = columnLetters(c).filter { v[it] == EMPTY }
                val cin = cy[c]
                val cout = cy[c + 1]
                if (adds.isEmpty()) {
                    // The total's first letter is the carry into it.
                    if (cin >= 0 && v[t] == EMPTY) { place(t, cin, c)?.let { return it }; changed = true }
                    else if (cin < 0 && v[t] != EMPTY) { cy[c] = v[t]; changed = true }
                    else if (cin >= 0 && v[t] != cin) { read += c; return told("${name(t)} would have to be $cin") }
                    continue
                }
                val known = adds.filter { v[it] != EMPTY }.sumOf { v[it] }
                if (open.isEmpty()) {
                    val total = v[t]
                    if (cin >= 0) {
                        val s = known + cin
                        if (s % 10 != total || (cout >= 0 && s / 10 != cout)) {
                            read += c
                            return told(if (s % 10 != total) "the ${places[c]} would make $s, not ending in $total" else "the ${places[c]} would carry ${s / 10}")
                        }
                        if (cout < 0) { cy[c + 1] = s / 10; changed = true }
                    } else if (cout >= 0) {
                        val need = total + 10 * cout - known
                        if (need !in 0..1) { read += c; return told("the ${places[c]} couldn't add up") }
                        cy[c] = need
                        changed = true
                    }
                    continue
                }
                if (open.size != 1 || cin < 0) continue
                val l = open[0]
                val times = adds.count { it == l }
                when {
                    l == t && times == 0 -> {
                        val s = known + cin
                        if (cout >= 0 && s / 10 != cout) { read += c; return told("the ${places[c]} would carry ${s / 10}") }
                        place(l, s % 10, c)?.let { return it }
                        cy[c + 1] = s / 10
                    }
                    times == 1 && l != t -> {
                        val d = if (cout >= 0) v[t] + 10 * cout - known - cin else ((v[t] - known - cin) % 10 + 10) % 10
                        place(l, d, c)?.let { return it }
                        if (cout < 0) cy[c + 1] = (known + d + cin) / 10
                    }
                    else -> continue
                }
                changed = true
            }
        }
        return null
    }

    /** Why carry [k] is [v], from the column on the [away] side of it (-1: the column it leaves, +1: the column it enters). */
    private fun explainCarry(k: Int, v: Int, away: Int, depth: Int): Carry? {
        val col = if (away < 0) k - 1 else k
        if (col < 0 || col >= sum.width) return null
        val otherK = if (away < 0) k - 1 else k + 1
        // The column's other carry left open when that is enough, else explained first.
        return listOf(Carry(listOf(0, 1)), carryAt(otherK, away, depth - 1)).distinctBy { it.values }
            .mapNotNull { explainCarry(k, v, away, col, it) }.minByOrNull { it.why.sumOf { w -> w.length } }
    }

    private fun explainCarry(k: Int, v: Int, away: Int, col: Int, other: Carry): Carry? {
        val (ins, outs) = if (away < 0) other.values to listOf(1 - v) else listOf(1 - v) to other.values
        if (ins.any { a -> outs.any { b -> fills(col, a, b, digits, strict = true).isNotEmpty() } }) return null
        val conclusion = if (v == 1) "the ${places[k - 1]} carry 1" else "the ${places[k - 1]} carry nothing"
        val t = sum.columnTotal[col]
        // The total's extra first letter is the carry itself.
        if (sum.columnAddends[col].isEmpty()) {
            if (digits[t] == EMPTY) return null
            return Carry(listOf(v), other.why + "${name(t)} is ${digits[t]}, so $conclusion.", other.columns + col)
        }
        val hypothesis = if (away < 0) {
            if (v == 1) "Carrying nothing" else "Carrying 1"
        } else {
            if (v == 1) "With nothing carried in" else "With 1 carried in"
        }
        val o = other.values.singleOrNull()
        val lhs = if (away < 0) equation(col, o, null) else equation(col, null, o)
        // Everything in the column is placed: the sum itself shows the carry.
        if (columnLetters(col).all { digits[it] != EMPTY }) {
            return Carry(listOf(v), other.why + "In the ${places[col]}, $lhs, so $conclusion.", other.columns + col)
        }
        val reason = failure(col, ins, outs) ?: return null
        return Carry(listOf(v), other.why + "In the ${places[col]}, $lhs. $hypothesis, $reason, so $conclusion.", other.columns + col)
    }

    /** Column [c] as the player reads it; a null carry is written "carry" ([a], in) or left as "ends in" ([b], out). */
    private fun equation(c: Int, a: Int?, b: Int?, v: List<Int> = digits): String {
        fun term(l: Int) = if (v[l] != EMPTY) v[l].toString() else name(l)
        val t = sum.columnTotal[c]
        val left = sum.columnAddends[c].map(::term) + when (a) {
            null -> listOf("carry")
            0 -> emptyList()
            else -> listOf("$a carried")
        }
        val total = term(t)
        val right = when {
            b == null -> "ends in $total"
            b == 0 -> "makes $total"
            v[t] != EMPTY -> "makes ${10 * b + v[t]}"
            else -> "makes ${10 * b} + $total"
        }
        return "${left.joinToString(" + ")} $right"
    }

    /** Why column [c] cannot add up with any carry in [ins] and out [outs] and the digits placed, as a clause; null if it is not short. */
    private fun failure(c: Int, ins: List<Int>, outs: List<Int>): String? {
        val open = columnLetters(c).filter { digits[it] == EMPTY }
        val adds = sum.columnAddends[c]
        val t = sum.columnTotal[c]
        val lhs = left(c, ins.singleOrNull())
        if (open.isEmpty()) return "$lhs would make ${list(ins.map { adds.sumOf { l -> digits[l] } + it })}"
        val raw = ins.flatMap { a -> outs.flatMap { b -> fills(c, a, b, digits, strict = false) } }
        if (raw.isEmpty()) {
            val max = adds.sumOf { if (digits[it] != EMPTY) digits[it] else 9 } + ins.max()
            val min = adds.sumOf { if (digits[it] != EMPTY) digits[it] else if (leads(it)) 1 else 0 } + ins.min()
            return when {
                outs == listOf(1) && max < 10 -> "$lhs is at most $max and can't reach 10"
                outs == listOf(0) && min > 9 -> "$lhs is at least $min, over 9"
                else -> null
            }
        }
        if (open.size == 1) {
            val y = open[0]
            val values = raw.map { it[y] }.distinct().sorted()
            val held = values.map { holder(it) }
            if (held.all { it != null }) {
                val who = held.map { name(it!!) }
                return "${name(y)} would be ${list(values)}, which ${list(who, "and")} ${if (who.size == 1) "has" else "have"}"
            }
            if (values == listOf(0) && leads(y)) return "${name(y)} would be 0, but it starts a word"
            return null
        }
        // Two open letters forced equal: "I + 0 is T" with nothing carried makes T the same as I.
        if (open.size == 2 && raw.all { it[open[0]] == it[open[1]] }) {
            val (p, q) = if (open[1] == t) open[1] to open[0] else open[0] to open[1]
            return "${name(p)} would be the same digit as ${name(q)}"
        }
        // One way only, and it needs a digit already placed: "A would be 9 and P 0, which E has".
        if (raw.size == 1) {
            val f = raw[0]
            val held = open.filter { holder(f[it]) != null }
            if (held.isNotEmpty()) {
                val parts = open.map { "${name(it)} ${f[it]}" }
                return "${parts.first().replace(" ", " would be ")}${if (parts.size > 1) " and " + parts.drop(1).joinToString(" and ") else ""}, " +
                    "but ${list(held.map { "${name(holder(f[it])!!)} has ${f[it]}" }, "and")}"
            }
        }
        // Every way to make it uses digits already placed: "E and L would have to come from 0 and 1, and I and F have those".
        val used = raw.flatMap { f -> open.map { f[it] } }.distinct().sorted()
        if (used.size <= 3 && raw.all { f -> open.any { holder(f[it]) != null } }) {
            val held = used.mapNotNull { holder(it) }.distinct()
            if (held.size == used.size) {
                return "${list(open.map { name(it) }, "and")} would have to come from ${list(used, "and")}, " +
                    "and ${list(held.map { name(it) }, "and")} have those"
            }
        }
        return null
    }

    /** The left of column [c]: its addends and carry [a] ("carry" when null). */
    private fun left(c: Int, a: Int?): String = equation(c, a, null).substringBefore(" ends in")

    /** Column [c] settles [x] at [d] once its carries are known: the sentence that shows it, or null. */
    private fun settle(c: Int, x: Int, d: Int, cin: List<Int>, cout: List<Int>): String? {
        val vals = cin.flatMap { a -> cout.flatMap { b -> fills(c, a, b, digits, strict = true).map { it[x] } } }.distinct()
        if (vals != listOf(d)) return null
        val a = cin.singleOrNull()
        val b = cout.singleOrNull()
        val open = columnLetters(c).filter { digits[it] == EMPTY && it != x }
        val adds = sum.columnAddends[c]
        val t = sum.columnTotal[c]
        val head = "In the ${places[c]}, ${equation(c, a, b)}"
        val xn = name(x)
        if (adds.isEmpty()) return null
        // Plain arithmetic: everything else in the column is known.
        if (open.isEmpty() && a != null) return "$head, so $xn is $d."
        // The total, bounded: 1 + R + carry makes 10 or more and at most 11, so I is 0 or 1.
        if (x == t && x !in adds && b != null) {
            val lo = adds.sumOf { if (digits[it] != EMPTY) digits[it] else if (leads(it)) 1 else 0 } + cin.min()
            val hiTerms = adds.map { if (digits[it] != EMPTY) digits[it] else 9 } + listOf(cin.max())
            val hi = hiTerms.sum()
            val from = maxOf(0, lo - 10 * b)
            val to = minOf(9, hi - 10 * b)
            if (to - from in 0..2) {
                val range = (from..to).toList()
                val taken = range.filter { it != d }
                if (taken.all { holder(it) != null || (it == 0 && leads(x)) }) {
                    val most = hiTerms.filter { it != 0 }.joinToString(" + ")
                    val bound = if (b == 1) "That is at least 10 and at most $most = $hi" else "That is at most $most = $hi"
                    val ruled = taken.joinToString(" and ") { e -> holder(e)?.let { "${name(it)} has $e" } ?: "$xn starts a word" }
                    return if (taken.isEmpty()) "$head. $bound, so $xn is $d." else "$head. $bound, so $xn is ${list(range)}; $ruled, so $xn is $d."
                }
            }
        }
        // One other open letter: try each digit for one of the two and see what the other would have to be.
        if (open.size == 1 && a != null) {
            val y = open[0]
            return listOf(x to y, y to x).mapNotNull { (p, q) -> tryEach(c, a, cout, x, d, p, q, head) }.minByOrNull { it.length }
        }
        // Nothing else open: whatever is carried, only a few digits make the column work, and all but one are taken.
        if (open.isEmpty()) {
            val v = digits.toMutableList().also { it[x] = EMPTY }
            val values = cin.flatMap { ai -> cout.flatMap { bo -> fills(c, ai, bo, v, strict = false).map { it[x] } } }.distinct().sorted()
            val bad = values.filter { it != d }
            if (bad.isEmpty() || values.size > 4) return null
            val ruled = bad.map { e -> holder(e)?.let { "${name(it)} has $e" } ?: if (e == 0 && leads(x)) "$xn starts a word" else return null }
            val whatever = if (a == null) ", whatever is carried" else ""
            return "$head. Only $xn = ${list(values)} can do that$whatever; ${list(ruled, "and")}, so $xn is $d."
        }
        return null
    }

    /**
     * Column [c] with carry [a] in: each free digit for [p] makes [q] one digit; every choice but those with [x] = [d]
     * gives [q] a digit it cannot have. "N = 5, 6, 7 or 9 would make U 0, 2, 4 or 8. A has 0, M has 2, W has 8, so N is 7."
     */
    private fun tryEach(c: Int, a: Int, cout: List<Int>, x: Int, d: Int, p: Int, q: Int, head: String): String? {
        val pairs = (0..9).filter { it !in digits && !(it == 0 && leads(p)) }.mapNotNull { pd ->
            val v = digits.toMutableList().also { it[p] = pd }
            val qs = cout.flatMap { b -> fills(c, a, b, v, strict = false).map { it[q] } }.distinct()
            when (qs.size) {
                0 -> null
                1 -> pd to qs[0]
                else -> return null
            }
        }
        if (pairs.size !in 2..6) return null
        val reasons = mutableListOf<String>()
        for ((pd, qd) in pairs) {
            val xd = if (p == x) pd else qd
            val reason = when {
                qd == pd -> "${name(p)} = $pd would make ${name(q)} $pd too"
                holder(qd) != null -> "${name(holder(qd)!!)} has $qd"
                qd == 0 && leads(q) -> "${name(q)} can't be 0"
                else -> null
            }
            if (reason == null && xd != d) return null
            if (reason != null) reasons += reason
        }
        if (reasons.isEmpty()) return null
        return "$head: ${name(p)} = ${list(pairs.map { it.first })} would make ${name(q)} ${list(pairs.map { it.second })}. " +
            "${reasons.distinct().joinToString(", ").replaceFirstChar { it.uppercase() }}, so ${name(x)} is $d."
    }

    private fun <T> list(xs: List<T>, joiner: String = "or"): String = when (xs.size) {
        1 -> "${xs[0]}"
        else -> xs.dropLast(1).joinToString(", ") + " $joiner " + xs.last()
    }

    /** The whole working for [x] = [d], from whichever of its columns can show it most briefly; null if none can. */
    fun explain(x: Int, d: Int): Working? =
        (0 until sum.width).filter { x in columnLetters(it) }.flatMap { c ->
            // Each carry either explained or left open: the column may settle the letter whatever is carried, more briefly.
            val open = Carry(listOf(0, 1))
            val ins = listOf(carryAt(c, -1, DEPTH), open).distinctBy { it.values }
            val outs = listOf(carryAt(c + 1, +1, DEPTH), open).distinctBy { it.values }
            ins.flatMap { cin ->
                outs.mapNotNull { cout ->
                    settle(c, x, d, cin.values, cout.values)?.let {
                        Working((cin.why + cout.why + it).joinToString(" "), cin.columns + cout.columns + c)
                    }
                }
            }
        }.minByOrNull { it.text.length } ?: suppose(x, d)

    /** Every other digit for [x], followed through the columns until it breaks: "If N were 3, T would be 4, which A has." */
    private fun suppose(x: Int, d: Int): Working? {
        val columns = mutableSetOf<Int>()
        val cases = (0..9).filter { it != d && it !in digits && !(it == 0 && leads(x)) }.map { xd ->
            val cy = IntArray(sum.width + 1) { plainCarry(it) ?: -1 }
            val (trace, read) = follow(digits.toMutableList().also { it[x] = xd }, cy) ?: return null
            columns += read
            "If ${name(x)} were $xd, $trace."
        }
        if (cases.size > 4) return null
        return Working((cases + "So ${name(x)} is $d.").joinToString(" "), columns)
    }

    private companion object {
        const val DEPTH = 3
        const val LIMIT = 3
    }
}
