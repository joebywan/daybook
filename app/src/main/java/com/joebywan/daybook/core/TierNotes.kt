package com.joebywan.daybook.core

/** Longest tier line, in characters: one line in the rules sheet on a phone. */
const val TIER_NOTE_MAX = 60

/**
 * What the player will see change between the tiers, one line each for Standard, Hard and Expert, keyed by puzzle id.
 * Only what shows on the board (sizes, counts, what is given) or, where every tier looks alike, which kind of step it
 * needs; never "harder", never a number the player cannot see. A puzzle without three lines fails `TierNotesTest`.
 * Taken from each puzzle's tier spec: change a tier, change its line.
 */
private val TIER_NOTES: Map<String, List<String>> = mapOf(
    "sudoku" to listOf(
        "Each digit has one place left in a row, column or box",
        "Some squares need every other digit ruled out",
        "Needs a pair, a locked digit or a what-if",
    ),
    "kings" to listOf("7x7 grid, seven colours", "8x8 grid, eight colours", "9x9 grid, nine colours"),
    "mambo" to listOf("6x6 grid", "8x8 grid", "10x10 grid"),
    "pipes" to listOf("5x7 grid", "6x9 grid", "8x11 grid"),
    "shikaku" to listOf(
        "6x7 grid, rectangles up to 6 squares",
        "8x9 grid, rectangles up to 8 squares",
        "9x11 grid, rectangles up to 10 squares",
    ),
    "mosaic" to listOf("8x12 grid, 3 colours, 4 moves", "9x14 grid, 4 colours, 5 moves", "10x16 grid, 5 colours, 6 moves"),
    "sets" to listOf("9 cards, 3 sets to find", "12 cards, 4 sets to find", "12 cards, 6 sets to find"),
    "atoms" to listOf("7x7 grid, 10 atoms", "9x9 grid, 18 atoms", "11x11 grid, 28 atoms"),
    "snap" to listOf("5x5 grid", "6x6 grid", "6x7 grid"),
    "lits" to listOf("6x6 grid", "7x7 grid", "8x8 grid"),
    "tower" to listOf("4 pegs, 5 colours, 10 guesses", "5 pegs, 6 colours, 12 guesses", "5 pegs, 8 colours, 14 guesses"),
    "words" to listOf("Five-letter words, six guesses", "Five-letter words, five guesses", "Four-letter words, seven guesses"),
    "nonogram" to listOf("5x5 grid", "10x10 grid", "15x15 grid"),
    "inequality" to listOf("4x4 grid, digits 1 to 4", "5x5 grid, digits 1 to 5", "6x6 grid, digits 1 to 6"),
    "chess" to listOf("Mate in 2", "Mate in 3", "Mate in 4"),
    "tents" to listOf("6x6 grid, 7 tents", "8x8 grid, 13 tents", "10x10 grid, 20 tents"),
    "regions" to listOf("5x5 grid", "7x7 grid", "9x9 grid"),
    "cages" to listOf(
        "Cages of 2 to 4 squares, some digits given",
        "Cages up to 5 squares, fewer digits given",
        "Cages up to 7 squares, no digits given",
    ),
    "target" to listOf(
        "Three large numbers among the six",
        "Two large numbers among the six",
        "One large number, and the answer uses all six",
    ),
    "cipher" to listOf(
        "Each digit follows from a column or two",
        "Some digits need every column at once",
        "Some digits need a what-if across two columns",
    ),
    "hexwords" to listOf(
        "19 hexes; biggest word first often gets there",
        "29 hexes; biggest word first always falls short",
        "44 hexes; looking one word ahead falls short too",
    ),
    "wordwheel" to listOf(
        "Five-letter wheel, 4 to 6 words",
        "Six-letter wheel, 6 to 8 words",
        "Seven-letter wheel, 8 to 11 words",
    ),
    "dwindle" to listOf(
        "4x4 tiles, 6 or 7 words, words up to 5 letters",
        "5x5 tiles, 10 to 12 words, words up to 6 letters",
        "6x6 tiles, 15 to 18 words, words up to 7 letters",
    ),
    "tally" to listOf("5x5 grid", "6x6 grid", "7x7 grid"),
    "sand" to listOf("5 colours, 7 tubes", "7 colours, 9 tubes", "8 colours, 10 tubes"),
    "pointers" to listOf("6x7 grid, short arrows", "12x12 grid, longer arrows", "14x17 grid, the longest arrows"),
    "dominoes" to listOf("5x5 board, 6 or 7 dominoes", "6x6 board, 9 or 10 dominoes", "7x7 board, 12 to 14 dominoes"),
    "mines" to listOf("8x8 field, 10 mines", "8x10 field, 16 mines", "8x12 field, 22 mines"),
    "lamps" to listOf("7x7 grid", "10x10 grid", "12x12 grid"),
    "codeword" to listOf("7x7 grid", "9x9 grid", "11x11 grid"),
    "pearls" to listOf("6x6 grid", "8x8 grid", "10x10 grid"),
    "whoswho" to listOf(
        "4 people, 1 thing each, clues name the person",
        "4 people, 2 things each, clues link things",
        "5 people, 2 things each, either-or clues",
    ),
    "mini" to listOf("5x5 grid, plain clues", "6x6 grid, many across clues indirect", "7x7 grid, most clues indirect"),
)

/** The three lines for [puzzleId], Standard first; empty for an unknown id. */
fun tierNotes(puzzleId: String): List<String> = TIER_NOTES[puzzleId] ?: emptyList()
