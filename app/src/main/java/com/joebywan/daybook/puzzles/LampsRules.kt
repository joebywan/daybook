package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/**
 * A Lamps board in progress: white squares and walls, some walls numbered, and the player's lamps and crosses.
 *
 * Squares are numbered row by row. [walls] holds one character a square: [LampsLogic.WHITE] a white square,
 * [LampsLogic.WALL] a blank wall, `'0'..'4'` a numbered wall. [solution] is the one answer (`'1'` a lamp), kept for
 * mistakes, the fallback and tests; `solved` reads the rules. [marks] is the player's: [LampsLogic.NONE],
 * [LampsLogic.LAMP_MARK] or [LampsLogic.CROSS] (a note, never read by the rules or the teacher); walls stay
 * [LampsLogic.NONE]. What is lit, which lamps clash and which numbers are broken are derived, never stored.
 *
 * Strings rather than lists, as Tally's: a saved game keeps two dozen undo copies.
 */
@Serializable
data class LampsState(
    val size: Int,
    val walls: String,
    val solution: String,
    val marks: String = LampsLogic.NONE.toString().repeat(size * size),
    override val moves: Int = 0,
) : PuzzleState {

    /** The rules, not a comparison with [solution]. */
    override val solved: Boolean get() = LampsLogic.isSolved(size, walls, lamps())

    fun isWall(i: Int) = walls[i] != LampsLogic.WHITE

    /** The number on wall [i], or -1 for a white square or a blank wall. */
    fun numberAt(i: Int): Int = walls[i].let { if (it in '0'..'4') it - '0' else -1 }

    fun lamps(): BooleanArray = BooleanArray(size * size) { marks[it] == LampsLogic.LAMP_MARK }

    /** Every white square some lamp lights (its own square included). Derived on every call. */
    fun lit(): BooleanArray = LampsLogic.lit(size, walls, lamps())

    /** Lamps that light another lamp. */
    fun clashes(): Set<Int> = LampsLogic.clashes(size, walls, lamps())

    /** Lamps beside numbered wall [w]. */
    fun lampsBeside(w: Int): Int = LampsLogic.orth(size, w).count { marks[it] == LampsLogic.LAMP_MARK }

    /** Sets [mark] on white square [i]; `this` on a wall or when nothing changes. */
    fun withMark(i: Int, mark: Char): LampsState =
        if (isWall(i) || marks[i] == mark) this
        else copy(marks = marks.substring(0, i) + mark + marks.substring(i + 1), moves = moves + 1)
}

/** What an exhaustive count found. Only [ExactlyOne] carries an answer, so nothing else can be shipped as one. */
sealed interface LampsVerdict {
    data object None : LampsVerdict
    data class ExactlyOne(val lamps: String) : LampsVerdict
    data object Several : LampsVerdict
}

/**
 * The rules, the ladder, the exhaustive count and the generator. Nothing here touches the screen, the platform or a
 * hash container, so the same seed makes the same board on the phone and in the browser.
 *
 * ## The ladder
 * Knowledge is a grid: [OPEN], [LAMP] or [NO] per white square. A square lit by a known lamp can never hold one, so
 * it is not a *candidate*; a square's *sources* are the candidates that would light it, itself included. The facts,
 * simplest first ([nextFact], restarting after each): [ZERO], [DONE], [ROOM], [ONLY_LIGHT] (the direct rung), [STARVE]
 * and [DARK] (the look-ahead rung: what one lamp would do), [WHAT_IF] (follow a supposition at most [MAX_LINKS]
 * direct facts to a contradiction). The teacher explains exactly these, and the generator ships a board only when they
 * finish it, so the hints never run out.
 */
internal object LampsLogic {

    const val WHITE = '.'
    const val WALL = '#'

    const val NONE = '.'
    const val LAMP_MARK = 'L'
    const val CROSS = 'x'

    /** Knowledge per square. */
    const val OPEN = 0
    const val LAMP = 1
    const val NO = 2

    /** Rungs of the ladder. */
    const val DIRECT = 0
    const val LOOKAHEAD = 1
    const val WHAT_IF_RUNG = 2

    const val ZERO = "zero"
    const val DONE = "done"
    const val ROOM = "room"
    const val ONLY_LIGHT = "only-light"
    const val STARVE = "starve"
    const val DARK = "dark"
    const val WHAT_IF = "what-if"

    /** How many direct facts a what-if may follow: the sentence has to carry them all. */
    const val MAX_LINKS = 3

