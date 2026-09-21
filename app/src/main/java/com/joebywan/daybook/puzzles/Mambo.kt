package com.joebywan.daybook.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/** Empty, or one of the two symbols. */
@Serializable
enum class Sym { NONE, SUN, MOON;
    fun other(): Sym = when (this) { SUN -> MOON; MOON -> SUN; NONE -> NONE }
}

/** A constraint printed between two orthogonally adjacent cells. */
@Serializable
data class Link(val a: Int, val b: Int, val same: Boolean)

/** Which of Mambo's three rules a group of cells is visibly breaking. */
enum class Broken { TRIPLE, BALANCE, LINK }

/** One rule break the player can see on the board, and the cells that show it. */
data class Violation(val rule: Broken, val cells: List<Int>)

@Serializable
data class MamboState(
    val size: Int,
    val givens: List<Boolean>,
    val cells: List<Sym>,
    val links: List<Link>,
    val solution: List<Sym>,
    override val moves: Int = 0,
) : PuzzleState {
    override val solved: Boolean get() = cells == solution

    /**
     * Every rule the board breaks on its own terms — [solution] is deliberately not consulted.
     *
     * Flagging cells that merely differ from the stored answer marks a player wrong the instant
     * they deviate, using a deduction they have not made themselves. A wrong board is not yet an
     * illegal one: the player is entitled to follow a mistaken line until it collides with a rule
     * they can check by eye, which is the only thing reported here.
     */
    fun violations(): List<Violation> {
        val out = mutableListOf<Violation>()
        val half = size / 2

        // Three in a line, scanning each run of three once along both axes.
        for (r in 0 until size) for (c in 0 until size) {
            val i = r * size + c
            val sym = cells[i]
            if (sym == Sym.NONE) continue
            if (c + 2 < size && cells[i + 1] == sym && cells[i + 2] == sym) {
                out += Violation(Broken.TRIPLE, listOf(i, i + 1, i + 2))
            }
            if (r + 2 < size && cells[i + size] == sym && cells[i + 2 * size] == sym) {
                out += Violation(Broken.TRIPLE, listOf(i, i + size, i + 2 * size))
            }
        }

        // A line already holding more than half of one symbol can never balance.
        for (line in 0 until size) {
            for (idx in listOf(rowOf(line), columnOf(line))) {
                if (listOf(Sym.SUN, Sym.MOON).any { sym -> idx.count { cells[it] == sym } > half }) {
                    out += Violation(Broken.BALANCE, idx)
                }
            }
        }

        // A printed link both of whose ends are filled in must hold.
        for (link in links) {
            val a = cells[link.a]
            val b = cells[link.b]
            if (a == Sym.NONE || b == Sym.NONE) continue
            if ((a == b) != link.same) out += Violation(Broken.LINK, listOf(link.a, link.b))
        }

        return out
    }

    private fun rowOf(r: Int): List<Int> = (0 until size).map { r * size + it }

    private fun columnOf(c: Int): List<Int> = (0 until size).map { it * size + c }

    fun withCell(index: Int, value: Sym): MamboState =
        copy(cells = cells.toMutableList().also { it[index] = value }, moves = moves + 1)
}

/**
 * Mambo — a Takuzu/Binairo variant.
 *
 * Fill every cell with one of two symbols so that each row and column holds an equal number of
 * each, no three identical symbols sit consecutively, and every printed link is respected:
 * `=` joins cells that must match, `x` joins cells that must differ.
 */
object Mambo : PuzzleType {

    override val id = "mambo"
    override val displayName = "Mambo"
    override val tagline = "Balance two symbols, never three in a row"
    override val accent = 0xFF6E8FD8
    override val rules = listOf(
        "Fill every cell with a sun or a moon.",
        "Each row and column must hold the same number of each.",
        "No three identical symbols may sit next to each other in a line.",
        "Cells joined by = must match; cells joined by x must differ.",
        "Tap a cell to cycle moon, sun, empty.",
        "Every puzzle can be solved by deduction alone.",
    )

