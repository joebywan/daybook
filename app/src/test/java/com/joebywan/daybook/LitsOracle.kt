package com.joebywan.daybook

/**
 * Every legal LITS answer of a board, found by exhaustive search, for the teaching tests.
 *
 * Written apart from `Lits` on purpose: its own idea of which letter a shape is (canonical offsets
 * under the eight symmetries, not a bounding-box reading), its own 2x2 and twin checks, and no
 * connectivity rule at all, because the win check has none (PR #15). No node budget either — an
 * answer list from here is complete, or the call never returns.
 */
internal object LitsOracle {

    private val shapes: Map<Char, List<Pair<Int, Int>>> = mapOf(
        'I' to listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
        'L' to listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1),
        'T' to listOf(0 to 0, 0 to 1, 0 to 2, 1 to 1),
        'S' to listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1),
    )

    private fun norm(ps: List<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val r = ps.minOf { it.first }
        val c = ps.minOf { it.second }
        return ps.map { (it.first - r) to (it.second - c) }.toSet()
    }

    private val forms: Map<Set<Pair<Int, Int>>, Char> = buildMap {
        for ((mark, base) in shapes) {
            var cur = base
            repeat(4) {
                cur = cur.map { (r, c) -> c to -r }
                put(norm(cur), mark)
                put(norm(cur.map { (r, c) -> r to -c }), mark)
            }
        }
    }

    fun letter(cells: Collection<Int>, w: Int): Char? = forms[norm(cells.map { it / w to it % w })]

    /** Every legal four-square shape inside [cells], with its letter. */
    fun shapesIn(cells: List<Int>, w: Int): List<Pair<List<Int>, Char>> {
        val out = mutableListOf<Pair<List<Int>, Char>>()
        for (a in cells.indices) for (b in a + 1 until cells.size)
            for (c in b + 1 until cells.size) for (d in c + 1 until cells.size) {
                val quad = listOf(cells[a], cells[b], cells[c], cells[d])
                val mark = letter(quad, w) ?: continue
                out += quad to mark
            }
        return out
    }

    private fun around(cell: Int, w: Int, h: Int) = buildList {
        val r = cell / w
        val c = cell % w
        if (r > 0) add(cell - w)
        if (r < h - 1) add(cell + w)
        if (c > 0) add(cell - 1)
        if (c < w - 1) add(cell + 1)
    }

    /**
     * Every answer whose shading includes [given], up to [cap]. Each is the set of shaded squares.
     */
    fun answers(w: Int, h: Int, region: List<Int>, given: Set<Int> = emptySet(), cap: Int = 1000): List<Set<Int>> {
        val n = w * h
        val options = region.distinct().sorted().map { id ->
            val cells = region.indices.filter { region[it] == id }
            val mustHave = cells.filter { it in given }
            shapesIn(cells, w).filter { it.first.containsAll(mustHave) }
        }.sortedBy { it.size }
        if (options.any { it.isEmpty() }) return emptyList()
        val shaded = BooleanArray(n)
        val mark = CharArray(n)
        val out = mutableListOf<Set<Int>>()

        fun blocks(quad: List<Int>): Boolean = quad.any { cell ->
            val r = cell / w
            val c = cell % w
            (-1..0).any { dr ->
                (-1..0).any { dc ->
                    val rr = r + dr
                    val cc = c + dc
                    rr >= 0 && cc >= 0 && rr + 1 < h && cc + 1 < w &&
                        shaded[rr * w + cc] && shaded[rr * w + cc + 1] &&
                        shaded[(rr + 1) * w + cc] && shaded[(rr + 1) * w + cc + 1]
                }
            }
        }

        fun twins(quad: List<Int>, m: Char) = quad.any { cell ->
            around(cell, w, h).any { it !in quad && shaded[it] && mark[it] == m }
        }

        fun walk(depth: Int) {
            if (out.size >= cap) return
            if (depth == options.size) {
                out += (0 until n).filter { shaded[it] }.toSet()
                return
            }
            for ((quad, m) in options[depth]) {
                quad.forEach { shaded[it] = true; mark[it] = m }
                if (!blocks(quad) && !twins(quad, m)) walk(depth + 1)
                quad.forEach { shaded[it] = false; mark[it] = ' ' }
                if (out.size >= cap) return
            }
        }
        walk(0)
        return out
    }
}
