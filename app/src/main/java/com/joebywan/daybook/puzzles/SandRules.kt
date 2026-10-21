package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng

/**
 * Sand's rules, and the search that proves a board can be sorted (`docs/SAND_SPEC.md`).
 *
 * A board is `C` colours of `H` layers each, dealt into `C` full tubes, plus two empty ones. A pour moves the whole run
 * of the source's top colour, or as much of it as fits, onto an empty tube or a matching top. Solved is every tube
 * empty or `H` of one colour. Boards have many answers; what [generateVerified] proves is that one exists, by finding it.
 *
 * Tubes are lists, bottom to top, of colour indices `0 until colours`. Pure Kotlin, no `java.*` (the web compiles it).
 * The search's visited set is membership only: its order never reaches a choice, so the JVM and wasm agree.
 */
internal object SandLogic {

    /** What [solve] found. Only [Solvable] carries anything, so "ran out of budget" cannot be read as an answer. */
    sealed interface Verdict {
        /** A sorting sequence of pours `(from, to)` from the position searched; replaying it ends solved. */
        data class Solvable(val path: List<Pair<Int, Int>>) : Verdict

        /** Every position reachable was searched (within the budget) and none is solved. A proof. */
        data object Unsolvable : Verdict

        /** The budget ran out first: nothing is known. Never read this as [Unsolvable]. */
        data object Truncated : Verdict
    }

    /** Colours and height per tier. Two spare tubes on every tier: slack never varies (standards section 3). */
    class Spec(val colours: Int, val height: Int, val budget: Int)

