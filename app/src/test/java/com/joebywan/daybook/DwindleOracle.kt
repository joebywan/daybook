package com.joebywan.daybook

import com.joebywan.daybook.puzzles.DwindleState

/**
 * Checks a Dwindle board from its letters and its goal words alone, on a different principle from the prover. The prover walks one
 * word at a time and leans on an argument that removal is monotone; this writes out every walk of tiles as plain text, reads the
 * goals' ways (and so the tiles' uses) off that table, and then visits **every subset of found goals**, all 2^n, asking whether each
 * goal not found can still be spelled on the tiles left. It never reads the board's stored ways except to compare them.
 */
object DwindleOracle {

    /** Every walk of 1 to [maxLength] tiles, as its text and its tiles: no word list, no pruning by letter. */
    private fun walks(s: DwindleState, maxLength: Int, visit: (String, List<Int>) -> Unit) {
        val rows = s.letters.chunked(s.width)
        fun go(r: Int, c: Int, path: List<Pair<Int, Int>>, text: String) {
            val here = path + (r to c)
            val spelled = text + rows[r][c]
            visit(spelled, here.map { (a, b) -> a * s.width + b })
            if (here.size == maxLength) return
            for (dr in -1..1) for (dc in -1..1) {
                if (dr != 0 && dc != 0 && !s.diagonal) continue
                val rr = r + dr
                val cc = c + dc
                if (rr in rows.indices && cc in 0 until s.width && (rr to cc) !in here) go(rr, cc, here, spelled)
            }
        }
        for (r in rows.indices) for (c in 0 until s.width) go(r, c, emptyList(), "")
    }

    /** What is wrong with [s], or nothing. Costs 2^goals: keep to the boards' real sizes. */
    fun problems(s: DwindleState): List<String> {
        val out = mutableListOf<String>()
        val words = s.goals.map { it.word }
        val ways = words.associateWith { mutableListOf<List<Int>>() }
        walks(s, words.maxOf { it.length }) { text, tiles -> ways[text]?.add(tiles) }
        for (w in words) if (ways.getValue(w).size != 1) out += "$w has ${ways.getValue(w).size} ways"
        if (out.isNotEmpty()) return out
        for (g in s.goals) if (ways.getValue(g.word)[0] != g.cells) out += "${g.word} is stored on tiles it does not spell"

        val tilesOf = words.map { ways.getValue(it)[0] }
        val uses = IntArray(s.letters.length)
        for (tiles in tilesOf) for (t in tiles) uses[t]++
        if (uses.any { it == 0 }) out += "a tile no goal uses"
        if (!uses.contentEquals(s.counts())) out += "the board's counts are not the table's"

        // Every position any order of finding can reach, the empty set included.
        for (mask in 0 until (1 shl words.size)) {
            val left = IntArray(uses.size)
            for (g in words.indices) if (mask shr g and 1 == 0) for (t in tilesOf[g]) left[t]++
            for (g in words.indices) if (mask shr g and 1 == 0) {
                val alive = ways.getValue(words[g]).any { way -> way.all { left[it] > 0 } }
                if (!alive) out += "after ${words.filterIndexed { i, _ -> mask shr i and 1 == 1 }}, ${words[g]} cannot be spelled"
            }
            if (mask == (1 shl words.size) - 1 && left.any { it != 0 }) out += "tiles left when every goal is found"
        }
        return out
    }
}
