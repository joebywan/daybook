package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import kotlinx.serialization.Serializable

/**
 * A Links board in progress: the numbered ends, the answer, and the player's lines.
 *
 * Every string has one character per square, row by row. [ends] is `.` for a plain square or `a`, `b`, ... for
 * pair 0, 1, ... (shown as 1, 2, ...). [answer] and [links] hold, per square, which of its sides carry a line, as a hex
 * digit: bit 1 up, 2 right, 4 down, 8 left ([LinksLogic.UP]...). A line is on both of its squares, so the masks always
 * agree across a side (every mutator writes both). [answer] is read only for mistakes and tests.
 *
 * Lines per square, not a pair number per square: a path may run beside itself, so which neighbours are joined
 * cannot be read off a colour per square.
 */
@Serializable
data class LinksState(
    val size: Int,
    val ends: String,
    val answer: String,
    val links: String = "0".repeat(size * size),
    override val moves: Int = 0,
) : PuzzleState {

    /** Solved when the lines join every pair and fill every square: the rules ([LinksLogic.isSolved]), never [answer]. */
    override val solved: Boolean get() = LinksLogic.isSolved(LinksLogic.Board.of(size, ends), LinksLogic.masksOf(links))

    /** One drag through the squares of [path] (each a side-neighbour of the one before): see [LinksLogic.drag]. One state. */
    fun drag(path: List<Int>): LinksState {
        val board = LinksLogic.Board.of(size, ends)
        val next = LinksLogic.drag(board, LinksLogic.masksOf(links), path) ?: return this
        val text = LinksLogic.maskString(next)
        return if (text == links) this else copy(links = text, moves = moves + 1)
    }
}

/**
 * The rules, the ladder, the exhaustive count and the generator. Nothing here touches the screen, the platform or a hash
 * container, so the same seed makes the same board on the phone and in the browser.
 *
 * ## What the ladder knows
 * Only the lines. Every rung reads them afresh: a square's *spare* (an end takes one line, any other square two, less
 * what it has), its *piece* (the squares joined to it by lines) and that piece's pair (the end it holds, if any). Two
 * side-neighbours *can join* when neither is full, they are not one piece already (that would close a loop) and their
 * pairs do not differ. A *tip* is a square that wants exactly one more line at the end of a piece (an end with no line
 * yet, or a line's last square); an *empty* square has no line and is not an end.
 *
 * ## The ladder, simplest first ([nextStep])
 * 1. [ONE_WAY]: a tip with one square it can join takes it. [TWO_WAYS]: an empty square with only two it can join takes both.
 * 2. [CUT]: a way that, taken, leaves something impossible at once (a pair cut off from its other end, a square no pair
 *    can reach, a square with too few ways) is struck, for tips and empty squares; joins no pair could hold are struck
 *    first ([reach]: a square can belong to a pair only if both of that pair's tips can reach it).
 * 3. [WHAT_IF]: a tip with two ways left: suppose one, follow rung 1 at most [MAX_LINKS] steps (checking every pair's
 *    reach after each), and if something breaks, the tip takes the other.
 * The reach on its own (rung 1 again once the joins it strikes are gone) was a rung of its own and measured: it gave no
 * step on 450 boards walked by hints, every such join being caught by rung 1 or by a cut, so it was dropped.
 * Every rung is sound for every answer (none assumes there is only one, so no "a path never fills a 2x2"), so the
 * generator ships a board only when its tier's rungs finish it, and the teacher ([LinksTeacher]) explains exactly these.
 */
internal object LinksLogic {

    const val UP = 0
    const val RIGHT = 1
    const val DOWN = 2
    const val LEFT = 3

    /** Rungs, in the order they are tried; the depth of a board is the deepest it needed. */
    const val LOCAL = 1
    const val CUT = 2
    const val WHAT_IF = 3

    /** Kinds of step: what made a line forced. */
    const val K_ONE_WAY = 0
    const val K_TWO_WAYS = 1

    /** How far a what-if follows the rungs below it. */
    const val MAX_LINKS = 3

    private const val HEX = "0123456789abcdef"

    // ---- squares and masks --------------------------------------------------------------------

    /** The square beside [q] in direction [d] on an [n] x [n] board, or -1 off the board. */
    fun next(n: Int, q: Int, d: Int): Int {
        val r = q / n
        val c = q % n
        return when (d) {
            UP -> if (r > 0) q - n else -1
            RIGHT -> if (c < n - 1) q + 1 else -1
            DOWN -> if (r < n - 1) q + n else -1
            else -> if (c > 0) q - 1 else -1
        }
    }

    /** The direction from [a] to its side-neighbour [b], or -1 when they are not side-neighbours. */
    fun dirTo(n: Int, a: Int, b: Int): Int {
        for (d in 0 until 4) if (next(n, a, d) == b) return d
        return -1
    }

    fun masksOf(s: String): IntArray = IntArray(s.length) { HEX.indexOf(s[it]).coerceAtLeast(0) }

    fun maskString(m: IntArray): String = buildString(m.size) { for (x in m) append(HEX[x and 15]) }

