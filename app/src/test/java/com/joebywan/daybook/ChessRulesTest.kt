package com.joebywan.daybook

import com.joebywan.daybook.puzzles.ChessPosition
import com.joebywan.daybook.puzzles.ChessRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessRulesTest {
    private fun pos(fen: String) = ChessPosition.fromFen(fen)

    // Published perft counts (chessprogramming.org "Perft Results"), hard-coded.
    private fun perft(p: ChessPosition, d: Int): Long {
        val ms = ChessRules.legalMoves(p)
        if (d == 1) return ms.size.toLong()
        return ms.sumOf { perft(ChessRules.play(p, it), d - 1) }
    }

    private fun check(fen: String, vararg counts: Long) {
        val p = pos(fen)
        counts.forEachIndexed { i, c -> assertEquals("perft ${i + 1} of $fen", c, perft(p, i + 1)) }
    }

    @Test fun perftStart() = check("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 20, 400, 8902, 197281)

    @Test fun perftKiwipete() =
        check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039, 97862)

    @Test fun perft3() = check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238)

    @Test fun perft4() = check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467)

    @Test fun perft4Mirrored() = check("r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1", 6, 264, 9467)

    @Test fun perft5() = check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486, 62379)

    @Test fun perft6() =
        check("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 46, 2079, 89890)

    @Test fun fenRoundTrip() {
        for (fen in listOf(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 b - -",
            "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6",
        )) assertEquals(fen, pos(fen).toFen())
        assertEquals("8/8/8/8/8/8/8/K6k w - -", pos("8/8/8/8/8/8/8/K6k w - - 12 40").toFen())
    }

    @Test fun malformedFenThrows() {
        for (bad in listOf("", "8/8/8 w - -", "8/8/8/8/8/8/8/K6k x - -", "9/8/8/8/8/8/8/8 w - -"))
            assertTrue(bad, runCatching { pos(bad) }.isFailure)
    }

    @Test fun moveOrderIsFromToPromo() {
        val ms = ChessRules.legalMoves(pos("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -")).map { ChessRules.uci(it) }
        assertEquals(listOf("b1a3", "b1c3", "g1f3", "g1h3"), ms.take(4))
        val promo = ChessRules.legalMoves(pos("8/P7/8/8/8/8/8/k6K w - -")).map { ChessRules.uci(it) }.filter { it.startsWith("a7") }
        assertEquals(listOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"), promo)
    }

    @Test fun castlingRules() {
        val p = pos("r3k2r/8/8/8/8/8/8/R3K2R w KQkq -")
        val ucis = ChessRules.legalMoves(p).map { ChessRules.uci(it) }
        assertTrue("e1g1" in ucis && "e1c1" in ucis)
        val after = ChessRules.play(p, ChessRules.parseUci(p, "e1g1")!!)
        assertEquals("r3k2r/8/8/8/8/8/8/R4RK1 b kq -", after.toFen())
        // cannot castle through an attacked square, nor out of check, nor with the right gone
        assertFalse("e1g1" in ChessRules.legalMoves(pos("r3k2r/8/8/8/8/8/5r2/R3K2R w KQkq -")).map { ChessRules.uci(it) })
        assertFalse("e1g1" in ChessRules.legalMoves(pos("4k3/8/8/8/8/8/4r3/R3K2R w KQ -")).map { ChessRules.uci(it) })
        assertFalse("e1g1" in ChessRules.legalMoves(pos("r3k2r/8/8/8/8/8/8/R3K2R w Qkq -")).map { ChessRules.uci(it) })
        // the b1 square may be attacked for queenside castling; d1 may not
        assertTrue("e1c1" in ChessRules.legalMoves(pos("1r2k3/8/8/8/8/8/8/R3K3 w Q -")).map { ChessRules.uci(it) })
        assertFalse("e1c1" in ChessRules.legalMoves(pos("3rk3/8/8/8/8/8/8/R3K3 w Q -")).map { ChessRules.uci(it) })
        // moving a rook, or capturing it, takes the right away
        val q = ChessRules.play(p, ChessRules.parseUci(p, "h1h2")!!)
        assertEquals("r3k2r/8/8/8/8/8/7R/R3K3 b Qkq -", q.toFen())
        val cap = pos("r3k2r/8/8/8/8/8/8/R3K2R b KQkq -")
        assertEquals("4k2r/8/8/8/8/8/8/r3K2R w Kk -", ChessRules.play(cap, ChessRules.parseUci(cap, "a8a1")!!).toFen())
    }

    @Test fun enPassant() {
        val p = pos("4k3/8/8/3pP3/8/8/8/4K3 w - d6")
        val m = ChessRules.parseUci(p, "e5d6")
        assertNotNull(m)
        assertEquals("4k3/8/3P4/8/8/8/8/4K3 b - -", ChessRules.play(p, m!!).toFen())
        assertEquals("exd6", ChessRules.san(p, m))
        // the ep square is only recorded when a pawn can take
        val d = pos("4k3/3p4/8/4P3/8/8/8/4K3 b - -")
        assertEquals("4k3/8/8/3pP3/8/8/8/4K3 w - d6", ChessRules.play(d, ChessRules.parseUci(d, "d7d5")!!).toFen())
        val e = pos("4k3/3p4/8/8/8/8/8/4K3 b - -")
        assertEquals("4k3/8/8/3p4/8/8/8/4K3 w - -", ChessRules.play(e, ChessRules.parseUci(e, "d7d5")!!).toFen())
        // en passant that exposes the king along the rank is illegal
        assertNull(ChessRules.parseUci(pos("8/8/8/KpP4r/8/8/8/7k w - b6"), "c5b6"))
    }

    @Test fun promotion() {
        val p = pos("1n2k3/P7/8/8/8/8/8/4K3 w - -")
        val ucis = ChessRules.legalMoves(p).map { ChessRules.uci(it) }
        for (s in listOf("a7a8q", "a7a8r", "a7a8b", "a7a8n", "a7b8q", "a7b8n")) assertTrue(s, s in ucis)
        val m = ChessRules.parseUci(p, "a7b8n")!!
        assertEquals("1N2k3/8/8/8/8/8/8/4K3 b - -", ChessRules.play(p, m).toFen())
        assertEquals("axb8=N", ChessRules.san(p, m))
        assertEquals("a8=Q", ChessRules.san(p, ChessRules.parseUci(p, "a7a8q")!!))
    }

    @Test fun pinnedPieceCannotLeaveTheLine() {
        val p = pos("4k3/4r3/8/8/8/8/4N3/4K3 w - -")
        assertTrue(ChessRules.legalMoves(p).none { ChessRules.uci(it).startsWith("e2") })
        val q = pos("4k3/8/8/8/1b6/8/3N4/4K3 w - -") // bishop pins the knight to the king along b4-e1
        assertTrue(ChessRules.legalMoves(q).none { ChessRules.uci(it).startsWith("d2") })
    }

    @Test fun mateAndStalemate() {
        val mate = pos("R5k1/5ppp/8/8/8/8/8/4K3 b - -")
        assertTrue(ChessRules.isCheckmate(mate))
        assertFalse(ChessRules.isStalemate(mate))
        val stale = pos("7k/5Q2/6K1/8/8/8/8/8 b - -")
        assertTrue(ChessRules.isStalemate(stale))
        assertFalse(ChessRules.isCheckmate(stale))
        assertFalse(ChessRules.inCheck(stale))
        assertTrue(ChessRules.inCheck(mate))
    }

    @Test fun sanSuffixesAndDisambiguation() {
        val back = pos("6k1/5ppp/8/8/8/8/8/R3K3 w - -")
        assertEquals("Ra8#", ChessRules.san(back, ChessRules.parseUci(back, "a1a8")!!))
        val two = pos("4k3/8/8/8/8/8/4K3/R6R w - -")
        assertEquals("Rad1", ChessRules.san(two, ChessRules.parseUci(two, "a1d1")!!))
        assertEquals("Rhf1", ChessRules.san(two, ChessRules.parseUci(two, "h1f1")!!))
        val cas = pos("4k3/8/8/8/8/8/8/R3K2R w KQ -")
        assertEquals("O-O", ChessRules.san(cas, ChessRules.parseUci(cas, "e1g1")!!))
        assertEquals("O-O-O", ChessRules.san(cas, ChessRules.parseUci(cas, "e1c1")!!))
        val rank = pos("4k3/R7/8/8/8/8/R7/4K3 w - -")
        assertEquals("R7a4", ChessRules.san(rank, ChessRules.parseUci(rank, "a7a4")!!))
        val three = pos("6k1/8/8/8/Q7/8/8/Q2QK3 w - -")
        assertEquals("Qa1d4", ChessRules.san(three, ChessRules.parseUci(three, "a1d4")!!))
        assertEquals("Q4d4", ChessRules.san(three, ChessRules.parseUci(three, "a4d4")!!))
        assertEquals("Qdd4", ChessRules.san(three, ChessRules.parseUci(three, "d1d4")!!))
        val fool = pos("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq -")
        assertEquals("Qh4#", ChessRules.san(fool, ChessRules.parseUci(fool, "d8h4")!!))
        assertEquals("Nf6", ChessRules.san(fool, ChessRules.parseUci(fool, "g8f6")!!))
    }

    @Test fun parseUciRejectsIllegal() {
        val p = pos("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -")
        assertNull(ChessRules.parseUci(p, "e2e5"))
        assertNotNull(ChessRules.parseUci(p, "e2e4"))
    }

    @Test fun attackersListsSquaresAscending() {
        val p = pos("4k3/8/8/3q4/8/2N1R3/8/4K2B w - -")
        assertEquals(listOf(7, 18), ChessRules.attackers(p, 35, true)) // h1 (bishop), c3 (knight)
        assertEquals(listOf(35), ChessRules.attackers(p, 3, false)) // the queen sees d1 down the open file
    }

    @Test fun forcedMateIsAProofOnlyWhenForced() {
        val back = pos("6k1/5ppp/8/8/8/8/8/R3K3 w - -")
        val r = ChessRules.forcedMate(back, 2)
        assertTrue(r is ChessRules.Mate.Forced)
        r as ChessRules.Mate.Forced
        assertEquals(1, r.inMoves)
        assertEquals(listOf("a1a8"), r.keys.map { ChessRules.uci(it) })
        assertTrue(ChessRules.forcedMate(pos("4k3/8/8/8/8/8/8/4K2R w - -"), 1) === ChessRules.Mate.None)
        assertTrue(ChessRules.forcedMate(pos("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -"), 2, 10) === ChessRules.Mate.Truncated)
    }

    @Test fun defencePolicyIsPinned() {
        // Saved games replay this. A reply that escapes mate is preferred, first in move order.
        val p = pos("7k/8/5K2/8/8/8/8/6Q1 b - -") // black is already in a lost corner; mate in 1 for white after any reply
        val r = ChessRules.defence(p, 1)
        assertNotNull(r)
        assertEquals("h8h7", ChessRules.uci(r!!)) // first legal reply (the only legal one)
        // an escaping reply exists: choose the first escaping one, not the first legal one
        val q = pos("6k1/R4ppp/8/8/8/8/8/R3K3 b - -")
        assertEquals("f7f5", ChessRules.uci(ChessRules.defence(q, 1)!!))
        // no legal move -> null
        assertNull(ChessRules.defence(pos("R5k1/5ppp/8/8/8/8/8/4K3 b - -"), 1))
        assertNull(ChessRules.defence(pos("7k/5Q2/6K1/8/8/8/8/8 b - -"), 1))
    }

    @Test fun defenceLosesSlowestWhenEveryReplyLoses() {
        // KQ v K: black's only replies all lose; the pick must be the longest resistance, ties by order
        val p = pos("k7/8/1K6/8/8/8/8/6Q1 b - -")
        val r = ChessRules.defence(p, 3)
        assertNotNull(r)
        assertTrue(r in ChessRules.legalMoves(p))
        // deterministic
        assertEquals(r, ChessRules.defence(p, 3))
    }
}
