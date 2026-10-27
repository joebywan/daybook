package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Training
import org.junit.Assume.assumeTrue
import org.junit.Test

class TrainingMeasureTest {
    @Test fun measure() {
        assumeTrue(System.getenv("DAYBOOK_MEASURE") != null)
        val seeds = (System.getenv("DAYBOOK_SEEDS") ?: "60").toInt()
        for (c in Training.COURSES.drop(1)) for (tier in Difficulty.entries) {
            val counts = HashMap<String, Int>()
            val t0 = System.nanoTime()
            val all = ArrayList<Int>()
            for (k in 0 until seeds) {
                val s = c.puzzle.generate(Training.seed(k), tier)
                val used = c.techniquesUsed(s)
                for (t in used) counts.merge(t, 1, Int::plus)
                if (used.containsAll(c.techniques)) all += k
            }
            val ms = (System.nanoTime() - t0) / 1e6
            println("MEASURE ${c.id} $tier ${"%.0f".format(ms)} ms/$seeds seeds: " + c.techniques.joinToString { "$it=${counts[it] ?: 0}" })
            println("MEASURE   all: $all")
        }
    }
}
