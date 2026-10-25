package com.joebywan.daybook

import com.joebywan.daybook.core.Rng
import java.util.BitSet

/**
 * Dense crossword fills, offline. Nothing here knows about Codeword: a mini crossword can call the same two functions
 * with its own word list and size (`docs/CODEWORD_SPEC.md`, "The filler").
 *
 * - [pattern] draws a block pattern: a `size x size` grid of white (true) and black squares, symmetric under a
 *   half-turn, white squares connected, every across and down run of white squares [minRun] to [maxRun] long (so every
 *   white square is in an across word and a down word: fully checked). Blocks of black squares are allowed: scattering
 *   them (no 2x2 all black) interlocks the grid so tightly that almost no fill from these lists exists.
 * - [fill] fills a pattern from per-length word lists by backtracking: the slot the fewest words still fit goes first,
 *   each slot's words in an order shuffled once by the `Rng`, no word twice, giving up after `budget` nodes.
 *
 * Both are pure in their `Rng`, so a build that draws them in a fixed order writes the same grids every time.
 */
object CrosswordFill {

    /** Runs of white squares, across then down, each as its squares (`row * size + col`), of length 1 or more. */
    fun runs(white: BooleanArray, size: Int): List<IntArray> {
        val out = mutableListOf<IntArray>()
        for (across in listOf(true, false)) for (line in 0 until size) {
            var i = 0
            while (i < size) {
                fun sq(k: Int) = if (across) line * size + k else k * size + line
                if (!white[sq(i)]) { i++; continue }
                val start = i
                while (i < size && white[sq(i)]) i++
                out += IntArray(i - start) { sq(start + it) }
            }
        }
        return out
    }

    private fun connected(white: BooleanArray, size: Int): Boolean {
        val start = white.indexOfFirst { it }
        if (start < 0) return false
        val seen = BooleanArray(white.size)
        val stack = ArrayDeque(listOf(start))
        seen[start] = true
        var count = 1
        while (stack.isNotEmpty()) {
            val s = stack.removeLast()
            val r = s / size
            val c = s % size
            for ((dr, dc) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val a = r + dr
                val b = c + dc
                if (a !in 0 until size || b !in 0 until size) continue
                val t = a * size + b
                if (white[t] && !seen[t]) { seen[t] = true; count++; stack.addLast(t) }
            }
        }
        return count == white.count { it }
    }

    private fun badness(white: BooleanArray, size: Int, minRun: Int, maxRun: Int): Int {
        var bad = 0
        for (run in runs(white, size)) {
            if (run.size < minRun) bad += minRun - run.size + 2
            if (run.size > maxRun) bad += run.size - maxRun
        }
        return bad
    }

    /**
     * A pattern with about [blacks] black squares, by a random climb (toggle a square and its mirror, keep the change
     * unless it scores worse, now and then keep it anyway), or null when [tries] climbs of [steps] toggles find none.
     */
    fun pattern(rng: Rng, size: Int, minRun: Int, maxRun: Int, blacks: Int, tries: Int = 50, steps: Int = 3000): BooleanArray? {
        repeat(tries) {
            var white = BooleanArray(size * size) { true }
            fun score(w: BooleanArray): Int {
                val nb = w.count { !it }
                return badness(w, size, minRun, maxRun) * 10 + maxOf(0, blacks - nb) + maxOf(0, nb - blacks - 2)
            }
            var sc = score(white)
            repeat(steps) {
                if (sc == 0 && connected(white, size)) return white
                val sq = rng.nextInt(size * size)
                val next = white.copyOf()
                next[sq] = !next[sq]
                next[size * size - 1 - sq] = next[sq]
                val s2 = score(next)
                if (s2 <= sc || rng.nextInt(100) == 0) { white = next; sc = s2 }
            }
        }
        return null
    }

    /** Words of one length, indexed by (place, letter) for the filler. */
    class Index(val words: List<String>) {
        val length = words.firstOrNull()?.length ?: 0
        private val at: Array<Array<BitSet>> = Array(length) { Array(26) { BitSet(words.size) } }

        init {
            words.forEachIndexed { i, w -> w.forEachIndexed { p, ch -> at[p][ch - 'a'].set(i) } }
        }

        /** The words with [letters] (a letter or 0 per place) in their places, or null when no place is set. */
        fun matching(letters: CharArray): BitSet? {
            var out: BitSet? = null
            for (p in letters.indices) {
                if (letters[p] == 0.toChar()) continue
                val b = at[p][letters[p] - 'a']
                out = if (out == null) b.clone() as BitSet else out.apply { and(b) }
            }
            return out
        }
    }

    /**
     * [white] filled from [indexes] (one per word length), or null when the search found none within [budget] nodes.
     * The result is the letter of every square, `#` for black.
     */
    fun fill(rng: Rng, white: BooleanArray, size: Int, indexes: Map<Int, Index>, budget: Int): CharArray? {
        val slots = runs(white, size)
        if (slots.any { indexes[it.size] == null }) return null
        val order = indexes.mapValues { (_, ix) -> rng.shuffled(ix.words.indices.toList()).toIntArray() }
        val grid = CharArray(size * size) { if (white[it]) 0.toChar() else '#' }
        val done = BooleanArray(slots.size)
        val used = HashSet<String>() // probed only, never iterated
        var nodes = 0

        fun letters(slot: IntArray) = CharArray(slot.size) { grid[slot[it]] }

        fun search(): Boolean {
            if (++nodes > budget) return false
            var best = -1
            var bestSet: BitSet? = null
            var bestCount = Int.MAX_VALUE
            for (s in slots.indices) {
                if (done[s]) continue
                val ix = indexes.getValue(slots[s].size)
                val set = ix.matching(letters(slots[s]))
                val count = set?.cardinality() ?: ix.words.size
                if (count == 0) return false
                if (count < bestCount) { best = s; bestSet = set; bestCount = count }
            }
            if (best < 0) return true
            val slot = slots[best]
            val ix = indexes.getValue(slot.size)
            for (w in order.getValue(slot.size)) {
                if (bestSet != null && !bestSet.get(w)) continue
                val word = ix.words[w]
                if (word in used) continue
                val opened = slot.filter { grid[it] == 0.toChar() }
                slot.forEachIndexed { i, sq -> grid[sq] = word[i] }
                used += word
                done[best] = true
                if (search()) return true
                done[best] = false
                used -= word
                for (sq in opened) grid[sq] = 0.toChar()
                if (nodes > budget) return false
            }
            return false
        }
        return if (search()) grid else null
    }
}
