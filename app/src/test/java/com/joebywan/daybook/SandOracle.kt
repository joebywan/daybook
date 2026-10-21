package com.joebywan.daybook

/**
 * An independent checker for Sand, written from `docs/SAND_SPEC.md`'s prose and on a different principle from
 * `SandLogic.solve` on every point the spec names: breadth-first, not depth-first; positions are the tubes in their own
 * order (no canonical sort, so two positions that differ only by swapping tubes are two positions); every legal pour is
 * tried (no pruning: a single-colour tube into an empty one, a pour into the second of two empty tubes and a pour straight
 * back are all expanded); no budget. So it is for small boards only.
 *
 * The pour is written a layer at a time: lift the top grain while it is the colour that started the pour and the target
 * has room, which is "the whole run, or as much of it as fits" without ever computing a run or a room.
 */
object SandOracle {

    /** "A is not empty, B is not full, and B is empty or B's top colour equals A's top colour"; a tube onto itself is not a pour. */
    fun canPour(tubes: List<List<Int>>, height: Int, from: Int, to: Int): Boolean {
        if (from == to) return false
        val a = tubes[from]
        val b = tubes[to]
        if (a.isEmpty() || b.size >= height) return false
        return b.isEmpty() || b.last() == a.last()
    }

    /** The position after pouring [from] onto [to], or null when that is not a pour. */
    fun pour(tubes: List<List<Int>>, height: Int, from: Int, to: Int): List<List<Int>>? {
        if (!canPour(tubes, height, from, to)) return null
        val a = tubes[from].toMutableList()
        val b = tubes[to].toMutableList()
        val colour = a.last()
        while (a.isNotEmpty() && a.last() == colour && b.size < height) b.add(a.removeAt(a.lastIndex))
        return tubes.mapIndexed { i, t -> if (i == from) a else if (i == to) b else t }
    }

    /** "Every tube is empty or holds H layers of one colour." */
    fun isSolved(tubes: List<List<Int>>, height: Int): Boolean =
        tubes.all { t -> t.isEmpty() || (t.size == height && t.toSet().size == 1) }

    fun hasPour(tubes: List<List<Int>>, height: Int): Boolean =
        tubes.indices.any { f -> tubes.indices.any { t -> canPour(tubes, height, f, t) } }

    /** "Not solved and no legal pour exists." */
    fun isStuck(tubes: List<List<Int>>, height: Int): Boolean = !isSolved(tubes, height) && !hasPour(tubes, height)

    /** [shortest] is a fewest-pours road to solved (empty when already solved), or null when every reachable position was seen. */
    class Result(val shortest: List<Pair<Int, Int>>?, val positions: Int) {
        val solvable get() = shortest != null
    }

    fun solve(start: List<List<Int>>, height: Int): Result {
        // Each position remembers the position and pour it was first reached by, so the road is read back from the end.
        val cameFrom = HashMap<List<List<Int>>, Pair<List<List<Int>>, Pair<Int, Int>>?>()
        cameFrom[start] = null
        var layer = listOf(start)
        while (layer.isNotEmpty()) {
            for (p in layer) if (isSolved(p, height)) return Result(roadTo(p, cameFrom), cameFrom.size)
            val next = ArrayList<List<List<Int>>>()
            for (p in layer) for (f in p.indices) for (t in p.indices) {
                val q = pour(p, height, f, t) ?: continue
                if (q !in cameFrom) {
                    cameFrom[q] = p to (f to t)
                    next += q
                }
            }
            layer = next
        }
        return Result(null, cameFrom.size)
    }

    private fun roadTo(end: List<List<Int>>, cameFrom: Map<List<List<Int>>, Pair<List<List<Int>>, Pair<Int, Int>>?>): List<Pair<Int, Int>> {
        val road = ArrayList<Pair<Int, Int>>()
        var p = end
        while (true) {
            val (prev, move) = cameFrom.getValue(p) ?: break
            road += move
            p = prev
        }
        return road.asReversed()
    }

    /** Applies [road] with the oracle's own pour rule; null as soon as one step is not a legal pour. */
    fun replay(start: List<List<Int>>, height: Int, road: List<Pair<Int, Int>>): List<List<Int>>? {
        var p = start
        for ((f, t) in road) p = pour(p, height, f, t) ?: return null
        return p
    }
}
