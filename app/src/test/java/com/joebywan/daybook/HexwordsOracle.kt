package com.joebywan.daybook

import com.joebywan.daybook.puzzles.WordWheelWords
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * An independent reading of a Hexwords board. Shares nothing with the generator's search: hexes are placed by their real
 * centres and touch by distance, words are found by walking every path against a set of prefixes (no letter bag, no word loop), points are
 * written out here, and the best score is a memo over bitsets of hexes with no bound and no budget.
 */
class HexwordsOracle(rows: List<Int>, private val letters: String, accepted: Boolean) {

    class Word(val word: String, val path: List<Int>)

    private val adjacent: List<List<Int>> = run {
        val centres = ArrayList<Pair<Double, Double>>()
        val widest = rows.max()
        rows.forEachIndexed { r, w -> repeat(w) { c -> centres += Pair((widest - w) / 2.0 + c, r * sqrt(3.0) / 2) } }
        // Touching hexes are one width apart, centre to centre; every other pair is at least sqrt(3) apart.
        centres.indices.map { a ->
            centres.indices.filter { b ->
                a != b && hypot(centres[a].first - centres[b].first, centres[a].second - centres[b].second) < 1.2
            }
        }
    }

    val words: Set<String> = buildSet {
        val all = listOf(WordWheelWords.GRID_3, WordWheelWords.GRID_4, WordWheelWords.GRID_5, WordWheelWords.GRID_6, WordWheelWords.GRID_7).flatten()
        addAll(all)
        if (accepted) addAll(WordWheelWords.BONUS.filter { it.length in 3..7 })
    }
    private val prefixes: Set<String> = buildSet { for (w in words) for (k in 1..w.length) add(w.take(k)) }

    fun neighbours(hex: Int): List<Int> = adjacent[hex]

    /** Every traced word. */
    val found: List<Word> = buildList {
        fun walk(path: List<Int>, text: String) {
            if (text !in prefixes) return
            if (text in words) add(Word(text, path))
            for (b in adjacent[path.last()]) if (b !in path) walk(path + b, text + letters[b])
        }
        for (h in letters.indices) walk(listOf(h), letters[h].toString())
    }

    private val masks = found.map { w -> w.path.fold(0L) { m, h -> m or (1L shl h) } }
    private val values = found.map { worth(it.word.length) }
    private val memo = HashMap<Long, Int>()

    /** The most the hexes of [free] can score. */
    fun best(free: Long): Int {
        if (free == 0L) return 0
        memo[free]?.let { return it }
        // The lowest hex is on some word or on none. No bound: every position below is worked out in full.
        val low = free and -free
        var top = best(free xor low)
        for (i in masks.indices) if (masks[i] and low != 0L && masks[i] and free == masks[i]) top = maxOf(top, values[i] + best(free xor masks[i]))
        memo[free] = top
        return top
    }

    companion object {
        fun worth(length: Int): Int = listOf(3 to 3, 4 to 5, 5 to 8, 6 to 12, 7 to 17).first { it.first == length }.second

        /** Plays out [taken] by the rules: every word real, its path touching, nothing reused. Returns the score. */
        fun score(o: HexwordsOracle, letters: String, taken: List<Pair<String, List<Int>>>): Int {
            val used = HashSet<Int>()
            var total = 0
            for ((word, path) in taken) {
                check(word in o.words) { "$word is no word" }
                check(path.map { letters[it] }.joinToString("") == word) { "$path does not spell $word" }
                for (k in 1 until path.size) check(path[k] in o.neighbours(path[k - 1])) { "$word has a gap" }
                for (h in path) check(used.add(h)) { "$word reuses a hex" }
                total += worth(word.length)
            }
            return total
        }
    }
}