    fun hasLine(n: Int, m: IntArray, a: Int, b: Int): Boolean {
        val d = dirTo(n, a, b)
        return d >= 0 && m[a] and (1 shl d) != 0
    }

    fun setLine(n: Int, m: IntArray, a: Int, b: Int, on: Boolean) {
        val d = dirTo(n, a, b)
        if (d < 0) return
        val back = (d + 2) % 4
        if (on) {
            m[a] = m[a] or (1 shl d)
            m[b] = m[b] or (1 shl back)
        } else {
            m[a] = m[a] and (1 shl d).inv()
            m[b] = m[b] and (1 shl back).inv()
        }
    }

    fun degree(mask: Int): Int = (mask and 1) + (mask shr 1 and 1) + (mask shr 2 and 1) + (mask shr 3 and 1)

    /** The board's fixed part: its size and which pair's end, if any, is on each square. Cached by ends (a probe-only lookup). */
    class Board private constructor(val n: Int, val pair: IntArray) {
        val squares = n * n
        val pairs = (pair.maxOrNull() ?: -1) + 1
        /** Each pair's two ends, the first in reading order in [endA]. */
        val endA = IntArray(pairs) { -1 }
        val endB = IntArray(pairs) { -1 }

        init {
            for (q in 0 until squares) {
                val p = pair[q]
                if (p < 0) continue
                if (endA[p] < 0) endA[p] = q else endB[p] = q
            }
        }

        fun cap(q: Int) = if (pair[q] >= 0) 1 else 2

        companion object {
            /** The last board asked for, with its ends: one reference, so a racing reader sees a matching pair or none. */
            private var last: Pair<String, Board>? = null

            fun of(n: Int, ends: String): Board {
                val hit = last
                if (hit != null && hit.first == ends && hit.second.n == n) return hit.second
                val b = Board(n, pairsOf(ends))
                last = ends to b
                return b
            }
        }
    }

    // ---- reading the lines ----------------------------------------------------------------------

    /**
     * What the lines say: each square's lines, its piece (numbered by its first square in reading order) and the piece's
     * pair (-1 for a loose piece with no end), and whether anything is already broken ([bad]: a square over its lines, a
     * closed loop, two pairs in one piece).
     */
    class View(val b: Board, val m: IntArray) {
        val n = b.n
        val deg = IntArray(b.squares) { degree(m[it]) }
        val piece = IntArray(b.squares) { -1 }
        val colour = IntArray(b.squares) { -1 }
        var bad = false
            private set

        init {
            val stack = IntArray(b.squares)
            val members = IntArray(b.squares)
            for (q in 0 until b.squares) {
                if (deg[q] > b.cap(q)) bad = true
                if (piece[q] >= 0) continue
                var top = 0
                var count = 0
                var lineEnds = 0
                var col = -1
                stack[top++] = q
                piece[q] = q
                while (top > 0) {
                    val x = stack[--top]
                    members[count++] = x
                    lineEnds += deg[x]
                    val p = b.pair[x]
                    if (p >= 0) {
                        if (col >= 0 && col != p) bad = true
                        col = p
                    }
                    for (d in 0 until 4) {
                        if (m[x] and (1 shl d) == 0) continue
                        val y = next(n, x, d)
                        if (y < 0 || m[y] and (1 shl ((d + 2) % 4)) == 0) {
                            bad = true
                            continue
                        }
                        if (piece[y] < 0) {
                            piece[y] = q
                            stack[top++] = y
                        }
                    }
                }
                if (lineEnds / 2 >= count && count > 1) bad = true // as many lines as squares: a loop
                for (i in 0 until count) colour[members[i]] = col
            }
        }

        fun spare(q: Int) = b.cap(q) - deg[q]

        /** Wants exactly one more line, at the end of a piece. */
        fun isTip(q: Int) = spare(q) == 1 && (deg[q] == 1 || b.pair[q] >= 0)

        fun isEmpty(q: Int) = deg[q] == 0 && b.pair[q] < 0

        /** Whether pair [p]'s two ends are one piece. */
        fun complete(p: Int) = piece[b.endA[p]] == piece[b.endB[p]]

        /** The tip of the piece holding [end] (an end with no line is its own tip), or -1 when that piece has none. */
        fun tipOf(end: Int): Int {
            if (deg[end] == 0) return end
            var prev = end
            var cur = next(n, end, (0 until 4).first { m[end] and (1 shl it) != 0 })
            var guard = 0
            while (guard++ < b.squares) {
                if (deg[cur] < 2) return cur
                var moved = false
                for (d in 0 until 4) {
                    if (m[cur] and (1 shl d) == 0) continue
                    val y = next(n, cur, d)
                    if (y != prev) {
                        prev = cur
                        cur = y
                        moved = true
                        break
                    }
                }
                if (!moved) return cur
            }
            return -1
        }

