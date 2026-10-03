package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Chess
import com.joebywan.daybook.puzzles.ChessPosition
import com.joebywan.daybook.puzzles.ChessRules
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * THE ORACLE: the first forcedMate (before the make/unmake + transposition-table rewrite), kept as it was, on the
 * public API only (play, legalMoves, isCheckmate), so it shares no search code with the engine. Slow on purpose.
 */
private object OldMate {
    private class S { val memo = HashMap<String, Boolean>() }

    private fun mateInOne(p: ChessPosition): Boolean = ChessRules.legalMoves(p).any { ChessRules.isCheckmate(ChessRules.play(p, it)) }

    private fun mates(p: ChessPosition, d: Int, s: S): Boolean {
        if (d == 1) return mateInOne(p)
        val key = p.toFen() + d
        s.memo[key]?.let { return it }
        val kids = ChessRules.legalMoves(p).map { ChessRules.play(p, it) }
        val res = kids.any { ChessRules.isCheckmate(it) } || kids.any { allReplies(it, d - 1, s) }
        s.memo[key] = res
        return res
    }

    private fun allReplies(p: ChessPosition, d: Int, s: S): Boolean {
        val replies = ChessRules.legalMoves(p)
        if (replies.isEmpty()) return false
        return replies.all { mates(ChessRules.play(p, it), d, s) }
    }

    /** (keys, shortest N) or null when there is no mate within n. */
    fun forcedMate(p: ChessPosition, n: Int): Pair<List<Int>, Int>? {
        val s = S()
        val moves = ChessRules.legalMoves(p)
        for (d in 1..n) {
            val keys = moves.filter { m ->
                val c = ChessRules.play(p, m)
                ChessRules.isCheckmate(c) || (d > 1 && allReplies(c, d - 1, s))
            }
            if (keys.isNotEmpty()) return keys to d
        }
        return null
    }
}

class ChessSearchBenchTest {
    private fun positions(d: Difficulty) =
        Chess.positionsFor(d).map { Chess.fromEntry(it, Chess.mateInFor(d)) }.map { ChessPosition.fromFen(it.start) to it.mateIn }

    private fun agree(d: Difficulty, count: Int) {
        val all = positions(d)
        val step = maxOf(1, all.size / count)
        for (i in all.indices step step) {
            val (p, n) = all[i]
            val old = OldMate.forcedMate(p, n)
            val new = ChessRules.forcedMate(p, n, Int.MAX_VALUE)
            val where = "${d.name} #$i ${p.toFen()}"
            if (old == null) assertEquals(where, ChessRules.Mate.None, new)
            else {
                val f = new as ChessRules.Mate.Forced
                assertEquals(where, old.first, f.keys)
                assertEquals(where, old.second, f.inMoves)
            }
            // one move fewer than the truth: no mate within n-1, same answer as the oracle
            if (n > 1) assertEquals(where, OldMate.forcedMate(p, n - 1) == null, ChessRules.forcedMate(p, n - 1, Int.MAX_VALUE) === ChessRules.Mate.None)
        }
    }

    @Test fun `new search agrees with the first implementation on shipped positions`() {
        val per = if (System.getenv("DAYBOOK_BENCH") != null) 320 else 60
        for (d in Difficulty.entries) agree(d, per)
    }

    @Test fun `bench forcedMate`() {
        assumeTrue(System.getenv("DAYBOOK_BENCH") != null)
        fun report(label: String, ms: List<Double>) {
            val s = ms.sorted()
            println("BENCH %-22s n=%3d median %7.1f p95 %7.1f max %7.1f".format(label, s.size, s[s.size / 2], s[(s.size * 95 / 100).coerceAtMost(s.size - 1)], s.last()))
        }
        for (d in Difficulty.entries.reversed()) {
            val ps = positions(d).take(400)
            val cold = ps.take(50).map { (p, n) ->
                val t = System.nanoTime(); ChessRules.forcedMate(p, n, Int.MAX_VALUE); (System.nanoTime() - t) / 1e6
            }
            report("${d.name} cold first50", cold)
            repeat(2) { ps.forEach { (p, n) -> ChessRules.forcedMate(p, n, Int.MAX_VALUE) } }
            report("${d.name} warm", ps.map { (p, n) ->
                val t = System.nanoTime(); ChessRules.forcedMate(p, n, Int.MAX_VALUE); (System.nanoTime() - t) / 1e6
            })
        }
    }
}