    const val SPARES = 2

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(5, 4, GEN_BUDGET)
        Difficulty.HARD -> Spec(7, 4, GEN_BUDGET)
        Difficulty.EXPERT -> Spec(8, 4, GEN_BUDGET)
    }

    /** Positions the generator may visit proving one board. */
    const val GEN_BUDGET = 200_000

    private const val ATTEMPTS = 40

    // ---- the rules ----------------------------------------------------------------------------

    /** The length of the run of [t]'s top colour. 0 for an empty tube. */
    fun topRun(t: List<Int>): Int {
        if (t.isEmpty()) return 0
        var n = 1
        while (n < t.size && t[t.size - 1 - n] == t.last()) n++
        return n
    }

    /** Whether pouring [from] onto [to] is a pour: not itself, a source with sand, a target with room and an empty or matching top. */
    fun canPour(tubes: List<List<Int>>, height: Int, from: Int, to: Int): Boolean {
        if (from == to) return false
        val a = tubes[from]
        val b = tubes[to]
        return a.isNotEmpty() && b.size < height && (b.isEmpty() || b.last() == a.last())
    }

    /** How many layers the pour moves: the source's top run, or as much as fits. Precondition: [canPour]. */
    fun pourSize(tubes: List<List<Int>>, height: Int, from: Int, to: Int): Int =
        minOf(topRun(tubes[from]), height - tubes[to].size)

    /** The tubes after pouring [from] onto [to]. Precondition: [canPour]. */
    fun pour(tubes: List<List<Int>>, height: Int, from: Int, to: Int): List<List<Int>> {
        require(canPour(tubes, height, from, to)) { "not a pour: $from -> $to" }
        val k = pourSize(tubes, height, from, to)
        val colour = tubes[from].last()
        return tubes.mapIndexed { i, t ->
            when (i) {
                from -> t.subList(0, t.size - k).toList()
                to -> t + List(k) { colour }
                else -> t
            }
        }
    }

    /** Every tube empty or [height] layers of one colour. */
    fun isSolved(tubes: List<List<Int>>, height: Int): Boolean =
        tubes.all { t -> t.isEmpty() || (t.size == height && t.all { it == t[0] }) }

    /** Not solved, and no pour exists at all. */
    fun isStuck(tubes: List<List<Int>>, height: Int): Boolean =
        !isSolved(tubes, height) && tubes.indices.none { a -> tubes.indices.any { b -> canPour(tubes, height, a, b) } }

    /** Every pour `(from, to)` the rules allow, in index order. */
    fun pours(tubes: List<List<Int>>, height: Int): List<Pair<Int, Int>> =
        tubes.indices.flatMap { a -> tubes.indices.filter { b -> canPour(tubes, height, a, b) }.map { b -> a to b } }

    /**
     * The positions one pour before [tubes]: every `(before, from, to)` such that pouring [from] onto [to] in `before`
     * is a pour and gives [tubes]. Each is checked by pouring forward, so it cannot disagree with the rules.
     */
    fun unpours(tubes: List<List<Int>>, height: Int): List<Triple<List<List<Int>>, Int, Int>> {
        val out = mutableListOf<Triple<List<List<Int>>, Int, Int>>()
        for (to in tubes.indices) {
            val b = tubes[to]
            val run = topRun(b)
            for (from in tubes.indices) {
                if (from == to) continue
                for (k in 1..run) {
                    if (tubes[from].size + k > height) break
                    val before = tubes.mapIndexed { i, t ->
                        when (i) {
                            to -> t.subList(0, t.size - k).toList()
                            from -> t + List(k) { b.last() }
                            else -> t
                        }
                    }
                    if (canPour(before, height, from, to) && pour(before, height, from, to) == tubes) out += Triple(before, from, to)
                }
            }
        }
        return out
    }

    // ---- the search ---------------------------------------------------------------------------

    // A tube packed in an Int: its length in bits 24 up, layer i's colour + 1 in bits 4i..4i+3 (bottom is layer 0).
    // Heights to 5 and colours to 15 fit. Packing is for speed only; the rules above are the definition.

    private fun pack(t: List<Int>): Int {
        var code = t.size shl 24
        for ((i, c) in t.withIndex()) code = code or ((c + 1) shl (4 * i))
        return code
    }

    private fun len(code: Int) = code ushr 24

    private fun at(code: Int, i: Int) = ((code ushr (4 * i)) and 15) - 1

    private fun top(code: Int) = at(code, len(code) - 1)

    private fun run(code: Int): Int {
        val n = len(code)
        if (n == 0) return 0
        val c = top(code)
        var r = 1
        while (r < n && at(code, n - 1 - r) == c) r++
        return r
    }

    private fun packedSolved(p: IntArray, height: Int): Boolean {
        for (code in p) {
            val n = len(code)
            if (n == 0) continue
            if (n != height || run(code) != n) return false
        }
        return true
    }

    /** The canonical key of a position: tubes are interchangeable, so the sorted tubes, as a string (two chars a tube). */
    private fun key(p: IntArray): String {
        val s = p.copyOf()
        s.sort()
        val chars = CharArray(s.size * 2)
        for (i in s.indices) {
            chars[2 * i] = (s[i] and 0xFFFF).toChar()
            chars[2 * i + 1] = (s[i] ushr 16).toChar()
        }
        return chars.concatToString()
    }

    private class Frame(val pos: IntArray, val from: Int, val to: Int) {
        var next = 0
    }

    /**
     * Depth first from [tubes], trying pours in index order (`from` outer, `to` inner), never visiting a canonical position
     * twice. [budget] is the number of new positions it may visit; past it the answer is [Verdict.Truncated].
     *
     * Two prunings, each exact because the position it skips has the same canonical key as one the search does visit
     * (tubes all hold [height], so which tube holds what is all that differs): a tube of one colour is never poured into
     * an empty tube (it moves whole, so the result is the same tubes with two swapped); and of several empty tubes only
     * the first receives a given pour (pouring into any other gives the same tubes in another order). A pour straight
     * back is not pruned: when the target already had that colour on top it does not restore the position before, and
     * the visited set already covers the case where it does.
     *
     * Works from any position (part-full tubes, any number of empty ones). A board already solved is `Solvable(empty)`;
     * [Verdict.Unsolvable] is returned only when the whole reachable space was searched without passing [budget].
     */
    fun solve(tubes: List<List<Int>>, height: Int, budget: Int): Verdict = search(tubes, height, budget).first

    /** [solve]'s verdict with the number of positions it visited, for measuring the tiers. */
    fun search(tubes: List<List<Int>>, height: Int, budget: Int): Pair<Verdict, Int> {
        val n = tubes.size
        val start = IntArray(n) { pack(tubes[it]) }
        if (packedSolved(start, height)) return Verdict.Solvable(emptyList()) to 0
        val seen = HashSet<String>() // membership only: its order never reaches a choice
        seen.add(key(start))
        val stack = ArrayList<Frame>()
        stack.add(Frame(start, -1, -1))
        var visited = 1
        while (stack.isNotEmpty()) {
            val f = stack[stack.size - 1]
            val p = f.pos
            var moved = false
            while (f.next < n * n) {
                val from = f.next / n
                val to = f.next % n
                f.next++
                if (from == to) continue
                val a = p[from]
                val b = p[to]
                val la = len(a)
                val lb = len(b)
                if (la == 0 || lb == height) continue
                if (lb > 0 && top(b) != top(a)) continue
                val ra = run(a)
                if (lb == 0) {
                    if (ra == la) continue
                    var earlierEmpty = false
                    for (k in 0 until to) if (k != from && len(p[k]) == 0) { earlierEmpty = true; break }
                    if (earlierEmpty) continue
                }
                val k = minOf(ra, height - lb)
                val c = top(a) + 1
                var na = a
                for (i in la - k until la) na = na and (15 shl (4 * i)).inv()
                na = (na and 0xFFFFFF) or ((la - k) shl 24)
                var nb = (b and 0xFFFFFF) or ((lb + k) shl 24)
                for (i in lb until lb + k) nb = nb or (c shl (4 * i))
                val child = p.copyOf()
                child[from] = na
                child[to] = nb
                if (!seen.add(key(child))) continue
                if (packedSolved(child, height)) {
                    val path = ArrayList<Pair<Int, Int>>()
                    for (i in 1 until stack.size) path.add(stack[i].from to stack[i].to)
                    path.add(from to to)
                    return Verdict.Solvable(path) to visited
                }
                if (++visited > budget) return Verdict.Truncated to visited
                stack.add(Frame(child, from, to))
                moved = true
                break
            }
            if (!moved) stack.removeAt(stack.size - 1)
        }
        return Verdict.Unsolvable to visited
    }

    // ---- generation ---------------------------------------------------------------------------

    /**
     * Whether a deal gives the board away: a tube already of one colour, or a run longer than `height - 2` in one tube
     * (a run of `height - 1` leaves one layer to move).
     */
    fun givesAway(tubes: List<List<Int>>, height: Int): Boolean =
        tubes.any { t -> t.isNotEmpty() && (t.all { it == t[0] } || longestRun(t) > height - 2) }

    private fun longestRun(t: List<Int>): Int {
        var best = 0
        var r = 0
        for (i in t.indices) {
            r = if (i > 0 && t[i] == t[i - 1]) r + 1 else 1
            best = maxOf(best, r)
        }
        return best
    }

    /** Attempt [attempt]'s deal for [seed]: every layer shuffled (Fisher-Yates), dealt into full tubes, two empty ones after. */
    fun deal(seed: Long, attempt: Int, spec: Spec): List<List<Int>> {
        val layers = (0 until spec.colours).flatMap { c -> List(spec.height) { c } }
        val shuffled = Rng(seed + attempt).shuffled(layers)
        return shuffled.chunked(spec.height) + List(SPARES) { emptyList() }
    }

    /** The board when a deal that gives nothing away was proved sortable by [solve], null when no attempt was. */
    fun generateVerified(seed: Long, difficulty: Difficulty): SandState? {
        val spec = specFor(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val tubes = deal(seed, attempt, spec)
            if (givesAway(tubes, spec.height)) continue
            if (solve(tubes, spec.height, spec.budget) !is Verdict.Solvable) continue
            if (trivial(tubes, spec.height, seed + attempt)) continue
            return SandState(tubes, spec.height, spec.colours)
        }
        return null
    }

    /** Pours a naive player may make before giving up. */
    const val NAIVE_CAP = 300

    /** Tries of the naive player per board in the difficulty filter. */
    const val NAIVE_TRIES = 6

    /**
     * A naive player: each turn a pour picked uniformly (with [rng]) among the legal ones that are not the last pour
     * poured straight back. True when it sorts the board within [NAIVE_CAP] pours.
     */
    fun naiveSorts(tubes: List<List<Int>>, height: Int, rng: Rng): Boolean {
        var t = tubes
        var last: Pair<Int, Int>? = null
        repeat(NAIVE_CAP) {
            if (isSolved(t, height)) return true
            val back = last
            val ps = pours(t, height).filter { back == null || !(it.first == back.second && it.second == back.first) }
            if (ps.isEmpty()) return false
            val m = rng.pick(ps)
            t = pour(t, height, m.first, m.second)
            last = m
        }
        return isSolved(t, height)
    }

    /** The difficulty filter: a board the naive player sorts on every one of [NAIVE_TRIES] tries asks no thought. */
    fun trivial(tubes: List<List<Int>>, height: Int, seed: Long): Boolean {
        val rng = Rng(seed xor NAIVE_SALT)
        return (0 until NAIVE_TRIES).all { naiveSorts(tubes, height, rng) }
    }

    private const val NAIVE_SALT = 0x5A4D_5A4DL

    /**
     * One fixed board per tier for when nothing was proved (a year per tier never reaches it, `FallbackTest`): the board of
     * seed 1, written out so a change to the deal cannot move it. A test proves each unsolved, `H` of every colour and sortable.
     */
    fun lastResort(difficulty: Difficulty): SandState {
        val spec = specFor(difficulty)
        val tubes = when (difficulty) {
            Difficulty.STANDARD -> "4431 3202 4324 0121 1003"
            Difficulty.HARD -> "5102 2603 4250 6415 5321 0314 4636"
            Difficulty.EXPERT -> "6501 2740 3521 0514 2047 3166 7267 4335"
        }.split(" ").map { t -> t.map { it - '0' } } + List(SPARES) { emptyList() }
        return SandState(tubes, spec.height, spec.colours)
    }
}
