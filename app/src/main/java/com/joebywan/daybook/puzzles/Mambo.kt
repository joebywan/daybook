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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import kotlinx.coroutines.delay
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
     * the owner to guess, and testing against propagation is what rules that out.
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

    /**
     * How long the board must sit untouched before a rule break is shown.
     *
     * The tap cycle runs empty -> moon -> sun, so *every* sun is a moon for as long as the
     * player's finger takes to come back. That in-between moon can duplicate its neighbour or tip
     * a line past half, and flagging it accuses the player of a board they were passing through
     * rather than aiming at. Waiting for the hand to stop lets the transient go unremarked.
     */
    private const val SETTLE_MILLIS = 1000L

    /**
     * Lines permanently reserved for the caption under the board.
     *
     * The slot is held open whether or not anything is broken, because the caption used to sit in
     * the layout flow: appearing and vanishing shunted the whole grid vertically, under a finger
     * already on its way down. Two lines is the whole budget — all three rules can break at once,
     * so [captionFor]'s wordings are cut to fit that worst case rather than the slot being grown
     * to fit them, which would leave a blank third line under every board for the rest of the game.
     */
    const val CAPTION_LINES = 2

    /**
     * Characters a caption line is assumed to hold, used only to keep [captionFor]'s wordings
     * honest in [CAPTION_LINES]. Deliberately pessimistic: sized for a 320dp-wide phone at
     * `labelLarge`, so the real wrap on any ordinary display has room to spare.
     */
    const val CAPTION_LINE_CHARS = 38

    /**
     * Names the rules behind the marks currently on the board.
     *
     * Colour alone says only "you are wrong"; the caption is what turns a red ring into the
     * specific rule it is complaining about, so it is never dropped, only bounded. Repeats
     * collapse — six cells in three bad runs are still one rule, and listing it six times would
     * blow the slot for no extra information.
     */
    fun captionFor(violations: List<Violation>): String =
        violations.map { it.rule }.distinct().joinToString(" · ") {
            when (it) {
                Broken.TRIPLE -> "Three alike in a line"
                Broken.BALANCE -> "A line is unbalanced"
                Broken.LINK -> "A link is broken"
            }
        }

    // ---- home-grid motif ----------------------------------------------------------------------

    /**
     * A hand-picked 3x3 corner of a board. Written out rather than generated because the tile has
     * to look the same on every device and every day; a motif that changed would make the home
     * screen look unstable for no gain.
     *
     * Chosen so both symbols appear in both rows and columns — a corner that happened to be all
     * suns would read as a colour swatch rather than as a two-symbol puzzle.
     */
    private val PREVIEW_CELLS = listOf(
        Sym.SUN, Sym.SUN, Sym.MOON,
        Sym.MOON, Sym.NONE, Sym.SUN,
        Sym.NONE, Sym.MOON, Sym.SUN,
    )

    /** Which motif cells are clues; the rest take the softer mark a filled-in cell gets. */
    private val PREVIEW_GIVENS = listOf(
        true, true, false,
        true, false, false,
        false, false, true,
    )

    /**
     * The badge is drawn larger than [Board] would draw it, relative to the cell.
     *
     * On a full board the badge only has to be found once the player is already reading that
     * seam; on an 80dp tile it is one of three things distinguishing Mambo from any other
     * two-colour grid, and at the board's own 0.32 it renders as an unreadable dot.
     */
    private const val PREVIEW_BADGE = 0.44f

    /**
     * Two symbols in a grid plus one link badge. The badge is the whole point: without it this is
     * any checkerboard, and with it the tile states the one rule that is Mambo's own.
     */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        BoxWithConstraints(modifier) {
            // Sized from both constraints, like Mosaic's board: a tile that is ever handed a
            // shorter box than it is wide should shrink rather than draw its bottom row outside.
            val cell = minOf(maxWidth, maxHeight) / 3
            for (r in 0 until 3) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    MamboCell(
                        sym = PREVIEW_CELLS[i],
                        given = PREVIEW_GIVENS[i],
                        ringed = false,
                        modifier = Modifier
                            .padding(start = cell * c, top = cell * r)
                            .size(cell)
                            .padding(cell * 0.06f),
                    )
                }
            }
            // Straddling the seam between the two suns on the top row, which is the pair it is
            // claiming must match.
            val badge = cell * PREVIEW_BADGE
            Box(
                Modifier
                    .padding(start = cell - badge / 2, top = cell / 2 - badge / 2)
                    .size(badge)
                    .clip(CircleShape)
                    .background(scheme.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "=",
                    color = scheme.background,
                    fontWeight = FontWeight.Black,
                    fontSize = (cell.value * 0.28f).sp,
                )
            }
        }
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as MamboState
        val scheme = MaterialTheme.colorScheme

        val live = remember(s) { s.violations() }
        // What the board is allowed to show, which lags [live] by the settle. Held here and not in
        // MamboState on purpose: the play screen pushes an undo entry for every state it is
        // handed, so a timer living in the state would make "a second passed" a step to undo.
        var shown by remember(s.givens, s.links) { mutableStateOf(emptyList<Violation>()) }

        LaunchedEffect(s) {
            // A break the player has just repaired goes at once — the delay exists to avoid crying
            // wolf, not to leave a stale accusation standing over a board that is now legal.
            shown = shown.filter { it in live }
            if (shown == live) return@LaunchedEffect
            // Anything newly broken waits. The next tap hands in a new state, which cancels this
            // effect mid-delay and starts the wait over, so the clock measures the pause after the
            // last tap rather than the time since the first.
            delay(SETTLE_MILLIS)
            shown = live
        }

        // Each rule gets its own treatment so the board says *which* rule broke, not merely that
        // something is wrong: a ring on the offending run, a halo down the unbalanced line, and
        // the link badge itself turning red.
        val ringed = shown.filter { it.rule == Broken.TRIPLE }.flatMap { it.cells }.toSet()
        val haloed = shown.filter { it.rule == Broken.BALANCE }.flatMap { it.cells }.toSet()
        val brokenLinks = shown
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

            // Always composed, never wrapped in an `if`: with minLines equal to maxLines the Text
            // measures to exactly CAPTION_LINES lines of its own style no matter what it holds, so
            // an empty caption occupies the same height as a full one and the grid above it cannot
            // move. Ellipsis is the backstop for a display narrower, or a font scale larger, than
            // CAPTION_LINE_CHARS assumes — losing the tail of the sentence still beats a board
            // that jumps.
            Text(
                text = captionFor(shown),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.error,
                minLines = CAPTION_LINES,
                maxLines = CAPTION_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            )
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
