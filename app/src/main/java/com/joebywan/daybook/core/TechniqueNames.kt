package com.joebywan.daybook.core

/** Longest technique name, so "You learned <name>." stays within two lines on a phone. */
const val TECHNIQUE_NAME_MAX = 40

/**
 * What the finished frame calls each teacher's techniques (`Deduction.technique`), keyed by puzzle id: a short noun
 * phrase in the words the hints use, so the name is the one the player was taught. Mistakes and fallbacks have none.
 *
 * Each puzzle's list is in the order praise prefers, the last the most worth naming: the teacher's ladder, simplest
 * first, with steps that only point at a word or a guess (Lexicon's, Tower's, Word Wheel's clue, Mosaic's fill within
 * the limit) moved to the front. `TechniqueNamesTest` fails for a teacher's technique without a name.
 * Steps in [NOT_PRAISED] keep their name but are never praised.
 */
private val NAMES: Map<String, Map<String, String>> = mapOf(
    "sudoku" to linkedMapOf(
        "full-house" to "the last square in a row, column or box",
        "hidden-single-box" to "a digit's only place in a box",
        "hidden-single-line" to "a digit's only place in a row or column",
        "naked-single" to "a square with one digit left",
        "locked-candidates" to "a digit locked to one line or box",
        "naked-pair" to "two squares sharing two digits",
        "hidden-pair" to "two digits with only two places",
        "what-if" to "a what-if",
    ),
    "kings" to linkedMapOf(
        "last-square" to "the last open square for a king",
        "locked-to-a-line" to "a colour locked to one line",
        "would-empty" to "a king that would leave no room",
        "confined" to "colours confined to as many lines",
        "what-if" to "a what-if",
    ),
    "mambo" to linkedMapOf(
        "pair" to "a pair of matching symbols",
        "sandwich" to "a gap between two matching symbols",
        "link" to "an = or x link",
        "quota-met" to "a line with all of one symbol placed",
        "quota-almost-met" to "a line one symbol short",
        "what-if" to "a what-if",
    ),
    "pipes" to linkedMapOf(
        "border" to "the edge rule",
        "set-neighbour" to "matching a set neighbour's opening",
        "whichever-way" to "a tile forced whichever way it turns",
        "no-loop" to "avoiding a closed loop",
    ),
    "shikaku" to linkedMapOf(
        "only-fits" to "a number with one rectangle that fits",
        "only-reaches" to "a square only one number can reach",
        "common-cells" to "squares every rectangle covers",
        "what-if" to "a what-if",
    ),
    "mosaic" to linkedMapOf(
        "keeps-in-limit" to "a fill that stays within the limit",
        "finish" to "the fill that finishes the board",
        "count" to "counting fills against colours",
        "biggest-swallow" to "filling to swallow the most areas",
        "centre" to "growing from the centre",
    ),
    "sets" to linkedMapOf(
        "continue" to "building on a pick",
        "fresh" to "a set of unused cards",
        "reuse" to "that a used card still counts",
    ),
    "atoms" to linkedMapOf(
        "one-neighbour" to "an atom with one neighbour left",
        "all-forced" to "an atom that needs every bond",
        "at-least-one" to "a neighbour that must take a bond",
        "crossing" to "a bond blocked by a crossing",
        "isolation" to "a bond that would close a group off",
        "only-way-out" to "a group's only way out",
        "what-if" to "a what-if",
    ),
    "lits" to linkedMapOf(
        "whole-region" to "a region of exactly four squares",
        "overlap" to "where every shape overlaps",
        "avoid-2x2" to "avoiding a 2x2 block",
        "letter-clash" to "keeping matching letters apart",
        "neighbour" to "a shape that would block a neighbour",
        "what-if" to "a what-if",
    ),
    "tower" to linkedMapOf(
        "opener" to "a good opening guess",
        "submit" to "a guess ready to try",
        "consistent-guess" to "a guess that fits every score",
        "only-code-left" to "the only code left",
        "one-change" to "comparing guesses one slot apart",
        "only-colour-left" to "the only colour left for a slot",
        "pips-accounted" to "accounting for every pip",
        "what-if" to "a what-if",
    ),
    "words" to linkedMapOf(
        "opener" to "a good opening word",
        "submit" to "a row that fits every mark",
        "best-guess" to "the word that narrows it most",
        "only-word-left" to "the one word left",
        "pinned-letter" to "a letter pinned to its slot",
    ),
    "nonogram" to linkedMapOf(
        "full-line" to "a clue that fills its line",
        "line-finished" to "a line whose clue is complete",
        "overlap" to "the overlap of a clue's slides",
        "line-logic" to "what a line's marks leave possible",
    ),
    "inequality" to linkedMapOf(
        "last-square" to "the last square in a line",
        "only-digit" to "a square with one digit left",
        "only-place" to "a digit's only place in a line",
        "sign-bound" to "a square the signs leave one digit",
        "sign-place" to "a digit's only place, signs counted",
        "naked-pair" to "two squares sharing two digits",
        "hidden-pair" to "two digits with only two places",
    ),
    "chess" to linkedMapOf(
        "mate-in-one" to "a mate in one",
        "forcing-key" to "a forcing key move",
        "quiet-key" to "a quiet key move",
    ),
    "tents" to linkedMapOf(
        "no-tree" to "grass where no tree is beside it",
        "line-done" to "a line with all its tents",
        "line-full" to "a line with just room for its tents",
        "touch" to "grass around a tent",
        "one-spot" to "a tree with one spot left",
        "shared" to "a square touching all a tree's spots",
        "served" to "a square whose trees all have tents",
    ),
    "regions" to linkedMapOf(
        "last-square" to "the last square in a region",
        "only-digit" to "a square with one digit left",
        "only-place" to "a digit's only place in a region",
        "locked-candidate" to "a digit locked beside one square",
    ),
    "cages" to linkedMapOf(
        "full-house" to "the last square in a row, column or box",
        "cage-last" to "the last square of a cage",
        "hidden-single-box" to "a digit's only place in a box",
        "hidden-single-line" to "a digit's only place in a row or column",
        "naked-single" to "a square with one digit left",
        "cage-combination" to "a cage's digit combinations",
        "forty-five" to "the 45 rule",
        "linked-totals" to "totals that share squares",
        "cage-chain" to "every cage worked through together",
    ),
    "target" to linkedMapOf(
        "step" to "a step towards the target",
        "finish" to "the step that makes the target",
    ),
    "cipher" to linkedMapOf(
        "leading-carry" to "the leading carry",
        "column" to "one column and its carry",
        "only-letter" to "the only letter left for a digit",
        "column-pair" to "two columns through their carry",
        "columns-together" to "every column worked through together",
        "pair-what-if" to "a what-if on two columns",
    ),
    "dwindle" to linkedMapOf(
        "clue" to "a word from its clue",
        "through" to "a tile only so many words reach",
        "length" to "a length only so many words fit",
    ),
    "wordwheel" to linkedMapOf(
        "clue" to "a word from its clue",
        "pinned" to "a slot only one word fits",
        "crossing" to "the word a crossing slot leaves",
    ),
    "tally" to linkedMapOf(
        "line-done" to "a line already making its target",
        "too-big" to "a number too big for its line",
        "too-much" to "a number the line can't lose",
        "all-left" to "a line where everything left stays",
        "every-way" to "the ways a line can make its target",
        "what-if" to "a what-if",
    ),
    "sand" to linkedMapOf(
        "finish" to "pouring to finish a tube",
        "gather" to "gathering onto a matching colour",
        "spare" to "using an empty tube",
        "plan" to "planning a pour ahead",
    ),
    "mines" to linkedMapOf(
        "all-found" to "a number with all its mines found",
        "all-mines" to "a number with as many squares as mines",
        "subset" to "one number's squares inside another's",
        "pair" to "two numbers that share squares",
        "count" to "counting the mines left",
        "count-edge" to "the numbers needing every mine left",
        "what-if" to "a what-if",
    ),
    "dominoes" to linkedMapOf(
        "only-partner" to "a square with one partner left",
        "only-place" to "a domino with one place left",
        "only-domino" to "the one domino that fits a square",
        "region-value" to "what a region's values allow",
        "counting" to "counting the halves left in the tray",
        "what-if" to "a what-if",
    ),
    "lamps" to linkedMapOf(
        "zero" to "the squares beside a 0",
        "done" to "a number with all its lamps",
        "room" to "a number with just enough room",
        "only-light" to "the one square that can light another",
        "starve" to "a lamp that would starve a number",
        "dark" to "a lamp that would leave a square dark",
        "what-if" to "a what-if",
    ),
    "mini" to linkedMapOf(
        "clue" to "a letter from its clue",
        "one-word" to "a word only one word fits",
        "crossing" to "the word its crossings leave",
        "all-words" to "every crossing worked through together",
    ),
    "codeword" to linkedMapOf(
        "one-word" to "a word only one word fits",
        "run-agrees" to "a letter every fitting word shares",
        "crossing" to "a number cut by every word it is in",
        "all-words" to "every word worked through together",
    ),
    "pearls" to linkedMapOf(
        "black-pearl" to "a black pearl's straight arms",
        "white-pearl" to "a white pearl's straight line",
        "white-turn" to "a white pearl's turn beside it",
        "one-way" to "a line with one way on",
        "pearl-ways" to "a pearl with two ways in",
        "dead-end" to "a dead end",
        "small-loop" to "the loop that must be one",
        "what-if" to "a what-if",
    ),
    "whoswho" to linkedMapOf(
        "clue" to "a clue that names someone",
        "only" to "the one item left",
        "link" to "two things a clue ties together",
        "house" to "where the houses allow",
        "what-if" to "a what-if",
    ),
)

