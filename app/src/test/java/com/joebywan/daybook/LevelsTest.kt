package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LevelBoard
import com.joebywan.daybook.core.LevelSpread
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

    /**
     * The pool levels shipped with, copied (Lamps came after). Appending to [Levels.POOL] is fine; anything else changes
     * opened levels. The pins below are at this size: every level opened with it keeps planning exactly so.
     */
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

    /** The deals dealt from decks from level 1. Deal 3 plans levels 1-100 by difficulty instead (its own tests below). */
    private val deals = 1..2

    /** Every puzzle deal 3 ranks, copied: its levels 1-100 draw on these 31. */
    private val ranked = pinnedPool + listOf("mines", "lamps", "codeword", "pearls", "whoswho", "mini")

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
        for (id in (1..5).flatMap { ids(Levels.plan(it, Levels.POOL.size, 3)) }) {
            assertTrue("$id has no walkthrough", PuzzleRegistry.byId(id)!!.tutorial.isNotEmpty())
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

    /** Pinned at [pinnedPool]'s size: a plan is a function of the pool size, and a puzzle appended later opens new levels at its own. */
    @Test
    fun `the plans of levels 1 to 50 are pinned for each deal`() {
        // Pinned at the pool of 25: a level opened then keeps that plan however the pool grows (the test below).
        fun got(deal: Int) = (1..50).map { n -> Levels.plan(n, pinnedPool.size, deal).joinToString(" ") { "${it.puzzleId}/${it.difficulty.name[0]}" } }
        assertEquals(GOLDEN, got(1))
        assertEquals(GOLDEN_2, got(2))
        assertEquals(3, Levels.LATEST)
        assertEquals("the default is the latest deal", Levels.plan(7, Levels.POOL.size, Levels.LATEST), Levels.plan(7))
        assertEquals(
            listOf(-3215747372223080039L, 6085545774054540416L, -4480082772839746525L, -8359627411479413995L, 7328236594175289981L),
            Levels.plan(1, pinnedPool.size, 1).map { it.seed },
        )
        assertEquals(
            listOf(7688255564085075123L, -4094480006933299752L, -224227637783284087L, -7924503025723838927L, 6766780658332473636L),
            Levels.plan(500, pinnedPool.size, 1).map { it.seed },
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
        for (deal in Levels.LATEST downTo 1) for (n in (if (deal == 3) 101 else 1)..500) {
            val tiers = Levels.plan(n, Levels.POOL.size, deal).map { it.difficulty }
            val got = Triple(tiers.count { it == Difficulty.STANDARD }, tiers.count { it == Difficulty.HARD }, tiers.count { it == Difficulty.EXPERT })
            assertEquals("deal $deal level $n", curve(n), got)
        }
    }

    @Test
    fun `every puzzle appears in any ten levels in a row`() {
        for (deal in deals) {
            val plans = (1..510).map { ids(Levels.plan(it, pinnedPool.size, deal)) }
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
        assertEquals("Mostly Hard, one Standard, one Expert", Levels.summary(Levels.plan(60, pinnedPool.size, 1)))
    }

    /** Deal 3's pool, and that pool with puzzles that do not exist yet appended. */
    private val future3 = ranked + (1..35).map { "future$it" }

    @Test
    fun `deal 3's plans are pinned`() {
        fun got(levels: IntRange) = levels.map { n -> Levels.plan(n, ranked, 3).joinToString(" ") { "${it.puzzleId}/${it.difficulty.name[0]}" } }
        assertEquals(GOLDEN_3, got(1..50))
        assertEquals(GOLDEN_3_TURN, got(98..103))
        assertEquals(Levels.plan(40, ranked.size, 3), Levels.plan(40, ranked, 3))
    }

    @Test
    fun `deal 3 keeps every level five puzzles, none from the level before, at any later pool size`() {
        for (size in ranked.size..future3.size) {
            val pool = future3.take(size)
            var before = emptyList<String>()
            for (n in 1..400) {
                val plan = Levels.plan(n, pool, 3)
                if (n <= Levels.SPREAD_LEVELS) assertEquals("pool $size level $n", Levels.plan(n, ranked, 3), plan)
                val now = ids(plan)
                assertEquals("pool $size level $n: $now", Levels.SIZE, now.toSet().size)
                assertTrue("pool $size level $n repeats ${now.intersect(before.toSet())}", now.none { it in before })
                assertTrue(now.all { it in pool })
                before = now
            }
        }
    }

    /** Levels 1-100 of deal 3, read against the measured scores (`LevelSpread`, generated; `docs/LEVELS_SPEC.md`). */
    @Test
    fun `deal 3 rises smoothly, mixes tiers and brings every puzzle in early`() {
        val levels = (1..100).map { Levels.plan(it, ranked, 3) }
        val score = { b: LevelBoard -> LevelSpread.SCORE.getValue(b.puzzleId)[b.difficulty.ordinal] }
        val mean = levels.map { l -> l.sumOf(score) / 5.0 }
        val all = LevelSpread.SCORE.values.flatten()
        val lo = all.min()
        val hi = all.max()
        for ((i, m) in mean.withIndex()) {
            val target = lo + (hi - lo) * i / 99.0
            assertTrue("level ${i + 1}: mean $m, target $target", kotlin.math.abs(m - target) < 100)
        }
        val tens = mean.chunked(10).map { it.average() }
        assertTrue("ten-level means rise: $tens", tens.zipWithNext().all { (a, b) -> b > a + 25 })
        val mixed = levels.count { l -> l.map { it.difficulty }.toSet().size > 1 }
        assertTrue("levels mixing tiers: $mixed", mixed >= 20)
        val first = ranked.associateWith { id -> levels.indexOfFirst { l -> l.any { it.puzzleId == id } } + 1 }
        assertTrue("every puzzle by level 15: $first", first.values.all { it in 1..15 })
        for (id in ranked) {
            val at = levels.indices.filter { n -> levels[n].any { it.puzzleId == id } }
            val gap = (at + 100).zipWithNext().maxOf { (a, b) -> b - a }
            assertTrue("$id is away $gap levels in a row", gap <= 15)
        }
        // After the spread, the decks: every puzzle in about 2 * poolSize / 5 levels, as deals 1 and 2.
        val window = (2 * ranked.size + Levels.SIZE - 1) / Levels.SIZE + 1
        val after = (101..400 + window).map { ids(Levels.plan(it, ranked, 3)) }
        for (start in 0..300) assertEquals("levels ${start + 101}+", ranked.toSet(), after.subList(start, start + window).flatten().toSet())
    }

    private companion object {
        val GOLDEN_3 = listOf(
            "chess/S atoms/S codeword/S tents/S whoswho/S",
            "shikaku/S pointers/S cipher/S dominoes/S lamps/S",
            "kings/S mosaic/S tally/S mines/S inequality/S",
            "lits/S regions/S pearls/S sand/S snap/S",
            "chess/S atoms/S codeword/S tents/S pointers/S",
            "whoswho/S shikaku/S dominoes/S lamps/S inequality/S",
            "cipher/S kings/S mosaic/S tally/S mines/S",
            "atoms/S lits/S regions/S pearls/S snap/S",
            "chess/S tents/S shikaku/S sets/S mambo/S",
            "codeword/S pointers/S target/S pipes/S sudoku/S",
            "whoswho/S kings/S tally/S mines/S nonogram/S",
            "atoms/S tents/S wordwheel/S cages/S words/S",
            "chess/S shikaku/S dominoes/S tower/S mini/S",
            "regions/S pearls/S sand/S snap/S sets/S",
            "inequality/S lits/S target/S pipes/S mambo/S",
            "kings/S tally/S sudoku/S nonogram/S wordwheel/S",
            "lamps/S snap/S sets/S cages/S words/S",
            "cipher/S mines/S lits/S tower/S chess/H",
            "pearls/S sand/S pipes/S mambo/S mini/S",
            "regions/S target/S sudoku/S nonogram/S wordwheel/S",
            "tally/S inequality/S snap/S cages/S words/S",
            "mosaic/S sand/S sets/S tower/S atoms/H",
            "mines/S mambo/S nonogram/S chess/H mini/S",
            "snap/S target/S pipes/S wordwheel/S shikaku/H",
            "whoswho/S lamps/S words/S pointers/H codeword/H",
            "sets/S sudoku/S cages/S tower/S tents/H",
            "inequality/S mambo/S chess/H mini/S atoms/H",
            "tally/S nonogram/S wordwheel/S shikaku/H dominoes/H",
            "target/S pipes/S words/S cipher/H mines/H",
            "sets/S sudoku/S tower/S pointers/H codeword/H",
            "mambo/S cages/S chess/H mini/S kings/H",
            "inequality/S nonogram/S atoms/H cipher/H dominoes/H",
            "pearls/S pipes/S shikaku/H mines/H lits/H",
            "wordwheel/S words/S tower/S pointers/H lamps/H",
            "regions/S chess/H mini/S sand/H mosaic/H",
            "nonogram/S cages/S cipher/H tents/H sudoku/H",
            "mambo/S atoms/H shikaku/H dominoes/H codeword/H",
            "wordwheel/S chess/H pointers/H target/H sets/H",
            "words/S tower/S mines/H sand/H snap/H",
            "cages/S mini/S tents/H whoswho/H lamps/H",
            "atoms/H cipher/H shikaku/H dominoes/H codeword/H",
            "chess/H pointers/H mines/H target/H kings/H",
            "tower/S sets/H lits/H snap/H tally/H",
            "mini/S cipher/H sand/H sudoku/H whoswho/H",
            "dominoes/H codeword/H tents/H mines/H lamps/H",
            "atoms/H target/H sets/H kings/H mosaic/H",
            "shikaku/H pointers/H sand/H tally/H inequality/H",
            "mini/S sudoku/H whoswho/H pipes/H pearls/H",
            "dominoes/H snap/H lits/H mosaic/H wordwheel/H",
            "mines/H sets/H kings/H lamps/H regions/H",
        )

        /** Levels 98-103: the spread's end, then deck 1 on the curve. */
        val GOLDEN_3_TURN = listOf(
            "lits/E pipes/E wordwheel/E words/E mambo/E",
            "pointers/E mosaic/E whoswho/E regions/E mini/E",
            "sets/E inequality/E cages/E tower/E pearls/E",
            "sand/E words/H regions/H mosaic/E pointers/H",
            "pearls/E tally/H tents/H nonogram/H sets/E",
            "codeword/E tower/E snap/H whoswho/H cages/H",
        )


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
