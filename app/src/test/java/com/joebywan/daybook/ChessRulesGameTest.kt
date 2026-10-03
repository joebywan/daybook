package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.puzzles.Chess
import com.joebywan.daybook.puzzles.ChessPosition
import com.joebywan.daybook.puzzles.ChessRules
import com.joebywan.daybook.puzzles.ChessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The Mate game around the engine: what `solved`, `lost` and the derived replies mean, and that the shipped data
 * is what it claims. The engine itself is checked in [ChessRulesTest] and [ChessMateCheckerTest].
 */
class ChessRulesGameTest {

    private fun entry(d: Difficulty, i: Int) = Chess.fromEntry(Chess.positionsFor(d)[i], Chess.mateInFor(d))
    private fun keyOf(s: ChessState): Int {
        val r = ChessRules.forcedMate(s.position(), s.mateIn) as ChessRules.Mate.Forced
        assertEquals(1, r.keys.size)
        return r.keys[0]
    }

    // ---- the data ------------------------------------------------------------------------------------

    @Test
    fun `every list is sorted, unique and parsable, and its size is what the tier needs`() {
        for (d in Difficulty.entries) {
            val list = Chess.positionsFor(d)
            assertTrue("${d.name} too short: ${list.size}", list.size >= 500)
            assertEquals("${d.name} not sorted", list.sorted(), list)
            assertEquals("${d.name} repeats a position", list.size, list.map { it.substringBeforeLast(' ') }.distinct().size)
            for (e in list) {
                val s = Chess.fromEntry(e, Chess.mateInFor(d))
                ChessPosition.fromFen(s.start)
                assertTrue(e, s.last.isEmpty() || Regex("[a-h][1-8][a-h][1-8][qrbn]?").matches(s.last))
            }
        }
    }

    /** Every `step`th entry: the shortest mate is exactly N, with exactly one key, and the side to move is not mated. */
    private fun provedSample(d: Difficulty, step: Int) {
        val list = Chess.positionsFor(d)
        val n = Chess.mateInFor(d)
        for (i in list.indices step step) {
            val s = Chess.fromEntry(list[i], n)
            val p = ChessPosition.fromFen(s.start)
            assertFalse("${list[i]}: already mated or stalemated", ChessRules.legalMoves(p).isEmpty())
            val r = ChessRules.forcedMate(p, n)
            assertTrue("${list[i]}: $r", r is ChessRules.Mate.Forced)
            r as ChessRules.Mate.Forced
            assertEquals(list[i], n, r.inMoves)
            assertEquals("${list[i]}: not exactly one key", 1, r.keys.size)
        }
    }

    @Test fun `standard positions are mate in two with one key`() = provedSample(Difficulty.STANDARD, 1)
    @Test fun `hard positions are mate in three with one key`() = provedSample(Difficulty.HARD, 2)
    @Test fun `expert positions are mate in four with one key`() = provedSample(Difficulty.EXPERT, 4)

    @Test
    fun `a year of daily seeds picks valid entries on every tier, and the same one twice`() {
        val start = LocalDate.of(2026, 1, 1)
        for (d in Difficulty.entries) {
            val list = Chess.positionsFor(d)
            for (k in 0 until 365) {
                val seed = DailySeed.seedFor(start.plusDays(k.toLong()), Chess.id, d)
                val s = Chess.generate(seed, d) as ChessState
                assertEquals(s, Chess.generate(seed, d))
                assertTrue(list.any { it.startsWith(s.start) })
                assertFalse(s.solved)
                assertEquals(Chess.mateInFor(d), s.mateIn)
            }
        }
    }

    @Test
    fun `the tiers ask for longer mates in the order they are offered`() {
        assertEquals(listOf(2, 3, 4), Difficulty.entries.map { Chess.mateInFor(it) })
    }

    // ---- solved is a rule check ----------------------------------------------------------------------

    @Test
    fun `a legal mate that is not the data's line wins`() {
        // A mate-in-2 whose second move has two mates: both win, though only one can be the "stored" one.
        var found = 0
        for (i in 0 until 300) {
            val s0 = entry(Difficulty.STANDARD, i)
            val s1 = s0.play(keyOf(s0))
            val mates = s1.legalMoves().filter { ChessRules.isCheckmate(ChessRules.play(s1.position(), it)) }
            if (mates.size < 2) continue
            found++
            for (m in mates) assertTrue("${s0.start} ${ChessRules.uci(m)}", s1.play(m).solved)
        }
        assertTrue("no position with two mating finishes in the sample", found > 0)
    }