    // ---- geometry -----------------------------------------------------------------------------

    fun orth(n: Int, i: Int): List<Int> = buildList {
        val r = i / n
        val c = i % n
        if (r > 0) add(i - n)
        if (c > 0) add(i - 1)
        if (c < n - 1) add(i + 1)
        if (r < n - 1) add(i + n)
    }

    /** The white squares a lamp on [i] lights besides [i]: its row and column, out to a wall or the edge. */
    fun sight(n: Int, walls: String, i: Int): List<Int> = buildList {
        for ((dr, dc) in DIRS) {
            var r = i / n + dr
            var c = i % n + dc
            while (r in 0 until n && c in 0 until n && walls[r * n + c] == WHITE) {
                add(r * n + c)
                r += dr
                c += dc
            }
        }
    }

    private val DIRS = listOf(-1 to 0, 0 to -1, 0 to 1, 1 to 0)

    /** The board's geometry, worked out once: sight lines, the numbers and their white neighbours. */
    class Geo(val n: Int, val walls: String) {
        val white = BooleanArray(n * n) { walls[it] == WHITE }
        val sight: Array<IntArray> = Array(n * n) { if (white[it]) sight(n, walls, it).toIntArray() else IntArray(0) }
        val number = IntArray(n * n) { walls[it].let { ch -> if (ch in '0'..'4') ch - '0' else -1 } }
        val numbered: IntArray = (0 until n * n).filter { number[it] >= 0 }.toIntArray()
        val beside: Array<IntArray> = Array(n * n) { orth(n, it).filter { j -> white[j] }.toIntArray() }
        val whites: IntArray = (0 until n * n).filter { white[it] }.toIntArray()
        val numbersBeside: Array<IntArray> = Array(n * n) { orth(n, it).filter { j -> number[j] >= 0 }.toIntArray() }
    }

    // ---- the rules ----------------------------------------------------------------------------

    fun lit(n: Int, walls: String, lamps: BooleanArray): BooleanArray {
        val out = BooleanArray(n * n)
        for (i in 0 until n * n) if (lamps[i] && walls[i] == WHITE) {
            out[i] = true
            for (j in sight(n, walls, i)) out[j] = true
        }
        return out
    }

    fun clashes(n: Int, walls: String, lamps: BooleanArray): Set<Int> =
        (0 until n * n).filterTo(mutableSetOf()) { lamps[it] && walls[it] == WHITE && sight(n, walls, it).any { j -> lamps[j] } }

    /** Every white square lit, no lamp lighting another, every number met, no lamp on a wall. */
    fun isSolved(n: Int, walls: String, lamps: BooleanArray): Boolean {
        for (i in 0 until n * n) if (lamps[i] && walls[i] != WHITE) return false
        val lit = lit(n, walls, lamps)
        for (i in 0 until n * n) {
            val ch = walls[i]
            if (ch == WHITE && !lit[i]) return false
            if (ch in '0'..'4' && orth(n, i).count { lamps[it] } != ch - '0') return false
        }
        return clashes(n, walls, lamps).isEmpty()
    }

    // ---- knowledge ----------------------------------------------------------------------------

    /** Lit by a known lamp, per square. */
    fun litBy(g: Geo, k: IntArray): BooleanArray {
        val out = BooleanArray(g.n * g.n)
        for (i in k.indices) if (k[i] == LAMP) {
            out[i] = true
            for (j in g.sight[i]) out[j] = true
        }
        return out
    }

    private fun candidate(g: Geo, k: IntArray, lit: BooleanArray, i: Int) = g.white[i] && k[i] == OPEN && !lit[i]

    /** The candidates that would light square [y]: itself and its sight lines. */
    fun sources(g: Geo, k: IntArray, lit: BooleanArray, y: Int): List<Int> = buildList {
        if (candidate(g, k, lit, y)) add(y)
        for (j in g.sight[y]) if (candidate(g, k, lit, j)) add(j)
    }

    fun finished(g: Geo, k: IntArray): Boolean {
        val lit = litBy(g, k)
        return (0 until g.n * g.n).all { !g.white[it] || lit[it] }
    }

    /**
     * One fact: [targets] become [value] ([LAMP] or [NO]). [wall] is the number it reads (-1 for none), [seen] the
     * square it is about (the one only-light or dark protects), [lit] the squares a supposed lamp would light (look-ahead),
     * and [whatIf] the chain when the fact is a supposition.
     */
    class Fact(
        val technique: String,
        val value: Int,
        val targets: List<Int>,
        val wall: Int = -1,
        val seen: Int = -1,
        val lit: List<Int> = emptyList(),
        val whatIf: WhatIf? = null,
    )

