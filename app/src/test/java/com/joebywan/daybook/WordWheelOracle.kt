package com.joebywan.daybook

import com.joebywan.daybook.puzzles.WheelWords
import com.joebywan.daybook.puzzles.WordWheelState

/**
 * Checks a Word Wheel board from its finished squares, on a different principle from the placer: the placer refuses a
 * square while laying a word (neighbours, ends, crossings); this writes every word into a plain grid and then reads the
 * grid back, run by run, as a crossword solver would. Every run of two or more letters across or down must be exactly one
 * of the board's words, every word must be such a run, the squares must be one connected piece, and every word must come
 * off the wheel.
 */
object WordWheelOracle {

    fun problems(s: WordWheelState): List<String> {
        val out = mutableListOf<String>()
        val grid = Array(s.height) { CharArray(s.width) { '.' } }
        for (slot in s.slots) {
            for (i in slot.word.indices) {
                val r = if (slot.across) slot.row else slot.row + i
                val c = if (slot.across) slot.col + i else slot.col
                if (r !in 0 until s.height || c !in 0 until s.width) {
                    out += "${slot.word} leaves the grid"
                    continue
                }
                if (grid[r][c] != '.' && grid[r][c] != slot.word[i]) out += "${slot.word} clashes at $r,$c"
                grid[r][c] = slot.word[i]
            }
        }
        // Read every run back.
        val runs = mutableListOf<String>()
        for (r in 0 until s.height) {
            var c = 0
            while (c < s.width) {
                if (grid[r][c] == '.') { c++; continue }
                val start = c
                while (c < s.width && grid[r][c] != '.') c++
                if (c - start >= 2) runs += "${String(grid[r], start, c - start)}@$r,${start}a"
            }
        }
        for (c in 0 until s.width) {
            var r = 0
            while (r < s.height) {
                if (grid[r][c] == '.') { r++; continue }
                val start = r
                val word = StringBuilder()
                while (r < s.height && grid[r][c] != '.') word.append(grid[r++][c])
                if (r - start >= 2) runs += "$word@$start,${c}d"
            }
        }
        val words = s.slots.map { "${it.word}@${it.row},${it.col}${if (it.across) "a" else "d"}" }
        if (runs.sorted() != words.sorted()) out += "runs ${runs.sorted()} are not the words ${words.sorted()}"

        // One piece.
        val filled = (0 until s.height).flatMap { r -> (0 until s.width).filter { grid[r][it] != '.' }.map { r to it } }
        if (filled.isNotEmpty()) {
            val seen = mutableSetOf(filled.first())
            val queue = ArrayDeque(listOf(filled.first()))
            while (queue.isNotEmpty()) {
                val (r, c) = queue.removeFirst()
                for ((dr, dc) in listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)) {
                    val n = r + dr to c + dc
                    if (n.first in 0 until s.height && n.second in 0 until s.width && grid[n.first][n.second] != '.' && seen.add(n)) queue += n
                }
            }
            if (seen.size != filled.size) out += "the grid is in pieces"
        }
        // Tight box.
        if ((0 until s.width).none { grid[0][it] != '.' } || (0 until s.width).none { grid[s.height - 1][it] != '.' } ||
            (0 until s.height).none { grid[it][0] != '.' } || (0 until s.height).none { grid[it][s.width - 1] != '.' }
        ) out += "the box is not tight"

        // Words: off the wheel, distinct, well known, and the first is the wheel's own.
        val wheelCount = s.wheel.groupingBy { it }.eachCount()
        for (slot in s.slots) {
            val need = slot.word.groupingBy { it }.eachCount()
            if (need.any { (ch, n) -> (wheelCount[ch] ?: 0) < n }) out += "${slot.word} is not on the wheel ${s.wheel}"
            if (slot.word.length < 3) out += "${slot.word} is too short"
            if (slot.word !in WheelWords.grid(slot.word.length)) out += "${slot.word} is not a grid word"
        }
        if (s.slots.map { it.word }.toSet().size != s.slots.size) out += "a word is used twice"
        if (s.slots.first().word.toList().sorted() != s.wheel.toList().sorted()) out += "slot 0 is not the wheel's own word"
        return out
    }
}
