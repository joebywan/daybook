package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Difficulty.EXPERT
import com.joebywan.daybook.core.Difficulty.HARD
import com.joebywan.daybook.core.Difficulty.STANDARD
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.ui.play.NextOption
import com.joebywan.daybook.ui.play.nextOptions
import com.joebywan.daybook.ui.play.tiersDoneOn
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The buttons on the finished-puzzle frame. Every expected list is spelled out by hand as
 * "label>where", where "where" is `random:<TIER>` (a fresh random board), `daily:<TIER>` (the same
 * date at that tier) or `home`, so the tests share nothing with the code that builds the options.
 */
class NextStepsTest {
    private fun render(options: List<NextOption>): List<String> = options.map {
        val where = when (it.kind.name) {
            "RANDOM" -> "random:${it.difficulty!!.name}"
            "DAILY" -> "daily:${it.difficulty!!.name}"
            else -> "home"
        }
        "${it.label}>$where"
    }

    private fun shown(daily: Boolean, tier: Difficulty, vararg done: Difficulty): List<String> =
        render(nextOptions(daily, tier, done.toSet()))

    // ---- random boards ----

    @Test fun randomStandardHasNoEasier() = assertEquals(
        listOf("Another>random:STANDARD", "Harder>random:HARD", "Done>home"),
        shown(false, STANDARD),
    )

    @Test fun randomHardOffersAllFour() = assertEquals(
        listOf("Another>random:HARD", "Easier>random:STANDARD", "Harder>random:EXPERT", "Done>home"),
        shown(false, HARD),
    )

    @Test fun randomExpertHasNoHarder() = assertEquals(
        listOf("Another>random:EXPERT", "Easier>random:HARD", "Done>home"),
        shown(false, EXPERT),
    )

    @Test fun randomIgnoresWhatTheDailiesHaveDone() = assertEquals(
        listOf("Another>random:HARD", "Easier>random:STANDARD", "Harder>random:EXPERT", "Done>home"),
        shown(false, HARD, STANDARD, HARD, EXPERT),
    )

    // ---- daily boards, nothing else done ----

    @Test fun dailyStandard() = assertEquals(
        listOf("Random>random:STANDARD", "Harder Daily>daily:HARD", "Done>home"),
        shown(true, STANDARD),
    )

    @Test fun dailyHard() = assertEquals(
        listOf("Easier Daily>daily:STANDARD", "Random>random:HARD", "Harder Daily>daily:EXPERT", "Done>home"),
        shown(true, HARD),
    )

    @Test fun dailyExpert() = assertEquals(
        listOf("Easier Daily>daily:HARD", "Random>random:EXPERT", "Done>home"),
        shown(true, EXPERT),
    )

    // ---- the tier just solved counts as done even if the store has not caught up ----

    @Test fun dailyCurrentTierIsNeverOffered() {
        for (tier in Difficulty.entries) {
            for (o in nextOptions(true, tier, emptySet())) {
                if (o.kind.name == "DAILY") assertNotEquals("$tier offered itself", tier, o.difficulty)
            }
        }
    }

    @Test fun dailyAlreadyListingTheCurrentTierChangesNothing() {
        for (tier in Difficulty.entries) {
            assertEquals(nextOptions(true, tier, emptySet()), nextOptions(true, tier, setOf(tier)))
        }
    }

    // ---- daily: skipping tiers that are done ----

    @Test fun hardDailyWithStandardDoneOffersOnlyHarder() = assertEquals(
        listOf("Random>random:HARD", "Harder Daily>daily:EXPERT", "Done>home"),
        shown(true, HARD, STANDARD),
    )

    @Test fun hardDailyWithExpertDoneOffersOnlyEasier() = assertEquals(
        listOf("Easier Daily>daily:STANDARD", "Random>random:HARD", "Done>home"),
        shown(true, HARD, EXPERT),
    )

    @Test fun expertDailyWithHardDoneJumpsToStandard() = assertEquals(
        listOf("Easier Daily>daily:STANDARD", "Random>random:EXPERT", "Done>home"),
        shown(true, EXPERT, HARD),
    )

    @Test fun standardDailyWithHardDoneJumpsToExpert() = assertEquals(
        listOf("Random>random:STANDARD", "Harder Daily>daily:EXPERT", "Done>home"),
        shown(true, STANDARD, HARD),
    )

    @Test fun expertDailyWithHardAndStandardDoneDropsEasier() = assertEquals(
        listOf("Random>random:EXPERT", "Done>home"),
        shown(true, EXPERT, HARD, STANDARD),
    )

    @Test fun standardDailyWithHardAndExpertDoneDropsHarder() = assertEquals(
        listOf("Random>random:STANDARD", "Done>home"),
        shown(true, STANDARD, HARD, EXPERT),
    )

    @Test fun hardDailyWithBothOthersDoneIsRandomAndDone() = assertEquals(
        listOf("Random>random:HARD", "Done>home"),
        shown(true, HARD, STANDARD, EXPERT),
    )

    @Test fun allThreeDoneIsRandomAndDone() {
        for (tier in Difficulty.entries) {
            assertEquals(
                listOf("Random>random:${tier.name}", "Done>home"),
                shown(true, tier, STANDARD, HARD, EXPERT),
            )
        }
    }

    // ---- which tiers count as done ----

    private val d1 = LocalDate(2026, 3, 4)
    private val d2 = LocalDate(2026, 3, 5)
    private fun c(puzzle: String, tier: Difficulty, day: LocalDate?) = Completion(puzzle, tier, day, 60, 0)

    @Test fun onlyThatDaysCompletionsOfThatPuzzleCount() {
        val all = listOf(
            c("sudoku", STANDARD, d1),
            c("sudoku", HARD, d2), // another date
            c("kings", EXPERT, d1), // another puzzle
            c("sudoku", EXPERT, null), // random
        )
        assertEquals(setOf(STANDARD), tiersDoneOn(all, "sudoku", d1))
        assertEquals(setOf(HARD), tiersDoneOn(all, "sudoku", d2))
        assertEquals(setOf(EXPERT), tiersDoneOn(all, "kings", d1))
    }

    @Test fun randomGamesNeverCountAsDaily() {
        val all = listOf(c("sudoku", STANDARD, null), c("sudoku", HARD, null))
        assertEquals(emptySet<Difficulty>(), tiersDoneOn(all, "sudoku", d1))
        assertEquals(emptySet<Difficulty>(), tiersDoneOn(all, "sudoku", null))
    }

    @Test fun anArchiveDayLooksAtItsOwnDate() {
        val past = LocalDate(2026, 1, 10)
        val all = listOf(c("mosaic", STANDARD, past), c("mosaic", EXPERT, d1))
        // Playing Hard on the past day: Standard is done there, Expert (done today) is not.
        assertEquals(
            listOf("Random>random:HARD", "Harder Daily>daily:EXPERT", "Done>home"),
            render(nextOptions(true, HARD, tiersDoneOn(all, "mosaic", past))),
        )
    }
}
