package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Training
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Training's costs, for docs/TRAINING_SPEC.md (Measured): run with `DAYBOOK_TRAINING_MEASURE=1`. Per course, the time to
 * find the first 30 practices of each technique and how many seeds that took; then how many of the first 60 seeds'
 * boards on each tier reach each technique, and which Expert boards need them all (the final board's candidates).
 */
class TrainingMeasureTest {
    @Test fun measure() {
        assumeTrue(System.getenv("DAYBOOK_TRAINING_MEASURE") != null)
        for (c in Training.COURSES) {
            for (t in c.techniques) {
                val t0 = System.nanoTime()
                val found = c.practices(t).take(30).toList()
                val ms = (System.nanoTime() - t0) / 1e6
                println("MEASURE ${c.id} $t: ${found.size} practices, ${found.distinctBy { it.board }.size} distinct, ${"%.0f".format(ms)} ms")
            }
            for (tier in Difficulty.entries) {
                val counts = HashMap<String, Int>()
                val all = ArrayList<Int>()
                for (k in 0 until 60) {
                    val used = c.techniquesUsed(c.puzzle.generate(Training.seed(k), tier))
                    for (t in used) counts.merge(t, 1, Int::plus)
                    if (used.containsAll(c.techniques)) all += k
                }
                println("MEASURE ${c.id} $tier of 60: " + c.techniques.joinToString { "$it=${counts[it] ?: 0}" } + "; all: $all")
            }
        }
    }
}
