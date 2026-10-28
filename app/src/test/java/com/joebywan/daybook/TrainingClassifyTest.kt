package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * For the classification in docs/TRAINING_SPEC.md: run with `DAYBOOK_TRAINING_CLASSIFY=1` (and optionally
 * `DAYBOOK_TRAINING_CLASSIFY_ONLY=<id,id>`). For every puzzle with hints, the first [SEEDS] practice seeds' boards per
 * tier are walked by the puzzle's own hints (`teach`, then `apply`, as a player taking every hint would, stopping at a
 * mistake, the fallback or no step), and per technique: on how many boards of each tier the walk reaches it, and the
 * time to the first such position on the easiest tier that has one (generating and walking every seed before it: what a
 * first practice board would cost).
 */
class TrainingClassifyTest {
    private val seeds = (System.getenv("DAYBOOK_TRAINING_CLASSIFY_SEEDS") ?: "30").toInt()

    @Test fun classify() {
        assumeTrue(System.getenv("DAYBOOK_TRAINING_CLASSIFY") != null)
        val only = System.getenv("DAYBOOK_TRAINING_CLASSIFY_ONLY")?.split(',')?.toSet()
        for (p in PuzzleRegistry.all) {
            if (!p.offersHints || (only != null && p.id !in only)) continue
            val names = techniqueNamesOf(p.id).keys.toList()
            val counts = LinkedHashMap<String, IntArray>()
            val first = LinkedHashMap<String, String>()
            val stops = sortedMapOf<String, Int>()
            for ((ti, tier) in Difficulty.entries.withIndex()) {
                var elapsed = 0.0
                for (k in 0 until seeds) {
                    val a = System.nanoTime()
                    var s = p.generate(Training.seed(k), tier)
                    val used = LinkedHashSet<String>()
                    var steps = 0
                    var stop = "solved"
                    while (!s.solved) {
                        val d = p.teach(s)
                        if (d == null) { stop = "none"; break }
                        if (d.mistake) { stop = "mistake"; break }
                        if (d.fallback) { stop = "fallback"; break }
                        val next = d.apply(s)
                        if (next == s || ++steps > 2000) { stop = "stuck"; break }
                        if (used.add(d.technique) && d.technique !in first) {
                            first[d.technique] = "$tier ${"%.0f".format(elapsed + (System.nanoTime() - a) / 1e6)} ms (seed $k)"
                        }
                        s = next
                    }
                    stops.merge("${tier.name[0]}:$stop", 1, Int::plus)
                    elapsed += (System.nanoTime() - a) / 1e6
                    for (t in used) counts.getOrPut(t) { IntArray(3) }[ti]++
                }
                println("CLASSIFY ${p.id} $tier: mean board+walk ${"%.0f".format(elapsed / seeds)} ms")
            }
            println("CLASSIFY ${p.id} stops: $stops")
            for (t in (names + counts.keys).distinct()) {
                val c = counts[t] ?: IntArray(3)
                println("CLASSIFY ${p.id} $t: of $seeds S/H/E ${c.joinToString("/")}; first ${first[t] ?: "never"}")
            }
        }
    }
}
