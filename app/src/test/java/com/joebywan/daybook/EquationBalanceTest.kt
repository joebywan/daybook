package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.EquationLogic
import com.joebywan.daybook.puzzles.EquationState
import com.joebywan.daybook.puzzles.EquationTeacher
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each tier must be winnable inside its budget, and harder in the order offered, measured by playing (the shape
 * constants alone say nothing), as `LexiconBalanceTest` does. Two deliberately modest players:
 * - the *hint follower* does what the hints say: always a sum that still fits every mark, the one that leaves the fewest
 *   in its worst case. It never spends a guess on a sum that cannot be the answer.
 * - the *prober* picks, from a sample of every true sum plus everything still possible, whichever splits the
 *   possibilities best, so it does spend guesses on sums that cannot be the answer.
 */
class EquationBalanceTest {

    private fun needed(s: EquationState): Int = EquationSupport.walkByHints(s.copy(maxGuesses = 20)).guesses.size

    private fun probe(s: EquationState): Int {
        val guesses = ArrayList<String>()
        val marks = ArrayList<List<Int>>()
        val all = EquationLogic.valid(s.length)
        val sample = all.filterIndexed { i, _ -> i % maxOf(1, all.size / 600) == 0 }
        while (guesses.size < 20) {
            val fits = EquationSupport.refCandidates(s.length, guesses, marks)
            val word = when {
                guesses.isEmpty() -> when (s.length) { 6 -> "28/7=4"; 7 -> "6+28=34"; else -> "43-25=18" }
                fits.size <= 2 -> fits[0]
                else -> (sample + fits).minBy { w ->
                    val counts = fits.groupingBy { LexiconSupport.refMark(w, it) }.eachCount()
                    counts.values.max() * 10_000 - counts.size * 10 + (if (w in fits) 0 else 5)
                }
            }
            guesses += word
            marks += LexiconSupport.refMark(word, s.answer)
            if (word == s.answer) break
        }
        return guesses.size
    }

    /** How many answers still fit after the board's opening hint: what is left to work out by hand. */
    private fun leftAfterOpener(s: EquationState): Int {
        val opener = EquationTeacher.opener(s.length, s.opener)
        return EquationSupport.refCandidates(s.length, listOf(opener), listOf(LexiconSupport.refMark(opener, s.answer))).size
    }

    @Test
    fun `every tier is winnable inside its budget, and harder in the order offered`() {
        val days = 100
        val slack = HashMap<Difficulty, Double>()
        val left = HashMap<Difficulty, Double>()
        for (tier in Difficulty.entries) {
            val boards = (0 until days).map { EquationSupport.board(it, tier) }
            val follower = boards.map(::needed)
            val prober = boards.map(::probe)
            val budget = boards[0].maxGuesses
            val followerWon = follower.count { it <= budget }
            val proberWon = prober.count { it <= budget }
            val opened = boards.map(::leftAfterOpener).sorted()
            slack[tier] = budget - follower.average()
            left[tier] = opened.average()
            println(
                "${tier.name}: ${EquationLogic.answers(boards[0].length).size} answers, budget $budget, hint follower mean " +
                    "${"%.2f".format(follower.average())} (most ${follower.max()}) wins $followerWon/$days, " +
                    "prober mean ${"%.2f".format(prober.average())} wins $proberWon/$days, " +
                    "left after the opener mean ${"%.1f".format(opened.average())} median ${opened[days / 2]} p90 ${opened[days * 9 / 10]}",
            )
            assertTrue("${tier.name}: a hint follower won only $followerWon of $days", followerWon >= days * 95 / 100)
            assertTrue("${tier.name}: a prober won only $proberWon of $days", proberWon >= days * 97 / 100)
        }
        // A careful player needs about three guesses at every length, so the tiers are not told apart by guesses (seven and
        // eight symbols take the same); what grows is the equations still to work out after the opener (on average: the
        // medians of seven and eight are close, the long tail is eight's).
        assertTrue("$slack", slack[Difficulty.STANDARD]!! > slack[Difficulty.HARD]!! + 0.2)
        assertTrue("$left", left[Difficulty.STANDARD]!! < left[Difficulty.HARD]!! && left[Difficulty.HARD]!! < left[Difficulty.EXPERT]!!)
    }
}
