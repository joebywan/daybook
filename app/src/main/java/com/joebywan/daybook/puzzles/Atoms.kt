package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleState
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng

/** An atom sits on a lattice point and needs exactly [bonds] bond-ends. */
data class Atom(val row: Int, val col: Int, val bonds: Int)

/** A possible bond line between two atoms that face each other with nothing in between. */
data class Pair2(val a: Int, val b: Int, val horizontal: Boolean)

data class AtomsState(
    val size: Int,
    val atoms: List<Atom>,
    val pairs: List<Pair2>,
    val counts: List<Int>,
    val solution: List<Int>,
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean get() = counts == solution

    fun degree(atom: Int): Int =
        pairs.indices.sumOf { if (pairs[it].a == atom || pairs[it].b == atom) counts[it] else 0 }

    /** Atoms already carrying more bonds than they want. */
    fun overloaded(): Set<Int> = atoms.indices.filter { degree(it) > atoms[it].bonds }.toSet()

    fun cycle(pair: Int): AtomsState {
        val next = (counts[pair] + 1) % 3
        // A bond may not cross a bond running the other way.
        if (next > 0 && crosses(pair)) {
            return copy(counts = counts.toMutableList().also { it[pair] = 0 }, moves = moves + 1)
        }
        return copy(counts = counts.toMutableList().also { it[pair] = next }, moves = moves + 1)
    }

    private fun crosses(pair: Int): Boolean =
        pairs.indices.any { other ->
            other != pair && counts[other] > 0 && Atoms.cross(this, pair, other)
        }
}

/**
 * Atoms — Hashiwokakero, dressed as chemistry.
 *
 * Join the atoms with bonds so every atom carries exactly its number, no bond crosses another, and
 * the whole molecule hangs together as one piece.
 */
object Atoms : PuzzleType {

    override val id = "atoms"
    override val displayName = "Atoms"
    override val tagline = "Bond every atom into one molecule"
    override val accent = 0xFF6FA8C4
    override val rules = listOf(
        "Each atom's number is exactly how many bond-ends it must carry.",
        "Bonds run straight, horizontally or vertically, between two atoms.",
        "At most two bonds may join the same pair. Bonds may never cross.",
        "Every atom must end up connected into a single molecule.",
        "Tap between two atoms to cycle none, one, two.",
    )

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 7 to 10
        Difficulty.HARD -> 9 to 16
        Difficulty.EXPERT -> 11 to 24
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val (n, wanted) = shape(difficulty)

        repeat(200) { attempt ->
            val rng = Rng(seed + attempt)
            val built = grow(rng, n, wanted) ?: return@repeat
            val (atoms, bonds) = built
            if (atoms.size < 4) return@repeat
            val pairs = pairsFor(atoms, n)
            val solution = pairs.map { p ->
                bonds[p.a to p.b] ?: bonds[p.b to p.a] ?: 0
            }
            if (solution.sum() == 0) return@repeat
            if (countSolutions(atoms, pairs) != 1) return@repeat
            return AtomsState(n, atoms, pairs, List(pairs.size) { 0 }, solution)
        }

