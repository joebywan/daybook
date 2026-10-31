package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import kotlin.math.abs
import kotlinx.serialization.Serializable

/** A goal word and the one way it runs across the tiles (squares `row * width + col`, in letter order). */
@Serializable
data class DwindleGoal(val word: String, val cells: List<Int>)

/**
 * Dwindle's board: a [width] x [height] grid of [letters] (reading order, never changed), the [goals], the goal words
 * [found] and the [bonus] words so far, both in the order found. How many uses each tile has left is derived from the
 * goals not yet found, never stored; a tile with none left has vanished. The word being spelled is never state (it lives in
 * the board's `remember`), so one accepted word is one state and one undo.
 */
@Serializable
data class DwindleState(
    val width: Int,
    val height: Int,
    val letters: String,
    val goals: List<DwindleGoal>,
    val found: List<String> = emptyList(),
    val bonus: List<String> = emptyList(),
    override val moves: Int = 0,
) : PuzzleState {

    /** The rule: every goal word is found (which leaves every tile vanished; `DwindleRulesTest` holds the two together). */
    override val solved: Boolean get() = goals.all { it.word in found }

    /** Uses left per tile: how many goal words not yet found run through it. */
    fun counts(): IntArray {
        val out = IntArray(letters.length)
        for (goal in goals) if (goal.word !in found) for (c in goal.cells) out[c]++
        return out
    }

    /** The board once [word] is entered: a goal word is found, a new real word joins the bonus words, anything else changes nothing. */
    fun enter(word: String): DwindleState = when (DwindleLogic.judge(this, word)) {
        DwindleEntry.Goal -> copy(found = found + word, moves = moves + 1)
        DwindleEntry.Bonus -> copy(bonus = bonus + word, moves = moves + 1)
        else -> this
    }
}

/** What happens to a word spelled on the tiles. */
enum class DwindleEntry { Goal, Bonus, Found, NotAWord, TooShort, NotOnTiles }

/**
 * Dwindle's rules and generator, with no screen. `docs/DWINDLE_SPEC.md` holds the reasoning.
 *
 * **No order can get stuck.** Tiles do not fall, so taking tiles away only ever removes ways of spelling a word, and a board
 * ships only if every goal has exactly one way on the full grid ([proved]). A tile on a goal's way counts that goal until it
 * is found, so its way stays whole until then, whatever else has been found. Nothing is searched per position, because the
 * position cannot matter; `DwindleOracle` walks every subset of found goals anyway, on its own reading of the rules.
 *
 * **Generation:** cover, then share. The most boxed-in empty square is covered by the next word of a seed-shuffled pool (sorted
 * lists, so no hash order can reach it) that can run through it over enough tiles already lettered, keeping every word so
 * far to one way. Then words the full grid already holds, one way each, are added, those over the tiles used least first,
 * until the tier's word count.
 */
internal object DwindleLogic {

