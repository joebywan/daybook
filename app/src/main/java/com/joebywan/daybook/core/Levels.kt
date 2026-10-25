package com.joebywan.daybook.core

/** One board of a level: which puzzle, at which tier, from which seed. */
data class LevelBoard(val puzzleId: String, val difficulty: Difficulty, val seed: Long)

/**
 * Numbered levels of five boards from five different puzzles (`docs/LEVELS_SPEC.md`). A plan is a pure
 * function of the level number and the pool size it was opened with, so level 12 is the same five boards
 * on every phone and in the browser, and nothing is shipped or stored but the pool size.
 *
 * **The deal.** The puzzles come from one endless stream of cards: deck 0 is the first [STARTER] puzzles of
 * the pool, shuffled, then decks 1, 2, ... are the whole pool (`POOL.take(poolSize)`), each shuffled by its
 * own seed. Level `n` takes stream cards `5(n-1) until 5n`. A deck is shuffled from `SeedHash.levelPick(deck)`
 * alone, so a plan builds at most three decks (the one or two it reads and the tail of the one before) and
 * level 500 costs what level 6 does; nothing is replayed from level 1.
 *
 * Where two decks meet, a puzzle near the end of one may come up again near the start of the next, inside
 * the same level or the level after. So the first ten cards of each deck are repaired: a card that clashes
 * with the previous deck's last ten is swapped with the first later card (before the deck's own last ten)
 * that does not. The last ten of a deck are never moved, which is what keeps one deck's repair from
 * depending on the deck before the one before: no chain, still pure. A clash no swap can mend is left
 * ("where avoidable"); at every pool size the tests try there is none.
 *
 * **Coverage.** Every puzzle is in every deck once, so between two of its appearances there are fewer than
 * two decks' cards: at the pool of 25 every puzzle is in any ten levels in a row. A bigger pool stretches
 * that window to about `2 * poolSize / 5` levels (ten levels hold only 50 boards).
 *
 * **Walkthroughs first.** Levels 1-5 are deck 0 exactly (25 cards), drawn from the first [STARTER] ids of
 * the pool, all of which have a walkthrough (a test checks). Pinning it to a fixed prefix rather than to
 * "has a tutorial today" keeps a plan a function of (level, poolSize, deal) alone: a walkthrough added to a later
 * puzzle cannot move an opened level, and new puzzles never reach the first five levels.
 *
 * **Deals.** How deck 0 is shuffled is the [deal], stored with each opened level (`"n|poolSize|deal"`; an entry
 * with no deal is deal 1). Deal 1 shuffles the 25 freely, so level 1 could hand a newcomer Cipher and Mate. Deal 2
 * ([GENTLE]) deals them in bands of a frozen gentlest-first order: levels 1-2 are the gentlest ten, level 3 is
 * drawn from what is left of the gentlest 17, levels 4-5 take the rest. Deck 0 feeds deck 1's repair, so the
 * deal moves every level, not just 1-5; a player keeps one deal for good (`ProgressStore.openLevel`).
 */
object Levels {

    const val SIZE = 5

    /**
     * Append-only. Its order is part of the contract: a new puzzle goes on the end and nothing is removed or
     * reordered, or every level not yet opened changes and every opened one plans differently.
     */
    val POOL: List<String> = listOf(
        "sudoku", "kings", "mambo", "pipes", "shikaku", "mosaic", "sets", "atoms", "snap", "lits",
        "tower", "words", "nonogram", "inequality", "chess", "tents", "regions", "cages", "target",
        "cipher", "wordwheel", "tally", "sand", "pointers", "dominoes", "lamps",
    )

    /** Deck 0, levels 1-5: the first this many pool ids, every one with a walkthrough. Never change it. */
    const val STARTER = 25

    /** Deal 1: deck 0 shuffled whole. Deal 2: deck 0 in [GENTLE] bands. A new player gets [LATEST]. */
    const val LATEST = 2

    /**
     * Deal 2's order for deck 0, gentlest first: `PuzzleOrder.GENTLEST` as it was on 2026-10-25, frozen here (as
     * [POOL] is) so a later edit to the home grid's order cannot move an opened level. Never change it.
     */
    val GENTLE: List<String> = listOf(
        "pointers", "pipes", "snap", "sudoku", "words", "sand", "wordwheel", "target", "nonogram", "kings",
        "mambo", "mosaic", "sets", "tower", "inequality", "tents", "regions",
        "tally", "shikaku", "atoms", "lits", "chess", "cages", "cipher", "dominoes",
    )

    /** Deal 2: level `i + 1` draws from the cards not yet dealt among the first `BANDS[i]` of [GENTLE]. */
    private val BANDS = listOf(10, 10, 17, STARTER, STARTER)

    /** Cards at each end of a deck that the repair looks at (two levels' worth). */
    private const val EDGE = 2 * SIZE

