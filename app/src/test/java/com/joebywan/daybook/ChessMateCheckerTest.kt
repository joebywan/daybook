package com.joebywan.daybook

import com.joebywan.daybook.puzzles.ChessPosition
import com.joebywan.daybook.puzzles.ChessRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An independent mate-in-N checker: plain full-width minimax. No move ordering, no "only checks can mate" shortcut,
 * no early exit, no node budget; it computes the exact shortest forced mate (in the attacker's moves) of every
 * first move and takes the minimum. It shares only the perft-verified move generator with the prover, so the
 * two cannot share a blind spot in the search (ordering, pruning, depth bookkeeping, key lists).
 */
class ChessMateCheckerTest {
    private fun pos(fen: String) = ChessPosition.fromFen(fen)

    /** Shortest mate length (attacker moves, at most [d]) when the attacker moves, or null. */
    private fun attack(p: ChessPosition, d: Int): Int? {
        var best: Int? = null
        for (m in ChessRules.legalMoves(p)) {
            val v = afterAttack(ChessRules.play(p, m), d)
            if (v != null && (best == null || v < best)) best = v
        }
        return best
    }

    /** The defender is to move after an attacker move that already used one of the [d] moves. */
    private fun afterAttack(c: ChessPosition, d: Int): Int? {
        val replies = ChessRules.legalMoves(c)
        if (replies.isEmpty()) return if (ChessRules.inCheck(c)) 1 else null // stalemate is not mate
        if (d <= 1) return null
        var worst: Int? = 0
        for (r in replies) {
            val v = attack(ChessRules.play(c, r), d - 1)
            worst = if (v == null || worst == null) null else maxOf(worst, v)
        }
        return worst?.plus(1)
    }

    private class Naive(val inMoves: Int, val keys: List<String>)

    private fun naive(p: ChessPosition, n: Int): Naive? {
        val vals = ChessRules.legalMoves(p).map { it to afterAttack(ChessRules.play(p, it), n) }
        val shortest = vals.mapNotNull { it.second }.minOrNull() ?: return null
        return Naive(shortest, vals.filter { it.second == shortest }.map { ChessRules.uci(it.first) })
    }

    private fun agree(fen: String, n: Int, expectN: Int?) {
        val p = pos(fen)
        val mine = ChessRules.forcedMate(p, n)
        val ref = naive(p, n)
        if (ref == null) {
            assertTrue("$fen: naive finds no mate within $n, prover said $mine", mine === ChessRules.Mate.None)
            assertEquals(fen, null, expectN)
        } else {
            assertTrue("$fen: prover said $mine, naive says mate in ${ref.inMoves}", mine is ChessRules.Mate.Forced)
            mine as ChessRules.Mate.Forced
            assertEquals(fen, ref.inMoves, mine.inMoves)
            assertEquals(fen, ref.keys, mine.keys.map { ChessRules.uci(it) })
            if (expectN != null) assertEquals(fen, expectN, mine.inMoves)
        }
    }

    @Test fun matesInOne() {
        agree("6k1/5ppp/8/8/8/8/8/R3K3 w - -", 2, 1)
        agree("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq -", 2, 1)
        agree("6rk/6pp/8/6N1/8/8/8/K7 w - -", 2, 1) // Nf7# smothered
        agree("7k/8/5K2/8/8/8/8/6Q1 w - -", 2, 1)
    }

    @Test fun matesInTwo() {
        agree("r2qkbnr/ppp2ppp/2np4/4N3/2B1P3/2N5/PPPP1PPP/R1BbK2R w KQkq -", 2, 2) // Legal's mate pattern
        agree("k7/8/2K5/8/8/8/8/7R w - -", 2, 2) // two quiet keys, Kb6 and Kc7
        agree("7k/8/8/8/8/8/5Q2/K7 w - -", 2, null) // no mate in two
        agree("r5k1/5ppp/8/8/8/8/5PPP/1R2R1K1 w - -", 2, null) // Rxa8+ is not mate: Rxa8 answers
    }

    @Test fun noMateWithinTheMovesGiven() {
        agree("4k3/8/8/8/8/8/8/4K2R w - -", 1, null)
        agree("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -", 1, null)
        agree("k7/8/8/8/8/8/8/K6R w - -", 2, null)
    }

    // Slow and so thinned as N grows: three small boards at N = 3, one at N = 4 (found by search, each checked here).
    @Test fun matesInThree() {
        agree("k7/8/3K4/p1Rb4/8/8/8/8 w - -", 3, 3)
        agree("8/5k2/3K2R1/8/8/8/2B5/8 w - -", 3, 3) // five keys
        agree("8/8/4k3/8/2R3K1/3Q4/8/8 w - -", 3, 3)
        agree("k7/8/8/8/8/8/8/K5Q1 w - -", 3, null) // nothing forces it in three
    }

    @Test fun matesInFour() {
        agree("8/1k6/8/8/2K5/2R5/8/8 w - -", 4, 4)
    }

    @Test fun stalemateIsNeverTheAnswer() {
        // the only way to "mate" here is Qc7?? which stalemates; the prover must not count it
        val p = pos("k7/8/1K6/8/8/8/8/2Q5 w - -")
        val r = ChessRules.forcedMate(p, 1)
        val ref = naive(p, 1)
        assertEquals(ref == null, r === ChessRules.Mate.None)
        if (r is ChessRules.Mate.Forced) assertTrue(r.keys.all { ChessRules.isCheckmate(ChessRules.play(p, it)) })
    }
}
