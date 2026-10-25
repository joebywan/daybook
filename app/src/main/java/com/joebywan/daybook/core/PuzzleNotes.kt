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
    "regions" to listOf(
        "A square touching a 3 can never be a 3, even at a corner.",
        "A region of two squares holds a 1 and a 2, nothing else.",
        "If all but one square in a region touch a 4, the 4 goes there.",
        "Count what a square's region and its neighbours already hold.",
    ),
    "tents" to listOf(
        "A square with no tree beside it can never hold a tent.",
        "A tent's eight neighbours are all grass.",
        "A tree with one free square beside it: a tent there.",
        "A row at zero is all grass.",
    ),
    "cages" to listOf(
        "A cage of two totalling 3 can only be 1 and 2.",
        "A cage of two totalling 17 can only be 8 and 9.",
        "A cage of three totalling 24 can only be 7, 8 and 9.",
        "A cage never repeats a digit, even across a box edge.",
        "A row, column or box adds up to 45: use it to find a lone cell.",
    ),
    "target" to listOf(
        "Work backwards: which two numbers could make the target?",
        "Make a big product near the target, then add or subtract.",
        "Dividing can shrink a large tile into a handy small one.",
        "You never have to use every number.",
        "Subtract or divide, and the bigger tile goes first for you.",
    ),
    "cipher" to listOf(
        "A total longer than the addends starts with a carried 1.",
        "Two digits and a carry make at most 19, so a carry is 0 or 1.",
        "X + Y ending in X: Y is 0, or 9 with a carry coming in.",
        "Work from the units: each column tells the next its carry.",
        "No word starts with 0, so 0 hides in a word's later letters.",
    ),
    "wordwheel" to listOf(
        "The longest slot always uses every letter on the wheel.",
        "A crossing letter cuts a slot down to a handful of words.",
        "Shuffle the wheel when a word will not come.",
        "A bonus word rules that word out for every slot.",
        "Plurals and -ED or -ING forms are never grid words.",
    ),
    "tally" to listOf(
        "A number bigger than what its line still needs must go.",
        "If removing a number would leave too little, it stays.",
        "Circled numbers that already make the target: the rest go.",
        "List the ways a short line can make its target.",
    ),
    "sand" to listOf(
        "Pour onto a matching colour before using an empty tube.",
        "An empty tube is your spare room: try to keep one free.",
        "Uncover a colour only when it has somewhere to go.",
        "A finished tube never needs touching again.",
    ),
    "pointers" to listOf(
        "An arrow with an empty line to the edge can always go.",
        "Clearing an arrow never blocks another: free stays free.",
        "Follow a blocked arrow's line to find what to clear first.",
        "An arrow on the edge pointing outwards is always free.",
    ),
    "dominoes" to listOf(
        "A square with one free neighbour must pair with it.",
        "A one-square region tells you that square's pips outright.",
        "The tray holds one of each domino: count what is left.",
        "A big total over two squares needs big halves on both.",
    ),
    "pearls" to listOf(
        "A black pearl on the edge of the board runs two squares inward.",
        "Three white pearls in a row can never share one straight line.",
        "A square with only one way in is never on the loop.",
        "Never join the two ends of a line unless it is the whole loop.",
    ),
)

/** The note for [puzzleId] on [epochDay]: pure, so the same day always says the same thing. Null for an unknown id. */
fun puzzleNote(puzzleId: String, epochDay: Long): String? {
    val notes = NOTES[puzzleId] ?: return null
    return notes[(epochDay + puzzleId.sumOf { it.code }).mod(notes.size.toLong()).toInt()]
}

internal fun notesOf(puzzleId: String): List<String> = NOTES[puzzleId] ?: emptyList()