    /** Why a supposition failed: [kind] is one of [OVER], [SHORT], [UNLIT], at square [at]. */
    class Broken(val kind: Int, val at: Int)

    const val OVER = 0
    const val SHORT = 1
    const val UNLIT = 2

    /** A supposition at [square] ([assume] [LAMP] or [NO]) that led through [links] to [broken]. */
    class WhatIf(val square: Int, val assume: Int, val links: List<Fact>, val broken: Broken)

    /** The first way [k] breaks the rules, or null while it could still be finished. */
    fun broken(g: Geo, k: IntArray): Broken? = broken(g, k, litBy(g, k))

    // Two lamps never light each other here: every lamp the ladder puts down is on a candidate (unlit), and the
    // candidates beside one wall never share a sight line, so a clash cannot arise and is not checked.
    private fun broken(g: Geo, k: IntArray, lit: BooleanArray): Broken? = broken(g, k, lit, g.numbered, g.whites)

    /** [broken] looking only at the numbered [walls] and white [squares] given (each ascending). */
    private fun broken(g: Geo, k: IntArray, lit: BooleanArray, walls: IntArray, squares: IntArray): Broken? {
        for (w in walls) {
            var on = 0
            var free = 0
            for (j in g.beside[w]) if (k[j] == LAMP) on++ else if (candidate(g, k, lit, j)) free++
            if (on > g.number[w]) return Broken(OVER, w)
            if (on + free < g.number[w]) return Broken(SHORT, w)
        }
        for (y in squares) if (!lit[y] && !hasSource(g, k, lit, y)) return Broken(UNLIT, y)
        return null
    }

    private fun hasSource(g: Geo, k: IntArray, lit: BooleanArray, y: Int): Boolean {
        if (candidate(g, k, lit, y)) return true
        for (j in g.sight[y]) if (candidate(g, k, lit, j)) return true
        return false
    }

    /** The only source of [y], -1 for none, -2 for several. */
    private fun onlySource(g: Geo, k: IntArray, lit: BooleanArray, y: Int): Int {
        var found = if (candidate(g, k, lit, y)) y else -1
        for (j in g.sight[y]) if (candidate(g, k, lit, j)) {
            if (found >= 0) return -2
            found = j
        }
        return found
    }

    /** The first fact of the ladder up to [rung] that [k] does not yet hold, or null when it has none. */
    fun nextFact(g: Geo, k: IntArray, rung: Int): Fact? = nextFact(g, k, rung, litBy(g, k))

    /** [nextFact] with [lit] already worked out from [k]. */
    /** The first direct fact among the numbered [walls] and white [squares] given (each ascending), in the ladder's order. */
    private fun directFact(g: Geo, k: IntArray, lit: BooleanArray, walls: IntArray, squares: IntArray): Fact? {
        fun cands(w: Int) = g.beside[w].filter { candidate(g, k, lit, it) }
        fun on(w: Int): Int {
            var c = 0
            for (j in g.beside[w]) if (k[j] == LAMP) c++
            return c
        }
        fun free(w: Int): Int {
            var c = 0
            for (j in g.beside[w]) if (candidate(g, k, lit, j)) c++
            return c
        }

        for (w in walls) if (g.number[w] == 0 && free(w) > 0) return Fact(ZERO, NO, cands(w), wall = w)
        for (w in walls) if (g.number[w] > 0 && on(w) == g.number[w] && free(w) > 0) return Fact(DONE, NO, cands(w), wall = w)
        for (w in walls) {
            val need = g.number[w] - on(w)
            if (need > 0 && free(w) == need) return Fact(ROOM, LAMP, cands(w), wall = w)
        }
        for (y in squares) {
            if (lit[y]) continue
            val s = onlySource(g, k, lit, y)
            if (s >= 0) return Fact(ONLY_LIGHT, LAMP, listOf(s), seen = y)
        }
        return null
    }

