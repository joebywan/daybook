package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/**
 * A Quartets board (`docs/QUARTETS_SPEC.md`): sixteen words hiding four groups of four. Words are numbered by their
 * place in [words] and a set of them is a bit mask. There is no stored answer: [titles] are every bank category that
 * fits three or more of the words, [masks] which words each fits, and the rules ([QuartetsLogic]) decide everything
 * from those.
 *
 * [found] holds the groups accepted, in the order found; [misses] the fours refused since the last [retry].
 */
@Serializable
data class QuartetsState(
    val words: List<String>,
    val titles: List<String>,
    val masks: List<Int>,
    val found: List<Int> = emptyList(),
    val misses: List<Int> = emptyList(),
    override val moves: Int = 0,
) : PuzzleState {

    /** Every word on the board. */
    val all: Int get() = (1 shl words.size) - 1

    /** The words in no found group. */
    val remaining: Int get() = found.fold(all) { acc, g -> acc and g.inv() }

    val livesLeft: Int get() = QuartetsLogic.LIVES - misses.size

    override val solved: Boolean get() = QuartetsLogic.isSolved(this)

    override val failed: Boolean get() = !solved && livesLeft <= 0

    /**
     * Submits four words. Null (nothing happens, nothing is spent) when [mask] is not four words still on the board,
     * repeats a four refused since the last retry, or the board is over. Otherwise one move: the four is set aside
     * when [QuartetsLogic.accepts] it, else it costs a life.
     */
    fun guess(mask: Int): QuartetsState? {
        if (solved || failed) return null
        if (mask.countOneBits() != QuartetsLogic.GROUP || mask and remaining != mask) return null
        if (mask in misses) return null
        return if (QuartetsLogic.accepts(this, mask)) {
            copy(found = found + mask, moves = moves + 1)
        } else {
            copy(misses = misses + mask, moves = moves + 1)
        }
    }

    /** The same board afresh: nothing found, every life back. One move. */
    fun retry(): QuartetsState = copy(found = emptyList(), misses = emptyList(), moves = moves + 1)
}

/** One category of the bank: what the player is shown, its level (1 plain, 2 a second look, 3 wordplay) and members. */
class QuartetsCategory(val title: String, val level: Int, val words: List<String>) {
    /** Membership only; never iterated, so no hash order reaches anything. */
    private val members: Set<String> = words.toHashSet()

    fun fits(word: String): Boolean = word in members
}

/** The parsed bank of categories (`QuartetsWords.CATEGORIES` when shipped; tests pass their own lines). */
class QuartetsBank(categories: List<QuartetsCategory>) {

    /** Sorted by title, so a board's [QuartetsState.titles] come out sorted. */
    val categories: List<QuartetsCategory> = categories.sortedBy { it.title }

    companion object {
        /** Lines in the generated format: `Title|level|word,word,...`. */
        fun parse(lines: List<String>): QuartetsBank = QuartetsBank(
            lines.map { line ->
                val parts = line.split('|')
                require(parts.size == 3) { "not Title|level|words: $line" }
                QuartetsCategory(parts[0], parts[1].trim().toInt(), parts[2].split(',').map { it.trim() }.filter { it.isNotEmpty() })
            },
        )

        val shipped: QuartetsBank by lazy { parse(QuartetsWords.CATEGORIES) }
    }
}

object QuartetsLogic {
    const val LIVES = 4
    const val GROUP = 4
    const val WORDS = 16

    /** Highlight index of the Submit button; tiles are 0..15. */
    const val SUBMIT = 100

    /** Draws a board may take before [generateVerified] gives up. */
    const val ATTEMPTS = 400

    /** The verdict of [split]. Only [ExactlyOne] carries groups; nothing here is ever a guess. */
    sealed interface Split {
        object None : Split
        data class ExactlyOne(val groups: List<Int>) : Split
        object Several : Split
    }

    /**
     * What a tier deals: categories of [levels], at least one of level [needsLevel], and spares plus phantoms
     * ([Overlap.total]) from [minOverlap] to [maxOverlap]. The first [linked] categories drawn after the first are
     * drawn among those sharing a word with one already drawn (when any does), which is where spares come from. The
     * one place to tune a tier.
     */
    class Spec(val levels: IntRange, val needsLevel: Int, val minOverlap: Int, val maxOverlap: Int, val linked: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(levels = 1..1, needsLevel = 1, minOverlap = 0, maxOverlap = 0, linked = 0)
        Difficulty.HARD -> Spec(levels = 1..2, needsLevel = 2, minOverlap = 1, maxOverlap = 2, linked = 1)
        Difficulty.EXPERT -> Spec(levels = 1..3, needsLevel = 3, minOverlap = 3, maxOverlap = Int.MAX_VALUE, linked = 3)
    }