        /** Whether side-neighbours [a] and [x] can be joined by a line, by the lines alone (and [sets], when given). */
        fun canJoin(a: Int, x: Int, sets: IntArray? = null): Boolean {
            if (x < 0 || m[a] and (1 shl dirTo(n, a, x)) != 0) return false
            if (spare(a) <= 0 || spare(x) <= 0 || piece[a] == piece[x]) return false
            if (colour[a] >= 0 && colour[x] >= 0 && colour[a] != colour[x]) return false
            if (sets != null && sets[piece[a]] and sets[piece[x]] == 0) return false
            return true
        }

        /** The squares [q] can join, in direction order (up, right, down, left). */
        fun ways(q: Int, sets: IntArray? = null): IntArray {
            val out = IntArray(4)
            var k = 0
            for (d in 0 until 4) {
                val x = next(n, q, d)
                if (x >= 0 && canJoin(q, x, sets)) out[k++] = x
            }
            return out.copyOf(k)
        }

        /** Whether every square has all its lines (with nothing [bad], that is a solved board). */
        val full: Boolean get() = (0 until b.squares).all { spare(it) == 0 }
    }

    /** Solved: every end has one line, every other square two, and the lines make paths each joining one pair. */
    fun isSolved(b: Board, m: IntArray): Boolean {
        if (m.size != b.squares) return false
        val v = View(b, m)
        if (v.bad || !v.full) return false
        // Full and loop-free with one pair a piece: every piece is a path whose two ends are ends, so of one pair.
        return (0 until b.pairs).all { v.complete(it) }
    }

    // ---- reach: which pairs can still use a square ------------------------------------------------

    /**
     * For each piece (by its number), the pairs that could still hold it, as a bit set: a pair's own pieces hold only it;
     * a loose piece or empty square holds a pair only when both of that pair's tips can reach it, moving along lines
     * and across joins into squares of no pair or that pair. [cutOff] is the first pair whose tips cannot reach each
     * other, or -1.
     */
    class Reach(val sets: IntArray, val cutOff: Int, val stranded: Int)

    fun reach(v: View): Reach {
        val b = v.b
        // One round: a pair's floods pass any piece of no pair. Settling it over rounds (floods passing only pieces still
        // open to the pair, until nothing changes) was measured and moved no board's depth, at three times the cost.
        val open = IntArray(b.squares) { q -> if (v.piece[q] != q) 0 else if (v.colour[q] >= 0) 1 shl v.colour[q] else (1 shl b.pairs) - 1 }
        val sets = IntArray(b.squares) { q -> if (v.piece[q] == q && v.colour[q] >= 0) 1 shl v.colour[q] else 0 }
        var cutOff = -1
        val fromA = BooleanArray(b.squares)
        val fromB = BooleanArray(b.squares)
        val queue = IntArray(b.squares)
        for (p in 0 until b.pairs) {
            if (v.complete(p)) continue
            val ta = v.tipOf(b.endA[p])
            val tb = v.tipOf(b.endB[p])
            flood(v, p, ta, fromA, queue, open)
            flood(v, p, tb, fromB, queue, open)
            if ((ta < 0 || tb < 0 || !fromA[tb]) && cutOff < 0) cutOff = p
            for (q in 0 until b.squares) {
                if (fromA[q] && fromB[q] && v.colour[q] < 0) sets[v.piece[q]] = sets[v.piece[q]] or (1 shl p)
            }
        }
        var stranded = -1
        for (q in 0 until b.squares) {
            if (v.piece[q] == q && v.colour[q] < 0 && sets[q] == 0) {
                stranded = q
                break
            }
        }
        return Reach(sets, cutOff, stranded)
    }

    private fun flood(v: View, p: Int, from: Int, seen: BooleanArray, queue: IntArray, sets: IntArray) {
        seen.fill(false)
        if (from < 0) return
        var head = 0
        var tail = 0
        seen[from] = true
        queue[tail++] = from
        val n = v.n
        val bit = 1 shl p
        while (head < tail) {
            val x = queue[head++]
            for (d in 0 until 4) {
                val y = next(n, x, d)
                if (y < 0 || seen[y]) continue
                val along = v.m[x] and (1 shl d) != 0
                val across = !along && v.spare(x) > 0 && v.spare(y) > 0 && sets[v.piece[y]] and bit != 0
                if (along || across) {
                    seen[y] = true
                    queue[tail++] = y
                }
            }
        }
    }

    // ---- what breaks ------------------------------------------------------------------------------

    /** Why a set of lines cannot be finished: what broke and where. */
    class Broken(val kind: Int, val square: Int, val pair: Int = -1)

    const val B_LOCKED = 0 // the lines themselves: too many at a square, a loop, two pairs joined
    const val B_DEAD_END = 1 // a tip with no way on
    const val B_DEAD_SQUARE = 2 // an empty square with fewer than two ways
    const val B_CLASH = 3 // an empty square whose only two ways are two pairs, or a loop
    const val B_CUT_OFF = 4 // a pair whose tips cannot reach each other
    const val B_STRANDED = 5 // a square no pair can reach