    private const val SALT_TIERS = 0x7469657273L // "tiers"
    private const val SALT_DECK = 0x6465636BL // "deck"
    private const val SALT_GENTLE = 0x67656E746C65L // "gentle"

    /** From level [from] on (until the next row), how many boards are Standard, Hard and Expert. */
    data class Step(val from: Int, val standard: Int, val hard: Int, val expert: Int)

    /** The difficulty curve; retuning it is one edit and moves only levels not yet opened. */
    val CURVE: List<Step> = listOf(
        Step(1, 5, 0, 0),
        Step(6, 4, 1, 0),
        Step(16, 3, 2, 0),
        Step(31, 2, 2, 1),
        Step(51, 1, 3, 1),
        Step(81, 0, 3, 2),
    )

    fun step(level: Int): Step = CURVE.last { it.from <= level }

    fun plan(level: Int, poolSize: Int = POOL.size, deal: Int = LATEST): List<LevelBoard> {
        require(poolSize in STARTER..POOL.size) { "pool size $poolSize outside $STARTER..${POOL.size}" }
        return plan(level, POOL.take(poolSize), deal)
    }

    /** [plan] over any pool, so tests can append puzzles that do not exist yet. */
    internal fun plan(level: Int, pool: List<String>, deal: Int = LATEST): List<LevelBoard> {
        require(level >= 1) { "levels start at 1, not $level" }
        require(pool.size >= STARTER)
        require(deal in 1..LATEST) { "no deal $deal" }
        val decks = HashMap<Int, List<String>>() // looked up by key only; its order never reaches an Rng
        val ids = (SIZE * (level - 1) until SIZE * level).map { pos ->
            val k = deckOf(pos, pool.size)
            decks.getOrPut(k) { deck(k, pool, deal) }[pos - deckStart(k, pool.size)]
        }
        val s = step(level)
        val tiers = Rng(SeedHash.levelPick(level, SALT_TIERS)).shuffled(
            List(s.standard) { Difficulty.STANDARD } + List(s.hard) { Difficulty.HARD } + List(s.expert) { Difficulty.EXPERT },
        )
        return ids.mapIndexed { i, id -> LevelBoard(id, tiers[i], SeedHash.level(level, id, tiers[i])) }
    }

    /** One line for the level card: "All Standard", "Mostly Hard, one Expert", "Two Standard, two Hard, one Expert". */
    fun summary(boards: List<LevelBoard>): String {
        val counts = Difficulty.entries.map { t -> t to boards.count { it.difficulty == t } }.filter { it.second > 0 }
        val top = counts.maxByOrNull { it.second } ?: return ""
        if (top.second == boards.size) return "All ${top.first.label}"
        fun words(list: List<Pair<Difficulty, Int>>) = list.joinToString(", ") { "${NUMBERS[it.second]} ${it.first.label}" }
        return if (top.second * 2 > boards.size) "Mostly ${top.first.label}, ${words(counts - top)}"
        else words(counts).replaceFirstChar { it.uppercaseChar() }
    }

    private val NUMBERS = listOf("no", "one", "two", "three", "four", "five")

    private fun deckStart(k: Int, poolSize: Int): Int = if (k == 0) 0 else STARTER + (k - 1) * poolSize

    private fun deckOf(pos: Int, poolSize: Int): Int = if (pos < STARTER) 0 else 1 + (pos - STARTER) / poolSize

    private fun rawDeck(k: Int, pool: List<String>, deal: Int): List<String> =
        if (k == 0 && deal == 2) gentleDeck()
        else Rng(SeedHash.levelPick(k, SALT_DECK)).shuffled(if (k == 0) pool.take(STARTER) else pool)

    private fun gentleDeck(): List<String> {
        val rng = Rng(SeedHash.levelPick(0, SALT_GENTLE))
        val left = GENTLE.toMutableList()
        return BANDS.flatMap { band ->
            rng.shuffled(left.filter { GENTLE.indexOf(it) < band }).take(SIZE).also { left -= it }
        }
    }

    private fun deck(k: Int, pool: List<String>, deal: Int): List<String> {
        val cards = rawDeck(k, pool, deal).toMutableList()
        if (k == 0) return cards
        val before = rawDeck(k - 1, pool, deal) // its last EDGE cards are never moved by its own repair
        val b = deckStart(k, pool.size)
        val prevStart = b - before.size
        // Does card c at stream position p meet itself in its own level or the one before?
        fun clashes(c: String, p: Int): Boolean {
            val from = maxOf(p - p % SIZE - SIZE, b - EDGE)
            return (from until b).any { q -> before[q - prevStart] == c }
        }
        for (i in 0 until EDGE) {
            if (!clashes(cards[i], b + i)) continue
            val j = (i + 1 until cards.size - EDGE).firstOrNull { !clashes(cards[it], b + i) } ?: continue
            cards[j] = cards[i].also { cards[i] = cards[j] }
        }
        return cards
    }
}
