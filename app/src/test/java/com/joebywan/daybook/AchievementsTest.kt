package com.joebywan.daybook

import com.joebywan.daybook.core.ACHIEVEMENTS
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.core.newlyEarned
import com.joebywan.daybook.data.Completion
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AchievementsTest {
    private val today = LocalDate(2026, 10, 10)
    private fun d(back: Int) = LocalDate.fromEpochDays(today.toEpochDays() - back)
    private val ids = listOf("a", "b", "c")
    private fun c(back: Int?, id: String = "a", tier: Difficulty = Difficulty.STANDARD, hints: Int = 0) =
        Completion(id, tier, back?.let { d(it) }, 100, hints)
    private fun got(h: List<Completion>, pids: List<String> = ids) = earnedAchievements(h, today, pids).map { it.id }.toSet()
    private fun new(h: List<Completion>, s: Completion, pids: List<String> = ids) =
        newlyEarned(h, s, today, pids).map { it.id }.toSet()

    @Test fun sizeAndUniqueIds() {
        assertTrue(ACHIEVEMENTS.size in 15..25)
        assertEquals(ACHIEVEMENTS.size, ACHIEVEMENTS.map { it.id }.toSet().size)
    }

    @Test fun firstSolveAndTopTier() {
        assertEquals(setOf("solves1"), new(emptyList(), c(null)))
        assertEquals(setOf("firstTop", "cleanTop"), new(listOf(c(null)), c(null, tier = Difficulty.EXPERT)))
        assertEquals(setOf("cleanTop"), new(listOf(c(null, tier = Difficulty.EXPERT, hints = 2)), c(null, tier = Difficulty.EXPERT)))
    }

    @Test fun streakAnnouncedOnTheSolveThatReachesIt() {
        val h = (1..6).map { c(it) }
        assertEquals(setOf("streak7", "puzzleStreak7"), new(h, c(0)))
        // second solve the same day adds nothing
        assertEquals(emptySet<String>(), new(h + c(0), c(0, id = "b")))
    }

    @Test fun perPuzzleStreakIgnoresOtherPuzzles() {
        val h = (1..6).map { c(it, id = if (it % 2 == 0) "a" else "b") }
        assertTrue("puzzleStreak7" !in new(h, c(0, "a")))
        val onlyA = (1..6).map { c(it, "a") }
        assertTrue("puzzleStreak7" in new(onlyA, c(0, "a")))
    }

    @Test fun fullSetNeedsAllTiersOneDay() {
        val h = listOf(c(1, tier = Difficulty.STANDARD), c(1, tier = Difficulty.HARD), c(2, tier = Difficulty.EXPERT))
        assertTrue("fullSet" !in got(h))
        assertTrue("fullSet" in new(h, c(1, tier = Difficulty.EXPERT)))
    }

    @Test fun legacyHistoryDoesNotFlood() {
        // A big old history satisfies many conditions; the next ordinary solve announces none of them.
        val h = (1..120).flatMap { n -> ids.flatMap { id -> Difficulty.entries.map { c(n + 1, id, it) } } }
        assertTrue(got(h).size >= 12)
        assertEquals(emptySet<String>(), new(h, c(null)))
        assertEquals(got(h), got(h + c(null)))
    }

    @Test fun scalesWithRegistry() {
        val h = listOf(c(1, "a"), c(1, "b"))
        assertTrue("allPuzzles" in new(h, c(1, "c"), ids))
        // a fourth puzzle in the registry raises the bar
        assertTrue("allPuzzles" !in new(h, c(1, "c"), ids + "d"))
        assertTrue("allPuzzles" in new(h + c(1, "c"), c(1, "d"), ids + "d"))
        assertTrue("allPuzzles" !in got(emptyList(), emptyList()))
    }

    @Test fun randomHistoriesMatchNaiveModelAndNewIsTheDifference() {
        val rnd = Random(7)
        repeat(300) {
            val h = List(rnd.nextInt(0, 40)) {
                c(rnd.nextInt(-1, 12).takeIf { it >= 0 }, ids[rnd.nextInt(3)], Difficulty.entries[rnd.nextInt(3)], rnd.nextInt(2))
            }
            val s = c(rnd.nextInt(0, 12), ids[rnd.nextInt(3)], Difficulty.entries[rnd.nextInt(3)], rnd.nextInt(2))
            val before = got(h)
            val after = got(h + s)
            // Monotone when the solve is the newest day (streakOf's runs are causal); an archive day can in
            // rare cases split a run differently, which the diff (below) still reports correctly.
            if (s.day!! >= (h.mapNotNull { it.day }.maxOrNull() ?: s.day!!)) assertTrue(before.all { it in after })
            assertEquals(after - before, new(h, s))

            // Independent model of the registry-wide and tier conditions, by plain loops.
            val all = h + s
            var fullSet = false
            var sweep = false
            for (back in 0..12) {
                val day = all.filter { it.day == d(back) }
                if (ids.all { id -> day.any { it.puzzleId == id } }) sweep = true
                for (id in ids) {
                    if (Difficulty.entries.all { t -> day.any { it.puzzleId == id && it.difficulty == t } }) fullSet = true
                }
            }
            assertEquals(fullSet, "fullSet" in after)
            assertEquals(sweep, "allPuzzles" in after)
            assertEquals(ids.all { id -> all.any { it.puzzleId == id } }, "oneOfEach" in after)
            assertEquals(all.any { it.difficulty == Difficulty.EXPERT }, "firstTop" in after)
            assertEquals(all.mapNotNull { it.day }.toSet().size >= 30, "days30" in after)
        }
    }
}