    private fun sizeFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 6
        Difficulty.HARD -> 8
        Difficulty.EXPERT -> 10
    }

    /** Carving leaves exactly one seed cell; a second is there so the opening looks deliberate. */
    private const val MIN_GIVENS = 2

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val rng = Rng(seed)
        val n = sizeFor(difficulty)
        val solution = fullGrid(rng, n) ?: fullGrid(Rng(seed + 1), n)!!
        val (givens, links) = carve(rng, n, solution)
        val cells = solution.indices.map { if (givens[it]) solution[it] else Sym.NONE }
        return MamboState(n, givens, cells, links, solution)
    }

    // ---- generation ---------------------------------------------------------------------------

    /** Randomised backtracking search for a complete legal grid. */
    private fun fullGrid(rng: Rng, n: Int): List<Sym>? {
        val grid = MutableList(n * n) { Sym.NONE }
        fun place(index: Int): Boolean {
            if (index == n * n) return true
            for (sym in rng.shuffled(listOf(Sym.SUN, Sym.MOON))) {
                grid[index] = sym
                if (legalSoFar(grid, n, index) && place(index + 1)) return true
                grid[index] = Sym.NONE
            }
            return false
        }
        return if (place(0)) grid else null
    }

    /** Checks only the constraints that the cell just written could have broken. */
    private fun legalSoFar(grid: List<Sym>, n: Int, index: Int): Boolean {
        val r = index / n
        val c = index % n
        val sym = grid[index]

        // No three consecutive, looking backwards along both axes.
        if (c >= 2 && grid[index - 1] == sym && grid[index - 2] == sym) return false
        if (r >= 2 && grid[index - n] == sym && grid[index - 2 * n] == sym) return false

        // Never exceed half a line of either symbol.
        val half = n / 2
        var rowCount = 0
        for (i in 0 until n) if (grid[r * n + i] == sym) rowCount++
        if (rowCount > half) return false
        var colCount = 0
        for (i in 0 until n) if (grid[i * n + c] == sym) colCount++
        if (colCount > half) return false

        return true
    }

    /**
     * Reduces a full grid to a puzzle by stripping clues the rest of the board still implies.
     *
     * Every candidate removal is tested with [solvableByLogic] rather than a solution count: a
     * board can have exactly one answer and still offer no legal next move, which is what forced
     * the owner to guess. Testing against propagation is the same bar Mosaic holds itself to.
     *
     * Cell clues are offered for removal before links so the links — the part of the board that
     * makes it a Mambo rather than a plain Takuzu — survive to carry the deduction.
     */
    private fun carve(rng: Rng, n: Int, solution: List<Sym>): Pair<List<Boolean>, List<Link>> {
        val givens = MutableList(n * n) { true }
        val allLinks = buildList {
            for (r in 0 until n) for (c in 0 until n) {
                val i = r * n + c
                if (c + 1 < n) add(Link(i, i + 1, solution[i] == solution[i + 1]))
                if (r + 1 < n) add(Link(i, i + n, solution[i] == solution[i + n]))
            }
        }
        val kept = allLinks.toMutableList()

        for (i in rng.shuffled((0 until n * n).toList())) {
            givens[i] = false
            if (!solvableByLogic(n, givens, kept, solution)) givens[i] = true
        }
        for (link in rng.shuffled(allLinks)) {
            kept.remove(link)
            if (!solvableByLogic(n, givens, kept, solution)) kept += link
        }

        // Propagation needs a symbol to start from, so a board can never come out blank — but in
        // practice it needs exactly one, and a single lone square reads as an accident rather than
        // an anchor. Extra clues only ever add deductions, so topping up keeps the board solvable.
        var short = MIN_GIVENS - givens.count { it }
        for (i in rng.shuffled((0 until n * n).toList())) {
            if (short <= 0) break
            if (givens[i]) continue
            givens[i] = true
            short--
        }

        return givens.toList() to kept.toList()
    }

    // ---- solver -------------------------------------------------------------------------------

    /**
     * True when the clues drive the board to a full grid by propagation alone.
     *
     * A unique solution is not the same as a solvable one. `countSolutions` could only promise
     * that exactly one answer existed, not that a player could ever reach it: boards passed that
     * test while offering no first move at all. Propagation makes only forced deductions from the
     * clues, so a grid it completes is both unique *and* reachable without a single guess — and no
     * search is needed to know it.
     */
    private fun solvableByLogic(
        n: Int,
        givens: List<Boolean>,
        links: List<Link>,
        solution: List<Sym>,
    ): Boolean {
        val grid = Array(n * n) { if (givens[it]) solution[it] else Sym.NONE }
        return propagate(n, grid, links) && grid.none { it == Sym.NONE }
    }

    /**
     * Applies Mambo's three rules until nothing more can be deduced.
     *
     * Returns false if the clues contradict — which doubles as the three-in-a-line and
     * over-filled-line checks, since both surface as an attempt to write two symbols into one
     * cell.
     */
    private fun propagate(n: Int, grid: Array<Sym>, links: List<Link>): Boolean {
        val half = n / 2
        var ok = true
        var changed = true

        fun write(i: Int, sym: Sym) {
            if (grid[i] == Sym.NONE) {
                grid[i] = sym
                changed = true
            } else if (grid[i] != sym) {
                ok = false
            }
        }

        // Two of a kind force the opposite at either end, and across a gap between them.
        fun trio(a: Int, b: Int, c: Int) {
            val x = grid[a]
            val y = grid[b]
            val z = grid[c]
            if (x != Sym.NONE && x == y) write(c, x.other())
            if (y != Sym.NONE && y == z) write(a, y.other())
            if (x != Sym.NONE && x == z) write(b, x.other())
        }

        // Once a line holds half its cells of one symbol, the rest must be the other.
        fun balance(idx: IntArray) {
            var sun = 0
            var moon = 0
            for (i in idx) when (grid[i]) {
                Sym.SUN -> sun++
                Sym.MOON -> moon++
                Sym.NONE -> Unit
            }
            if (sun > half || moon > half) {
                ok = false
                return
            }
            if (sun == half) for (i in idx) if (grid[i] == Sym.NONE) write(i, Sym.MOON)
            if (moon == half) for (i in idx) if (grid[i] == Sym.NONE) write(i, Sym.SUN)
        }

        val lines = buildList {
            for (line in 0 until n) {
                add(IntArray(n) { line * n + it })
                add(IntArray(n) { it * n + line })
            }
        }

        while (changed && ok) {
            changed = false

            for (r in 0 until n) for (c in 0 until n) {
                val i = r * n + c
                if (c + 2 < n) trio(i, i + 1, i + 2)
                if (r + 2 < n) trio(i, i + n, i + 2 * n)
            }

            for (idx in lines) balance(idx)

            // `=` carries a known symbol across, `x` carries its opposite.
            for (link in links) {
                val a = grid[link.a]
                val b = grid[link.b]
                if (a != Sym.NONE && b == Sym.NONE) {
                    write(link.b, if (link.same) a else a.other())
                } else if (b != Sym.NONE && a == Sym.NONE) {
                    write(link.a, if (link.same) b else b.other())
                } else if (a != Sym.NONE && b != Sym.NONE && (a == b) != link.same) {
                    ok = false
                }
            }
        }

        return ok
    }

    // ---- play ---------------------------------------------------------------------------------

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as MamboState
        // A hint is asked for, so it may read the answer the live feedback must not.
        val wrong = s.cells.indices.firstOrNull {
            s.cells[it] != Sym.NONE && s.cells[it] != s.solution[it]
        }
        if (wrong != null) return s.withCell(wrong, s.solution[wrong])
        val blank = s.cells.indices.firstOrNull { s.cells[it] == Sym.NONE } ?: return null
        return s.withCell(blank, s.solution[blank])
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as MamboState
        val scheme = MaterialTheme.colorScheme
        val violations = s.violations()
        // Each rule gets its own treatment so the board says *which* rule broke, not merely that
        // something is wrong: a ring on the offending run, a halo down the unbalanced line, and
        // the link badge itself turning red.
        val ringed = violations.filter { it.rule == Broken.TRIPLE }.flatMap { it.cells }.toSet()
        val haloed = violations.filter { it.rule == Broken.BALANCE }.flatMap { it.cells }.toSet()
        val brokenLinks = violations
            .filter { it.rule == Broken.LINK }
            .map { it.cells[0] to it.cells[1] }
            .toSet()

        Column(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
                val board = maxWidth
                val cell = board / s.size
                val cellPx = with(LocalDensity.current) { cell.toPx() }
                Box(
                    Modifier
                        .size(board)
                        .pointerInput(s, interactive) {
                            if (!interactive) return@pointerInput
                            detectTapGestures { offset: Offset ->
                                val c = (offset.x / cellPx).toInt().coerceIn(0, s.size - 1)
                                val r = (offset.y / cellPx).toInt().coerceIn(0, s.size - 1)
                                val i = r * s.size + c
                                if (!s.givens[i]) {
                                    val next = when (s.cells[i]) {
                                        Sym.NONE -> Sym.MOON
                                        Sym.MOON -> Sym.SUN
                                        Sym.SUN -> Sym.NONE
                                    }
                                    onState(s.withCell(i, next))
                                }
                            }
                        }
                ) {
                    // Drawn first so the cells sit on top and leave the halo showing as a frame.
                    haloed.forEach { i ->
                        Box(
                            Modifier
                                .padding(start = cell * (i % s.size), top = cell * (i / s.size))
                                .size(cell)
                                .background(scheme.error.copy(alpha = 0.22f))
                        )
                    }
                    for (r in 0 until s.size) {
                        for (c in 0 until s.size) {
                            val i = r * s.size + c
                            MamboCell(
                                sym = s.cells[i],
                                given = s.givens[i],
                                ringed = i in ringed,
                                modifier = Modifier
                                    .padding(start = cell * c, top = cell * r)
                                    .size(cell)
                                    .padding(cell * 0.06f),
                            )
                        }
                    }
                    s.links.forEach { link ->
                        val horizontal = link.b == link.a + 1
                        val r = link.a / s.size
                        val c = link.a % s.size
                        val x = if (horizontal) cell * (c + 1) else cell * c + cell / 2
                        val y = if (horizontal) cell * r + cell / 2 else cell * (r + 1)
                        val broken = (link.a to link.b) in brokenLinks
                        Box(
                            Modifier
                                .padding(start = x - cell * 0.16f, top = y - cell * 0.16f)
                                .size(cell * 0.32f)
                                .clip(CircleShape)
                                .background(if (broken) scheme.error else scheme.onBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (link.same) "=" else "x",
                                color = if (broken) scheme.onError else scheme.background,
                                fontWeight = FontWeight.Black,
                                fontSize = (cell.value * 0.20f).sp,
                            )
                        }
                    }
                }
            }

            // Names the rule as well as showing it, so a red mark is never just "you are wrong".
            val notes = violations.map { it.rule }.distinct().map {
                when (it) {
                    Broken.TRIPLE -> "Three identical symbols in a line"
                    Broken.BALANCE -> "A line holds too many of one symbol"
                    Broken.LINK -> "A linked pair breaks its = or x"
                }
            }
            if (notes.isNotEmpty()) {
                Text(
                    notes.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                )
            }
        }
    }

    @Composable
    private fun MamboCell(sym: Sym, given: Boolean, ringed: Boolean, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(22)
        val fill = when (sym) {
            Sym.SUN -> Color(accent)
            Sym.MOON -> Color(0xFF8FC79A)
            Sym.NONE -> scheme.surfaceVariant
        }
        Box(
            modifier
                .clip(shape)
                .background(fill)
                .then(if (ringed) Modifier.border(2.dp, scheme.error, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (sym != Sym.NONE) {
                val mark = scheme.background.copy(alpha = if (given) 1f else 0.72f)
                Box(
                    Modifier
                        .fillMaxWidth(0.42f)
                        .aspectRatio(1f)
                        .clip(if (sym == Sym.SUN) CircleShape else RoundedCornerShape(28))
                        .background(mark)
                )
            }
        }
    }
}