    /** The first thing broken on [v], reading the lines alone ([withReach] off) or with every pair's reach. */
    fun broken(v: View, withReach: Boolean, reach: Reach? = null): Broken? {
        if (v.bad) return Broken(B_LOCKED, -1)
        val r = if (withReach) reach ?: reach(v) else null
        if (r != null && r.cutOff >= 0) return Broken(B_CUT_OFF, v.b.endA[r.cutOff], r.cutOff)
        if (r != null && r.stranded >= 0) return Broken(B_STRANDED, r.stranded)
        val sets = r?.sets
        for (q in 0 until v.b.squares) {
            val spare = v.spare(q)
            if (spare <= 0) continue
            val w = v.ways(q, sets)
            if (w.size < spare) return Broken(if (v.isEmpty(q)) B_DEAD_SQUARE else B_DEAD_END, q, v.colour[q])
            if (v.isEmpty(q) && w.size == 2 && !twoJoin(v, q, w[0], w[1], sets)) return Broken(B_CLASH, q)
        }
        return null
    }

    /** Whether empty [q] can join both [x] and [y] at once: not one piece (a loop), and some pair could hold all three. */
    private fun twoJoin(v: View, q: Int, x: Int, y: Int, sets: IntArray?): Boolean {
        if (v.piece[x] == v.piece[y]) return false
        if (v.colour[x] >= 0 && v.colour[y] >= 0 && v.colour[x] != v.colour[y]) return false
        if (sets != null && sets[v.piece[x]] and sets[v.piece[y]] and sets[v.piece[q]] == 0) return false
        return true
    }

    // ---- the ladder -------------------------------------------------------------------------------

    /**
     * A forced step: the [lines] (pairs of squares) it draws, at [square], of [kind] ([K_ONE_WAY] or [K_TWO_WAYS]), found on
     * [rung]. [struck] are the squares [square] could otherwise join that the rung ruled out, each with why ([why], parallel);
     * a what-if also carries the way it supposed ([supposed]) and the squares its chain drew ([chain]).
     */
    class Step(
        val rung: Int,
        val kind: Int,
        val square: Int,
        val lines: List<Pair<Int, Int>>,
        val struck: List<Int> = emptyList(),
        val why: List<Broken?> = emptyList(),
        val supposed: Int = -1,
        val chain: List<Int> = emptyList(),
        val chainBroke: Broken? = null,
    )

    /** The simplest forced step on [m], or null when the ladder up to [maxRung] finds none (or the lines are broken). */
    fun nextStep(b: Board, m: IntArray, maxRung: Int = WHAT_IF): Step? {
        val v = View(b, m)
        if (v.bad) return null
        local(v, null)?.let { return it.let { s -> Step(LOCAL, s.kind, s.square, s.lines) } }
        if (maxRung < CUT) return null
        val r = reach(v)
        if (r.cutOff >= 0 || r.stranded >= 0) return null
        cut(v, r)?.let { return it }
        if (maxRung < WHAT_IF) return null
        return whatIf(v, r)
    }

    /** A bare step found by the local rules, with [sets] striking joins when given. */
    private fun local(v: View, sets: IntArray?): Step? {
        for (q in 0 until v.b.squares) {
            if (!v.isTip(q)) continue
            val w = v.ways(q, sets)
            if (w.size == 1) return Step(LOCAL, K_ONE_WAY, q, listOf(q to w[0]))
        }
        for (q in 0 until v.b.squares) {
            if (!v.isEmpty(q)) continue
            val w = v.ways(q, sets)
            if (w.size == 2 && twoJoin(v, q, w[0], w[1], sets)) return Step(LOCAL, K_TWO_WAYS, q, listOf(w[0] to q, q to w[1]))
        }
        return null
    }

    /** What taking the line [a]-[x] would break at once (with reach), or null. */
    private fun breaksIf(v: View, a: Int, x: Int): Broken? {
        val m2 = v.m.copyOf()
        setLine(v.n, m2, a, x, true)
        val v2 = View(v.b, m2)
        return broken(v2, withReach = true)
    }

    private fun cut(v: View, r: Reach): Step? {
        val b = v.b
        // Tips first, then empty squares, each in reading order.
        for (pass in 0..1) for (q in 0 until b.squares) {
            val need = if (pass == 0) (if (v.isTip(q)) 1 else 0) else (if (v.isEmpty(q)) 2 else 0)
            if (need == 0) continue
            val w = v.ways(q, r.sets)
            if (w.size <= need) continue
            val why = w.map { breaksIf(v, q, it) }
            val kept = w.filterIndexed { i, _ -> why[i] == null }
            if (kept.size != need) continue
            if (need == 2 && !twoJoin(v, q, kept[0], kept[1], r.sets)) continue
            val lines = if (need == 1) listOf(q to kept[0]) else listOf(kept[0] to q, q to kept[1])
            val all = v.ways(q)
            val struck = all.filter { it !in kept }
            return Step(CUT, if (need == 1) K_ONE_WAY else K_TWO_WAYS, q, lines, struck, struck.map { x -> why.getOrNull(w.indexOf(x)) })
        }
        return null
    }

