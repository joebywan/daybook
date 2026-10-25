package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LevelBoard
import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.core.PuzzleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The level plan (`core/Levels.kt`). The curve, the window and the pool are written out here rather than read
 * from [Levels], so a change there that quietly loosens its own rule cannot pass.
 */
class LevelsTest {

    /** Today's pool, copied. Appending to [Levels.POOL] is fine; anything else changes opened levels. */
    private val pinnedPool = listOf(
        "sudoku", "kings", "mambo", "pipes", "shikaku", "mosaic", "sets", "atoms", "snap", "lits",
        "tower", "words", "nonogram", "inequality", "chess", "tents", "regions", "cages", "target",
        "cipher", "wordwheel", "tally", "sand", "pointers", "dominoes",
    )

    /** Deal 2's gentlest-first order, copied: `PuzzleOrder.GENTLEST` on 2026-10-25. It must never change. */
    private val pinnedGentle = listOf(
        "pointers", "pipes", "snap", "sudoku", "words", "sand", "wordwheel", "target", "nonogram", "kings",
        "mambo", "mosaic", "sets", "tower", "inequality", "tents", "regions",
        "tally", "shikaku", "atoms", "lits", "chess", "cages", "cipher", "dominoes",
    )

    private val deals = 1..Levels.LATEST

    /** Puzzles that do not exist yet, appended to see what a bigger pool does. */
    private val future = pinnedPool + (1..35).map { "future$it" }

    private fun ids(plan: List<LevelBoard>) = plan.map { it.puzzleId }

    @Test
    fun `the pool starts with today's list and holds every registered puzzle`() {
        assertEquals(pinnedPool, Levels.POOL.take(pinnedPool.size))
        assertEquals(Levels.POOL.size, Levels.POOL.toSet().size)
        val missing = PuzzleRegistry.all.map { it.id }.filter { it !in Levels.POOL }
        assertTrue("not in Levels.POOL: $missing", missing.isEmpty())
    }

    @Test
    fun `levels 1 to 5 use only puzzles with a walkthrough`() {
        for (deal in deals) {
            val starters = (1..5).flatMap { ids(Levels.plan(it, Levels.POOL.size, deal)) }
            for (id in starters + Levels.POOL.take(Levels.STARTER)) {
                assertTrue("$id has no walkthrough", PuzzleRegistry.byId(id)!!.tutorial.isNotEmpty())
            }
            assertEquals("deal $deal: levels 1-5 deal the starter puzzles once each", Levels.POOL.take(Levels.STARTER).sorted(), starters.sorted())
        }
    }

    @Test
    fun `deal 2 is frozen and deals the gentlest first`() {
        assertEquals(pinnedGentle, Levels.GENTLE)
        assertEquals(Levels.POOL.take(Levels.STARTER).sorted(), pinnedGentle.sorted())
        fun level(n: Int) = ids(Levels.plan(n, Levels.POOL.size, 2))
        assertEquals("levels 1-2 are the gentlest ten", pinnedGentle.take(10).toSet(), (level(1) + level(2)).toSet())
        assertTrue("level 3 is from the gentlest 17", level(3).all { pinnedGentle.indexOf(it) < 17 })
    }

    @Test
    fun `the plans of levels 1 to 50 are pinned for each deal`() {
        fun got(deal: Int) = (1..50).map { n -> Levels.plan(n, Levels.POOL.size, deal).joinToString(" ") { "${it.puzzleId}/${it.difficulty.name[0]}" } }
        assertEquals(GOLDEN, got(1))
        assertEquals(GOLDEN_2, got(2))
        assertEquals("the default is the latest deal", Levels.plan(7, Levels.POOL.size, Levels.LATEST), Levels.plan(7))
        assertEquals(
            listOf(-3215747372223080039L, 6085545774054540416L, -4480082772839746525L, -8359627411479413995L, 7328236594175289981L),
            Levels.plan(1, Levels.POOL.size, 1).map { it.seed },
        )
        assertEquals(
            listOf(7688255564085075123L, -4094480006933299752L, -224227637783284087L, -7924503025723838927L, 6766780658332473636L),
            Levels.plan(500, Levels.POOL.size, 1).map { it.seed },
        )
    }

