package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.MiniLogic
import com.joebywan.daybook.puzzles.MiniTeacher
import com.joebywan.daybook.puzzles.WheelWords
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Builds `puzzles/MiniGrids.kt`, the grids the Mini crossword's boards come from. Not a check: it runs only when asked,
 *
 *     DAYBOOK_MINI_GRIDS=app/src/main/java/com/joebywan/daybook/puzzles/MiniGrids.kt \
 *         ./gradlew :app:testDebugUnitTest --tests '*MiniGridsBuildTest*' --rerun
 *
 * and writes the same file every time: draw `i` of a tier uses `Rng(seed(tier, i))` alone, the draws are split across
 * threads but read back in draw order, and a tier keeps its first [PER_TIER] grids in that order.
 *
 * A draw is a block pattern and a dense fill ([CrosswordFill], from Word Wheel's grid lists less [SKIP]), kept when
 * [MiniLogic.board] accepts it for the tier: every slot a different grid word with a clue, the hints' walk finishing it
 * needing exactly the tier's deepest step, and the search proving that the letters the walk read from the clues and the
 * crossings leave one grid. `DAYBOOK_MINI_SWEEP="5,4;5,5"` (with `DAYBOOK_MINI_TIER`) tries shapes and writes nothing.
 */
class MiniGridsBuildTest {

    @Test
    fun build() {
        val out = System.getenv("DAYBOOK_MINI_GRIDS")
        val sweep = System.getenv("DAYBOOK_MINI_SWEEP")
        assumeTrue("set DAYBOOK_MINI_GRIDS to build the list", out != null || sweep != null)
        val only = System.getenv("DAYBOOK_MINI_TIER")?.let { Difficulty.valueOf(it) }
        val pool = Executors.newFixedThreadPool(THREADS)
        if (sweep != null) {
            for (sh in sweep.split(";")) {
                sweepShape = sh.split(",").let { CrosswordFill.Shape(it[0].toInt(), it[1].toInt()) }
                for (i in 0 until FAILS.length()) FAILS.set(i, 0)
                println("shape $sh")
                PROBE.clear()
                buildTier(only!!, pool, limit = SWEEP_DRAWS)
                // Every fill the sweep drew, kept or not: the clue share's spread, and which rungs the walks needed.
                val ps = PROBE.toList()
                val shares = ps.map { it[0].toDouble() / it[1] }.sorted()
                fun at(q: Int) = "%.3f".format(shares[ps.size * q / 100])
                println("fills ${ps.size}: clue share p10 ${at(10)} p25 ${at(25)} p50 ${at(50)} p75 ${at(75)} p90 ${at(90)}")
                println("needs crossing ${ps.count { it[2] > 0 }}, all-words ${ps.count { it[3] > 0 }}, neither ${ps.count { it[2] == 0 && it[3] == 0 }}")
            }
            pool.shutdown()
            return
        }
        val tiers = Difficulty.entries.filter { only == null || it == only }.associateWith { tier -> buildTier(tier, pool) }
        pool.shutdown()
        File(out!!).writeText(source(tiers))
    }

    private fun buildTier(tier: Difficulty, pool: java.util.concurrent.ExecutorService, limit: Int = MAX_DRAWS): List<String> {
        val want = System.getenv("DAYBOOK_MINI_PER_TIER")?.toInt() ?: PER_TIER
        return CrosswordFill.buildTier(tier, pool, want, limit, BATCH, FAILS, stall = STALL, stats = { f, d -> stats(tier, f, d) }) { i ->
            make(tier, Rng(seed(tier, i)))
        }
    }

    private fun stats(tier: Difficulty, found: List<String>, drawn: Int) {
        if (found.isEmpty()) return
        val size = MiniLogic.specFor(tier).size
        val walks = found.map { MiniTeacher.walk(size, it)!! }
        val clueLetters = walks.map { w -> w.count { it.technique == MiniTeacher.CLUE } }
        val whites = found.map { e -> e.count { it != MiniLogic.BLACK } }
        val blacks = found.map { e -> e.count { it == MiniLogic.BLACK } }
        val slots = found.map { MiniLogic.slots(size, it).size }
        fun avg(xs: List<Int>) = "%.2f".format(xs.average())
        // The device's proof, timed on the JVM.
        val times = found.take(200).map { e -> val t0 = System.nanoTime(); MiniLogic.board(e, tier); (System.nanoTime() - t0) / 100_000 / 10.0 }.sorted()
        println("$tier board ms median ${times[times.size / 2]} p95 ${times[times.size * 95 / 100]} max ${times.last()}")
        println(
            "$tier: ${found.size} of $drawn draws; black ${avg(blacks)}, white ${avg(whites)}, slots ${avg(slots)}, clue letters ${avg(clueLetters)} " +
                "(${clueLetters.min()}-${clueLetters.max()}), share ${"%.3f".format(clueLetters.sum().toDouble() / whites.sum())}; steps " +
                MiniTeacher.TECHNIQUES.joinToString(" ") { t -> "$t ${"%.2f".format(walks.sumOf { w -> w.count { it.technique == t } }.toDouble() / walks.size)}" },
        )
    }

    private fun source(tiers: Map<Difficulty, List<String>>): String = CrosswordFill.source(
        "MiniGrids",
        listOf(
            "GENERATED by MiniGridsBuildTest from Word Wheel's grid lists. Do not edit by hand: a board is picked by",
            "index into these sorted lists, so reordering changes every daily board. An entry is the filled grid in",
            "reading order (`#` black). Each list is chunks of space-separated entries, because a JVM string constant",
            "is capped at 64 KB.",
        ),
        tiers, CHUNK,
    )

    companion object {
        const val SEED = 20261026L
        const val PER_TIER = 1000
        const val BATCH = 240
        const val MAX_DRAWS = 400_000
        const val SWEEP_DRAWS = 1440

        /** Batches in a row that find nothing new before a tier stops: its distinct grids are used up. */
        const val STALL = 40
        const val THREADS = 12
        const val CHUNK = 400
        const val BUDGET = 20_000

        @Volatile
        var sweepShape: CrosswordFill.Shape? = null

        val SHAPES = mapOf(
            Difficulty.STANDARD to CrosswordFill.Shape(maxRun = 5, blacks = 4),
            Difficulty.HARD to CrosswordFill.Shape(maxRun = 6, blacks = 8),
            Difficulty.EXPERT to CrosswordFill.Shape(maxRun = 7, blacks = 15),
        )

        /**
         * The breakfast-table test: words the grid lists keep (they are Lexicon's and Word Wheel's contract, and a guess
         * or a wheel word is never shown with a clue) that a crossword printing them as answers should not.
         */
        val SKIP = setOf(
            "abort", "abuse", "arson", "belch", "bile", "blood", "bomb", "booze", "burp", "casket", "corpse", "crime",
            "dead", "death", "demon", "devil", "die", "drug", "drunk", "dumb", "famine", "fat", "fatal", "funeral", "gore",
            "gory", "grave", "grief", "gun", "hang", "hate", "hatred", "kill", "knife", "lame", "leper", "lice", "louse",
            "maim", "morgue", "mourn", "murder", "naked", "noose", "nude", "opium", "orphan", "plague", "pus", "rifle",
            "robber", "rot", "rotten", "savage", "slain", "slay", "stab", "stench", "torture", "toxic", "tragedy", "tragic",
            "ugly", "ulcer", "urine", "vomit", "war", "widow", "wound",
        )

        /** Why draws fail, for the log: pattern, fill, board. */
        val FAILS = AtomicIntegerArray(3)

        /** Per filled grid in a sweep: clue letters, white squares, crossing steps, all-words steps. */
        val PROBE = java.util.concurrent.ConcurrentLinkedQueue<IntArray>()

        fun seed(tier: Difficulty, i: Int): Long = SEED * 1_000_003L + tier.ordinal * 10_000_019L + i

        private val INDEXES: Map<Int, CrosswordFill.Index> by lazy {
            (3..7).associateWith { l -> CrosswordFill.Index(WheelWords.grid(l).filter { it !in SKIP }) }
        }

        /** One draw: the entry, or null when the pattern, the fill or the tier's test failed. */
        fun make(tier: Difficulty, rng: Rng): String? {
            val size = MiniLogic.specFor(tier).size
            val shape = sweepShape ?: SHAPES.getValue(tier)
            val white = CrosswordFill.pattern(rng, size, 3, shape.maxRun, shape.blacks) ?: return null.also { FAILS.incrementAndGet(0) }
            val grid = CrosswordFill.fill(rng, white, size, INDEXES, BUDGET) ?: return null.also { FAILS.incrementAndGet(1) }
            val entry = String(grid)
            val walk = if (sweepShape != null) MiniTeacher.walk(size, entry) else null
            if (sweepShape != null && walk != null) {
                PROBE.add(intArrayOf(walk.count { it.technique == MiniTeacher.CLUE }, entry.count { it != '#' }, walk.count { it.technique == MiniTeacher.CROSSING }, walk.count { it.technique == MiniTeacher.ALL_WORDS }))
            }
            return entry.takeIf { MiniLogic.board(it, tier) != null } ?: null.also { FAILS.incrementAndGet(2) }
        }
    }
}
