package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/**
 * A Pointers board: [arrows] are paths of orthogonally adjacent squares on a [width] x [height] grid, each listed
 * tail first, so its head is the last square and it points the way its last step went. [removed] holds the arrows
 * cleared so far, in the order they left; [bumps] the arrows that bumped, in order, one life each ([lives] is
 * [PointersLogic.LIVES] less those, so the two cannot disagree).
 *
 * Every field after [arrows] has a default, so a save that lacks one loads as a board not yet touched there.
 */
@Serializable
data class PointersState(
    val width: Int,
    val height: Int,
    val arrows: List<List<Int>>,
    val removed: List<Int> = emptyList(),
    val bumps: List<Int> = emptyList(),
    override val moves: Int = 0,
) : PuzzleState {

    val lives: Int get() = PointersLogic.LIVES - bumps.size

    /** Every arrow gone: a rule check, since there is no stored answer to compare with. */
    override val solved: Boolean get() = arrows.isNotEmpty() && removed.size == arrows.size

    override val failed: Boolean get() = !solved && lives <= 0

    /** The arrow still on the board at [cell], or -1. */
    fun arrowAt(cell: Int): Int {
        for (i in arrows.indices) if (i !in removed && cell in arrows[i]) return i
        return -1
    }

    /** The first arrow still on the board in arrow [i]'s way, or -1 when its line to the edge is clear. */
    fun blocker(i: Int): Int {
        for (cell in PointersLogic.ray(arrows[i], width, height)) {
            val at = arrowAt(cell)
            if (at >= 0) return at
        }
        return -1
    }

    /**
     * A tap on [cell]: the arrow there slides off if its way is clear, else it bumps and a life goes. Null (nothing
     * happens, nothing is spent) for an empty or cleared square, or a board already won or lost.
     */
    fun tap(cell: Int): PointersState? {
        if (solved || failed) return null
        val i = arrowAt(cell)
        if (i < 0) return null
        return if (blocker(i) < 0) copy(removed = removed + i, moves = moves + 1)
        else copy(bumps = bumps + i, moves = moves + 1)
    }

    /** The same board from the start, lives restored: one state, so one move. */
    fun retry(): PointersState = copy(removed = emptyList(), bumps = emptyList(), moves = moves + 1)
}

/**
 * Rules and generator. A board ships only if it can be cleared: arrows are added one at a time, and an arrow goes
 * down only where the "is in the way of" relation stays acyclic, so some order always clears it (and since taking
 * an arrow away only ever opens lines, any arrow free now stays free, so a player can never be stranded). The
 * finished board is then cleared greedily as a check before it ships ([clearable]).
 *
 * Web parity: no hash container anywhere here; every walk is over arrays in index order and every choice is the
 * [Rng]'s over a list.
 */
internal object PointersLogic {

    const val LIVES = 3

    /** Attempts before [lastResort]; each is a fresh `Rng(seed + attempt)`. */
    private const val ATTEMPTS = 12

    /** Row and column step of each direction: up, right, down, left. */
    private val DR = intArrayOf(-1, 0, 1, 0)
    private val DC = intArrayOf(0, 1, 0, -1)

    /**
     * [width] x [height] squares; arrows of [minLength] to [maxLength] squares that keep straight with
     * [straight] percent chance a step; a board ships with at most [maxEmpty] squares left bare.
     */
    class Spec(val width: Int, val height: Int, val minLength: Int, val maxLength: Int, val straight: Int, val maxEmpty: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(6, 7, 2, 4, 50, 3)
        Difficulty.HARD -> Spec(12, 12, 3, 7, 35, 8)
        Difficulty.EXPERT -> Spec(14, 17, 3, 9, 30, 14)
    }

    /** Direction (index into up, right, down, left) the arrow's head points. */
    fun dirOf(arrow: List<Int>, width: Int): Int {
        val d = arrow[arrow.size - 1] - arrow[arrow.size - 2]
        return when (d) {
            -width -> 0
            1 -> 1
            width -> 2
            else -> 3
        }
    }

    fun rowStep(dir: Int) = DR[dir]
    fun colStep(dir: Int) = DC[dir]

