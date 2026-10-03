package com.joebywan.daybook

import com.joebywan.daybook.puzzles.ChessPosition
import com.joebywan.daybook.puzzles.ChessRules
import com.joebywan.daybook.puzzles.ChessState
import com.joebywan.daybook.puzzles.ChessTutorial
import com.joebywan.daybook.puzzles.ChessTutorial.sq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Mate walkthrough: gestures, captions, and an independent proof (its own plain minimax, nothing from
 * `forcedMate`) that every walkthrough position has one forced mate, every defence to it is mated, and a walk
 * that always plays the proved key finishes without a mistake.
 */
class ChessTutorialTest {
    private val frames = ChessTutorial.frames
    private fun pos(fen: String) = ChessPosition.fromFen(fen)
    private fun st(i: Int) = frames[i].state as ChessState
    private fun ChessState.play(uci: String) = copy(played = played + uci, moves = moves + 1)
    private fun uci(p: ChessPosition, san: String) =
        ChessRules.legalMoves(p).first { ChessRules.san(p, it) == san }.let(ChessRules::uci)

    // ---- independent minimax (shortest forced mate, attacker moves; stalemate is not mate) ----

    private fun attack(p: ChessPosition, d: Int): Int? {
        var best: Int? = null
        for (m in ChessRules.legalMoves(p)) {
            val v = afterAttack(ChessRules.play(p, m), d)
            if (v != null && (best == null || v < best)) best = v
        }
        return best
    }

    private fun afterAttack(c: ChessPosition, d: Int): Int? {
        val replies = ChessRules.legalMoves(c)
        if (replies.isEmpty()) return if (ChessRules.inCheck(c)) 1 else null
        if (d <= 1) return null
        var worst = 0
        for (r in replies) worst = maxOf(worst, attack(ChessRules.play(c, r), d - 1) ?: return null)
        return worst + 1
    }

    /** Every first move (as UCI) that mates in exactly [n], or empty when [n] is not the shortest. */
    private fun keys(p: ChessPosition, n: Int): List<String> {
        val v = ChessRules.legalMoves(p).map { it to afterAttack(ChessRules.play(p, it), n) }
        if (v.mapNotNull { it.second }.minOrNull() != n) return emptyList()
        return v.filter { it.second == n }.map { ChessRules.uci(it.first) }
    }

    private val positions = listOf(
        ChessTutorial.BACK_RANK to 1, ChessTutorial.LADDER to 2, ChessTutorial.PROMOTION to 1, ChessTutorial.FREE to 2,
    )

    @Test fun `the walkthrough has nine frames and the explanatory ones take no move`() {
        assertEquals(9, frames.size)
        listOf(0, 2, 3, 4).forEach { assertNull("frame ${it + 1} is Next-only", frames[it].accepts) }
        listOf(1, 5, 6, 7).forEach { assertTrue("frame ${it + 1} takes a move", frames[it].accepts != null) }
        assertTrue(frames.last().freePlay)
        assertEquals(1, frames.count { it.freePlay })
        frames.forEach { assertTrue("caption too long: ${it.caption}", it.caption.length <= 170) }
        frames.forEach { assertTrue("done too long: ${it.done}", it.done.length <= 170) }
        frames.forEach { assertTrue("retry too long: ${it.retry}", it.retry.length <= 170) }
    }

    @Test fun `each walkthrough frame accepts its move, made by the real gesture, and rejects a near miss`() {
        // 2: a1 -> a8 is one state with one move.
        val rook = frames[1].accepts!!
        assertTrue(rook(st(1).play("a1a8")))
        assertFalse("another rook move", rook(st(1).play("a1a7")))
        assertFalse("a king move", rook(st(1).play("e1e2")))
        assertFalse("no move", rook(st(1)))
        assertFalse("a different position", rook((st(5)).play("a1a8")))
        assertEquals("frame 3 continues from frame 2's move", st(2), st(1).play("a1a8"))

        // 6: the king move that is the key; the non-key rook move is refused.
        val king = frames[5].accepts!!
        assertTrue(king(st(5).play("a5b6")))
        assertFalse("the tempting Rd7", king(st(5).play("d1d7")))
        assertFalse("a king move elsewhere", king(st(5).play("a5b5")))
        assertFalse("a different king move", king(st(5).play("a5a6")))
        assertEquals("frame 7 continues from frame 6's move", st(6), st(5).play("a5b6"))

        // 7: the rook mate, after the derived reply (the state holds only the player's moves).
        val mate = frames[6].accepts!!
        assertTrue(mate(st(6).play("d1d8")))
        assertFalse("rook to d7", mate(st(6).play("d1d7")))
        assertFalse("rook to the wrong square on the file", mate(st(6).play("d1d6")))
        assertFalse("not the rook", mate(st(6).play("b6a6")))
        assertFalse("only one move played", mate(st(6)))

        // 8: promotion is one state, and only the queen.
        val promo = frames[7].accepts!!
        assertTrue(promo(st(7).play("h7h8q")))
        assertFalse("rook", promo(st(7).play("h7h8r")))
        assertFalse("knight", promo(st(7).play("h7h8n")))
        assertFalse("king step", promo(st(7).play("f5f6")))
    }