    private fun whatIf(v: View, r: Reach): Step? {
        // Tips with two ways left, in reading order: each way supposed in turn. Supposing every way of every square was
        // measured too: it finished no more boards and cost far more on a board it cannot finish.
        for (q in 0 until v.b.squares) {
            if (!v.isTip(q)) continue
            val w = v.ways(q, r.sets)
            if (w.size != 2) continue
            for (i in 0..1) {
                val (broke, chain) = follow(v, q, w[i])
                if (broke == null) continue
                val other = w[1 - i]
                val struck = v.ways(q).filter { it != other }
                return Step(
                    WHAT_IF, K_ONE_WAY, q, listOf(q to other), struck, struck.map { null },
                    supposed = w[i], chain = chain, chainBroke = broke,
                )
            }
        }
        return null
    }

    /** Suppose [a]-[x], then take at most [MAX_LINKS] steps of rung 1: what breaks (null if nothing) and the squares drawn. */
    private fun follow(v: View, a: Int, x: Int): Pair<Broken?, List<Int>> {
        val m2 = v.m.copyOf()
        setLine(v.n, m2, a, x, true)
        val chain = mutableListOf<Int>()
        for (link in 0..MAX_LINKS) {
            val v2 = View(v.b, m2)
            broken(v2, withReach = true)?.let { return it to chain }
            if (link == MAX_LINKS || v2.full) break
            val s = nextStep(v.b, m2, LOCAL) ?: break
            for ((p, y) in s.lines) {
                setLine(v.n, m2, p, y, true)
                chain += y
            }
        }
        return null to chain
    }

    /** Draws [s]'s lines on [m]. */
    fun apply(n: Int, m: IntArray, s: Step) {
        for ((a, x) in s.lines) setLine(n, m, a, x, true)
    }

    /**
     * The deepest rung a walk from no lines needs, step by step through [nextStep], or null when the ladder stops short
     * (or [maxRung] is not enough).
     */
    fun depth(b: Board, maxRung: Int = WHAT_IF): Int? {
        val m = IntArray(b.squares)
        var deepest = LOCAL
        while (true) {
            val v = View(b, m)
            if (v.bad) return null
            if (v.full) return if (isSolved(b, m)) deepest else null
            val s = nextStep(b, m, maxRung) ?: return null
            if (s.rung > deepest) deepest = s.rung
            apply(b.n, m, s)
        }
    }

    // ---- the exhaustive count -----------------------------------------------------------------------

    /**
     * Every way to finish the board from no lines, counted to two: rungs 1-3 to a fixpoint (each sound for every answer:
     * with the local rules alone, a wrong branch on a 10x10 could take seconds to refute), every pair's reach checked,
     * then a branch over the ways of the tip with fewest. No budget: only [Verdict.ExactlyOne] carries an answer (the masks).
     */
    fun solutions(b: Board): Verdict<IntArray> {
        var found = 0
        var first: IntArray? = null
        fun search(m: IntArray) {
            if (found >= 2) return
            // Settle rungs 1-3.
            while (true) {
                val s = nextStep(b, m, CUT) ?: break
                apply(b.n, m, s)
            }
            val v = View(b, m)
            if (v.bad) return
            if (v.full) {
                if (isSolved(b, m)) {
                    found++
                    if (first == null) first = m.copyOf()
                }
                return
            }
            val r = reach(v)
            if (broken(v, withReach = true, reach = r) != null) return
            // Branch on the tip with fewest ways the reach leaves: exactly one of them is its next line in any answer.
            var best = -1
            var bestWays: IntArray? = null
            for (q in 0 until b.squares) {
                if (!v.isTip(q)) continue
                val w = v.ways(q, r.sets)
                if (bestWays == null || w.size < bestWays.size) {
                    best = q
                    bestWays = w
                }
            }
            if (best < 0 || bestWays == null) return
            for (x in bestWays) {
                val m2 = m.copyOf()
                setLine(b.n, m2, best, x, true)
                search(m2)
                if (found >= 2) return
            }
        }
        search(IntArray(b.squares))
        return Verdict.of(found) { first!! }
    }

    // ---- the drag ---------------------------------------------------------------------------------

