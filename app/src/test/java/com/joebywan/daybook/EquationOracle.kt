package com.joebywan.daybook

/**
 * Equation's rules on a different principle from [com.joebywan.daybook.puzzles.EquationLogic]: a row is cut into a
 * list of numbers and signs, the × and ÷ are folded out of the list one pair at a time, then what is left is added up;
 * and the lists are found by trying every left side of every length symbol by symbol, with no solving for the last
 * number. Nothing is shared with the code under test.
 */
internal object EquationOracle {

    /** The left side's value, or null if it is not a left side the rules allow (malformed, a leading 0, a ÷ not whole, no sign). */
    fun value(left: String): Long? {
        val tokens = ArrayList<String>()
        val number = StringBuilder()
        for (c in left) {
            if (c in '0'..'9') number.append(c)
            else if (c in "+-*/") {
                if (number.isEmpty()) return null
                tokens += number.toString()
                number.clear()
                tokens += c.toString()
            } else return null
        }
        if (number.isEmpty()) return null
        tokens += number.toString()
        if (tokens.size < 3) return null
        if (tokens.filterIndexed { i, _ -> i % 2 == 0 }.any { it.length > 1 && it[0] == '0' }) return null
        // Fold × and ÷ out of the list, leftmost first.
        val list = tokens.toMutableList<Any>()
        for (i in list.indices step 2) list[i] = (list[i] as String).toLong()
        var i = 1
        while (i < list.size) {
            val op = list[i] as String
            if (op == "*" || op == "/") {
                val a = list[i - 1] as Long
                val b = list[i + 1] as Long
                val r = if (op == "*") a * b else {
                    if (b == 0L || a % b != 0L) return null
                    a / b
                }
                list[i - 1] = r
                list.removeAt(i)
                list.removeAt(i)
            } else i += 2
        }
        var total = list[0] as Long
        var j = 1
        while (j < list.size) {
            val b = list[j + 1] as Long
            total = if (list[j] == "+") total + b else total - b
            j += 2
        }
        return total
    }

    fun isTrue(row: String): Boolean {
        val parts = row.split("=")
        if (parts.size != 2) return false
        val v = value(parts[0]) ?: return false
        return v >= 0 && v.toString() == parts[1]
    }

    /** Every true sum of 5 to 8 symbols, by length, sorted: every left side of 3 to 6 symbols tried. */
    val lists: Map<Int, List<String>> by lazy {
        val found = HashMap<Int, MutableList<String>>()
        val symbols = "0123456789+-*/"
        for (l in 3..6) {
            val idx = IntArray(l)
            val chars = CharArray(l)
            while (true) {
                for (p in 0 until l) chars[p] = symbols[idx[p]]
                val left = chars.concatToString()
                val v = value(left)
                if (v != null && v >= 0) {
                    val total = l + 1 + v.toString().length
                    if (total in 5..8) found.getOrPut(total) { ArrayList() } += "$left=$v"
                }
                var p = l - 1
                while (p >= 0 && idx[p] == symbols.length - 1) { idx[p] = 0; p-- }
                if (p < 0) break
                idx[p]++
            }
        }
        found.mapValues { it.value.sorted() }
    }

    /** Whether [row] has a number that is exactly 0, read by splitting on the signs. */
    fun loneZero(row: String): Boolean = row.split('+', '-', '*', '/', '=').any { it == "0" }
}
