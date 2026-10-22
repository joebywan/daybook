package com.joebywan.daybook

import com.joebywan.daybook.puzzles.ChessPosition
import com.joebywan.daybook.puzzles.ChessRules

/**
 * An independent minimax for the Chess tests: shortest forced mate in attacker moves, every move tried, no
 * pruning, no budget, stalemate not mate. It shares nothing with [ChessRules.forcedMate] but the move generator.
 */
internal object ChessNaiveMate {
    /** Shortest mate length (attacker moves, at most [d]) when the attacker moves, or null. */
    fun attack(p: ChessPosition, d: Int): Int? {
        var best: Int? = null
        for (m in ChessRules.legalMoves(p)) {
            val v = afterAttack(ChessRules.play(p, m), d)
            if (v != null && (best == null || v < best)) best = v
        }
        return best
    }

    /** The defender is to move after an attacker move that already used one of the [d] moves. */
    fun afterAttack(c: ChessPosition, d: Int): Int? {
        val replies = ChessRules.legalMoves(c)
        if (replies.isEmpty()) return if (ChessRules.inCheck(c)) 1 else null // stalemate is not mate
        if (d <= 1) return null
        var worst = 0
        for (r in replies) worst = maxOf(worst, attack(ChessRules.play(c, r), d - 1) ?: return null)
        return worst + 1
    }

    class Naive(val inMoves: Int, val keys: List<String>)

    /** The shortest mate within [n] and every first move (as UCI) that gives it, or null when there is none. */
    fun naive(p: ChessPosition, n: Int): Naive? {
        val vals = ChessRules.legalMoves(p).map { it to afterAttack(ChessRules.play(p, it), n) }
        val shortest = vals.mapNotNull { it.second }.minOrNull() ?: return null
        return Naive(shortest, vals.filter { it.second == shortest }.map { ChessRules.uci(it.first) })
    }
}