    @Test fun `each frame's board is the previous frame's result, except where a new position starts or Undo shows`() {
        // Same position continues: 1->2 (start), 2->3 (the move), 4->6 (Undo returns to the start), 6->7, 7->8 differ.
        assertEquals(st(0), st(1))
        assertEquals(ChessTutorial.LADDER, st(3).start)
        assertEquals("the mistake is one move from the same start", st(3).copy(played = listOf("d1d7"), moves = 1), st(4))
        assertEquals("Undo goes back to the start", st(3), st(5))
        assertEquals(st(5).play("a5b6"), st(6))
        assertEquals(ChessTutorial.PROMOTION, st(7).start)
        assertEquals(ChessTutorial.FREE, st(8).start)
        assertTrue(st(8).played.isEmpty())
        // Every state's move list is playable from its start.
        frames.forEach { f ->
            val s = f.state as ChessState
            var p = pos(s.start)
            s.played.forEachIndexed { i, u ->
                p = ChessRules.play(p, ChessRules.parseUci(p, u)!!)
                val remaining = s.mateIn - (i + 1)
                if (remaining > 0 && !ChessRules.isCheckmate(p)) p = ChessRules.play(p, ChessRules.defence(p, remaining)!!)
            }
        }
    }

    @Test fun `every walkthrough position has exactly one forced mate, proved independently`() {
        val expected = mapOf(
            ChessTutorial.BACK_RANK to listOf("a1a8"), ChessTutorial.LADDER to listOf("a5b6"),
            ChessTutorial.PROMOTION to listOf("h7h8q"),
        )
        positions.forEach { (fen, n) ->
            val p = pos(fen)
            assertTrue("$fen: white to move and black not in check", p.whiteToMove)
            val k = keys(p, n)
            assertEquals("$fen: one key, mate in exactly $n", 1, k.size)
            expected[fen]?.let { assertEquals(fen, it, k) }
            assertEquals(fen, k, (ChessRules.forcedMate(p, n) as ChessRules.Mate.Forced).keys.map(ChessRules::uci))
        }
    }

    @Test fun `after each key every defender reply is mated, exhaustively`() {
        positions.forEach { (fen, n) ->
            val p = pos(fen)
            val key = keys(p, n).single()
            val c = ChessRules.play(p, ChessRules.parseUci(p, key)!!)
            if (n == 1) { assertTrue(fen, ChessRules.isCheckmate(c)); return@forEach }
            val replies = ChessRules.legalMoves(c)
            assertTrue("$fen: the defender has a reply (not stalemate)", replies.isNotEmpty())
            replies.forEach { r ->
                val c2 = ChessRules.play(c, r)
                assertEquals("$fen after $key, reply ${ChessRules.uci(r)}", 1, attack(c2, n - 1))
            }
        }
    }

