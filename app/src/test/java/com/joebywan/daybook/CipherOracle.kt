package com.joebywan.daybook

/**
 * Every answer of a word sum, found the dumbest way there is: try every digit for every letter (all different,
 * in alphabetical letter order), then read the words as numbers and add them up. No columns, no carries, no
 * pruning beyond "different letters, different digits", so it shares nothing with `CipherLogic.prove` or the
 * teacher's ladder but the rules themselves.
 */
object CipherOracle {

    /** All answers of addends + ... = total ([words], lowercase), each a digit per letter in alphabetical order. */
    fun answers(words: List<String>): List<List<Int>> {
        val letters = words.joinToString("").toSortedSet().toList()
        require(letters.size <= 10)
        val firsts = words.map { it[0] }.toSet()
        val digit = IntArray(letters.size)
        val out = mutableListOf<List<Int>>()
        val index = words.map { w -> w.map { letters.indexOf(it) }.toIntArray() }
        fun number(w: IntArray): Long = w.fold(0L) { v, l -> v * 10 + digit[l] }
        fun go(i: Int, used: Int) {
            if (i == letters.size) {
                if (letters.indices.any { letters[it] in firsts && digit[it] == 0 }) return
                if (index.dropLast(1).sumOf { number(it) } == number(index.last())) out += digit.toList()
                return
            }
            for (d in 0..9) {
                if (used and (1 shl d) != 0) continue
                digit[i] = d
                go(i + 1, used or (1 shl d))
            }
        }
        go(0, 0)
        return out
    }
}
