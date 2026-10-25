package com.joebywan.daybook

import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.PearlsGrid
import com.joebywan.daybook.puzzles.PearlsLogic
import com.joebywan.daybook.puzzles.PearlsVerdict
import org.junit.Test

class PearlsProbeTest {
    @Test
    fun probe() {
        val n = 5
        var shown = 0
        for (seed in 1L..4000L) {
            val rng = Rng(seed)
            val loop = PearlsLogic.randomLoop(rng, n, 60, 90) ?: continue
            val all = PearlsLogic.implied(n, loop)
            val p = PearlsLogic.takeBack(rng, n, all, PearlsLogic.LOCAL) ?: continue
            if (PearlsLogic.depth(n, p) != PearlsLogic.LOCAL) continue
            val v = PearlsLogic.solutions(n, p)
            if (v !is PearlsVerdict.ExactlyOne) continue
            val pc = p.count { it != '.' }
            if (pc > 6 || p[0] != 'b' || p.count { it == 'w' } < 2) continue
            println("PROBE seed=$seed pearls=\"$p\" loop=\"$loop\" count=$pc")
            println(show(n, p, loop))
            if (++shown >= 8) break
        }
    }

    private fun show(n: Int, pearls: String, loop: String): String {
        val g = PearlsGrid.of(n)
        val sb = StringBuilder()
        for (r in 0 until n) {
            sb.append("PROBE ")
            for (c in 0 until n) {
                val s = r * n + c
                sb.append(if (pearls[s] == '.') 'o' else if (pearls[s] == 'b') '#' else '@')
                if (c < n - 1) sb.append(if (loop[g.edge(s, 1)] == '1') '-' else ' ')
            }
            sb.append('\n')
            if (r < n - 1) {
                sb.append("PROBE ")
                for (c in 0 until n) {
                    sb.append(if (loop[g.edge(r * n + c, 2)] == '1') '|' else ' ')
                    if (c < n - 1) sb.append(' ')
                }
                sb.append('\n')
            }
        }
        return sb.toString()
    }
}
