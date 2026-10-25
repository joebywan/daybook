package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Sand
import com.joebywan.daybook.puzzles.SandLogic
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sand's tiers, measured as `LexiconBalanceTest` measures Lexicon's: two players over a year of daily boards per tier.
 * The rules here are this test's own (`SandSupport`), not the game's, so a bug in the game's pour cannot flatter both.
 *
 * - The **naive** player picks uniformly among the legal pours that are not the last pour poured straight back.
 * - The **careful** player prefers a pour that finishes a tube, then one onto a matching colour that uncovers a colour
 *   with somewhere to go, then any onto a matching colour, then any other (never a tube of one colour into an empty one).
 *
 * Each plays every board five times with its own seeds, [CAP] pours at most. Measured (2026, 365 days a tier), see
 * `build/reports/sand-balance.txt` and docs/PUZZLE_STANDARDS.md section 3.
 */
class SandBalanceTest {


    private fun play(t0: List<List<Int>>, h: Int, rng: Rng, careful: Boolean): Boolean {
        var t = t0
        var last: Pair<Int, Int>? = null
        repeat(CAP) {
            if (SandSupport.sorted(t, h)) return true
            val back = last
            var ps = SandSupport.pours(t, h).filter { back == null || !(it.first == back.second && it.second == back.first) }
            if (ps.isEmpty()) return false
            if (careful) {
                ps = ps.filter { (a, b) -> !(t[b].isEmpty() && t[a].all { it == t[a][0] }) }.ifEmpty { ps }
                fun after(m: Pair<Int, Int>) = SandSupport.pour(t, h, m.first, m.second)
                fun finishes(m: Pair<Int, Int>) = after(m)[m.second].let { it.size == h && it.all { c -> c == it[0] } }
                fun uncovers(m: Pair<Int, Int>): Boolean {
                    if (t[m.second].isEmpty()) return false
                    val a = after(m)
                    val src = a[m.first]
                    return src.isEmpty() || a.indices.any { i -> i != m.first && a[i].size < h && (a[i].isEmpty() || a[i].last() == src.last()) }
                }
                ps = listOf(ps.filter(::finishes), ps.filter(::uncovers), ps.filter { t[it.second].isNotEmpty() }, ps).first { it.isNotEmpty() }
            }
            val m = rng.pick(ps)
            t = SandSupport.pour(t, h, m.first, m.second)
            last = m
        }
        return SandSupport.sorted(t, h)
    }

    private class Tier(val naive: Double, val careful: Double, val nodes: Int)

    private fun measure(d: Difficulty): Tier {
        var naive = 0
        var careful = 0
        val nodes = mutableListOf<Int>()
        for (day in 0 until 365) {
            val seed = daySeed(day, Sand.id, d)
            val s = Sand.generateVerified(seed, d) ?: error("no board ${d.name} day $day")
            for (k in 0 until 5) {
                if (play(s.tubes, s.height, Rng(seed * 31 + k), careful = false)) naive++
                if (play(s.tubes, s.height, Rng(seed * 37 + k), careful = true)) careful++
            }
            nodes += SandLogic.search(s.tubes, s.height, SandLogic.GEN_BUDGET).second
        }
        nodes.sort()
        return Tier(naive / (5.0 * 365), careful / (5.0 * 365), nodes[nodes.size / 2])
    }

    @Test
    fun `the tiers get harder in the order they are offered`() {
        val tiers = Difficulty.entries.associateWith(::measure)
        val report = tiers.entries.joinToString("\n") { (d, t) ->
            val spec = SandLogic.specFor(d)
            "${d.name} ${spec.colours}x${spec.height}: naive wins ${"%.3f".format(t.naive)}, careful ${"%.3f".format(t.careful)}, solver positions median ${t.nodes}"
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/sand-balance.txt").writeText(report + "\n")
        val (s, h, e) = Difficulty.entries.map { tiers.getValue(it) }
        assertTrue("naive: $report", s.naive > h.naive + 0.1 && h.naive > e.naive + 0.1)
        assertTrue("careful: $report", s.careful > h.careful && h.careful > e.careful)
        assertTrue("solver: $report", s.nodes < h.nodes && h.nodes < e.nodes)
    }

    @Test
    fun `no tier varies the spare tubes`() {
        for (d in Difficulty.entries) {
            val spec = SandLogic.specFor(d)
            val s = Sand.generate(DailySeed.seedFor(TEST_START, Sand.id, d), d) as com.joebywan.daybook.puzzles.SandState
            assertTrue(s.tubes.size == spec.colours + 2 && s.tubes.count { it.isEmpty() } == 2)
        }
    }

    companion object {
        const val CAP = 300
    }
}