    @Test fun `every caption claim is true of its board`() {
        val back = pos(ChessTutorial.BACK_RANK)
        // Frames 1-3.
        assertEquals(-6, back.pieceAt(sq("g8")))
        listOf("f7", "g7", "h7").forEach { assertEquals(-1, back.pieceAt(sq(it))) }
        assertEquals(4, back.pieceAt(sq("a1")))
        assertEquals("Ra8#", ChessRules.san(back, ChessRules.parseUci(back, "a1a8")!!))
        val mated = ChessRules.play(back, ChessRules.parseUci(back, "a1a8")!!)
        assertTrue(ChessRules.isCheckmate(mated))
        assertTrue("f8 is attacked, and h8 is behind the king on the same rank", ChessRules.attackers(mated, sq("f8"), true).isNotEmpty() && sq("h8") / 8 == sq("a8") / 8)

        // Frame 4: black's king on a8 has a7, b7, b8 and white's king on a5 guards none of them.
        val ladder = pos(ChessTutorial.LADDER)
        assertEquals(-6, ladder.pieceAt(sq("a8")))
        assertEquals(6, ladder.pieceAt(sq("a5")))
        listOf("a7", "b7", "b8").forEach { assertTrue(it, ChessRules.attackers(ladder, sq(it), true).isEmpty()) }

        // Frame 5: Rd7 forces Kb8 and then there is no forced mate in 1.
        val rd7 = ChessRules.play(ladder, ChessRules.parseUci(ladder, "d1d7")!!)
        assertEquals("Rd7", ChessRules.san(ladder, ChessRules.parseUci(ladder, "d1d7")!!))
        assertEquals(listOf("a8b8"), ChessRules.legalMoves(rd7).map(ChessRules::uci))
        val afterKb8 = ChessRules.play(rd7, ChessRules.parseUci(rd7, "a8b8")!!)
        assertNull("no forced mate left", attack(afterKb8, 1))

        // Frame 6: Kb6 guards a7 and b7 and leaves exactly Kb8.
        val kb6 = ChessRules.play(ladder, ChessRules.parseUci(ladder, "a5b6")!!)
        assertEquals("Kb6", ChessRules.san(ladder, ChessRules.parseUci(ladder, "a5b6")!!))
        assertTrue(listOf("a7", "b7").all { ChessRules.attackers(kb6, sq(it), true).isNotEmpty() })
        assertEquals(listOf("a8b8"), ChessRules.legalMoves(kb6).map(ChessRules::uci))

        // Frame 7: after Kb8, Rd8# with a7 b7 c7 held by the king and a8 c8 by the rook.
        val kb8 = ChessRules.play(kb6, ChessRules.parseUci(kb6, "a8b8")!!)
        assertEquals("Rd8#", ChessRules.san(kb8, ChessRules.parseUci(kb8, "d1d8")!!))
        val rd8 = ChessRules.play(kb8, ChessRules.parseUci(kb8, "d1d8")!!)
        assertTrue(ChessRules.isCheckmate(rd8))
        assertTrue(listOf("a7", "b7", "c7", "c8").all { ChessRules.attackers(rd8, sq(it), true).isNotEmpty() })
        assertEquals("a8 is behind the king on the rook's rank", sq("d8") / 8, sq("a8") / 8)

        // Frame 8: h8=R+ is legal, is not mate, and the king escapes to g7; the queen mates.
        val pr = pos(ChessTutorial.PROMOTION)
        assertEquals(1, pr.pieceAt(sq("h7")))
        assertEquals("h8=Q#", ChessRules.san(pr, ChessRules.parseUci(pr, "h7h8q")!!))
        val asRook = ChessRules.play(pr, ChessRules.parseUci(pr, "h7h8r")!!)
        assertFalse(ChessRules.isCheckmate(asRook))
        assertTrue("Kg7 escapes the rook", ChessRules.legalMoves(asRook).map(ChessRules::uci).contains("h6g7"))
        val asQueen = ChessRules.play(pr, ChessRules.parseUci(pr, "h7h8q")!!)
        assertTrue(listOf("g7", "h7", "g5", "g6").all { ChessRules.attackers(asQueen, sq(it), true).isNotEmpty() })
        assertEquals(105, frames[7].highlight.soft.single { it >= 100 })
        // Highlight squares are on the board.
        frames.forEach { f -> (f.highlight.strong + f.highlight.soft).forEach { assertTrue(it in 0..63 || it == 105) } }
    }

    @Test fun `the last frame is free play and playing the proved keys finishes it without a mistake`() {
        assertTrue(frames.last().freePlay)
        var s = st(8)
        // Stand-in for the hints until the teaching API exists: the independent checker's key, every turn.
        var p = pos(s.start)
        while (!ChessRules.isCheckmate(p)) {
            val remaining = s.mateIn - s.played.size
            assertTrue("a mate remains on the way", remaining >= 1)
            val key = keys(p, attack(p, remaining)!!).first()
            s = s.play(key)
            p = ChessRules.play(p, ChessRules.parseUci(p, key)!!)
            if (!ChessRules.isCheckmate(p) && s.mateIn - s.played.size > 0) {
                p = ChessRules.play(p, ChessRules.defence(p, s.mateIn - s.played.size)!!)
            }
        }
        assertEquals(2, s.played.size)
        assertTrue(ChessRules.isCheckmate(p))
    }

    @Test fun `the opponent's reply in the walkthrough is the derived one`() {
        val p = ChessRules.play(pos(ChessTutorial.LADDER), ChessRules.parseUci(pos(ChessTutorial.LADDER), "a5b6")!!)
        assertEquals("a8b8", ChessRules.uci(ChessRules.defence(p, 1)!!))
        assertEquals(uci(pos(ChessTutorial.LADDER), "Kb6"), "a5b6")
    }
}
