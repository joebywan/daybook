package com.joebywan.daybook

import com.joebywan.daybook.puzzles.WheelWords

/**
 * An independent count of a Codeword board's answers, for tests. It shares nothing with `CodewordLogic.solve`: no
 * shapes, no word index, no branching on words. It walks the alphabet instead, one number at a time in the order the
 * numbers first appear in reading order, trying every letter no other number holds, and checks each run with its own
 * plain reading of the squares: a run whose letters are all known must be in the list (a `Set`), and one partly known
 * must agree with some word of its length letter by letter. Slow, and fine for the boards tests sample.
 */
object CodewordOracle {

    private val words: Map<Int, Set<String>> by lazy { (3..7).associateWith { WheelWords.grid(it).toSet() } }

    /** Answers of the board, counted up to [limit]. [known] is a letter (0..25) or -1 per number (index number - 1). */
    fun count(size: Int, numbers: List<Int>, known: List<Int>, limit: Int = 2): Int {
        val lines = mutableListOf<List<Int>>()
        for (r in 0 until size) lines += splitWhite((0 until size).map { r * size + it }, numbers)
        for (c in 0 until size) lines += splitWhite((0 until size).map { it * size + c }, numbers)
        val runs = lines.filter { it.size >= 2 }
        val order = numbers.filter { it != 0 }.distinct().map { it - 1 }.filter { known[it] < 0 }
        val letter = known.toIntArray()
        var found = 0

        fun agrees(run: List<Int>): Boolean {
            val pattern = run.map { letter[numbers[it] - 1] }
            if (pattern.all { it >= 0 }) return String(CharArray(run.size) { 'a' + pattern[it] }) in words.getValue(run.size)
            return words.getValue(run.size).any { w -> pattern.indices.all { pattern[it] < 0 || w[it] - 'a' == pattern[it] } }
        }

        fun go(i: Int) {
            if (found >= limit) return
            if (i == order.size) {
                if (runs.all { agrees(it) }) found++
                return
            }
            val n = order[i]
            for (l in 0 until 26) {
                if (letter.contains(l)) continue
                letter[n] = l
                if (runs.filter { run -> run.any { numbers[it] - 1 == n } }.all { agrees(it) }) go(i + 1)
                letter[n] = -1
            }
        }
        if (runs.all { agrees(it) }) go(0)
        return found
    }

    private fun splitWhite(line: List<Int>, numbers: List<Int>): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        var cur = mutableListOf<Int>()
        for (sq in line) {
            if (numbers[sq] == 0) { out += cur; cur = mutableListOf() } else cur += sq
        }
        out += cur
        return out
    }
}
