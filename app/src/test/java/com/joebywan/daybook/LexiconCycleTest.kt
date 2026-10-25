package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.LexiconRules
import com.joebywan.daybook.puzzles.WordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Daily words from [SeedHash.LEXICON_CYCLE_FROM] go round a cycle: no word comes back until its tier has used every
 * other, and the days before the cutover keep the words they always had. Random boards are not cycled.
 */
class LexiconCycleTest {

    private val from = SeedHash.LEXICON_CYCLE_FROM

    private fun word(epochDay: Long, tier: Difficulty) =
        LexiconRules.newBoard(SeedHash.daily(epochDay, "words", tier), tier).answer

    @Test
    fun `days before the cutover keep their hashed words`() {
        for (tier in Difficulty.entries) {
            val answers = WordList.answers(LexiconRules.shape(tier).length)
            for (d in SeedHash.epochDay(2026, 1, 1) until from) {
                val seed = SeedHash.daily(d, "words", tier)
                assertNull(SeedHash.lexiconCycleDay(seed))
                assertEquals(answers[Rng(seed).nextInt(answers.size)], word(d, tier))
            }
        }
    }

    @Test
    fun `two full rounds per tier, every word once a round, and the first round saves last year's words for its end`() {
        for (tier in Difficulty.entries) {
            val length = LexiconRules.shape(tier).length
            val answers = WordList.answers(length)
            val n = answers.size
            val before = Difficulty.entries.filter { LexiconRules.shape(it).length == length }
                .flatMap { t -> (SeedHash.epochDay(2026, 1, 1) until from).map { word(it, t) } }.toSet()
            for (round in 0 until 2) {
                val words = (0 until n).map { word(from + round * n + it, tier) }
                assertEquals("${tier.name} round $round", answers.toSet(), words.toSet())
                if (round == 0) {
                    val fresh = n - before.size
                    assertTrue("${tier.name}: a word of the year before came back early", words.take(fresh).none { it in before })
                }
            }
            println("${tier.name}: $n words a round, ${before.size} drawn before the cutover go last in round 0")
        }
    }

    @Test
    fun `random seeds are never cycle seeds`() {
        for (tier in Difficulty.entries) for (nonce in 0L until 2000L) {
            assertNull(SeedHash.lexiconCycleDay(SeedHash.random("words", tier, nonce)))
        }
        // And the tiers' cycle seeds differ, so a saved game's key does too.
        assertEquals(3, Difficulty.entries.map { SeedHash.daily(from + 5, "words", it) }.toSet().size)
    }
}
