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
 *
 * **Deal 3, the spread** ([spread]): levels 1-[SPREAD_LEVELS] are not dealt from decks at all. Every puzzle-tier has a
 * measured difficulty ([LevelSpread], generated; `docs/LEVELS_SPEC.md`, "Level difficulty"), and each level takes the five
 * boards, from five puzzles, that best match a target rising evenly from the gentlest score to the hardest, so tiers mix
 * across puzzles (one puzzle's Expert beside another's Standard) and the curve above does not apply. Puzzles are met on
 * Standard in [LEARN] order, quickest to learn first, all by level 15. Levels after that
 * are dealt from decks 1, 2, ... as before, on the curve, deck 1 repaired against the spread's last two levels.
 */
object Levels {

    const val SIZE = 5

    /** Every level number the app plans or accepts from storage or a link; far past any player, and `SIZE * level` never overflows. */
    val LEVELS: IntRange = 1..10_000

    /**
     * Append-only. Its order is part of the contract: a new puzzle goes on the end and nothing is removed or
     * reordered, or every level not yet opened changes and every opened one plans differently.
     */
    val POOL: List<String> = listOf(
        "sudoku", "kings", "mambo", "pipes", "shikaku", "mosaic", "sets", "atoms", "snap", "lits",
        "tower", "words", "nonogram", "inequality", "chess", "tents", "regions", "cages", "target",
        "cipher", "wordwheel", "tally", "sand", "pointers", "dominoes",
        "mines", "lamps",
        "codeword",
        "pearls",
        "whoswho",
        "mini",
    )

    /** Deck 0, levels 1-5: the first this many pool ids, every one with a walkthrough. Never change it. */
    const val STARTER = 25

    /** Deal 1: deck 0 shuffled whole. Deal 2: deck 0 in [GENTLE] bands. Deal 3: the [spread]. A new player gets [LATEST]. */
    const val LATEST = 3

