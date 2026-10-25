package com.joebywan.daybook.puzzles

/**
 * Who's Who's vocabulary: the names and the five categories a board draws from, and how a clue says each item.
 *
 * Plain, screened words with no brand and nothing regional. **The lists' order is part of the contract**: a board
 * stores indices into them ([WhosWhoState.groups], [WhosWhoState.items]) and the generator draws by index, so
 * reordering, adding or removing an entry changes saved games and every daily board (`BoardPinTest` goes red
 * on purpose). Append-only, and only with a parity re-pin.
 */
internal object WhosWhoWords {

    /** The people. Short, easy to tell apart, no two sharing an initial. */
    val NAMES = listOf(
        "Ann", "Ben", "Cara", "Dev", "Eve", "Finn", "Gus", "Hana", "Ivy", "Jon", "Kai", "Leo",
        "Mia", "Nora", "Omar", "Pia", "Raj", "Sam", "Tess", "Uma", "Vic", "Zoe",
    )

    /** House numbers: category 0, on every board, always the first column. */
    const val HOUSE = 0
    const val COLOUR = 1
    const val PET = 2
    const val JOB = 3
    const val DRINK = 4
    const val MUSIC = 5

    /** The categories a board may draw besides the house, by id. */
    val ATTRIBUTES = listOf(COLOUR, PET, JOB, DRINK, MUSIC)

    /** Column headers, by category id. */
    val HEADERS = listOf("House", "Colour", "Pet", "Job", "Drink", "Music")

    /** Five words per category, by id; a board takes four or five of them in this order. */
    val WORDS = listOf(
        listOf("1", "2", "3", "4", "5"),
        listOf("red", "blue", "green", "yellow", "white"),
        listOf("cat", "dog", "fish", "bird", "rabbit"),
        listOf("baker", "doctor", "farmer", "pilot", "teacher"),
        listOf("tea", "coffee", "milk", "juice", "water"),
        listOf("piano", "violin", "drums", "guitar", "harp"),
    )

    private val PLAYERS = listOf("the pianist", "the violinist", "the drummer", "the guitarist", "the harpist")

    /** What one of a category is called in a sentence ("Ann has no pet left"), by id. */
    val NOUNS = listOf("house", "colour", "pet", "job", "drink", "instrument")

    /**
     * How good a subject the category makes, lower first: a clue between two items puts the better one first, so it
     * reads "The baker lives in the red house" rather than "Whoever lives in the red house is the baker".
     */
    private val RANK = listOf(6, 5, 3, 1, 4, 2)

    /** Names are category -1 here: what a clue item is, as the sentence needs it. */
    const val NAME = -1

    fun rank(category: Int): Int = if (category == NAME) 0 else RANK[category]

    /** The subject of a sentence: "Ann", "the baker", "the cat owner", "whoever lives in house 2". */
    fun who(category: Int, word: String): String = when (category) {
        NAME -> word
        HOUSE -> "whoever lives in house $word"
        COLOUR -> "whoever lives in the $word house"
        PET -> "the $word owner"
        JOB -> "the $word"
        DRINK -> "the $word drinker"
        else -> PLAYERS[WORDS[MUSIC].indexOf(word)]
    }

    /** "is Ann", "lives in house 2", "has the cat"... */
    fun has(category: Int, word: String): String = when (category) {
        NAME -> "is $word"
        HOUSE -> "lives in house $word"
        COLOUR -> "lives in the $word house"
        PET -> "has the $word"
        JOB -> "is the $word"
        DRINK -> "drinks $word"
        else -> "plays the $word"
    }

    /** The negative of [has]. */
    fun hasNot(category: Int, word: String): String = when (category) {
        NAME -> "is not $word"
        HOUSE -> "does not live in house $word"
        COLOUR -> "does not live in the $word house"
        PET -> "does not have the $word"
        JOB -> "is not the $word"
        DRINK -> "does not drink $word"
        else -> "does not play the $word"
    }

    /** "has either the cat or the dog". */
    fun either(category: Int, a: String, b: String): String = when (category) {
        NAME -> "is either $a or $b"
        HOUSE -> "lives in either house $a or house $b"
        COLOUR -> "lives in either the $a or the $b house"
        PET -> "has either the $a or the $b"
        JOB -> "is either the $a or the $b"
        DRINK -> "drinks either $a or $b"
        else -> "plays either the $a or the $b"
    }

    /** After "could": "have the cat", "live in house 2", "be Ann". */
    fun base(category: Int, word: String): String = when (category) {
        NAME -> "be $word"
        HOUSE -> "live in house $word"
        COLOUR -> "live in the $word house"
        PET -> "have the $word"
        JOB -> "be the $word"
        DRINK -> "drink $word"
        else -> "play the $word"
    }

    /** The item as a thing to cross off: "the cat", "house 2", "tea", "the red house", "Ann". */
    fun thing(category: Int, word: String): String = when (category) {
        NAME, DRINK -> word
        HOUSE -> "house $word"
        COLOUR -> "the $word house"
        else -> "the $word"
    }

    /** The other end of "next to" and "left of": the house itself for a colour ("next to the red house"). */
    fun near(category: Int, word: String): String = if (category == COLOUR) "the $word house" else who(category, word)
}
