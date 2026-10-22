package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoTeacher
import org.junit.Test
import java.time.LocalDate

class DominoBenchProbe {
    @Test
    fun bench() {
        val boards = (0 until 30).map { DominoLogic.generateVerified(DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), "dominoes", Difficulty.EXPERT), Difficulty.EXPERT)!! }
        for (lvl in listOf(3, 5, 6)) {
            repeat(3) {
                val t0 = System.nanoTime()
                var ok = 0
                for (b in boards) if (DominoTeacher.solveLevel(b.layout, lvl) != null) ok++
                println("level $lvl: ${(System.nanoTime() - t0) / 1e6 / boards.size} ms/board ok=$ok")
            }
        }
        repeat(3) {
            val t0 = System.nanoTime()
            for (b in boards) DominoLogic.solutions(b.layout)
            println("solutions: ${(System.nanoTime() - t0) / 1e6 / boards.size} ms/board")
        }
    }
}

