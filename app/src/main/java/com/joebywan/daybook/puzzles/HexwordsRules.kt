package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/** One word the player took: [word], traced through hexes [path] in order (hexes numbered in reading order). */
@Serializable
data class HexTake(val word: String, val path: List<Int>)

@Serializable
data class HexwordsState(
    /** Hexes in each row, top down; each row sits centred on the one above, so neighbouring rows differ by one. */
    val rows: List<Int>,
    val letters: String,
    val target: Int,
    val taken: List<HexTake> = emptyList(),
    override val moves: Int = 0,
) : PuzzleState {
    val score: Int get() = taken.sumOf { HexLogic.points(it.word.length) }
    val left: Int get() = maxOf(0, target - score)

    /** The rule: the words taken reach the target. */
    override val solved: Boolean get() = score >= target

    /** Bit `i` set while hex `i` still holds its letter. */
    val free: Long get() {
        var m = (1L shl letters.length) - 1
        for (t in taken) for (i in t.path) m = m and (1L shl i).inv()
        return m
    }

    fun take(word: String, path: List<Int>): HexwordsState = copy(taken = taken + HexTake(word, path), moves = moves + 1)

    /** The board with the words from [k] on put back, as one move. */
    fun withoutFrom(k: Int): HexwordsState = copy(taken = taken.take(k), moves = moves + 1)
}

/** A word laid on the board: its hexes in order, the same as a mask, and what it scores. */
class HexRoad(val word: String, val path: IntArray, val mask: Long) {
    val points: Int get() = HexLogic.points(word.length)
}

/** Whether a position can reach a score. Only [Yes] carries a road; [Truncated] is not a [No]. */
sealed interface HexReach {
    data class Yes(val road: List<HexRoad>) : HexReach
    data object No : HexReach
    data object Truncated : HexReach
}

/** The most a position can score. Only [Proved] carries a number. */
sealed interface HexBest {
    data class Proved(val points: Int, val road: List<HexRoad>) : HexBest
    data object Truncated : HexBest
}

/**
 * An exact search over which roads to take from the hexes left: the first free hex is on some road, or it is wasted.
 * The bound is each free hex's best share of any road through it. [budget] counts positions, the same on every platform,
 * and running out is its own verdict. The memo is probed, never iterated.
 */
class HexSearch(val roads: List<HexRoad>, n: Int, private val budget: Int) {
    private val first = Array(n) { ArrayList<HexRoad>() }
    private val share = IntArray(n)
    private val failed = HashMap<Long, Int>()
    var nodes = 0
        private set
    private var out = false

    init {
        for (r in roads.sortedWith(compareByDescending<HexRoad> { it.points }.thenBy { it.word })) {
            first[r.mask.countTrailingZeroBits()] += r
            for (h in r.path) share[h] = maxOf(share[h], r.points * SCALE / r.path.size)
        }
    }

    private fun walk(free: Long, need: Int, road: ArrayList<HexRoad>): Boolean {
        if (need <= 0) return true
        var ub = 0
        var f = free
        while (f != 0L) {
            ub += share[f.countTrailingZeroBits()]
            f = f and (f - 1)
        }
        if (ub < need * SCALE) return false
        if (need >= (failed[free] ?: Int.MAX_VALUE)) return false // what cannot reach n cannot reach more
        if (++nodes > budget) {
            out = true
            return false
        }
        val h = free.countTrailingZeroBits()
        for (r in first[h]) {
            if (r.mask and free != r.mask) continue
            road += r
            if (walk(free xor r.mask, need - r.points, road)) return true
            road.removeAt(road.size - 1)
            if (out) return false
        }
        if (walk(free and (1L shl h).inv(), need, road)) return true
        if (out) return false
        failed[free] = need
        return false
    }

    fun reach(free: Long, need: Int): HexReach {
        val road = ArrayList<HexRoad>()
        val yes = walk(free, need, road)
        return when {
            yes -> HexReach.Yes(road)
            out -> HexReach.Truncated
            else -> HexReach.No
        }
    }