        // Fallback: a short chain always has a unique answer.
        val atoms = listOf(Atom(0, 0, 1), Atom(0, 2, 2), Atom(2, 2, 1))
        val pairs = pairsFor(atoms, 3)
        val solution = pairs.map { 1 }
        return AtomsState(3, atoms, pairs, List(pairs.size) { 0 }, solution)
    }

    /** Grows a connected molecule by sprouting new atoms off existing ones. */
    private fun grow(rng: Rng, n: Int, wanted: Int): kotlin.Pair<List<Atom>, Map<kotlin.Pair<Int, Int>, Int>>? {
        val occupied = HashMap<kotlin.Pair<Int, Int>, Int>()   // cell -> atom index
        val positions = mutableListOf<kotlin.Pair<Int, Int>>()
        val bonds = HashMap<kotlin.Pair<Int, Int>, Int>()
        val used = HashSet<kotlin.Pair<Int, Int>>()            // cells a bond passes through

        val start = rng.nextInt(n) to rng.nextInt(n)
        positions += start
        occupied[start] = 0

        var guard = 0
        while (positions.size < wanted && guard++ < wanted * 60) {
            val from = rng.nextInt(positions.size)
            val (r, c) = positions[from]
            val (dr, dc) = rng.pick(listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
            val distance = rng.nextInt(2, 4)
            val nr = r + dr * distance
            val nc = c + dc * distance
            if (nr !in 0 until n || nc !in 0 until n) continue
            if (occupied.containsKey(nr to nc)) continue

            // The line between must be clear of atoms and of bonds running the other way.
            val between = (1 until distance).map { step -> (r + dr * step) to (c + dc * step) }
            if (between.any { occupied.containsKey(it) || it in used }) continue

            val index = positions.size
            positions += (nr to nc)
            occupied[nr to nc] = index
            between.forEach { used += it }
            bonds[from to index] = rng.nextInt(1, 2)
        }

        if (positions.size < 4) return null

        val degrees = IntArray(positions.size)
        bonds.forEach { (pair, count) ->
            degrees[pair.first] += count
            degrees[pair.second] += count
        }
        if (degrees.any { it == 0 || it > 8 }) return null

        val atoms = positions.mapIndexed { i, (r, c) -> Atom(r, c, degrees[i]) }
        return atoms to bonds
    }

    /** Every ordered-by-index pair of atoms that see each other along a clear row or column. */
    private fun pairsFor(atoms: List<Atom>, n: Int): List<Pair2> = buildList {
        for (i in atoms.indices) for (j in i + 1 until atoms.size) {
            val a = atoms[i]
            val b = atoms[j]
            if (a.row == b.row) {
                val lo = minOf(a.col, b.col)
                val hi = maxOf(a.col, b.col)
                val blocked = atoms.any { it.row == a.row && it.col in (lo + 1) until hi }
                if (!blocked) add(Pair2(i, j, horizontal = true))
            } else if (a.col == b.col) {
                val lo = minOf(a.row, b.row)
                val hi = maxOf(a.row, b.row)
                val blocked = atoms.any { it.col == a.col && it.row in (lo + 1) until hi }
                if (!blocked) add(Pair2(i, j, horizontal = false))
            }
        }
    }

    /** True when the two bond lines intersect. */
    fun cross(s: AtomsState, p: Int, q: Int): Boolean {
        val one = s.pairs[p]
        val two = s.pairs[q]
        if (one.horizontal == two.horizontal) return false
        val (h, v) = if (one.horizontal) one to two else two to one
        val hRow = s.atoms[h.a].row
        val hLo = minOf(s.atoms[h.a].col, s.atoms[h.b].col)
        val hHi = maxOf(s.atoms[h.a].col, s.atoms[h.b].col)
        val vCol = s.atoms[v.a].col
        val vLo = minOf(s.atoms[v.a].row, s.atoms[v.b].row)
        val vHi = maxOf(s.atoms[v.a].row, s.atoms[v.b].row)
        return vCol in (hLo + 1) until hHi && hRow in (vLo + 1) until vHi
    }

    // ---- solver -------------------------------------------------------------------------------

    private fun countSolutions(atoms: List<Atom>, pairs: List<Pair2>): Int {
        val counts = IntArray(pairs.size)
        val degree = IntArray(atoms.size)
        val incident = Array(atoms.size) { a ->
            pairs.indices.filter { pairs[it].a == a || pairs[it].b == a }
        }
        // Capacity still available to each atom if all its untouched pairs were maxed out.
        var found = 0

        fun crossesNow(index: Int): Boolean {
            val one = pairs[index]
            return pairs.indices.any { other ->
                other != index && counts[other] > 0 && run {
                    val two = pairs[other]
                    if (one.horizontal == two.horizontal) return@run false
                    val (h, v) = if (one.horizontal) one to two else two to one
                    val hRow = atoms[h.a].row
                    val hLo = minOf(atoms[h.a].col, atoms[h.b].col)
                    val hHi = maxOf(atoms[h.a].col, atoms[h.b].col)
                    val vCol = atoms[v.a].col
                    val vLo = minOf(atoms[v.a].row, atoms[v.b].row)
                    val vHi = maxOf(atoms[v.a].row, atoms[v.b].row)
                    vCol in (hLo + 1) until hHi && hRow in (vLo + 1) until vHi
                }
            }
        }

        fun feasible(upTo: Int): Boolean {
            for (a in atoms.indices) {
                var remaining = 0
                for (p in incident[a]) if (p >= upTo) remaining += 2
                if (degree[a] > atoms[a].bonds) return false
                if (degree[a] + remaining < atoms[a].bonds) return false
            }
            return true
        }

        fun connected(): Boolean {
            val seen = BooleanArray(atoms.size)
            val stack = ArrayDeque<Int>()
            stack.addLast(0)
            seen[0] = true
            var count = 1
            while (stack.isNotEmpty()) {
                val a = stack.removeLast()
                for (p in incident[a]) {
                    if (counts[p] == 0) continue
                    val other = if (pairs[p].a == a) pairs[p].b else pairs[p].a
                    if (!seen[other]) {
                        seen[other] = true
                        count++
                        stack.addLast(other)
                    }
                }
            }
            return count == atoms.size
        }

        fun assign(index: Int) {
            if (found >= 2) return
            if (index == pairs.size) {
                if (atoms.indices.all { degree[it] == atoms[it].bonds } && connected()) found++
                return
            }
            for (value in 0..2) {
                counts[index] = value
                degree[pairs[index].a] += value
                degree[pairs[index].b] += value
                if ((value == 0 || !crossesNow(index)) && feasible(index + 1)) {
                    assign(index + 1)
                }
                degree[pairs[index].a] -= value
                degree[pairs[index].b] -= value
                counts[index] = 0
                if (found >= 2) return
            }
        }

        assign(0)
        return found
    }

    // ---- play ---------------------------------------------------------------------------------

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as AtomsState
        val wrong = s.counts.indices.firstOrNull { s.counts[it] != s.solution[it] } ?: return null
        return s.copy(
            counts = s.counts.toMutableList().also { it[wrong] = s.solution[wrong] },
            moves = s.moves + 1,
        )
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as AtomsState
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val overloaded = s.overloaded()

        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            val step = maxWidth / s.size
            val stepPx = with(LocalDensity.current) { step.toPx() }
            val radius = stepPx * 0.34f

            Canvas(
                Modifier
                    .width(step * s.size)
                    .height(step * s.size)
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { offset: Offset ->
                            val hit = s.pairs.indices.minByOrNull { index ->
                                distanceToPair(s, index, offset, stepPx)
                            } ?: return@detectTapGestures
                            if (distanceToPair(s, hit, offset, stepPx) < stepPx * 0.34f) {
                                onState(s.cycle(hit))
                            }
                        }
                    }
            ) {
                fun centre(atom: Atom) =
                    Offset((atom.col + 0.5f) * stepPx, (atom.row + 0.5f) * stepPx)

                s.pairs.forEachIndexed { index, pair ->
                    val count = s.counts[index]
                    if (count == 0) return@forEachIndexed
                    val from = centre(s.atoms[pair.a])
                    val to = centre(s.atoms[pair.b])
                    val offsets = if (count == 1) listOf(0f) else listOf(-stepPx * 0.09f, stepPx * 0.09f)
                    offsets.forEach { shift ->
                        val dx = if (pair.horizontal) 0f else shift
                        val dy = if (pair.horizontal) shift else 0f
                        drawLine(
                            color = Color(accent),
                            start = Offset(from.x + dx, from.y + dy),
                            end = Offset(to.x + dx, to.y + dy),
                            strokeWidth = stepPx * 0.055f,
                        )
                    }
                }

                s.atoms.forEachIndexed { index, atom ->
                    val c = centre(atom)
                    val complete = s.degree(index) == atom.bonds
                    drawCircle(color = scheme.background, radius = radius, center = c)
                    drawCircle(
                        color = when {
                            index in overloaded -> scheme.error
                            complete -> Color(accent)
                            else -> scheme.onBackground
                        },
                        radius = radius,
                        center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stepPx * 0.05f),
                    )
                    val layout = measurer.measure(
                        atom.bonds.toString(),
                        TextStyle(
                            color = if (complete) Color(accent) else scheme.onBackground,
                            fontSize = (step.value * 0.34f).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(
                            c.x - layout.size.width / 2f,
                            c.y - layout.size.height / 2f,
                        ),
                    )
                }
            }
        }
    }

    /** Perpendicular distance from a tap to a bond line, used for hit-testing. */
    private fun distanceToPair(s: AtomsState, index: Int, point: Offset, stepPx: Float): Float {
        val pair = s.pairs[index]
        val a = s.atoms[pair.a]
        val b = s.atoms[pair.b]
        val ax = (a.col + 0.5f) * stepPx
        val ay = (a.row + 0.5f) * stepPx
        val bx = (b.col + 0.5f) * stepPx
        val by = (b.row + 0.5f) * stepPx
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0f) return Float.MAX_VALUE
        val t = (((point.x - ax) * dx + (point.y - ay) * dy) / lengthSq).coerceIn(0f, 1f)
        val px = ax + t * dx
        val py = ay + t * dy
        return kotlin.math.hypot(point.x - px, point.y - py)
    }
}