    @Test
    fun `appending to the pool never changes a plan at an older pool size`() {
        for (deal in deals) for (n in 1..500) {
            val today = Levels.plan(n, pinnedPool.size, deal)
            assertEquals("level $n", today, Levels.plan(n, future.take(pinnedPool.size), deal))
            assertEquals("level $n", today, Levels.plan(n, Levels.POOL.take(pinnedPool.size), deal))
        }
    }

    @Test
    fun `every level is five different puzzles and none from the level before`() {
        for (deal in deals) for (size in pinnedPool.size..future.size) {
            val pool = future.take(size)
            var before = emptyList<String>()
            for (n in 1..500) {
                val now = ids(Levels.plan(n, pool, deal))
                assertEquals("pool $size level $n: $now", Levels.SIZE, now.toSet().size)
                assertTrue("pool $size level $n repeats ${now.intersect(before.toSet())}", now.none { it in before })
                assertTrue(now.all { it in pool })
                before = now
            }
        }
    }

    @Test
    fun `tiers follow the curve for levels 1 to 500`() {
        fun curve(n: Int): Triple<Int, Int, Int> = when (n) {
            in 1..5 -> Triple(5, 0, 0)
            in 6..15 -> Triple(4, 1, 0)
            in 16..30 -> Triple(3, 2, 0)
            in 31..50 -> Triple(2, 2, 1)
            in 51..80 -> Triple(1, 3, 1)
            else -> Triple(0, 3, 2)
        }
        for (n in 1..500) {
            val tiers = Levels.plan(n).map { it.difficulty }
            val got = Triple(tiers.count { it == Difficulty.STANDARD }, tiers.count { it == Difficulty.HARD }, tiers.count { it == Difficulty.EXPERT })
            assertEquals("level $n", curve(n), got)
        }
    }

    @Test
    fun `every puzzle appears in any ten levels in a row`() {
        for (deal in deals) {
            val plans = (1..510).map { ids(Levels.plan(it, Levels.POOL.size, deal)) }
            for (start in 0..500) {
                val seen = plans.subList(start, start + 10).flatten().toSet()
                assertEquals("levels ${start + 1}-${start + 10} miss ${pinnedPool - seen}", pinnedPool.toSet(), seen)
            }
            // A bigger pool stretches the window to about 2 * size / 5 levels (see Levels).
            for (size in listOf(26, 30, 40, 60)) {
                val pool = future.take(size)
                val window = (2 * size + Levels.SIZE - 1) / Levels.SIZE + 1
                val big = (1..300 + window).map { ids(Levels.plan(it, pool, deal)) }
                for (start in 5..300) {
                    val seen = big.subList(start, start + window).flatten().toSet()
                    assertEquals("pool $size, $window levels from ${start + 1}", pool.toSet(), seen)
                }
            }
        }
    }