    /** Climbs from [start], which a road must already reach, until nothing more can be reached. */
    fun best(free: Long, start: Int = 1): HexBest {
        var points = 0
        var road = emptyList<HexRoad>()
        var need = start
        while (true) {
            when (val v = reach(free, need)) {
                is HexReach.Yes -> {
                    road = v.road
                    points = v.road.sumOf { it.points }
                    need = points + 1
                }
                HexReach.No -> return HexBest.Proved(points, road)
                HexReach.Truncated -> return HexBest.Truncated
            }
        }
    }

    private companion object {
        const val SCALE = 420 // every length's share is a whole number
    }
}

internal object HexLogic {

    /** 3, 5, 8, 12, 17 points for three to seven letters. */
    fun points(length: Int): Int = when (length) {
        3 -> 3
        4 -> 5
        5 -> 8
        6 -> 12
        7 -> 17
        else -> 0
    }

    const val MIN_WORD = 3
    const val MAX_WORD = 7

    /** Doubled coordinates: a hex's x is twice its place in the row less the row's width, so rows centre on each other. */
    fun neighbours(rows: List<Int>): Array<IntArray> {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        rows.forEachIndexed { y, w -> repeat(w) { c -> xs += 2 * c - w; ys += y } }
        return Array(xs.size) { a ->
            xs.indices.filter { b ->
                val dx = xs[a] - xs[b]
                val dy = ys[a] - ys[b]
                (dy == 0 && (dx == 2 || dx == -2)) || ((dy == 1 || dy == -1) && (dx == 1 || dx == -1))
            }.toIntArray()
        }
    }

    /** Every way to trace a word of [words] on the hexes of [free], in list order. */
    fun roads(letters: String, nb: Array<IntArray>, free: Long, words: List<String>): List<HexRoad> {
        val bag = IntArray(26)
        for (i in letters.indices) if (free shr i and 1L == 1L) bag[letters[i] - 'a']++
        val out = ArrayList<HexRoad>()
        val need = IntArray(26)
        val path = IntArray(MAX_WORD)
        for (word in words) {
            var fits = true
            for (c in word) if (++need[c - 'a'] > bag[c - 'a']) fits = false
            for (c in word) need[c - 'a'] = 0
            if (!fits) continue
            fun trace(k: Int, at: Int, used: Long) {
                path[k] = at
                if (k == word.length - 1) {
                    out += HexRoad(word, path.copyOf(word.length), used)
                    return
                }
                for (b in nb[at]) {
                    val bit = 1L shl b
                    if (free and bit != 0L && used and bit == 0L && letters[b] == word[k + 1]) trace(k + 1, b, used or bit)
                }
            }
            for (i in letters.indices) if (letters[i] == word[0] && free shr i and 1L == 1L) trace(0, i, 1L shl i)
        }
        return out
    }

    val gridWords: List<String> by lazy { (MIN_WORD..MAX_WORD).flatMap { WheelWords.grid(it) } }
    val acceptedWords: List<String> by lazy { gridWords + WordWheelWords.BONUS.filter { it.length in MIN_WORD..MAX_WORD } }

    fun isWord(word: String): Boolean = word.length in MIN_WORD..MAX_WORD && WheelWords.isWord(word)

