package com.joebywan.daybook

import com.joebywan.daybook.core.LoopEdges
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.LoopLogic
import org.junit.Test

class LoopProbeTest {
    @Test
    fun tutorialCandidates() {
        val n = 4
        var shown = 0
        for (seed in 1L..4000L) {
            val rng = Rng(seed)
            val loop = LoopEdges.growLoop(rng, n + 1, 45, 70, 0) { 0 } ?: continue
            val all = LoopLogic.cluesOf(n, loop)
            val clues = LoopEdges.takeBack(rng, all, LoopEdges.LOCAL) { c, d -> LoopEdges.finishes(LoopLogic.Rules(n, c), d) } ?: continue
            if (clues[0] != '3' || '0' !in clues || clues.count { it != '.' } > 8) continue
            val v = LoopLogic.solutions(n, clues)
            if (v !is Verdict.ExactlyOne) continue
            println("seed $seed clues=$clues loop=$loop")
            println(render(n, clues, loop))
            if (++shown >= 8) break
        }
    }

    private fun render(n: Int, clues: String, loop: String): String {
        val m = n + 1
        val h = n * m
        val sb = StringBuilder()
        for (r in 0..n) {
            for (c in 0..n) {
                sb.append('+')
                if (c < n) sb.append(if (loop[r * n + c] == '1') "--" else "  ")
            }
            sb.append('\n')
            if (r < n) {
                for (c in 0..n) {
                    sb.append(if (loop[h + r * m + c] == '1') '|' else ' ')
                    if (c < n) sb.append(clues[r * n + c].let { if (it == '.') ' ' else it }).append(' ')
                }
                sb.append('\n')
            }
        }
        return sb.toString()
    }
}
