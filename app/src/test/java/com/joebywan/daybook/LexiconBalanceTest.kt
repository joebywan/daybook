package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.LexiconRules
import com.joebywan.daybook.puzzles.LexiconState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tiers must get harder in the order they are offered, and each must be winnable. Both are
 * measured by playing, because the shape constants alone say nothing: four-letter words look easier
 * than five and are not, and hard mode changes nothing for a player who only ever guesses words that
 * could still be the answer.
 *
 * Two players, both deliberately modest (neither knows the answer list's frequencies or any
 * dictionary lore):
 * - the *hint follower* does what [LexiconTeacher][com.joebywan.daybook.puzzles.LexiconTeacher] says:
 *   always a word that still fits every mark, the one that leaves the fewest in its worst case. It is
 *   legal in hard mode by construction, so it measures the word length and nothing else.
 * - the *prober* picks, from a sixth of the accepted words plus everything still possible, whichever
 *   splits the possibilities best. In free play it may use words that cannot be the answer; in hard
 *   mode it may not. Played on the same answers both ways, the gap is what the rule costs.
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
                    .filter { !s.hard || LexiconRules.hardProblem(it, guesses, marks) == null }
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
    fun `a harder tier never gets fewer guesses than an easier one`() {
        val budgets = Difficulty.entries.map { LexiconSupport.board(0, it).maxGuesses }
        assertTrue("budgets $budgets must not fall as the tiers rise", budgets.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `hard mode costs a player who probes, on the very same answers`() {
        val boards = (0 until 100).map { LexiconSupport.board(it, Difficulty.STANDARD) }
        val free = boards.map { probe(it.copy(hard = false)) }
        val hard = boards.map { probe(it.copy(hard = true)) }
        println("5 letters, prober: free mean ${"%.2f".format(free.average())}, hard mean ${"%.2f".format(hard.average())}")
        assertTrue("hard ${hard.average()} should need more guesses than free ${free.average()}", hard.average() > free.average())
    }

    @Test
    fun `four letters are the harder length, by a margin, and every tier is winnable inside its budget`() {
        val days = 365
        val means = HashMap<Difficulty, Double>()
        for (tier in Difficulty.entries) {
            val boards = (0 until days).map { LexiconSupport.board(it, tier) }
            val results = boards.map(::needed)
            val won = boards.indices.count { results[it] <= boards[it].maxGuesses }
            means[tier] = results.average()
            println(
                "${tier.name}: hint follower needs ${"%.2f".format(results.average())} guesses on average, " +
                    "wins $won/$days inside ${boards[0].maxGuesses}, worst ${results.max()}",
            )
            assertTrue("${tier.name}: a hint follower won only $won of $days", won >= days * 97 / 100)
        }
        // Not noise: each tier's boards are its own, so only a clear gap means anything.
        assertTrue("Expert ${means[Difficulty.EXPERT]} vs Hard ${means[Difficulty.HARD]}", means[Difficulty.EXPERT]!! >= means[Difficulty.HARD]!! + 0.5)
    }
}
