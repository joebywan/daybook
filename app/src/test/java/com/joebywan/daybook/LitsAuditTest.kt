package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Independent LITS rule check, deliberately sharing no code with the production one.
 *
 * Written after a report that same-letter pieces were allowed to touch. They are not: this walks
 * thousands of candidate shadings past both checkers and requires them to agree, so a gap in the
 * win condition cannot hide behind a generator that never produces one.
 */
class LitsAuditTest {

    private fun letterOf(cells: List<Int>, w: Int): String {
        val pts = cells.map { it / w to it % w }
        // Canonical form over all 8 rotations/reflections, so L==J and S==Z by construction.
        fun norm(ps: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val r0 = ps.minOf { it.first }; val c0 = ps.minOf { it.second }
            return ps.map { it.first - r0 to it.second - c0 }.sortedWith(compareBy({ it.first }, { it.second }))
        }
        val forms = mutableListOf<List<Pair<Int, Int>>>()
        var cur = pts
        repeat(4) {
            forms += norm(cur)
            forms += norm(cur.map { it.first to -it.second })
            cur = cur.map { it.second to -it.first }
        }
        val canon = forms.minWithOrNull(compareBy({ it.toString() }))!!
        val I = norm(listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3))
        val O = norm(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1))
        val T = norm(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 1))
        return when {
            canon == canonOf(I) -> "I"
            canon == canonOf(O) -> "O"
            canon == canonOf(T) -> "T"
            // L and S are what remain; separate them by whether any cell has 3 neighbours.
            else -> if (hasSpine(cells, w)) "L" else "S"
        }
    }

    private fun canonOf(ps: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val forms = mutableListOf<List<Pair<Int, Int>>>()
        var cur = ps
        fun norm(q: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val r0 = q.minOf { it.first }; val c0 = q.minOf { it.second }
            return q.map { it.first - r0 to it.second - c0 }.sortedWith(compareBy({ it.first }, { it.second }))
        }
        repeat(4) {
            forms += norm(cur)
            forms += norm(cur.map { it.first to -it.second })
            cur = cur.map { it.second to -it.first }
        }
        return forms.minWithOrNull(compareBy({ it.toString() }))!!
    }

    /** An L has a 3-in-a-line spine; an S does not. */
    private fun hasSpine(cells: List<Int>, w: Int): Boolean {
        val set = cells.toSet()
        return cells.any { c ->
            (set.contains(c - 1) && set.contains(c + 1)) || (set.contains(c - w) && set.contains(c + w))
        }
    }

    @Test
    fun `audit generated solutions for same-letter contact`() {
        for (d in Difficulty.entries) {
            var touching = 0
            var square = 0
            val n = 40
            for (i in 0 until n) {
                val seed = DailySeed.seedFor(LocalDate.of(2026, 2, 1).plusDays(i.toLong()), "lits", d)
                val s = Lits.generate(seed, d) as LitsState
                val w = s.width; val h = s.height
                val byRegion = s.region.indices.filter { s.solution[it] }.groupBy { s.region[it] }
                val letter = byRegion.mapValues { (_, cells) -> letterOf(cells, w) }
                // same letter touching across a region wall?
                outer@ for ((ra, ca) in byRegion) for ((rb, cb) in byRegion) {
                    if (ra >= rb || letter[ra] != letter[rb]) continue
                    for (a in ca) for (b in cb) {
                        val adj = (a / w == b / w && kotlin.math.abs(a % w - b % w) == 1) ||
                            (a % w == b % w && kotlin.math.abs(a / w - b / w) == 1)
                        if (adj) { touching++; break@outer }
                    }
                }
                for (r in 0 until h - 1) for (c in 0 until w - 1) {
                    if (s.solution[r * w + c] && s.solution[r * w + c + 1] &&
                        s.solution[(r + 1) * w + c] && s.solution[(r + 1) * w + c + 1]
                    ) { square++; break }
                }
            }
            assertEquals("${d.name}: generated solutions with same-letter pieces touching", 0, touching)
            assertEquals("${d.name}: generated solutions containing a shaded 2x2", 0, square)
        }
    }

    /** Every legal tetromino that fits inside a region. */
    private fun quads(cells: List<Int>, w: Int): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        val list = cells.sorted()
        for (a in list.indices) for (b in a + 1 until list.size)
            for (c in b + 1 until list.size) for (d in c + 1 until list.size) {
                val q = listOf(list[a], list[b], list[c], list[d])
                val set = q.toHashSet()
                val seen = HashSet<Int>(); val stack = ArrayDeque<Int>()
                stack.addLast(q[0]); seen += q[0]
                while (stack.isNotEmpty()) {
                    val x = stack.removeLast()
                    for (n in listOf(x - 1, x + 1, x - w, x + w)) {
                        if (n in set && (x % w != 0 || n != x - 1) && (x % w != w - 1 || n != x + 1) && seen.add(n)) stack.addLast(n)
                    }
                }
                if (seen.size != 4) continue
                val rows = q.map { it / w }; val cols = q.map { it % w }
                val rs = rows.max() - rows.min() + 1; val cs = cols.max() - cols.min() + 1
                if (rs == 2 && cs == 2) continue   // the O block
                out += q
            }
        return out
    }

    /** My own full rule check, sharing nothing with production. */
    private fun legal(s: LitsState): Boolean {
        val w = s.width; val h = s.height
        val byRegion = s.region.indices.filter { s.shaded[it] }.groupBy { s.region[it] }
        if (byRegion.size != s.region.distinct().size) return false
        for ((_, cells) in byRegion) {
            if (cells.size != 4) return false
            if (cells !in quads(s.region.indices.filter { s.region[it] == s.region[cells[0]] }, w)) return false
        }
        for (r in 0 until h - 1) for (c in 0 until w - 1) {
            if (s.shaded[r * w + c] && s.shaded[r * w + c + 1] &&
                s.shaded[(r + 1) * w + c] && s.shaded[(r + 1) * w + c + 1]) return false
        }
        val shaded = s.shaded.indices.filter { s.shaded[it] }
        if (shaded.isEmpty()) return false
        val seen = HashSet<Int>(); val stack = ArrayDeque<Int>()
        stack.addLast(shaded[0]); seen += shaded[0]
        while (stack.isNotEmpty()) {
            val x = stack.removeLast()
            val r = x / w; val c = x % w
            listOfNotNull(
                if (r > 0) x - w else null, if (r < h - 1) x + w else null,
                if (c > 0) x - 1 else null, if (c < w - 1) x + 1 else null,
            ).forEach { if (s.shaded[it] && seen.add(it)) stack.addLast(it) }
        }
        if (seen.size != shaded.size) return false
        val letter = byRegion.mapValues { (_, cells) -> letterOf(cells, w) }
        for ((ra, ca) in byRegion) for ((rb, cb) in byRegion) {
            if (ra >= rb || letter[ra] != letter[rb]) continue
            for (a in ca) for (b in cb) {
                val adj = (a / w == b / w && kotlin.math.abs(a % w - b % w) == 1) ||
                    (a % w == b % w && kotlin.math.abs(a / w - b / w) == 1)
                if (adj) return false
            }
        }
        return true
    }

    @Test
    fun `validator agrees with an independent checker on many candidate boards`() {
        var checked = 0
        var disagreed = 0
        val examples = mutableListOf<String>()
        for (d in Difficulty.entries) {
            for (i in 0 until 6) {
                val seed = DailySeed.seedFor(LocalDate.of(2026, 3, 1).plusDays(i.toLong()), "lits", d)
                val s = Lits.generate(seed, d) as LitsState
                val w = s.width
                val regions = s.region.distinct().sorted()
                val options = regions.map { r -> quads(s.region.indices.filter { s.region[it] == r }, w) }
                val rng = java.util.Random(seed)
                repeat(400) {
                    val pick = options.map { it[rng.nextInt(it.size)] }
                    if (pick.flatten().distinct().size != pick.flatten().size) return@repeat
                    val shaded = List(w * s.height) { false }.toMutableList()
                    pick.flatten().forEach { shaded[it] = true }
                    val cand = s.copy(shaded = shaded.toList())
                    val mine = legal(cand)
                    val theirs = cand.solved
                    checked++
                    if (mine != theirs) {
                        disagreed++
                        if (examples.size < 3) examples += "${d.name}/$i mine=$mine theirs=$theirs"
                    }
                }
            }
        }
        assertEquals(
            "solved disagreed with an independent rule check on $disagreed of $checked boards: $examples",
            0,
            disagreed,
        )
    }
}