    /**
     * A drag through [path] on lines [m], as a pen: a new array, or null when nothing changes.
     *
     * Its first step decides. Along a line, it rubs out every line it runs along. Anywhere else it draws: from an end,
     * a line's last square or an empty square on, and from the middle of a line after cutting it there (the side
     * with an end, else the longer, stays). Each square it enters is cleared first if it is full, of another pair, or
     * of its own line (Flow-style: the pen cuts through; a piece of a pair's line left with no end is taken away), and
     * running back onto its own line cuts it back to there. An end of another pair, or any square once its pair is
     * joined, stops it.
     */
    fun drag(b: Board, m0: IntArray, path: List<Int>): IntArray? {
        if (path.size < 2) return null
        val n = b.n
        val m = m0.copyOf()
        if (dirTo(n, path[0], path[1]) < 0) return null
        if (hasLine(n, m, path[0], path[1])) {
            for (i in 1 until path.size) if (hasLine(n, m, path[i - 1], path[i])) setLine(n, m, path[i - 1], path[i], false)
            return m
        }
        val chain = mutableListOf(path[0])
        openStart(b, m, path[0])
        for (i in 1 until path.size) {
            val t = path[i]
            val at = chain.indexOf(t)
            if (at >= 0) {
                for (j in chain.size - 1 downTo at + 1) setLine(n, m, chain[j - 1], chain[j], false)
                while (chain.size > at + 1) chain.removeAt(chain.size - 1)
                continue
            }
            val p = chain.last()
            if (dirTo(n, p, t) < 0) break
            var v = View(b, m)
            if (v.spare(p) <= 0) break
            val own = v.colour[p]
            if (b.pair[t] >= 0 && own >= 0 && b.pair[t] != own) break
            if (v.piece[t] == v.piece[p]) {
                // Back onto its own line: cut it back to t and draw on from there.
                cutBack(b, m, p, t)
                chain.clear()
                chain += t
                continue
            }
            val clash = v.colour[t] >= 0 && own >= 0 && v.colour[t] != own
            if (v.spare(t) <= 0 || clash) {
                clearSquare(b, m, t)
                v = View(b, m)
            }
            if (b.pair[t] >= 0 && v.colour[p] >= 0 && b.pair[t] != v.colour[p]) break
            if (v.spare(t) <= 0 || v.piece[t] == v.piece[p]) break
            setLine(n, m, p, t, true)
            chain += t
        }
        return m
    }

    /** Before drawing from a full square [q], free one of its lines: an end lets go of its line; a line's middle is cut. */
    private fun openStart(b: Board, m: IntArray, q: Int) {
        val v = View(b, m)
        if (v.spare(q) > 0) return
        val n = b.n
        val sides = (0 until 4).filter { m[q] and (1 shl it) != 0 }.map { next(n, q, it) }
        if (sides.size == 1) {
            clearSquare(b, m, q)
            return
        }
        // The middle of a line: keep the side with an end on it, else the longer.
        fun side(start: Int): Pair<Boolean, Int> {
            var prev = q
            var cur = start
            var len = 1
            var end = b.pair[cur] >= 0
            while (v.deg[cur] == 2 && len <= b.squares) {
                val nxt = (0 until 4).map { next(n, cur, it) }.first { it >= 0 && it != prev && m[cur] and (1 shl dirTo(n, cur, it)) != 0 }
                prev = cur
                cur = nxt
                len++
                if (b.pair[cur] >= 0) end = true
            }
            return end to len
        }
        val (endA, lenA) = side(sides[0])
        val (endB, lenB) = side(sides[1])
        val keepA = when {
            endA != endB -> endA
            else -> lenA >= lenB
        }
        val drop = if (keepA) sides[1] else sides[0]
        val wasColoured = v.colour[q] >= 0
        setLine(n, m, q, drop, false)
        if (wasColoured) dropOrphan(b, m, drop)
    }

    /** Takes every line off [q]; any piece that held an end before and has none now goes too. */
    private fun clearSquare(b: Board, m: IntArray, q: Int) {
        val v = View(b, m)
        val n = b.n
        val wasColoured = v.colour[q] >= 0
        val sides = (0 until 4).filter { m[q] and (1 shl it) != 0 }.map { next(n, q, it) }
        for (x in sides) setLine(n, m, q, x, false)
        if (wasColoured) for (x in sides) dropOrphan(b, m, x)
    }

    /** Takes away the piece holding [q] when it holds no end. */
    private fun dropOrphan(b: Board, m: IntArray, q: Int) {
        val v = View(b, m)
        if (v.colour[q] >= 0 || v.deg[q] == 0) return
        val root = v.piece[q]
        val n = b.n
        for (x in 0 until b.squares) {
            if (v.piece[x] != root) continue
            for (d in 0 until 4) {
                val y = next(n, x, d)
                if (y >= 0 && m[x] and (1 shl d) != 0) setLine(n, m, x, y, false)
            }
        }
    }

    /** On the one piece holding [p] and [t], takes out every line between them. */
    private fun cutBack(b: Board, m: IntArray, p: Int, t: Int) {
        val n = b.n
        // Walk from p along the piece until t, remembering the way.
        val prev = IntArray(b.squares) { -2 }
        prev[p] = -1
        val queue = ArrayDeque<Int>()
        queue.addLast(p)
        while (queue.isNotEmpty()) {
            val x = queue.removeFirst()
            if (x == t) break
            for (d in 0 until 4) {
                val y = next(n, x, d)
                if (y >= 0 && m[x] and (1 shl d) != 0 && prev[y] == -2) {
                    prev[y] = x
                    queue.addLast(y)
                }
            }
        }
        var x = t
        while (prev[x] >= 0) {
            setLine(n, m, prev[x], x, false)
            x = prev[x]
        }
        dropOrphan(b, m, p)
    }

    // ---- generation -------------------------------------------------------------------------------