    /** The squares from just past the head to the edge, nearest first. */
    fun ray(arrow: List<Int>, width: Int, height: Int): List<Int> {
        val d = dirOf(arrow, width)
        val head = arrow.last()
        var r = head / width + DR[d]
        var c = head % width + DC[d]
        val out = ArrayList<Int>()
        while (r in 0 until height && c in 0 until width) {
            out += r * width + c
            r += DR[d]
            c += DC[d]
        }
        return out
    }

    /** Takes every free arrow away until none is free; true if that empties the board. */
    fun clearable(s: PointersState): Boolean {
        var cur = s.copy(removed = emptyList())
        while (!cur.solved) {
            val free = cur.arrows.indices.firstOrNull { it !in cur.removed && cur.blocker(it) < 0 } ?: return false
            cur = cur.copy(removed = cur.removed + free)
        }
        return true
    }

    /**
     * How deep each arrow sits: 0 when its way is clear at the start, else one more than the deepest arrow in its
     * way. The number of rounds of "take every free arrow" before it can go; a measure of how tangled a board is.
     */
    fun depths(s: PointersState): IntArray {
        val n = s.arrows.size
        val owner = IntArray(s.width * s.height) { -1 }
        s.arrows.forEachIndexed { i, a -> a.forEach { owner[it] = i } }
        val depth = IntArray(n) { -1 }
        fun of(i: Int): Int {
            if (depth[i] >= 0) return depth[i]
            var d = 0
            for (cell in ray(s.arrows[i], s.width, s.height)) {
                val b = owner[cell]
                if (b >= 0) d = maxOf(d, of(b) + 1)
            }
            depth[i] = d
            return d
        }
        for (i in 0 until n) of(i)
        return depth
    }

    fun generate(seed: Long, difficulty: Difficulty): PointersState =
        generateVerified(seed, difficulty) ?: lastResort(difficulty)

    /** A board that covers the grid to the tier's density and clears, or null if no attempt made one. */
    fun generateVerified(seed: Long, difficulty: Difficulty): PointersState? {
        val spec = specFor(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val arrows = build(Rng(seed + attempt), spec)
            val bare = spec.width * spec.height - arrows.sumOf { it.size }
            if (bare > spec.maxEmpty) continue
            val s = PointersState(spec.width, spec.height, arrows)
            if (s.arrows.indices.all { s.blocker(it) < 0 }) continue
            if (clearable(s)) return s
        }
        return null
    }

    /** Every row one arrow pointing right: trivially clear, and only ever reached if every attempt failed. */
    fun lastResort(difficulty: Difficulty): PointersState {
        val spec = specFor(difficulty)
        val w = spec.width
        return PointersState(w, spec.height, (0 until spec.height).map { r -> (0 until w).map { r * w + it } })
    }