    /**
     * How much a board's words overlap: a spare is a word that fits an answer group's category but belongs to another
     * group; a phantom is a category that is no answer group's yet fits three or more of the words.
     */
    data class Overlap(val spares: Int, val phantoms: Int) {
        val total: Int get() = spares + phantoms
    }

    /** Four words on the board that all fit one category. */
    fun valid(s: QuartetsState, mask: Int): Boolean =
        mask.countOneBits() == GROUP && mask and s.all == mask && s.masks.any { it and mask == mask }

    /**
     * Every way to cut [remaining] into fours each inside one of [masks]. Exhaustive, no budget: it stops only once it
     * has seen two splits, which already settles the verdict.
     */
    fun split(masks: List<Int>, remaining: Int): Split {
        val found = splits(masks, remaining, limit = 2)
        return when (found.size) {
            0 -> Split.None
            1 -> Split.ExactlyOne(found[0])
            else -> Split.Several
        }
    }

    /** Valid, and what is left still splits: no answer is lost by taking it. */
    fun accepts(s: QuartetsState, mask: Int): Boolean =
        valid(s, mask) && mask and s.remaining == mask && split(s.masks, s.remaining and mask.inv()) !is Split.None

    /** Refused, and three of its four are in one group of the split of what is left. */
    fun oneAway(s: QuartetsState, mask: Int): Boolean {
        if (mask.countOneBits() != GROUP || accepts(s, mask)) return false
        val split = split(s.masks, s.remaining) as? Split.ExactlyOne ?: return false
        return split.groups.any { (it and mask).countOneBits() == GROUP - 1 }
    }

    /** The category a found group is shown under: of those it fits, the one fitting fewest words, then by title. */
    fun titleOf(s: QuartetsState, mask: Int): String {
        var best = -1
        for (i in s.masks.indices) {
            if (s.masks[i] and mask != mask) continue
            if (best < 0 || s.masks[i].countOneBits() < s.masks[best].countOneBits()) best = i
        }
        return if (best < 0) "" else s.titles[best]
    }

    /** The found groups are valid, disjoint and cover every word. */
    fun isSolved(s: QuartetsState): Boolean {
        var union = 0
        for (g in s.found) {
            if (!valid(s, g) || union and g != 0) return false
            union = union or g
        }
        return s.words.isNotEmpty() && union == s.all
    }

    /** The split of what remains, for a lost board: the one split, else the first of several, else nothing. */
    fun reveal(s: QuartetsState): List<Int> = splits(s.masks, s.remaining, limit = 1).firstOrNull() ?: emptyList()

    /** Spares and phantoms of a board whose words split exactly one way; null when they do not. */
    fun overlap(s: QuartetsState): Overlap? {
        val groups = (split(s.masks, s.all) as? Split.ExactlyOne)?.groups ?: return null
        var spares = 0
        var phantoms = 0
        for (m in s.masks) {
            val inside = groups.filter { m and it == it }
            if (inside.isEmpty()) {
                if (m.countOneBits() >= GROUP - 1) phantoms++
            } else {
                for (g in inside) spares = spares or (m and g.inv())
            }
        }
        return Overlap(spares.countOneBits(), phantoms)
    }

    fun generateVerified(seed: Long, difficulty: Difficulty, bank: QuartetsBank = QuartetsBank.shipped): QuartetsState? =
        deal(seed, difficulty, bank)?.state

    fun generate(seed: Long, difficulty: Difficulty): QuartetsState =
        generateVerified(seed, difficulty) ?: lastResort(difficulty)

    /** A proved board and the draws it took (1 is the first). */
    class Dealt(val state: QuartetsState, val attempts: Int)

