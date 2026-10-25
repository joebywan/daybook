package com.joebywan.daybook.core

// Small pieces of English the teachers share. Free of java.*: the web compiles this file.

/** The first letter in capitals. */
fun String.cap() = replaceFirstChar { it.uppercaseChar() }

/** "1", "1 and 2", "1, 2 and 4" ([and] can be "or"; [oxford] gives "1, 2, and 4"). Empty is "". */
fun <T> listed(items: List<T>, and: String = "and", oxford: Boolean = false): String = when (items.size) {
    0 -> ""
    1 -> "${items[0]}"
    else -> items.dropLast(1).joinToString(", ") + (if (oxford) ", " else " ") + and + " " + items.last()
}

private val NUMBER_WORDS =
    listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")

/** [k] in words up to [upTo] (at most twelve), digits above it; [zero] is "zero" or "no". */
fun numberWord(k: Int, zero: String = "zero", upTo: Int = 12): String = when {
    k == 0 -> zero
    k in 1..upTo -> NUMBER_WORDS[k]
    else -> k.toString()
}

/** "1 tent", "3 tents": digits, for the counts a player reads at a glance. */
fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

/** Line [line] of an [n] by [n] board: rows first, then columns. */
fun lineName(n: Int, line: Int) = if (line < n) "row ${line + 1}" else "column ${line - n + 1}"
