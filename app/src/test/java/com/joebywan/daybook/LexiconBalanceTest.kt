package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.LexiconState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each tier must be winnable inside its budget, and the budgets must make the tiers harder in the order
 * they are offered. Both are measured by playing, because the shape constants alone say nothing:
 * four-letter words look easier than five and are not.
 *
 * Two players, both deliberately modest (neither knows the answer list's frequencies or any
 * dictionary lore):
 * - the *hint follower* does what [LexiconTeacher][com.joebywan.daybook.puzzles.LexiconTeacher] says:
 *   always a word that still fits every mark, the one that leaves the fewest in its worst case. It never
 *   spends a guess on a word that cannot be the answer.
 * - the *prober* picks, from a sixth of the accepted words plus everything still possible, whichever
 *   splits the possibilities best, so it does spend guesses on words that cannot be the answer.
 */
class LexiconBalanceTest {

    private fun needed(s: LexiconState): Int =
        LexiconSupport.walkByHints(s.copy(maxGuesses = 20)).guesses.size

    private fun probe(s: LexiconState): Int {
        val guesses = ArrayList<String>()
        val marks = ArrayList<List<Int>>()
        val sample = LexiconSupport.guessList(s.length).filterIndexed { i, _ -> i % 6 == 0 }
        while (guesses.size < 20) {
            val fits = LexiconSupport.refCandidates(s.length, guesses, marks)
            val word = when {
                guesses.isEmpty() -> if (s.length == 4) "tale" else "raise"
                fits.size <= 2 -> fits[0]
                else -> (sample + fits)
                    .minBy { w ->
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

    @Test
    fun `every tier is winnable inside its budget, by either kind of player`() {
        val days = 200
        val slack = HashMap<Difficulty, Double>()
        val means = HashMap<Difficulty, Double>()
        for (tier in Difficulty.entries) {
            val boards = (0 until days).map { LexiconSupport.board(it, tier) }
            val follower = boards.map(::needed)
            val prober = boards.map(::probe)
            val budget = boards[0].maxGuesses
            val followerWon = follower.count { it <= budget }
            val proberWon = prober.count { it <= budget }
            means[tier] = follower.average()
            slack[tier] = budget - follower.average()
            println(
                "${tier.name}: budget $budget, hint follower mean ${"%.2f".format(follower.average())} " +
                    "wins $followerWon/$days, prober mean ${"%.2f".format(prober.average())} wins $proberWon/$days",
            )
            assertTrue("${tier.name}: a hint follower won only $followerWon of $days", followerWon >= days * 88 / 100)
            assertTrue("${tier.name}: a prober won only $proberWon of $days", proberWon >= days * 97 / 100)
        }
        // Hard leaves less room to spare than Standard. Expert's extra guess is paid for by the word: four
        // letters need more guesses on average, and that, not the budget, is what makes it the top tier.
        assertTrue("Hard slack ${slack[Difficulty.HARD]} vs Standard ${slack[Difficulty.STANDARD]}", slack[Difficulty.HARD]!! < slack[Difficulty.STANDARD]!!)
        assertTrue("Expert ${means[Difficulty.EXPERT]} vs Standard ${means[Difficulty.STANDARD]}", means[Difficulty.EXPERT]!! >= means[Difficulty.STANDARD]!! + 0.5)
    }
}