    private fun nextFact(g: Geo, k: IntArray, rung: Int, lit: BooleanArray): Fact? {
        directFact(g, k, lit, g.numbered, g.whites)?.let { return it }
        if (rung < LOOKAHEAD) return null
        fun cands(w: Int) = g.beside[w].filter { candidate(g, k, lit, it) }
        fun on(w: Int): Int {
            var c = 0
            for (j in g.beside[w]) if (k[j] == LAMP) c++
            return c
        }

        // What a lamp on x would light, as a stamp per square (x itself included).
        val stamp = IntArray(k.size) { -1 }
        fun mark(x: Int) {
            stamp[x] = x
            for (j in g.sight[x]) stamp[j] = x
        }
        for (x in k.indices) {
            if (!candidate(g, k, lit, x)) continue
            mark(x)
            for (w in g.numbered) {
                var lost = 0
                var free = 0
                var gain = 0
                for (j in g.beside[w]) {
                    if (j == x) gain = 1
                    if (!candidate(g, k, lit, j)) continue
                    free++
                    if (stamp[j] == x) lost++
                }
                if (lost == 0) continue
                if (on(w) + gain + free - lost < g.number[w]) {
                    val gone = g.beside[w].filter { it != x && candidate(g, k, lit, it) && stamp[it] == x }
                    return Fact(STARVE, NO, listOf(x), wall = w, lit = gone)
                }
            }
        }
        val unlit = (0 until k.size).filter { g.white[it] && !lit[it] }
        val src = Array(k.size) { IntArray(0) }
        for (y in unlit) src[y] = sources(g, k, lit, y).toIntArray()
        for (x in k.indices) {
            if (!candidate(g, k, lit, x)) continue
            mark(x)
            for (y in unlit) {
                if (stamp[y] == x) continue
                if (src[y].all { stamp[it] == x }) return Fact(DARK, NO, listOf(x), seen = y, lit = src[y].filter { it != x })
            }
        }
        if (rung < WHAT_IF_RUNG) return null

        val w = firstWhatIf(g, k) ?: return null
        return Fact(WHAT_IF, if (w.assume == LAMP) NO else LAMP, listOf(w.square), whatIf = w)
    }

    fun apply(k: IntArray, f: Fact) {
        for (i in f.targets) k[i] = f.value
    }

    /** One what-if search's working copy of the knowledge [k] and light [lit], reused across its suppositions. */
    private class Scratch(val k: IntArray, val lit: BooleanArray) {
        val t = k.copyOf()
        val l = lit.copyOf()
        val touched = BooleanArray(k.size)
        val wallMark = BooleanArray(k.size)
        val squareMark = BooleanArray(k.size)
        val walls = IntArray(k.size)
        var wallCount = 0
        val squares = IntArray(k.size)
        var squareCount = 0
        val changed = IntArray(k.size)
        var changedCount = 0

        /** Back to [k] and [lit], undoing only what the last supposition changed. */
        fun reset() {
            for (c in 0 until changedCount) {
                val i = changed[c]
                t[i] = k[i]
                l[i] = lit[i]
                touched[i] = false
            }
            for (c in 0 until wallCount) wallMark[walls[c]] = false
            for (c in 0 until squareCount) squareMark[squares[c]] = false
            changedCount = 0
            wallCount = 0
            squareCount = 0
        }
    }

    /**
     * The supposition [assume] at [square], followed at most [maxLinks] direct facts; null when it breaks nothing.
     *
     * The scratch's knowledge (and light) must hold no direct fact and break no rule, as it does wherever the ladder reaches a what-if.
     * Then only a number or a square the supposition has touched (a square whose knowledge or light changed, or one that
     * sees it) can break or yield a fact, so only those are looked at, in the ladder's own order: the same chain as a
     * scan of the whole board, for a fraction of the work.
     */
    private fun whatIf(g: Geo, z: Scratch, square: Int, assume: Int, maxLinks: Int): WhatIf? {
        z.reset()
        val t = z.t
        val l = z.l
        fun touch(i: Int) {
            if (z.touched[i]) return
            z.touched[i] = true
            z.changed[z.changedCount++] = i
            for (w in g.numbersBeside[i]) if (!z.wallMark[w]) { z.wallMark[w] = true; z.walls[z.wallCount++] = w }
            if (!z.squareMark[i]) { z.squareMark[i] = true; z.squares[z.squareCount++] = i }
            for (j in g.sight[i]) if (!z.squareMark[j]) { z.squareMark[j] = true; z.squares[z.squareCount++] = j }
        }
        fun set(i: Int, v: Int) {
            t[i] = v
            touch(i)
            if (v == LAMP) {
                l[i] = true
                for (j in g.sight[i]) if (!l[j]) { l[j] = true; touch(j) }
            }
        }
        set(square, assume)
        val links = ArrayList<Fact>()
        while (true) {
            val ws = z.walls.copyOf(z.wallCount).also { it.sort() }
            val ys = z.squares.copyOf(z.squareCount).also { it.sort() }
            broken(g, t, l, ws, ys)?.let { return WhatIf(square, assume, links, it) }
            if (links.size == maxLinks) return null
            val f = directFact(g, t, l, ws, ys) ?: return null
            for (i in f.targets) set(i, f.value)
            links.add(f)
        }
    }