    /**
     * A tier. [minOverlap] is the share of a covering word (after the first) that must lie on tiles already lettered;
     * [minShared] the share of tiles a board must use more than once.
     */
    class Spec(
        val width: Int,
        val height: Int,
        val maxLength: Int,
        val minWords: Int,
        val maxWords: Int,
        val minOverlap: Float,
        val minShared: Float,
    )

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(4, 4, 5, 6, 7, 0.25f, 0.55f)
        Difficulty.HARD -> Spec(5, 5, 6, 10, 12, 0.3f, 0.65f)
        Difficulty.EXPERT -> Spec(6, 6, 7, 15, 18, 0.3f, 0.75f)
    }

    const val MIN_LENGTH = 3
    const val ATTEMPTS = 40

    /** How far down the pool one square is tried before the draw is abandoned: a stubborn square is cheaper to redraw than to search. */
    private const val MAX_SCAN = 1500

    fun adjacent(a: Int, b: Int, width: Int): Boolean =
        a != b && abs(a / width - b / width) <= 1 && abs(a % width - b % width) <= 1

    /** The squares touching [i], diagonals included, in reading order. */
    fun neighbours(i: Int, width: Int, height: Int): List<Int> {
        val r = i / width
        val c = i % width
        val out = ArrayList<Int>(8)
        for (rr in maxOf(r - 1, 0)..minOf(r + 1, height - 1)) for (cc in maxOf(c - 1, 0)..minOf(c + 1, width - 1)) {
            if (rr != r || cc != c) out += rr * width + cc
        }
        return out
    }

    /**
     * Every way [word] runs across the tiles [live] allows (all of them when null), counted exhaustively, no budget. Only
     * [Verdict.ExactlyOne] carries a way. On a full grid the letters bound the walk, so it is tiny.
     */
    fun ways(letters: String, width: Int, height: Int, word: String, live: IntArray? = null): Verdict<List<Int>> {
        var count = 0
        var first: List<Int> = emptyList()
        val path = IntArray(word.length)
        val used = BooleanArray(letters.length)
        fun go(i: Int, at: Int) {
            if (letters[at] != word[i] || used[at] || (live != null && live[at] == 0)) return
            path[i] = at
            if (i == word.lastIndex) {
                if (count++ == 0) first = path.toList()
                return
            }
            used[at] = true
            for (n in neighbours(at, width, height)) go(i + 1, n)
            used[at] = false
        }
        for (at in letters.indices) go(0, at)
        return Verdict.of(count) { first }
    }

    /** The first way in reading order [word] runs across the tiles still live, or null. Typing finds its tiles with this. */
    fun liveWay(s: DwindleState, word: String): List<Int>? {
        val counts = s.counts()
        val path = ArrayList<Int>(word.length)
        fun go(i: Int, at: Int): Boolean {
            if (s.letters[at] != word[i] || counts[at] == 0 || at in path) return false
            path += at
            if (i == word.lastIndex) return true
            for (n in neighbours(at, s.width, s.height)) if (go(i + 1, n)) return true
            path.removeAt(path.lastIndex)
            return false
        }
        return if (word.isNotEmpty() && s.letters.indices.any { go(0, it) }) path else null
    }

    fun judge(s: DwindleState, word: String): DwindleEntry {
        if (word.length < MIN_LENGTH) return DwindleEntry.TooShort
        if (word in s.found || word in s.bonus) return DwindleEntry.Found
        if (liveWay(s, word) == null) return DwindleEntry.NotOnTiles
        if (s.goals.any { it.word == word }) return DwindleEntry.Goal
        return if (WheelWords.isWord(word)) DwindleEntry.Bonus else DwindleEntry.NotAWord
    }

    /** The path after the finger reaches tile [i]: a live neighbour of the last tile joins, the tile before it backs out. */
    fun swipe(s: DwindleState, counts: IntArray, path: List<Int>, i: Int): List<Int> = when {
        counts[i] == 0 -> path
        path.isEmpty() -> listOf(i)
        adjacent(path.last(), i, s.width) -> swipeWheelLetter(path, i)
        else -> path
    }

    /** The path after a tap on tile [i]: the last tile comes back off, a neighbour joins, any other live tile starts again. */
    fun tap(s: DwindleState, counts: IntArray, path: List<Int>, i: Int): List<Int> = when {
        counts[i] == 0 -> path
        path.lastOrNull() == i -> path.dropLast(1)
        i in path -> path
        path.isNotEmpty() && adjacent(path.last(), i, s.width) -> path + i
        else -> listOf(i)
    }

    /** The share of tiles the goals use more than once. */
    fun sharedShare(s: DwindleState): Float = s.counts().count { it >= 2 }.toFloat() / s.letters.length

    /**
     * The proof: every tile is used, no word twice, and each goal has exactly one way on the full grid, the one stored.
     * Monotonicity (the class comment) does the rest for every order.
     */
    fun proved(s: DwindleState): Boolean =
        s.counts().all { it > 0 } && s.goals.distinctBy { it.word }.size == s.goals.size && s.goals.all { goal ->
            val v = ways(s.letters, s.width, s.height, goal.word)
            v is Verdict.ExactlyOne && v.value == goal.cells
        }

    fun generateVerified(seed: Long, difficulty: Difficulty): DwindleState? {
        val spec = specFor(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val board = build(spec, Rng(seed + attempt)) ?: continue
            if (accepts(board, spec)) return board
        }
        return null
    }

    fun accepts(s: DwindleState, spec: Spec): Boolean =
        s.goals.size in spec.minWords..spec.maxWords && sharedShare(s) >= spec.minShared && proved(s)

    fun build(spec: Spec, rng: Rng): DwindleState? {
        val size = spec.width * spec.height
        val grid = CharArray(size) { EMPTY }
        val pool = rng.shuffled((MIN_LENGTH..spec.maxLength).flatMap { WheelWords.grid(it) })
        val placed = ArrayList<DwindleGoal>()
        var cursor = 0
        while (grid.any { it == EMPTY }) {
            val target = mostBoxedIn(grid, spec)
            var found = false
            for (k in 0 until minOf(pool.size, MAX_SCAN)) {
                val word = pool[(cursor + k) % pool.size]
                if (placed.any { it.word == word }) continue
                val need = if (placed.isEmpty()) 0 else maxOf(1, (word.length * spec.minOverlap + 0.999f).toInt())
                val way = cover(grid, spec, word, target, need, rng) ?: continue
                val before = grid.copyOf()
                way.forEachIndexed { i, c -> grid[c] = word[i] }
                val candidate = placed + DwindleGoal(word, way)
                if (candidate.all { oneWay(grid, spec, it) }) {
                    placed += DwindleGoal(word, way)
                    cursor = (cursor + k + 1) % pool.size
                    found = true
                    break
                }
                before.copyInto(grid)
            }
            if (!found || placed.size > spec.maxWords) return null
        }
        share(grid.concatToString(), spec, pool, placed, rng.nextInt(spec.minWords, spec.maxWords))
        // A goal's place in the list says nothing about when it was laid: longest first, ties shuffled.
        val goals = rng.shuffled(placed).sortedByDescending { it.word.length }
        return DwindleState(spec.width, spec.height, grid.concatToString(), goals)
    }

    /** The word's way stays the only one on the letters so far (an empty square matches nothing here). */
    private fun oneWay(grid: CharArray, spec: Spec, goal: DwindleGoal): Boolean {
        val v = ways(grid.concatToString(), spec.width, spec.height, goal.word)
        return v is Verdict.ExactlyOne && v.value == goal.cells
    }

    /** The empty square with the fewest empty neighbours, the first in reading order on a tie. */
    private fun mostBoxedIn(grid: CharArray, spec: Spec): Int {
        var best = -1
        var bestFree = Int.MAX_VALUE
        for (i in grid.indices) {
            if (grid[i] != EMPTY) continue
            val free = neighbours(i, spec.width, spec.height).count { grid[it] == EMPTY }
            if (free < bestFree) {
                best = i
                bestFree = free
            }
        }
        return best
    }

    /** A way for [word] through [target], empty squares taking any letter, at least [need] of its squares already lettered. */
    private fun cover(grid: CharArray, spec: Spec, word: String, target: Int, need: Int, rng: Rng): List<Int>? {
        val path = IntArray(word.length)
        val used = BooleanArray(grid.size)
        fun reach(a: Int, b: Int) = maxOf(abs(a / spec.width - b / spec.width), abs(a % spec.width - b % spec.width))
        fun go(i: Int, at: Int, overlap: Int, through: Boolean): Boolean {
            if (used[at] || (grid[at] != EMPTY && grid[at] != word[i])) return false
            val over = overlap + if (grid[at] != EMPTY) 1 else 0
            val thru = through || at == target
            val left = word.lastIndex - i
            if (over + left < need || (!thru && reach(at, target) > left)) return false
            path[i] = at
            if (left == 0) return thru && over >= need
            used[at] = true
            for (n in rng.shuffled(neighbours(at, spec.width, spec.height))) if (go(i + 1, n, over, thru)) return true
            used[at] = false
            return false
        }
        for (start in rng.shuffled(grid.indices.toList())) if (go(0, start, 0, false)) return path.toList()
        return null
    }

    /** Adds words the full grid already holds, one way each, until [target], those over the least-used tiles first. */
    private fun share(letters: String, spec: Spec, pool: List<String>, placed: MutableList<DwindleGoal>, target: Int) {
        val uses = IntArray(letters.length)
        for (g in placed) for (c in g.cells) uses[c]++
        val options = ArrayList<DwindleGoal>()
        for (word in pool) {
            if (placed.any { it.word == word } || !WheelWords.spellable(word, letters)) continue
            val v = ways(letters, spec.width, spec.height, word)
            if (v is Verdict.ExactlyOne) options += DwindleGoal(word, v.value)
        }
        while (placed.size < target && options.isNotEmpty()) {
            val pick = options.maxBy { g -> g.cells.sumOf { c -> if (uses[c] == 1) 4L else if (uses[c] == 2) 1L else 0L } } // first of the best, in pool order
            options.remove(pick)
            placed += pick
            for (c in pick.cells) uses[c]++
        }
    }

    private const val EMPTY = '.'

    /** The seed [lastResort] lays: a fixed board per tier, proved by `DwindleRulesTest`. */
    const val LAST_RESORT_SEED = 1L

    /** The board for the day nothing was proved (a year per tier never reaches it, `FallbackTest`). */
    fun lastResort(difficulty: Difficulty): DwindleState =
        generateVerified(LAST_RESORT_SEED, difficulty) ?: error("the fixed last-resort seed no longer proves a board")
}