    /**
     * Draws until a board splits exactly one way and its overlap fits the tier. Lists and sorted orders only reach the
     * [Rng]: the bank is in title order, members in the bank's order.
     */
    fun deal(seed: Long, difficulty: Difficulty, bank: QuartetsBank): Dealt? {
        val spec = specFor(difficulty)
        val allowed = bank.categories.filter { it.level in spec.levels && it.words.size >= GROUP }
        val needed = allowed.filter { it.level == spec.needsLevel }
        if (needed.isEmpty() || allowed.size < GROUP) return null
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            val drawn = mutableListOf(rng.pick(needed))
            while (drawn.size < GROUP) {
                val open = allowed.filter { c -> drawn.none { it === c } }
                val linked = open.filter { c -> drawn.any { d -> d.words.any { c.fits(it) } } }
                drawn += rng.pick(if (drawn.size <= spec.linked && linked.isNotEmpty()) linked else open)
            }
            val chosen = drawn.flatMap { rng.shuffled(it.words).take(GROUP) }
            if (chosen.distinct().size != WORDS) continue
            val state = board(rng.shuffled(chosen), bank.categories)
            val overlap = overlap(state) ?: continue
            if (overlap.total in spec.minOverlap..spec.maxOverlap) return Dealt(state, attempt + 1)
        }
        return null
    }

    /** The board for [words]: every category fitting three or more of them, in the order given (title order). */
    fun board(words: List<String>, categories: List<QuartetsCategory>): QuartetsState {
        val titles = ArrayList<String>()
        val masks = ArrayList<Int>()
        for (c in categories) {
            var m = 0
            for (i in words.indices) if (c.fits(words[i])) m = m or (1 shl i)
            if (m.countOneBits() >= GROUP - 1) {
                titles += c.title
                masks += m
            }
        }
        return QuartetsState(words, titles, masks)
    }

    /**
     * A fixed board per tier, written out so it never depends on the shipped bank: Standard no overlap; Hard MERCURY
     * also a planet (one spare); Expert MERCURY a planet, GOLD a metal and a night-sky phantom (three).
     */
    fun lastResort(difficulty: Difficulty): QuartetsState {
        val planets = QuartetsCategory("Planets", 1, listOf("earth", "mars", "mercury", "saturn", "venus"))
        val birds = QuartetsCategory("Birds", 1, listOf("crow", "eagle", "owl", "robin"))
        val fruit = QuartetsCategory("Fruit", 1, listOf("apple", "lemon", "pear", "plum"))
        return when (difficulty) {
            Difficulty.STANDARD -> board(
                listOf("mars", "owl", "tin", "plum", "earth", "copper", "robin", "apple",
                    "zinc", "venus", "pear", "crow", "saturn", "iron", "lemon", "eagle"),
                listOf(birds, fruit, QuartetsCategory("Metals", 1, listOf("copper", "iron", "tin", "zinc")), planets),
            )
            Difficulty.HARD -> board(
                listOf("mercury", "owl", "pear", "mars", "iron", "robin", "lemon", "earth",
                    "tin", "crow", "apple", "venus", "copper", "eagle", "plum", "saturn"),
                listOf(birds, fruit, QuartetsCategory("Metals", 1, listOf("copper", "iron", "mercury", "tin")), planets),
            )
            Difficulty.EXPERT -> board(
                listOf("gold", "mars", "owl", "star", "mercury", "robin", "sword", "earth",
                    "iron", "cat", "crow", "venus", "tin", "saturn", "eagle", "copper"),
                listOf(
                    birds,
                    QuartetsCategory("In the night sky", 1, listOf("mars", "star", "venus")),
                    QuartetsCategory("Metals", 1, listOf("copper", "gold", "iron", "mercury", "tin")),
                    planets,
                    QuartetsCategory("___fish", 3, listOf("cat", "gold", "star", "sword")),
                ),
            )
        }
    }

    /** Up to [limit] splits of [remaining]: the lowest word left goes in some four, every such four tried in turn. */
    private fun splits(masks: List<Int>, remaining: Int, limit: Int): List<List<Int>> {
        val fours = ArrayList<Int>()
        for (m in masks) foursOf(m and remaining, fours)
        val candidates = fours.sorted().distinct()
        val out = ArrayList<List<Int>>()
        val path = ArrayList<Int>()
        fun walk(left: Int) {
            if (out.size >= limit) return
            if (left == 0) {
                out += path.toList()
                return
            }
            val low = left and -left
            for (q in candidates) {
                if (q and low == 0 || q and left != q) continue
                path += q
                walk(left and q.inv())
                path.removeAt(path.lastIndex)
                if (out.size >= limit) return
            }
        }
        if (remaining.countOneBits() % GROUP == 0) walk(remaining)
        return out
    }

    /** Every four-word subset of [mask], added to [into]. */
    private fun foursOf(mask: Int, into: MutableList<Int>) {
        if (mask.countOneBits() < GROUP) return
        val bits = (0 until 32).filter { mask and (1 shl it) != 0 }.map { 1 shl it }
        for (a in bits.indices) for (b in a + 1 until bits.size) for (c in b + 1 until bits.size) for (d in c + 1 until bits.size) {
            into += bits[a] or bits[b] or bits[c] or bits[d]
        }
    }
}