    /** A tier: the block's rows, the longest planted word, and which simple player the target sets out to beat. */
    class Spec(val rows: List<Int>, val longest: Int, val difficulty: Difficulty)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(listOf(3, 4, 5, 4, 3), 5, difficulty)
        Difficulty.HARD -> Spec(listOf(5, 6, 7, 6, 5), 6, difficulty)
        Difficulty.EXPERT -> Spec(listOf(5, 6, 7, 8, 7, 6, 5), 7, difficulty)
    }

    const val ATTEMPTS = 400
    const val GREEDY_TRIES = 12
    const val BUDGET = 2_000_000

    /**
     * The target: on Standard what a biggest-word-first player makes on a middling try; on Hard one more than its best try; on
     * Expert one more than a player who also looks one word ahead. Computed from the board's own roads and [rng], in list order.
     */
    fun targetFor(roads: List<HexRoad>, all: Long, difficulty: Difficulty, rng: Rng): Int {
        val tries = List(GREEDY_TRIES) { greedy(roads, all, rng) }.sorted()
        return when (difficulty) {
            Difficulty.STANDARD -> tries[tries.size / 2]
            Difficulty.HARD -> tries.last() + 1
            Difficulty.EXPERT -> maxOf(tries.last(), careful(roads, all)) + 1
        }
    }

    /**
     * Plant words over the block, find every road the common words make, set the target, and ship only if the exact search
     * says [HexReach.Yes]: common words alone reach the target. A target it cannot reach, or a search out of budget, is
     * another draw.
     */
    fun generateVerified(seed: Long, difficulty: Difficulty): HexwordsState? {
        val spec = specFor(difficulty)
        val nb = neighbours(spec.rows)
        val n = spec.rows.sum()
        val all = (1L shl n) - 1
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            val (letters, _) = plant(spec.rows, nb, spec.longest, rng) ?: continue
            val roads = roads(letters, nb, all, gridWords)
            val target = targetFor(roads, all, difficulty, rng)
            if (HexSearch(roads, n, BUDGET).reach(all, target) is HexReach.Yes) return HexwordsState(spec.rows, letters, target)
        }
        return null
    }

    const val LAST_RESORT_SEED = 1L

    /** The board for the day nothing was proved (a year per tier never reaches it, `FallbackTest`). */
    fun lastResort(difficulty: Difficulty): HexwordsState =
        generateVerified(LAST_RESORT_SEED, difficulty) ?: error("the fixed last-resort seed no longer proves a board")

    /** A greedy player: the biggest word left, ties by [rng] (or list order), until nothing is left. */
    fun greedy(roads: List<HexRoad>, free0: Long, rng: Rng? = null): Int {
        var free = free0
        var alive = roads.filter { it.mask and free == it.mask }
        var total = 0
        while (alive.isNotEmpty()) {
            val top = alive.maxOf { it.points }
            val ties = alive.filter { it.points == top }
            val r = if (rng == null) ties[0] else ties[rng.nextInt(ties.size)]
            total += r.points
            free = free xor r.mask
            alive = alive.filter { it.mask and free == it.mask }
        }
        return total
    }

    /** A careful player: tries each word, follows it greedily, takes the first word of the best line. */
    fun careful(roads: List<HexRoad>, free0: Long): Int {
        var free = free0
        var alive = roads.filter { it.mask and free == it.mask }
        var total = 0
        while (alive.isNotEmpty()) {
            var pick = alive[0]
            var best = -1
            for (r in alive) {
                val v = r.points + greedy(alive, free xor r.mask)
                if (v > best) {
                    best = v
                    pick = r
                }
            }
            total += pick.points
            free = free xor pick.mask
            alive = alive.filter { it.mask and free == it.mask }
        }
        return total
    }

    /** Words of three to seven letters laid end to end over every hex; null when the walk boxes itself in. */
    fun plant(rows: List<Int>, nb: Array<IntArray>, longest: Int, rng: Rng): Pair<String, List<IntArray>>? {
        val n = rows.sum()
        val letters = CharArray(n) { ' ' }
        val paths = ArrayList<IntArray>()
        var free = (1L shl n) - 1
        fun degree(h: Int) = nb[h].count { free shr it and 1L == 1L }
        while (free != 0L) {
            val cells = (0 until n).filter { free shr it and 1L == 1L }
            val start = cells.minByOrNull { degree(it) }!!
            val want = rng.nextInt(longest - MIN_WORD + 1) + MIN_WORD
            val path = arrayListOf(start)
            free = free xor (1L shl start)
            while (path.size < want) {
                val next = nb[path.last()].filter { free shr it and 1L == 1L }
                if (next.isEmpty()) break
                val low = next.minOf { degree(it) }
                val ties = next.filter { degree(it) == low }
                val h = ties[rng.nextInt(ties.size)]
                path += h
                free = free xor (1L shl h)
            }
            if (path.size < MIN_WORD) return null
            val list = WheelWords.grid(path.size)
            val word = list[rng.nextInt(list.size)]
            // The walk hugs the edges, so half run backwards or a top row would read straight off as a word.
            val forwards = rng.nextInt(2) == 0
            path.forEachIndexed { i, h -> letters[h] = if (forwards) word[i] else word[word.length - 1 - i] }
            paths += path.toIntArray()
        }
        return letters.concatToString() to paths
    }
}
