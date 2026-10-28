package com.joebywan.daybook

import com.joebywan.daybook.core.ChessTraining
import com.joebywan.daybook.core.Course
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Chess
import com.joebywan.daybook.puzzles.ChessRules
import com.joebywan.daybook.puzzles.ChessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chess's course (docs/TRAINING_SPEC.md). A mate puzzle can have several keys, so there is no "one answer" to hold a
 * practice to: instead every legal move on a practice is judged, and must be right exactly when [ChessNaiveMate]'s
 * full-width minimax (nothing shared with the prover but the move generator) says it still mates within the moves left.
 * The naive search is slow past two moves, so it judges the practices with two moves or fewer left; deeper ones are
 * held to the prover, which `ChessMateCheckerTest` holds to the same minimax.
 */
class ChessTrainingTest {

    private fun keeps(s: ChessState, m: Int): Boolean {
        val pos = s.position()
        return ChessNaiveMate.afterAttack(ChessRules.play(pos, m), s.remaining) != null
    }

    private fun isCheck(s: ChessState, m: Int) = ChessRules.inCheck(ChessRules.play(s.position(), m))

    private fun play(s: ChessState, m: Int) = s.copy(played = s.played + ChessRules.uci(m), moves = s.moves + 1)

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertEquals(techniqueNamesOf("chess").keys.toList(), ChessTraining.techniques)
        var naive = 0
        for ((rung, t) in ChessTraining.techniques.withIndex()) {
            val list = ChessTraining.practices(t).take(8).toList()
            assertEquals("practices for $t", 8, list.size)
            assertEquals("distinct boards for $t", 8, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as ChessState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Chess.teach(b)!!.technique)
                assertFalse("the walk's moves keep the mate", Chess.teach(b)!!.mistake)
                val moves = ChessRules.legalMoves(b.position())
                if (b.remaining > 2) {
                    // Held to the prover: the step's rung is the first that has a key.
                    val keys = (ChessRules.forcedMate(b.position(), b.remaining, Int.MAX_VALUE) as ChessRules.Mate.Forced)
                    assertTrue(keys.inMoves > 1)
                    assertEquals(t == ChessTraining.techniques[1], keys.keys.any { isCheck(b, it) })
                    continue
                }
                naive++
                val winning = moves.filter { keeps(b, it) }
                val mateInOne = moves.any { ChessRules.isCheckmate(ChessRules.play(b.position(), it)) }
                val checkKey = winning.any { isCheck(b, it) }
                when (rung) {
                    0 -> assertTrue(mateInOne)
                    1 -> { assertFalse(mateInOne); assertTrue(checkKey) }
                    else -> { assertFalse(mateInOne); assertFalse(checkKey); assertTrue(winning.isNotEmpty()) }
                }
                // Every legal move is judged: right exactly when it still mates in time, wrong otherwise. No move plays on.
                for (m in moves) assertEquals("${b.start} ${b.played} ${ChessRules.uci(m)}", m in winning, ChessTraining.judge(p, play(b, m)))
            }
        }
        assertTrue("barely checked by the naive search: $naive", naive >= 16)
    }

    @Test fun `a practice with several keys passes each of them`() {
        // Found by the prover's key count (cheap), then every key confirmed by the naive search.
        val p: Course.Practice = ChessTraining.techniques.asSequence()
            .flatMap { ChessTraining.practices(it).take(30) }
            .first { val b = it.board as ChessState
                b.remaining <= 2 && (ChessRules.forcedMate(b.position(), b.remaining, Int.MAX_VALUE) as ChessRules.Mate.Forced).keys.size > 1 }
        val b = p.board as ChessState
        val keys = ChessRules.legalMoves(b.position()).filter { keeps(b, it) }
        assertTrue(keys.size > 1)
        for (k in keys) assertEquals(true, ChessTraining.judge(p, play(b, k)))
    }

    @Test fun `the final board is an Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            ChessTraining.techniquesUsed(Chess.generate(Training.seed(k), Difficulty.EXPERT)).containsAll(ChessTraining.techniques)
        }
        assertEquals(Training.seed(index), ChessTraining.examSeed)
        val exam = ChessTraining.exam() as ChessState
        val proof = ChessRules.forcedMate(exam.position(), exam.mateIn, Int.MAX_VALUE)
        assertTrue(proof is ChessRules.Mate.Forced && proof.inMoves == exam.mateIn)
        assertTrue(ChessTraining.techniquesUsed(exam).containsAll(ChessTraining.techniques))
    }
}
