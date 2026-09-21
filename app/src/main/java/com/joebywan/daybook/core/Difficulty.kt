package com.joebywan.daybook.core

/**
 * Three tiers, matching how daily puzzles are usually laid out: one warm-up, one real, one nasty.
 * Each [PuzzleType] decides for itself what the tiers mean — bigger grid, more colours, fewer
 * clues, whatever fits that puzzle.
 */
enum class Difficulty(val label: String) {
    STANDARD("Standard"),
    HARD("Hard"),
    EXPERT("Expert");

    companion object {
        fun fromKey(key: String): Difficulty = entries.firstOrNull { it.name == key } ?: STANDARD
    }
}