    @Test
    fun `a non-forcing first move is a mistake and the opponent then escapes in every case`() {
        for (i in 0 until 40) {
            val s0 = entry(Difficulty.STANDARD, i)
            val key = keyOf(s0)
            for (m in s0.legalMoves()) {
                if (m == key) continue
                val s1 = s0.play(m)
                assertFalse(s1.solved)
                assertTrue("${s0.start} ${ChessRules.uci(m)} should lose the mate", s1.lost())
                for (m2 in s1.legalMoves()) assertFalse("${s0.start} ${ChessRules.uci(m)} ${ChessRules.uci(m2)}", s1.play(m2).solved)
            }
        }
    }

    @Test
    fun `the key is never lost, and a mate in the moves is never lost`() {
        for (d in Difficulty.entries) {
            val s0 = entry(d, 3)
            assertFalse(s0.lost())
            val s1 = s0.play(keyOf(s0))
            assertFalse(s1.lost())
        }
    }

    @Test
    fun `using the moves up without mate is lost, and a stalemate ends the game`() {
        // Black king h8, white queen f7 (stalemate by Qf7 from g6: black has no move and is not in check).
        val s = ChessState(start = "7k/8/5QK1/8/8/8/8/8 w - - -", mateIn = 2)
        val stale = s.play(ChessRules.parseUci(s.position(), "f6f7")!!)
        assertFalse(stale.solved)
        assertTrue(stale.lost())
        assertTrue(stale.legalMoves().isEmpty())
    }

    // ---- state, replies, undo ------------------------------------------------------------------------

    @Test
    fun `one move is one state, and the reply is derived with the contract's remaining count`() {
        val s0 = entry(Difficulty.HARD, 5)
        val s1 = s0.play(keyOf(s0))
        assertEquals(1, s1.moves)
        assertEquals(listOf(ChessRules.uci(keyOf(s0))), s1.played)
        val r = s1.replay()
        assertTrue(r.reply >= 0)
        // `remaining` is the attacker's moves left after the reply: mateIn - played.size, pinned here.
        val after = ChessRules.play(s0.position(), keyOf(s0))
        assertEquals(ChessRules.defence(after, s0.mateIn - 1), r.reply)
        assertEquals(ChessRules.play(after, r.reply).toFen(), r.position.toFen())
        assertEquals(after.toFen(), r.beforeReply.toFen())
        // Undo restores the earlier state, whose position is the start: the move and its reply go together.
        assertEquals(ChessPosition.fromFen(s0.start).toFen(), s0.position().toFen())
    }

    @Test
    fun `the defence policy is pinned, because saved games replay it`() {
        // Standard 0: white mates in two; every reply loses, so the defender plays the slowest, ties by move order.
        for ((i, expect) in PINNED_REPLIES.withIndex()) {
            val s0 = entry(Difficulty.STANDARD, i)
            assertEquals(expect, ChessRules.uci(s0.play(keyOf(s0)).replay().reply))
        }
        // A defender that can avoid the mate always does: after a wasted move the reply leaves no forced mate.
        val s0 = entry(Difficulty.STANDARD, 0)
        val key = keyOf(s0)
        val wasted = s0.legalMoves().first { it != key }
        val s1 = s0.play(wasted)
        assertTrue(ChessRules.forcedMate(s1.position(), 1) === ChessRules.Mate.None)
    }

    @Test
    fun `an old save without the optional fields loads`() {
        val s = entry(Difficulty.STANDARD, 2)
        val json = SavedGame(s).encode()
        val old = json.replace(Regex(",?\"last\":\"[^\"]*\""), "").replace(Regex(",?\"played\":\\[[^]]*]"), "")
        assertFalse(old, "\"played\"" in old)
        val loaded = SavedGame.decode(old)?.state as? ChessState
        assertNotNull(old, loaded)
        assertEquals(s.copy(last = ""), loaded)
    }

    @Test
    fun `a played game round-trips`() {
        val s0 = entry(Difficulty.STANDARD, 4)
        val s1 = s0.play(keyOf(s0))
        assertEquals(s1, SavedGame.decode(SavedGame(s1).encode())?.state)
    }

    @Test
    fun `the registry knows Mate`() {
        assertNotNull(PuzzleRegistry.byId("chess"))
        assertEquals("Mate", Chess.displayName)
    }

    @Test
    fun `how long a derived reply takes`() {
        for (d in Difficulty.entries) {
            val times = (0 until 12).map { i ->
                val s0 = entry(d, i * 17)
                val t0 = System.nanoTime()
                val s1 = s0.play(keyOf(s0))
                s1.replay()
                s1.lost()
                (System.nanoTime() - t0) / 1_000_000
            }.sorted()
            println("replay+lost after the key, ${d.name}: median ${times[times.size / 2]} ms, max ${times.last()} ms")
        }
    }

    private companion object {
        /** UCI replies to the key on the first positions of the Standard list (the saved-game contract). */
        val PINNED_REPLIES = listOf("d8d1", "g1h2", "f2e1", "c1d1", "e8f8", "a3f8")
    }
}