/**
 * Steps that are not a skill the player now has: "You learned <name>." would not make sense of them. They point at a word,
 * a guess, a set or a move rather than teach a way of seeing one, so a solve whose hints showed only these keeps the
 * generic hint line.
 */
private val NOT_PRAISED: Map<String, Set<String>> = mapOf(
    "mosaic" to setOf("keeps-in-limit", "finish"),
    "sets" to setOf("continue", "fresh"),
    "tower" to setOf("opener", "submit", "consistent-guess", "only-code-left"),
    "words" to setOf("opener", "submit", "best-guess", "only-word-left"),
    "target" to setOf("step", "finish"),
    "wordwheel" to setOf("clue"),
    "dwindle" to setOf("clue"),
    "mini" to setOf("clue"),
    "sand" to setOf("finish"),
)

/** The techniques of [puzzleId] that praise may name. */
fun praisedTechniquesOf(puzzleId: String): Set<String> = techniqueNamesOf(puzzleId).keys - NOT_PRAISED[puzzleId].orEmpty()

/** [puzzleId]'s technique names in praise order; empty for a puzzle without a teacher. */
fun techniqueNamesOf(puzzleId: String): Map<String, String> = NAMES[puzzleId].orEmpty()

/** The name of the one of [ids] most worth naming, or null when none has a name (only mistakes, say). */
fun techniqueToPraise(puzzleId: String, ids: Collection<String>): String? =
    techniqueNamesOf(puzzleId).entries.lastOrNull { it.key in ids && it.key in praisedTechniquesOf(puzzleId) }?.value