    /**
     * The what-if with the shortest chain over every candidate (a lamp tried before none), reading order on a tie. Only
     * where no direct fact is left (the ladder's order guarantees it); null on a board that already breaks a rule.
     */
    fun firstWhatIf(g: Geo, k: IntArray): WhatIf? {
        val lit = litBy(g, k)
        if (broken(g, k, lit) != null) return null
        val z = Scratch(k, lit)
        var best: WhatIf? = null
        for (x in k.indices) {
            if (!candidate(g, k, lit, x)) continue
            for (assume in intArrayOf(LAMP, NO)) {
                val limit = best?.links?.size?.minus(1) ?: MAX_LINKS
                if (limit < 0) return best
                val w = whatIf(g, z, x, assume, limit) ?: continue
                best = w
            }
        }
        return best
    }

    /** The ladder up to [rung] from [k] to its end; [k] is changed in place. Returns how many what-ifs it took. */
    fun settle(g: Geo, k: IntArray, rung: Int): Int {
        var used = 0
        while (true) {
            val f = nextFact(g, k, rung) ?: return used
            if (f.technique == WHAT_IF) used++
            apply(k, f)
        }
    }

    /** Whether the ladder up to [rung] finishes the board from blank. */
    fun finishes(g: Geo, rung: Int): Boolean {
        val k = IntArray(g.n * g.n)
        settle(g, k, rung)
        return finished(g, k) && broken(g, k) == null
    }

    /** The deepest of [STARVE], [DARK] and [WHAT_IF] the ladder's walk from blank uses, or null when it needs none. */
    fun deepest(s: LampsState): String? {
        val g = Geo(s.size, s.walls)
        val k = IntArray(s.size * s.size)
        val order = listOf(STARVE, DARK, WHAT_IF)
        var best = -1
        while (true) {
            val f = nextFact(g, k, WHAT_IF_RUNG) ?: break
            best = maxOf(best, order.indexOf(f.technique))
            apply(k, f)
        }
        return order.getOrNull(best)
    }

    /** The lowest rung that finishes [s] from blank, or null when none does. */
    fun depth(s: LampsState): Int? {
        val g = Geo(s.size, s.walls)
        return listOf(DIRECT, LOOKAHEAD, WHAT_IF_RUNG).firstOrNull { finishes(g, it) }
    }

    // ---- the exhaustive count -------------------------------------------------------------------

    /**
     * How many lamp sets obey the rules, with no budget. The direct facts are propagated at every node (they are sound,
     * so no answer is lost); then the unlit square with the fewest sources is split by which of them is its first lamp
     * (sources before it hold none), a partition of the answers, so none is counted twice. Only
     * [LampsVerdict.ExactlyOne] carries the answer.
     */
    fun countSolutions(n: Int, walls: String): LampsVerdict {
        val g = Geo(n, walls)
        var found = 0
        var answer = ""
        fun walk(k: IntArray) {
            if (found > 1) return
            settle(g, k, DIRECT)
            if (broken(g, k) != null) return
            val lit = litBy(g, k)
            var pick = -1
            var pickSources: List<Int> = emptyList()
            for (y in k.indices) {
                if (!g.white[y] || lit[y]) continue
                val s = sources(g, k, lit, y)
                if (pick < 0 || s.size < pickSources.size) {
                    pick = y
                    pickSources = s
                }
            }
            if (pick < 0) {
                found++
                if (found == 1) answer = CharArray(n * n) { if (k[it] == LAMP) '1' else '0' }.concatToString()
                return
            }
            for ((idx, src) in pickSources.withIndex()) {
                val next = k.copyOf()
                for (j in 0 until idx) next[pickSources[j]] = NO
                next[src] = LAMP
                walk(next)
                if (found > 1) return
            }
        }
        walk(IntArray(n * n))
        return when (found) {
            0 -> LampsVerdict.None
            1 -> LampsVerdict.ExactlyOne(answer)
            else -> LampsVerdict.Several
        }
    }

    // ---- generation -------------------------------------------------------------------------