    @Test
    fun `seeds are the level's own and never a daily board of the same puzzle and tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val daily = HashSet<Long>()
        for (id in pinnedPool) for (tier in Difficulty.entries) for (d in 0 until 365L) {
            daily += DailySeed.seedFor(start.plusDays(d), id, tier)
        }
        val boards = (1..365).flatMap { Levels.plan(it) }
        assertTrue(boards.none { it.seed in daily })
        assertEquals("level seeds are all different", boards.size, boards.map { it.seed }.toSet().size)
    }

    @Test
    fun `a plan is cheap at level 500`() {
        repeat(1000) { Levels.plan(500 + it) } // warm up
        val began = System.nanoTime()
        repeat(10_000) { Levels.plan(1 + it % 1000) }
        val micros = (System.nanoTime() - began) / 10_000 / 1000.0
        println("Levels.plan: $micros us a plan")
        assertTrue("a plan took $micros us", micros < 1000)
    }

    @Test
    fun `the summary names the tiers`() {
        fun of(vararg t: Difficulty) = Levels.summary(t.map { LevelBoard("x", it, 0L) })
        val (s, h, e) = Difficulty.entries
        assertEquals("All Standard", of(s, s, s, s, s))
        assertEquals("Mostly Standard, one Hard", of(s, h, s, s, s))
        assertEquals("Mostly Standard, two Hard", of(h, s, s, h, s))
        assertEquals("Two Standard, two Hard, one Expert", of(e, s, h, s, h))
        assertEquals("Mostly Hard, one Standard, one Expert", of(h, s, h, e, h))
        assertEquals("Mostly Hard, two Expert", of(h, e, h, e, h))
        assertEquals("Mostly Hard, one Standard, one Expert", Levels.summary(Levels.plan(60)))
    }

    private companion object {
        /**
         * Deal 2: levels 1-5 differ. Deck 1 on is shuffled as in deal 1 and its repair reads deck 0's last ten, which
         * could differ; today neither deal's tail clashes with deck 1's start, so levels 6 on are deal 1's.
         */
        val GOLDEN_2 by lazy {
            listOf(
                "kings/S sudoku/S nonogram/S words/S snap/S",
                "pointers/S pipes/S target/S sand/S wordwheel/S",
                "tents/S tower/S sets/S regions/S mambo/S",
                "dominoes/S shikaku/S tally/S lits/S inequality/S",
                "mosaic/S chess/S cages/S cipher/S atoms/S",
            ) + GOLDEN.drop(5)
        }

        val GOLDEN = listOf(
            "tower/S snap/S regions/S sets/S nonogram/S",
            "sand/S chess/S lits/S sudoku/S dominoes/S",
            "shikaku/S cipher/S cages/S words/S wordwheel/S",
            "mambo/S mosaic/S tally/S kings/S inequality/S",
            "tents/S pipes/S target/S atoms/S pointers/S",
            "kings/H wordwheel/S snap/S sets/S mambo/S",
            "tower/S words/S sudoku/S pipes/S cipher/H",
            "tally/S pointers/S tents/H lits/S nonogram/S",
            "cages/S sand/H dominoes/S mosaic/S target/S",
            "atoms/S shikaku/S inequality/S regions/S chess/H",
            "nonogram/S words/S mambo/S lits/H mosaic/S",
            "atoms/S snap/S sets/H target/S cages/S",
            "tower/S kings/H tents/S tally/S dominoes/S",
            "shikaku/S wordwheel/S inequality/S pipes/H cipher/S",
            "sand/S pointers/S sudoku/H regions/S chess/S",
            "tents/H kings/H wordwheel/S pipes/S atoms/S",
            "sets/S sudoku/S lits/H mambo/S mosaic/H",
            "cages/S target/H nonogram/S cipher/H snap/S",
            "sand/S tally/S dominoes/H words/S chess/H",
            "shikaku/S regions/H tower/S inequality/H pointers/S",
            "cipher/S sudoku/H lits/S tents/H wordwheel/S",
            "words/S shikaku/S nonogram/H target/S pointers/H",
            "regions/S sand/H inequality/S chess/S dominoes/H",
            "mambo/S sets/H snap/H pipes/S mosaic/S",
            "atoms/H tally/S cages/S kings/S tower/H",
            "chess/S target/H nonogram/S wordwheel/S snap/H",
            "pipes/S mambo/S cipher/S kings/H tally/H",
            "pointers/H mosaic/S atoms/S cages/H sand/S",
            "words/H lits/S tower/H dominoes/S shikaku/S",
            "tents/H sudoku/S sets/S regions/S inequality/H",
            "lits/H cipher/E mambo/S sand/H tally/S",
            "sudoku/H sets/H inequality/S atoms/E tents/S",
            "target/S nonogram/H pipes/S mosaic/E dominoes/H",
            "snap/S regions/H chess/H wordwheel/E pointers/S",
            "shikaku/S words/H kings/E tower/S cages/H",
            "target/S tents/H atoms/E sets/S cipher/H",
            "regions/H nonogram/H tower/S shikaku/E pointers/S",
            "mambo/S kings/E sudoku/S inequality/H cages/H",
            "dominoes/H words/E sand/H pipes/S mosaic/S",
            "tally/S chess/E wordwheel/H snap/S lits/H",
            "sudoku/H shikaku/E dominoes/H inequality/S tents/S",
            "chess/H wordwheel/E cages/H words/S mosaic/S",
            "tally/H pointers/S target/S regions/H pipes/E",
            "snap/E tower/H mambo/S sets/H atoms/S",
            "kings/S cipher/H nonogram/H lits/E sand/S",
            "mosaic/S cages/H inequality/H target/S chess/E",
            "kings/S sand/H nonogram/H snap/S cipher/E",
            "tally/E dominoes/S words/H mambo/S lits/H",
            "tower/S pipes/H atoms/E tents/S wordwheel/H",
            "regions/H shikaku/S sets/E sudoku/S pointers/H",
        )
    }
}