    /**
     * Side, how many pairs a cover may end with, and the deepest rung a board's walk may need: Standard the local rules
     * alone; Hard a cut at least once, never a what-if; Expert a cut or a what-if. A what-if as
     * Expert's floor was measured and dropped: 3% of 10x10 draws need it (the rest stall or finish sooner), so a board
     * would take about 40 draws, over a second on the JVM.
     */
    class Spec(val side: Int, val minPairs: Int, val maxPairs: Int, val needs: IntRange)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(6, 5, 7, LOCAL..LOCAL)
        Difficulty.HARD -> Spec(8, 7, 9, CUT..CUT)
        Difficulty.EXPERT -> Spec(10, 9, 12, CUT..WHAT_IF)
    }

    var TOUCHING = true

    /** Draws tried per seed before the generator gives up and falls back. */
    const val ATTEMPTS = 2000

    /** A board for [seed] the tier's ladder finishes and the count proves, or null when none of [ATTEMPTS] draws was. */
    fun generateVerified(seed: Long, difficulty: Difficulty): LinksState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    fun generate(seed: Long, difficulty: Difficulty): LinksState = generateVerified(seed, difficulty) ?: lastResort(difficulty)

    /** Draw number [a] for [seed], if accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): LinksState? {
        val spec = specFor(difficulty)
        val s = draw(Rng(seed + a), spec) ?: return null
        return s.takeIf { accepts(it, spec) }
    }

    /** Whether [s] may ship at [spec]: the ladder finishes it, needing exactly the tier's rung, and it has one answer, the stored one. */
    fun accepts(s: LinksState, spec: Spec): Boolean {
        val b = Board.of(s.size, s.ends)
        val d = depth(b, spec.needs.last) ?: return false
        if (d !in spec.needs) return false
        val verdict = solutions(b)
        return verdict is Verdict.ExactlyOne && maskString(verdict.value) == s.answer
    }

    fun pairsOf(ends: String) = IntArray(ends.length) { if (ends[it] == '.') -1 else ends[it] - 'a' }

    /**
     * A random cover of the board by paths, as a state: every square its own path, then paths joined end to end (the
     * shortest first while any is under three squares, else any at random) while no path would run beside itself,
     * until [Spec.maxPairs] or fewer are left. Null when it stops above that, or a path is shorter than three.
     */
    fun draw(rng: Rng, spec: Spec): LinksState? {
        val n = spec.side
        val sq = n * n
        val owner = IntArray(sq) { -1 }
        val paths = Array(sq) { mutableListOf<Int>() }
        val alive = BooleanArray(sq)
        var count = 0
        val target = rng.nextInt(spec.minPairs, spec.maxPairs)
        // Worms: from the most hemmed-in free square, walk on to the free neighbour with fewest free neighbours of its
        // own (ties drawn), never beside the worm's own body, for up to a drawn length. Few, long paths, few holes.
        fun freeAround(q: Int) = (0 until 4).count { val y = next(n, q, it); y >= 0 && owner[y] < 0 }
        while (true) {
            var least = 5
            val starts = mutableListOf<Int>()
            for (q in 0 until sq) {
                if (owner[q] >= 0) continue
                val k = freeAround(q)
                if (k < least) { least = k; starts.clear() }
                if (k == least) starts += q
            }
            if (starts.isEmpty()) break
            val start = starts[rng.nextInt(starts.size)]
            val worm = paths[start]
            worm += start
            owner[start] = start
            alive[start] = true
            count++
            val length = rng.nextInt(n / 2 + 1, 2 * n)
            while (worm.size < length) {
                val tip = worm.last()
                var best = 5
                val ways = mutableListOf<Int>()
                for (d in 0 until 4) {
                    val y = next(n, tip, d)
                    if (y < 0 || owner[y] >= 0) continue
                    // Not beside any square of the worm but its tip.
                    if ((0 until 4).any { dd -> val z = next(n, y, dd); z >= 0 && z != tip && owner[z] == start }) continue
                    val k = freeAround(y)
                    if (k < best) { best = k; ways.clear() }
                    if (k == best) ways += y
                }
                if (ways.isEmpty()) break
                val y = ways[rng.nextInt(ways.size)]
                worm += y
                owner[y] = start
            }
        }
        fun isEnd(q: Int) = paths[owner[q]].let { it.first() == q || it.last() == q }
        fun touches(a: Int, e: Int, f: Int): Boolean {
            val pb = owner[f]
            for (x in paths[owner[a]]) for (d in 0 until 4) {
                val y = next(n, x, d)
                if (y >= 0 && owner[y] == pb && !(x == e && y == f)) return true
            }
            return false
        }
        fun merge(e: Int, f: Int) {
            val pe = owner[e]
            val pf = owner[f]
            val a = paths[pe]
            val c = paths[pf]
            if (a.last() != e) a.reverse()
            if (c.first() != f) c.reverse()
            a.addAll(c)
            for (x in c) owner[x] = pe
            alive[pf] = false
            paths[pf] = mutableListOf()
            count--
        }

        /**
         * A path [s] under three squares beside the middle of another: that path splits there and one side takes [s] on
         * its end, so nothing is lost and the short one grows. False when no split keeps every path clear of itself.
         */
        fun repair(s: Int): Boolean {
            val short = paths[s]
            for (e in listOf(short.first(), short.last())) for (d in 0 until 4) {
                val x = next(n, e, d)
                if (x < 0 || owner[x] == s || isEnd(x)) continue
                val pid = owner[x]
                val path = paths[pid]
                val i = path.indexOf(x)
                for (keepHead in listOf(true, false)) {
                    val side = if (keepHead) path.subList(0, i + 1).toMutableList() else path.subList(i, path.size).reversed().toMutableList()
                    val rest = if (keepHead) path.subList(i + 1, path.size).toMutableList() else path.subList(0, i).toMutableList()
                    val grown = side + (if (short.first() == e) short else short.reversed())
                    // The grown path may not run beside itself.
                    val at = HashMap<Int, Int>() // probe-only: membership by square
                    grown.forEachIndexed { k, q -> at[q] = k }
                    val clear = grown.indices.all { k ->
                        (0 until 4).all { dd -> val y = next(n, grown[k], dd); val j = at[y]; y < 0 || j == null || j == k - 1 || j == k + 1 }
                    }
                    if (!clear) continue
                    paths[pid] = grown.toMutableList()
                    for (q in grown) owner[q] = pid
                    paths[s] = rest
                    for (q in rest) owner[q] = s
                    return true
                }
            }
            return false
        }

        var rounds = 0
        while (rounds++ < 4 * sq) {
            if (count <= target && (0 until sq).none { alive[it] && paths[it].size < 3 }) break
            // Every legal join: (end e, neighbouring end f of another path) with e < f once.
            val cands = mutableListOf<Long>()
            var shortest = Int.MAX_VALUE
            for (pass in 0..1) {
                // Joins that keep every path clear of itself; once none is left, any join (the count sorts them out).
                if (pass == 1 && (cands.isNotEmpty() || count <= target || !TOUCHING)) break
                for (e in 0 until sq) {
                    if (!isEnd(e)) continue
                    for (d in 0 until 4) {
                        val f = next(n, e, d)
                        if (f < 0 || f < e || owner[f] == owner[e] || !isEnd(f)) continue
                        if (pass == 0 && touches(e, e, f)) continue
                        val len = minOf(paths[owner[e]].size, paths[owner[f]].size)
                        if (len < shortest) shortest = len
                        cands += (len.toLong() shl 40) or (e.toLong() shl 20) or f.toLong()
                    }
                }
            }
            if (cands.isNotEmpty() && (count > target || shortest < 3)) {
                val pool = if (shortest < 3) cands.filter { (it shr 40).toInt() == shortest } else cands
                val pick = pool[rng.nextInt(pool.size)]
                merge(((pick shr 20) and 0xFFFFF).toInt(), (pick and 0xFFFFF).toInt())
                continue
            }
            // Stuck: grow a short path into the middle of a neighbour, then join again.
            val shorts = (0 until sq).filter { alive[it] && paths[it].isNotEmpty() && paths[it].size < 3 }
            if (shorts.isEmpty() || shorts.none { repair(it) }) break
        }
        if (count > spec.maxPairs || count < spec.minPairs) return null
        val live = (0 until sq).filter { alive[it] && paths[it].isNotEmpty() }.map { paths[it] }
        if (live.any { it.size < 3 }) return null
        // Pairs numbered by their first end in reading order.
        val ordered = live.sortedBy { minOf(it.first(), it.last()) }
        val ends = CharArray(sq) { '.' }
        val m = IntArray(sq)
        ordered.forEachIndexed { p, path ->
            ends[path.first()] = 'a' + p
            ends[path.last()] = 'a' + p
            for (i in 1 until path.size) setLine(n, m, path[i - 1], path[i], true)
        }
        return LinksState(n, ends.concatToString(), maskString(m))
    }

    /**
     * Only when none of [ATTEMPTS] draws was accepted, which a year of daily seeds on every tier never reaches
     * (`FallbackTest`). A fixed board per tier, what seed 424242 drew, which `LinksRulesTest` proves is accepted at its
     * tier.
     */
    fun lastResort(difficulty: Difficulty): LinksState = LAST_RESORT[difficulty.ordinal]

    private val LAST_RESORT = listOf(
        LinksState(6, "a..........a.....b..c...de.ed.c..b..", "2aaaac6aaac156ac545545551151152a92a9"),
        LinksState(
            8,
            ".........a....a......b....c.......d.e......c.f.....d.eb.g.fg....",
            "6aaaaaac52aaaa8556aac2c5552c3c555545455555515155553838151382aaa9",
        ),
        LinksState(
            10,
            ".........a.......b.........cd....ef...g..............he.i.......f.j.......j.....d...h...g.b.ic..a...",
            "6aaaaaaac456aaaaa855556aaac41555542c55455553c555555554151555555529455555552a9555155386a9152912a92aa9",
        ),
    )
}