    /** Side, walls (per mille of the squares) and the rung a board must need: exactly that one, no less, no more. */
    class Spec(val side: Int, val wallPermille: Int, val needs: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(7, 260, DIRECT)
        Difficulty.HARD -> Spec(10, 260, LOOKAHEAD)
        Difficulty.EXPERT -> Spec(12, 260, WHAT_IF_RUNG)
    }

    /** Draws tried per seed before the generator gives up and falls back. */
    const val ATTEMPTS = 400

    fun generateVerified(seed: Long, difficulty: Difficulty): LampsState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    /** Draw number [a] for [seed], if it is accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): LampsState? {
        val spec = specFor(difficulty)
        val rng = Rng(seed + a)
        val full = draw(rng, spec.side, spec.wallPermille)
        val n = spec.side
        if (!finishes(Geo(n, full.walls), spec.needs)) return null
        // Take numbers back while the tier's ladder still finishes the board: first rung by rung up to the one below the
        // tier's (cheap), then with the tier's own, which must take at least one more back or the board needs less than it.
        val w = full.walls.toCharArray()
        val order = rng.shuffled((0 until n * n).filter { w[it] in '0'..'4' })
        for (rung in 0..spec.needs) {
            var taken = 0
            for (i in order) {
                if (w[i] !in '0'..'4') continue
                val was = w[i]
                w[i] = WALL
                if (finishes(Geo(n, w.concatToString()), rung)) taken++ else w[i] = was
            }
            if (rung == spec.needs && rung > DIRECT && taken == 0) return null
        }
        val s = full.copy(walls = w.concatToString())
        return s.takeIf { accepts(it, spec) }
    }

    /** Whether [s] may ship at [spec]: its ladder needs exactly the tier's rung, and it has one answer, the stored one. */
    fun accepts(s: LampsState, spec: Spec): Boolean {
        if (depth(s) != spec.needs) return false
        val verdict = countSolutions(s.size, s.walls)
        return verdict is LampsVerdict.ExactlyOne && verdict.lamps == s.solution
    }

    /** Half-turn-symmetric walls, every wall numbered, and a lamp on each white square still dark in a random order. */
    private fun draw(rng: Rng, n: Int, wallPermille: Int): LampsState {
        val want = (n * n * wallPermille + 500) / 1000
        val w = CharArray(n * n) { WHITE }
        var placed = 0
        for (i in rng.shuffled((0 until (n * n + 1) / 2).toList())) {
            if (placed >= want) break
            val j = n * n - 1 - i
            w[i] = WALL
            w[j] = WALL
            placed += if (i == j) 1 else 2
        }
        val walls = w.concatToString()
        val lamps = BooleanArray(n * n)
        val lit = BooleanArray(n * n)
        for (i in rng.shuffled((0 until n * n).filter { w[it] == WHITE })) {
            if (lit[i]) continue
            lamps[i] = true
            lit[i] = true
            for (j in sight(n, walls, i)) lit[j] = true
        }
        for (i in 0 until n * n) if (w[i] == WALL) w[i] = '0' + orth(n, i).count { lamps[it] }
        return LampsState(n, w.concatToString(), CharArray(n * n) { if (lamps[it]) '1' else '0' }.concatToString())
    }

    /**
     * Only when none of [ATTEMPTS] draws was accepted, which a year of daily seeds on every tier never reaches
     * (`FallbackTest`). A fixed board per tier (what seed 424242 made), which `LampsRulesTest` proves is accepted at its tier.
     */
    fun lastResort(difficulty: Difficulty): LampsState = when (difficulty) {
        Difficulty.STANDARD -> LampsState(
            7,
            ".1....#..1.#..1....10.......#2....1..1.#..0....1.",
            "0010000100001000010000100000000010001000010000100",
        )
        Difficulty.HARD -> LampsState(
            10,
            "...0......2..11..2.#.#.1#............02.##................##.01............#0.#.2.#..0#..1......#...",
            "1000000100001001001010000010000001000001000000001010000000000000100000001000000101000001001000000010",
        )
        Difficulty.EXPERT -> LampsState(
            12,
            "....#.#..3.......1..#...1...##.###....2.#.3...#..0...2..2..1........................#..#..2...#..0...3.2.2" +
                "....#0#.21...#...#..#.......3..#.2....",
            "100001001010010000010100001000100001100101001000000000100010000000001000000001000000001000010001000010100010" +
                "100001000100001010010000010100100001",
        )
    }
}
