package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Kings

/**
 * TEMPORARY web-only stand-in for app/'s `core/PuzzleRegistry.kt`, which cannot compile for the web
 * until every puzzle it names is on the `sharedFromApp` list in web/build.gradle.kts.
 *
 * Once all eleven puzzles are there: delete this file, drop its line from web/build.gradle.kts and
 * add "com/joebywan/daybook/core/PuzzleRegistry.kt" to `sharedFromApp` instead. The shell reads the
 * puzzle list only through `PuzzleRegistry`, so nothing else changes. Keep the API identical to the
 * real one meanwhile.
 */
object PuzzleRegistry {

    val all: List<PuzzleType> = listOf(
        Kings,
    )

    fun byId(id: String): PuzzleType? = all.firstOrNull { it.id == id }

    /** Puzzle featured as "today's headline" — rotates daily but deterministically. */
    fun featured(epochDay: Long): PuzzleType = all[(epochDay.mod(all.size.toLong())).toInt()]
}