    /** Deal 3's levels planned by difficulty; later ones are dealt from decks. */
    const val SPREAD_LEVELS = 100

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
    private const val SALT_SPREAD = 0x737072656164L // "spread"

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
        require(poolSize in (if (deal == 3) LevelSpread.RANKED else STARTER)..POOL.size) { "pool size $poolSize outside the range for deal $deal" }
        return plan(level, POOL.take(poolSize), deal)
    }

    /** [plan] over any pool, so tests can append puzzles that do not exist yet. */
    internal fun plan(level: Int, pool: List<String>, deal: Int = LATEST): List<LevelBoard> {
        require(level in LEVELS) { "no level $level" }
        require(pool.size >= STARTER)
        require(deal in 1..LATEST) { "no deal $deal" }
        if (deal == 3) {
            require(pool.take(LevelSpread.RANKED) == POOL.take(LevelSpread.RANKED)) { "deal 3 needs the ranked puzzles first" }
            if (level <= SPREAD_LEVELS) return spread[level - 1].map { (id, t) -> LevelBoard(id, t, SeedHash.level(level, id, t)) }
        }
        // Deal 3 after the spread reads the stream from deck 1 on: its level 101 is the stream's level 6.
        val at = if (deal == 3) level - SPREAD_LEVELS + STARTER / SIZE else level
        val decks = HashMap<Int, List<String>>() // looked up by key only; its order never reaches an Rng
        val ids = (SIZE * (at - 1) until SIZE * at).map { pos ->
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
        else if (k == 0 && deal == 3) spread.takeLast(STARTER / SIZE).flatMap { level -> level.map { it.first } }
        else Rng(SeedHash.levelPick(k, SALT_DECK)).shuffled(if (k == 0) pool.take(STARTER) else pool)

    private fun gentleDeck(): List<String> {
        val rng = Rng(SeedHash.levelPick(0, SALT_GENTLE))
        val left = GENTLE.toMutableList()
        return BANDS.flatMap { band ->
            rng.shuffled(left.filter { GENTLE.indexOf(it) < band }).take(SIZE).also { left -= it }
        }
    }

    /**
     * Deal 3's levels 1-[SPREAD_LEVELS], each five (puzzle, tier) pairs, gentlest first. Built once, in order, from
     * [LevelSpread] and [LEARN] alone. Level `n` aims at a target `T` rising evenly from the mean Standard of the five
     * quickest to learn to the mean of the five hardest Experts. Each puzzle not in the level before offers its tier nearest `T`; they queue
     * by staleness (never met first, quickest to learn first; then the longest away, blurred by up to [SPREAD_BLUR]
     * levels by the level's own `Rng`), and the level is the five of the first [SPREAD_QUEUE] whose mean is nearest `T`,
     * each board's own distance counting a little ([SPREAD_LEAN]). A puzzle away [SPREAD_GAP] levels must come back.
     *
     * Learning cost: a puzzle not yet met may join only once its place in [LEARN] is inside a window that opens on the
     * first [LEARN_FROM] and takes in all of them by level [LEARN_BY], and must join by level `1 + r * SPREAD_INTRO /
     * puzzles` (`r` its place). So the first levels are the quickest to learn, on Standard, and the term is gone by
     * about level 15, after which only the measured score counts. Integer arithmetic only.
     */
    private val spread: List<List<Pair<String, Difficulty>>> by lazy {
        val score = LevelSpread.SCORE
        val ids = Rng(SeedHash.levelPick(0, SALT_SPREAD)).shuffled(POOL.take(LevelSpread.RANKED))
        val learn = ids.associateWith { LEARN.indexOf(it).toLong() }
        val lo5 = LEARN.take(SIZE).sumOf { score.getValue(it)[0] }.toLong() // five times the first target
        val hi5 = score.values.map { it[2] }.sortedDescending().take(SIZE).sum().toLong() // five times the last target
        val last = HashMap<String, Int>() // looked up by key only
        var before = emptySet<String>()
        (1..SPREAD_LEVELS).map { n ->
            val target = 99 * lo5 + (hi5 - lo5) * (n - 1) // 495 times the target, so nothing is rounded
            fun scaled(id: String, t: Int) = 99L * SIZE * score.getValue(id)[t]
            // A puzzle is met on Standard; after that, its tier nearest the target.
            fun tierFor(id: String): Int = if (id !in last) 0 else (0..2).minBy { kotlin.math.abs(scaled(id, it) - target) }
            val window = LEARN_FROM * (LEARN_BY - 1L) + (ids.size - LEARN_FROM) * (n - 1L)
            val rng = Rng(SeedHash.levelPick(n, SALT_SPREAD))
            val key = ids.associateWith { id -> last[id]?.let { 10_000L + SIZE * it + rng.nextInt(SIZE * SPREAD_BLUR) } ?: learn.getValue(id) }
            val queue = ids.filter { it !in before && (it in last || learn.getValue(it) * (LEARN_BY - 1) < window) }
                .sortedBy { key.getValue(it) }.take(SPREAD_QUEUE)
            val must = queue.filter { id ->
                last[id]?.let { n - it >= SPREAD_GAP } ?: ((n - 1L) * ids.size >= learn.getValue(id) * SPREAD_INTRO)
            }.take(SIZE)
            val s = queue.map { scaled(it, tierFor(it)) }
            var best = -1L
            var pick = IntArray(0)
            val c = IntArray(SIZE)
            fun choose(from: Int, depth: Int) {
                if (depth == SIZE) {
                    if (must.any { m -> c.none { queue[it] == m } }) return
                    val sum = c.sumOf { s[it] }
                    val cost = 10 * kotlin.math.abs(sum - SIZE * target) + SPREAD_LEAN * c.sumOf { kotlin.math.abs(s[it] - target) }
                    if (best < 0 || cost < best) { best = cost; pick = c.copyOf() }
                    return
                }
                for (i in from..queue.size - SIZE + depth) { c[depth] = i; choose(i + 1, depth + 1) }
            }
            choose(0, 0)
            val level = pick.map { queue[it] to Difficulty.entries[tierFor(queue[it])] }.sortedBy { (id, t) -> score.getValue(id)[t.ordinal] }
            level.forEach { last[it.first] = n }
            before = level.map { it.first }.toSet()
            level
        }
    }

    /**
     * Deal 3's learning order, quickest to learn first: `PuzzleOrder.GENTLEST` as it was on 2026-10-26, frozen here (as
     * [GENTLE] is) so a later edit to the home grid's order cannot move a plan. Never change it.
     */
    val LEARN: List<String> = listOf(
        "pointers", "pipes", "snap", "sudoku", "words", "sand", "wordwheel", "mini", "target", "nonogram", "mines",
        "kings", "mambo", "mosaic", "sets", "inequality", "tents", "lamps", "regions", "tally",
        "shikaku", "atoms", "lits", "chess", "cages", "cipher", "dominoes", "codeword", "pearls", "whoswho", "tower",
    )

    private const val SPREAD_QUEUE = 16
    private const val SPREAD_LEAN = 3 // tenths: a board's own distance from the target counts 0.3 of the level's
    private const val SPREAD_GAP = 15
    private const val SPREAD_INTRO = 13
    private const val SPREAD_BLUR = 2
    private const val LEARN_FROM = 10
    private const val LEARN_BY = 15

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
