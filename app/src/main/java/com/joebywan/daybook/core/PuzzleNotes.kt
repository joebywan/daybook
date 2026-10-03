package com.joebywan.daybook.core

/**
 * Longest note, in characters. The finished frame wraps text rather than clipping it, and the existing
 * lines top out near 58, so a note this long stays on one line on a phone.
 */
const val NOTE_MAX = 64

/**
 * One true technique tip per puzzle, hand-checked against the rules and the teachers. Keyed by puzzle id;
 * a puzzle without an entry fails `PuzzleNotesTest`. No history or attribution: only things the rules make true.
 */
private val NOTES: Map<String, List<String>> = mapOf(
    "sudoku" to listOf(
        "A digit with one place left in its box goes there.",
        "A cell with only one candidate left is settled.",
        "A row with one empty cell has its missing digit decided.",
        "Two cells sharing the same two candidates own those digits.",
        "A box digit confined to one row is out of that row elsewhere.",
    ),
    "kings" to listOf(
        "A region with a single square left must hold its king.",
        "A king crosses off its eight neighbours.",
        "A region inside one row owns that row's king.",
        "If a king leaves a row or colour with no square, it is wrong.",
    ),
    "mambo" to listOf(
        "Two matching cells side by side: both ends are the other symbol.",
        "Same symbol at both ends of a gap: the middle one differs.",
        "A row with all its suns placed has only moons left.",
        "An = or x links two cells: settle one and you settle both.",
    ),
    "pipes" to listOf(
        "An opening that would leave the grid is never right.",
        "One network only: a closed ring cut off from the rest is wrong.",
        "Tiles beside ones already set often have a forced turn.",
    ),
    "shikaku" to listOf(
        "A number with one rectangle that fits: draw it.",
        "A 1 is its own rectangle, so draw those first.",
        "Squares every rectangle of a number covers can be claimed now.",
        "A lonely square points to the one number that can reach it.",
    ),
    "mosaic" to listOf(
        "A fill that merges with neighbours covers more ground.",
        "The fill limit is the minimum: seek the fill that swallows most.",
        "There is no spare fill, so a wasted one is not made up.",
    ),
    "sets" to listOf(
        "Each trait is all alike or all different: never two and one.",
        "Any two cards fix the one third card that completes a set.",
        "Cards are never used up: one card can be in several sets.",
    ),
    "atoms" to listOf(
        "An atom with one possible neighbour bonds only to it.",
        "Bonds never cross, so a bond rules out the lines it cuts.",
        "A cluster closed off from the other atoms is wrong.",
    ),
    "snap" to listOf(
        "A square with one way in or out must be an end of the line.",
        "Work back from the highest number as well as from 1.",
        "Count the squares between two numbers to check a route.",
    ),
    "lits" to listOf(
        "A full 2x2 block is never shaded: use it to rule squares out.",
        "If every shape a region can take shares a square, shade it.",
        "Same-letter tetrominoes may not touch, even across regions.",
    ),
    "tower" to listOf(
        "Filled pip: right colour, right slot. Hollow: wrong slot.",
        "Change one peg per guess and the pips show what it did.",
        "Colours may repeat: count the pips before ruling one out.",
        "A guess that could be the answer beats one that cannot.",
    ),
    "words" to listOf(
        "A green square fixes that letter in that place for good.",
        "Yellow means the letter is in the word, just not there.",
        "Open with common, different letters to learn the most.",
        "A guess that could still be the word can win on the spot.",
    ),
    "nonogram" to listOf(
        "If clues and gaps fill a line exactly, fill it completely.",
        "In a 10-square line, a 6 always fills the middle two squares.",
        "When a run is complete, cross the squares on each side.",
        "Crosses are only notes; they never count against you.",
    ),
    "chess" to listOf(
        "Count the king's flight squares before anything else.",
        "A check is often the key; a quiet move can be too.",
        "Ask what every reply allows, not only what you threaten.",
        "A move that spoils the mate is allowed; Undo takes it back.",
    ),
    "inequality" to listOf(
        "A sign's narrow end points at the smaller digit.",
        "A square below a neighbour can never hold the top digit.",
        "In a chain a < b < c, a is at most two below the top digit.",
        "A row or column with one gap left names its digit.",
    ),
)

/** The note for [puzzleId] on [epochDay]: pure, so the same day always says the same thing. Null for an unknown id. */
fun puzzleNote(puzzleId: String, epochDay: Long): String? {
    val notes = NOTES[puzzleId] ?: return null
    return notes[(epochDay + puzzleId.sumOf { it.code }).mod(notes.size.toLong()).toInt()]
}

internal fun notesOf(puzzleId: String): List<String> = NOTES[puzzleId] ?: emptyList()
