package com.joebywan.daybook.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.joebywan.daybook.core.PuzzleState
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng

/** Empty, or one of the two symbols. */
enum class Sym { NONE, SUN, MOON;
    fun other(): Sym = when (this) { SUN -> MOON; MOON -> SUN; NONE -> NONE }
}

/** A constraint printed between two orthogonally adjacent cells. */
data class Link(val a: Int, val b: Int, val same: Boolean)

data class MamboState(
    val size: Int,
    val givens: List<Boolean>,
    val cells: List<Sym>,
    val links: List<Link>,
    val solution: List<Sym>,
    override val moves: Int = 0,
) : PuzzleState {
    override val solved: Boolean get() = cells == solution

    /** Cells the player has filled that contradict the solution — drawn in red. */
    fun conflicts(): Set<Int> = cells.indices.filter { i ->
        cells[i] != Sym.NONE && cells[i] != solution[i]
    }.toSet()

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
        "Tap a cell to cycle sun, moon, empty.",
    )

    private fun sizeFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 6
        Difficulty.HARD -> 8
        Difficulty.EXPERT -> 10
    }

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
     * Reduces a full grid to a puzzle: offer every possible clue, keep shuffling them in until the
     * solver reports a unique solution, then strip any clue the puzzle no longer needs.
     */
    private fun carve(rng: Rng, n: Int, solution: List<Sym>): Pair<List<Boolean>, List<Link>> {
        val cellClues = (0 until n * n).map { Clue.Cell(it) }
        val linkClues = buildList {
            for (r in 0 until n) for (c in 0 until n) {
                val i = r * n + c
                if (c + 1 < n) add(Clue.Join(Link(i, i + 1, solution[i] == solution[i + 1])))
                if (r + 1 < n) add(Clue.Join(Link(i, i + n, solution[i] == solution[i + n])))
            }
        }
        // Links carry more deductive weight than givens, so offer them first for a prettier board.
        val pool = rng.shuffled(linkClues) + rng.shuffled(cellClues)

        val chosen = mutableListOf<Clue>()
        for (clue in pool) {
            chosen += clue
            if (uniquelySolvable(n, chosen, solution)) break
        }
        // Minimise: drop anything the remaining clues already imply.
        for (clue in rng.shuffled(chosen.toList())) {
            val trimmed = chosen.filterNot { it === clue }
            if (uniquelySolvable(n, trimmed, solution)) {
                chosen.clear()
                chosen += trimmed
            }
        }

        val givens = MutableList(n * n) { false }
        val links = mutableListOf<Link>()
        chosen.forEach { clue ->
            when (clue) {
                is Clue.Cell -> givens[clue.index] = true
                is Clue.Join -> links += clue.link
            }
        }
        return givens to links
    }

    private sealed interface Clue {
        data class Cell(val index: Int) : Clue
        data class Join(val link: Link) : Clue
    }

    /** True when [clues] admit exactly one completion. */
    private fun uniquelySolvable(n: Int, clues: List<Clue>, solution: List<Sym>): Boolean {
        val grid = MutableList(n * n) { Sym.NONE }
        val links = mutableListOf<Link>()
        clues.forEach { clue ->
            when (clue) {
                is Clue.Cell -> grid[clue.index] = solution[clue.index]
                is Clue.Join -> links += clue.link
            }
        }
        return countSolutions(grid, n, links, 0, 0) == 1
    }

    /** Counts completions, abandoning the search as soon as a second one turns up. */
    private fun countSolutions(
        grid: MutableList<Sym>,
        n: Int,
        links: List<Link>,
        index: Int,
        found: Int,
    ): Int {
        if (found >= 2) return found
        if (index == n * n) return found + 1
        if (grid[index] != Sym.NONE) {
            return if (violates(grid, n, links, index)) found
            else countSolutions(grid, n, links, index + 1, found)
        }
        var total = found
        for (sym in listOf(Sym.SUN, Sym.MOON)) {
            grid[index] = sym
            if (!violates(grid, n, links, index)) {
                total = countSolutions(grid, n, links, index + 1, total)
            }
            grid[index] = Sym.NONE
            if (total >= 2) break
        }
        return total
    }

    private fun violates(grid: List<Sym>, n: Int, links: List<Link>, index: Int): Boolean {
        if (!legalSoFar(grid, n, index)) return true
        // Any link whose far end is already decided must hold now.
        for (link in links) {
            if (link.a != index && link.b != index) continue
            val a = grid[link.a]
            val b = grid[link.b]
            if (a == Sym.NONE || b == Sym.NONE) continue
            if ((a == b) != link.same) return true
        }
        return false
    }

    // ---- play ---------------------------------------------------------------------------------

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as MamboState
        // Clear a wrong cell first; otherwise reveal an empty one.
        val wrong = s.conflicts().minOrNull()
        if (wrong != null) return s.withCell(wrong, s.solution[wrong])
        val blank = s.cells.indices.firstOrNull { s.cells[it] == Sym.NONE } ?: return null
        return s.withCell(blank, s.solution[blank])
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as MamboState
        val conflicts = s.conflicts()
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
                                    Sym.NONE -> Sym.SUN
                                    Sym.SUN -> Sym.MOON
                                    Sym.MOON -> Sym.NONE
                                }
                                onState(s.withCell(i, next))
                            }
                        }
                    }
            ) {
                for (r in 0 until s.size) {
                    for (c in 0 until s.size) {
                        val i = r * s.size + c
                        MamboCell(
                            sym = s.cells[i],
                            given = s.givens[i],
                            conflicted = i in conflicts,
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
                    Box(
                        Modifier
                            .padding(start = x - cell * 0.16f, top = y - cell * 0.16f)
                            .size(cell * 0.32f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (link.same) "=" else "x",
                            color = MaterialTheme.colorScheme.background,
                            fontWeight = FontWeight.Black,
                            fontSize = (cell.value * 0.20f).sp,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MamboCell(sym: Sym, given: Boolean, conflicted: Boolean, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val fill = when {
            conflicted -> scheme.error
            sym == Sym.SUN -> Color(accent)
            sym == Sym.MOON -> Color(0xFF8FC79A)
            else -> scheme.surfaceVariant
        }
        Box(
            modifier.clip(RoundedCornerShape(22)).background(fill),
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