    /**
     * One board's arrows. Rounds over the bare squares in a shuffled order try each as a head, in each direction,
     * with a body walked back from it; a candidate goes down when it keeps "in the way of" acyclic. When a round
     * places nothing, bare squares next to an arrow's tail are folded into that arrow where that stays acyclic.
     */
    private fun build(rng: Rng, spec: Spec): List<List<Int>> {
        val w = spec.width
        val h = spec.height
        val n = w * h
        val owner = IntArray(n) { -1 }
        val arrows = ArrayList<MutableList<Int>>()
        // blocks[x] = arrows that x is in the way of (x has a square on their ray); edges point to who must wait.
        val blocks = ArrayList<MutableList<Int>>()
        // rayOver[cell] = arrows whose ray crosses cell.
        val rayOver = Array(n) { ArrayList<Int>(4) }
        val seen = IntArray(n + 1)
        var stamp = 0

        /** Whether some arrow in [from] can reach an arrow in [to] through "is in the way of". */
        fun reaches(from: List<Int>, to: BooleanArray): Boolean {
            if (from.isEmpty()) return false
            stamp++
            val stack = ArrayList<Int>(from)
            from.forEach { seen[it] = stamp }
            while (stack.isNotEmpty()) {
                val x = stack.removeAt(stack.size - 1)
                if (to[x]) return true
                for (y in blocks[x]) if (seen[y] != stamp) {
                    seen[y] = stamp
                    stack += y
                }
            }
            return false
        }

        val mark = BooleanArray(n)
        val onRay = BooleanArray(n)
        val walk = ArrayList<Int>()

        fun tryPlace(head: Int, dir: Int, length: Int): Boolean {
            val hr = head / w
            val hc = head % w
            val br = hr - DR[dir]
            val bc = hc - DC[dir]
            if (br !in 0 until h || bc !in 0 until w || owner[br * w + bc] >= 0) return false
            // The ray: squares it crosses, and the arrows sitting on it (they are in this arrow's way).
            val ray = ArrayList<Int>()
            run {
                var r = hr + DR[dir]
                var c = hc + DC[dir]
                while (r in 0 until h && c in 0 until w) {
                    ray += r * w + c
                    r += DR[dir]
                    c += DC[dir]
                }
            }
            ray.forEach { onRay[it] = true }
            // Walk the body back from the head, never onto its own ray.
            walk.clear()
            walk += head
            walk += br * w + bc
            var wd = (dir + 2) % 4
            while (walk.size < length) {
                val cur = walk.last()
                val options = ArrayList<Int>(3)
                for (d in 0 until 4) {
                    if (d == (wd + 2) % 4) continue
                    val r = cur / w + DR[d]
                    val c = cur % w + DC[d]
                    if (r !in 0 until h || c !in 0 until w) continue
                    val cell = r * w + c
                    if (owner[cell] >= 0 || onRay[cell] || cell in walk) continue
                    options += d
                }
                if (options.isEmpty()) break
                val next = if (wd in options && rng.nextInt(100) < spec.straight) wd else rng.pick(options)
                wd = next
                walk += (cur / w + DR[next]) * w + cur % w + DC[next]
            }
            ray.forEach { onRay[it] = false }
            // Acyclic: no arrow this one would be in the way of may already (indirectly) be in this one's way.
            val waitsFor = ArrayList<Int>()
            for (cell in ray) {
                val o = owner[cell]
                if (o >= 0 && o !in waitsFor) waitsFor += o
            }
            val holds = ArrayList<Int>()
            for (cell in walk) for (a in rayOver[cell]) if (a !in holds) holds += a
            waitsFor.forEach { mark[it] = true }
            val cycle = reaches(holds, mark)
            waitsFor.forEach { mark[it] = false }
            if (cycle) return false
            val id = arrows.size
            arrows += walk.asReversed().toMutableList()
            blocks += holds.toMutableList()
            for (o in waitsFor) blocks[o] += id
            walk.forEach { owner[it] = id }
            ray.forEach { rayOver[it] += id }
            return true
        }

        while (true) {
            val bare = (0 until n).filter { owner[it] < 0 }
            if (bare.isEmpty()) break
            var placed = false
            for (head in rng.shuffled(bare)) {
                if (owner[head] >= 0) continue
                val length = rng.nextInt(spec.minLength, spec.maxLength)
                for (dir in rng.shuffled(listOf(0, 1, 2, 3))) {
                    if (tryPlace(head, dir, length)) {
                        placed = true
                        break
                    }
                }
            }
            if (!placed) break
        }

        // Fold leftover squares into a neighbouring arrow's tail where that stays acyclic.
        var grew = true
        while (grew) {
            grew = false
            for (cell in 0 until n) {
                if (owner[cell] >= 0) continue
                for (d in 0 until 4) {
                    val r = cell / w + DR[d]
                    val c = cell % w + DC[d]
                    if (r !in 0 until h || c !in 0 until w) continue
                    val a = owner[r * w + c]
                    if (a < 0 || arrows[a][0] != r * w + c || a in rayOver[cell]) continue
                    val holds = rayOver[cell].filter { it !in blocks[a] }
                    mark[a] = true
                    val cycle = reaches(holds, mark)
                    mark[a] = false
                    if (cycle) continue
                    arrows[a].add(0, cell)
                    blocks[a] += holds
                    owner[cell] = a
                    grew = true
                    break
                }
            }
        }
        return arrows
    }
}
