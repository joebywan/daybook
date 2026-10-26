package com.joebywan.daybook

import com.joebywan.daybook.puzzles.WheelWords

/**
 * An independent reading of the Mini crossword, sharing nothing with `MiniLogic` or `MiniTeacher`: runs are found by
 * splitting rows and columns on `#`, and fills are counted square by square in reading order over the alphabet, each
 * letter kept only while the across and down runs through it still start some word of their length. No slot list, no
 * word index, no "fewest words first".
 */
object MiniOracle {

    /** Every across and down run of white squares (`#` black), as text, rows first. */
    fun runs(size: Int, grid: String): List<String> {
        val rows = (0 until size).map { r -> grid.substring(r * size, r * size + size) }
        val cols = (0 until size).map { c -> String(CharArray(size) { grid[it * size + c] }) }
        return (rows + cols).flatMap { it.split('#') }.filter { it.isNotEmpty() }
    }

    private val prefixes: Map<Int, Set<String>> by lazy {
        (3..7).associateWith { n -> WheelWords.grid(n).flatMap { w -> (1..n).map { w.substring(0, it) } }.toSet() }
    }

    /**
     * How many fills of [letters]' open squares (`' '`) make every run a grid word, counted to [limit]. Runs of one
     * square are not words and are skipped; a run of two cannot be a word (the lists start at three) and kills the fill.
     */
    fun count(size: Int, letters: String, limit: Int = 2): Int {
        val g = letters.toCharArray()
        var found = 0
        fun runAt(sq: Int, across: Boolean): IntRange? {
            val r = sq / size
            val c = sq % size
            fun at(k: Int) = if (across) r * size + k else k * size + c
            val p = if (across) c else r
            var a = p
            while (a > 0 && g[at(a - 1)] != '#') a--
            var b = p
            while (b < size - 1 && g[at(b + 1)] != '#') b++
            return if (b > a) a..b else null
        }
        fun ok(sq: Int): Boolean {
            for (across in listOf(true, false)) {
                val range = runAt(sq, across) ?: continue
                val r = sq / size
                val c = sq % size
                fun at(k: Int) = if (across) r * size + k else k * size + c
                val word = range.map { g[at(it)] }
                val upTo = word.indexOf(' ').let { if (it < 0) word.size else it }
                val set = prefixes[word.size] ?: return false
                if (upTo > 0 && String(word.subList(0, upTo).toCharArray()) !in set) return false
                if (upTo == word.size && !WheelWords.isGridWord(String(word.toCharArray()))) return false
            }
            return true
        }
        val open = g.indices.filter { g[it] == ' ' }
        if (g.indices.any { g[it] != '#' && g[it] != ' ' && !ok(it) }) return 0
        fun dfs(k: Int) {
            if (found >= limit) return
            if (k == open.size) {
                if (runs(size, g.concatToString()).all { WheelWords.isGridWord(it) }) found++
                return
            }
            val sq = open[k]
            for (ch in 'a'..'z') {
                g[sq] = ch
                if (ok(sq)) dfs(k + 1)
                g[sq] = ' '
                if (found >= limit) return
            }
        }
        dfs(0)
        return found
    }
}
