package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.digitKeyAction
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.PAD_GAP
import com.joebywan.daybook.ui.padBoardSide
import com.joebywan.daybook.ui.PadKey
import com.joebywan.daybook.ui.dimmed
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.ring
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.ArcadeFill
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.arcadeGlow
import com.joebywan.daybook.ui.theme.arcadeLift
import com.joebywan.daybook.ui.theme.classicLook
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.style


/**
 * The Sudoku grid and its digit pad, shared by Sudoku, Cage Sudoku and Reckon: all draw exactly this, and differ in
 * what a tap, a key and a digit do to their own state (the callbacks), which squares clash ([conflicts]),
 * and what is drawn over the grid ([overlay]: Cage Sudoku's dashed cages, Reckon's walls and labels). [topBand] is the
 * share of a square's height that a label of the overlay takes from the top, which the pencil marks leave clear.
 * The grid is [n] squares a side with digits 1..n; only a 9x9 has 3x3 boxes (their shade and frame lines).
 */

/** Squares holding the selected square's digit: apart from the entered blue-violet, the selected amber and the error red. */
internal val SudokuTwin = ArcadeFill(Color(0xFF5CF2C8), Color(0xFF14D3A6), Color(0xFF06281F))

/** The hint's ring in light, seen on both a white square and the indigo frame (amber misses on white, the frame's ink on the frame). */
internal val SudokuHintLight = Color(0xFFE6007E)

/** How strongly a note is inked in the Classic look (its size is `BoardGlyph.NOTE`). Tuned by rendering, before Arcade. */
private const val SUDOKU_NOTE_ALPHA_CLASSIC = 0.85f

/** Every other box's empty squares. */
internal fun ArcadeBoard.boxShade() = lerp(square, given, 0.35f)

/** The frame showing on one side of a square (the other half is its neighbour's): the board's edge and a wall are the heavy ones. */
private fun gap(edge: Boolean, wall: Boolean) = when {
    edge -> 2.dp
    wall -> 1.5.dp
    else -> 0.5.dp
}

/** Whether neighbouring squares [a] and [b] of a 9x9 lie in different 3x3 boxes. */
private fun boxWall(a: Int, b: Int) = a % 9 / 3 != b % 9 / 3 || a / 27 != b / 27

/** Sudoku's units for the move feel: the rows, the columns and the boxes, each in reading order. */
internal val SUDOKU_UNITS: List<IntArray> = List(27) { u ->
    IntArray(9) { k ->
        when (u / 9) {
            0 -> u * 9 + k
            1 -> k * 9 + u % 9
            else -> (u % 9 / 3 * 3 + k / 3) * 9 + u % 3 * 3 + k % 3
        }
    }
}

/** Whether [unit] holds 1 to 9 once each: finished by the rules, whatever the stored answer says. */
internal fun List<Int>.holdsOneToNine(unit: IntArray): Boolean = unit.fold(0) { m, i -> m or (1 shl this[i]) } == 0x3FE

/** The squares holding [digit], in reading order: a unit of the move feel, so a digit-first player hears each digit finished. */
internal fun List<Int>.squaresOf(digit: Int): IntArray = indices.filter { this[it] == digit }.toIntArray()

/** Whether [digit] is on an [n]-wide board n times, never twice in a row, column (or, on a 9x9, box): finished by the rules, as a unit is. */
internal fun List<Int>.allPlaced(digit: Int, n: Int): Boolean {
    val at = squaresOf(digit)
    return at.size == n && at.map { it / n }.toSet().size == n && at.map { it % n }.toSet().size == n &&
        (n != 9 || at.map { it / 27 * 3 + it % 9 / 3 }.toSet().size == 9)
}

/** Whether [digit] is on a 9x9 board nine times, never twice in a row, column or box. */
internal fun List<Int>.allNine(digit: Int): Boolean = allPlaced(digit, 9)

@Composable
internal fun SudokuGridBoard(
    s: SudokuState,
    conflicts: Set<Int>,
    interactive: Boolean,
    /** The puzzle's accent: the Classic look's move feel. */
    accent: Long,
    onSelect: (Int) -> Unit,
    onDeselect: () -> Unit,
    onEnter: (cell: Int, digit: Int, notesMode: Boolean) -> Unit,
    onKey: (DigitKeyAction, notesMode: Boolean) -> Unit,
    topBand: (Int) -> Float = { 0f },
    units: List<IntArray> = SUDOKU_UNITS,
    complete: (Int) -> Boolean = { s.cells.holdsOneToNine(SUDOKU_UNITS[it]) },
    overlay: @Composable BoxScope.(cell: Dp) -> Unit = {},
    n: Int = 9,
    /** Whether neighbouring squares a and b are parted by a wall, which Arcade's frame shows wider: a box's edge here, a cage's in Reckon. */
    wall: (Int, Int) -> Boolean = ::boxWall,
    /** Highlight indices of a digit's key and of the pencil. */
    padKey: (Int) -> Int = SudokuTeacher::pad,
    notesKey: Int = SudokuTeacher.NOTES_KEY,
) {
    // Sudoku's 3x3 boxes (their shade and frame lines); a smaller grid has none.
    val boxed = n == 9
    // The board's own units, then each digit's n squares (where they stand now; it glows once all are in).
    val feel = rememberMoveFeel(s.cells, units + List(n) { s.cells.squaresOf(it + 1) }) { u ->
        if (u < units.size) complete(u) else s.cells.allPlaced(u - units.size + 1, n)
    }
    val a = ArcadeBoard.current
    val scheme = MaterialTheme.colorScheme
    val dark = BoardHues.isDark(scheme)
    val boxShade = a.boxShade()
    val keyShape = RoundedCornerShape(10.dp)
    // The Classic look (issue #418) draws what Sudoku drew before Arcade: hues derived per render from the solution, never
    // stored (docs/COLOUR.md "Roles"), A the structure (box tint, selection, pencil), B the player's digits and their twins.
    val classic = classicLook()
    val (hueA, hueB) = BoardHues.pair(s.solution.hashCode())
    val boxTint = BoardHues.fill(hueA, dark).copy(alpha = if (dark) 0.55f else 0.30f).compositeOver(scheme.surface)
    val classicTwin = BoardHues.fill(hueB, dark).copy(alpha = 0.8f).compositeOver(scheme.surface)
    val (highlight, glow, pulse) = if (classic) rememberHintPaint() else rememberHintPaint(if (dark) a.selected.from else SudokuHintLight)
    // Which way the digit keys act. Transient UI state, so it lives here and not in the state:
    // PlayScreen would otherwise make every flip of it an undo step.
    var notesMode by rememberSaveable { mutableStateOf(false) }
    // The digit picked first (issue #384): with no square selected, a key arms and each tap on a square enters it. Not a state, as notesMode.
    var armed by rememberSaveable { mutableStateOf<Int?>(null) }
    val armedDigit = armed.takeIf { s.selected == null }
    // The last digit noted and its square (issue #407), so a note press highlights as a filled square does. Not a state, as notesMode.
    var notedDigit by rememberSaveable { mutableStateOf(0) }
    var notedAt by rememberSaveable { mutableStateOf<Int?>(null) }
    // Forgotten as soon as the selection moves, so coming back to the square does not bring it back.
    if (notedAt != s.selected) notedAt = null
    val notePress = { digit: Int ->
        notedAt = s.selected?.takeIf { notesMode && s.canNote(it) }
        notedDigit = digit
    }
    val selectedValue = highlightedDigit(s.cells, s.selected, armedDigit, notesMode, notedAt, notedDigit)
    val measurer = rememberTextMeasurer()
    // The keyboard (web, or a hardware keyboard on Android): digits, clear and arrows, mapped by
    // digitKeyAction and applied through the same state functions the pad and a tap use. See boardKeys.

    // Sized from both axes (CLAUDE.md): from width alone, a 693dp-tall screen ran the grid up
    // over the header and down under the hint slot. The pad keeps the full width.
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .boardKeys(interactive) { key, _, repeat ->
                val action = digitKeyAction(key, n) ?: return@boardKeys false
                if (!repeat || action is DigitKeyAction.Move) {
                    if (action is DigitKeyAction.Digit) notePress(action.digit)
                    if (action is DigitKeyAction.Clear) notedAt = null
                    onKey(action, notesMode)
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        val side = padBoardSide()
        val cell = side / n
        val padWidth = maxWidth
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(side).highlightGrid(n, n).background(if (classic) Color.Transparent else a.frame, RoundedCornerShape(8.dp)).drawBehind {
                    if (classic) return@drawBehind
                    // The frame again over a box's edge: dark's is translucent, and one layer of it hardly parts the boxes.
                    if (boxed) for (k in 1..2) {
                        val at = size.width * k / 3f
                        drawLine(a.frame, Offset(at, 0f), Offset(at, size.height), 3.dp.toPx())
                        drawLine(a.frame, Offset(0f, at), Offset(size.width, at), 3.dp.toPx())
                    } else {
                        // The same over each wall, a side at a time (a cage's walls bend).
                        val step = size.width / n
                        for (i in 0 until n * n) {
                            val x = i % n * step
                            val y = i / n * step
                            if (i % n < n - 1 && wall(i, i + 1)) drawLine(a.frame, Offset(x + step, y), Offset(x + step, y + step), 3.dp.toPx())
                            if (i / n < n - 1 && wall(i, i + n)) drawLine(a.frame, Offset(x, y + step), Offset(x + step, y + step), 3.dp.toPx())
                        }
                    }
                },
            ) {
                for (r in 0 until n) {
                    for (c in 0 until n) {
                        val i = r * n + c
                        val isSelected = s.selected == i
                        val sameValue = selectedValue != 0 && s.cells[i] == selectedValue
                        val look = highlight.look(i)
                        val wrong = i in conflicts
                        // A state by colour also changes shape or weight: wrong is underlined and heavy, selected and twins have an edge.
                        val fill = if (classic) {
                            // Classic: no fill for a wrong square (its digit turns the error colour), no frame, no edge.
                            val ground = when {
                                isSelected -> BoardHues.mark(hueA).copy(alpha = if (dark) 0.55f else 0.40f).compositeOver(scheme.surface)
                                sameValue -> classicTwin
                                boxed && (r / 3 + c / 3) % 2 == 0 -> boxTint
                                else -> scheme.surface
                            }
                            val ink = when {
                                wrong -> scheme.error
                                s.givens[i] -> scheme.onSurface
                                else -> BoardHues.ink(hueB, dark)
                            }
                            ArcadeFill(ground, ground, ink)
                        } else when {
                            wrong -> ArcadeFill(a.error, a.error, a.errorInk)
                            isSelected -> a.selected
                            sameValue -> SudokuTwin
                            s.cells[i] != 0 && !s.givens[i] -> a.control
                            s.givens[i] -> ArcadeFill(a.given, a.given, a.givenInk)
                            boxed && (r / 3 + c / 3) % 2 == 0 -> ArcadeFill(boxShade, boxShade, a.squareInk)
                            else -> ArcadeFill(a.square, a.square, a.squareInk)
                        }
                        Box(
                            Modifier
                                // Above its neighbours while a hint names it, which would otherwise cover the ring.
                                .then(if (look.strong || look.soft) Modifier.zIndex(1f) else Modifier)
                                .padding(start = cell * c, top = cell * r)
                                .size(cell)
                                .moveFeel(feel, i, if (classic) Color(accent) else a.selected.from)
                                .then(
                                    if (classic) Modifier.padding(1.dp)
                                    else Modifier.padding(
                                        start = gap(c == 0, c > 0 && wall(i - 1, i)), end = gap(c == n - 1, c < n - 1 && wall(i, i + 1)),
                                        top = gap(r == 0, r > 0 && wall(i - n, i)), bottom = gap(r == n - 1, r < n - 1 && wall(i, i + n)),
                                    )
                                )
                                .then(a.selected.glow?.takeIf { isSelected }?.let { Modifier.arcadeGlow(it, 4.dp, 1.5.dp) } ?: Modifier)
                                // Under the dimming, so a dimmed square fades to itself and not to the frame.
                                .then(if (classic) Modifier else Modifier.background(a.square, RoundedCornerShape(4.dp)))
                                .dimmed(look)
                                .ring(look, glow, pulse, corner = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                // The selected cell's row, column and box are deliberately
                                // left alone. Shading them restyled twenty of the eighty-one
                                // squares on every tap, so the board had to be re-read after
                                // each selection to find the 3x3 structure again -- the cost
                                // outweighed the help. Only the selection itself, cells
                                // holding the same digit and notes of that digit (a disc behind
                                // the note, the square's own fill unchanged) react.
                                .background(fill.brush)
                                .then(
                                    when {
                                        classic -> Modifier
                                        isSelected -> Modifier.border(2.dp, if (wrong) a.errorInk else a.selectedEdge, RoundedCornerShape(4.dp))
                                        sameValue && !wrong -> Modifier.border(1.dp, fill.ink, RoundedCornerShape(4.dp))
                                        else -> Modifier
                                    }
                                )
                                .clickable(enabled = interactive) {
                                    when (squareTap(i, s.selected, armedDigit)) {
                                        SquareTap.ENTER -> onEnter(i, armedDigit ?: 0, notesMode)
                                        SquareTap.DESELECT -> onDeselect()
                                        SquareTap.SELECT -> onSelect(i)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val noted = s.visibleNotes(i)
                            if (noted != 0) {
                                val ink = when {
                                    classic -> scheme.onSurface.copy(alpha = SUDOKU_NOTE_ALPHA_CLASSIC)
                                    isSelected -> a.selected.ink
                                    else -> a.givenInk
                                }
                                // The disc behind a highlighted digit's note: Classic's twin square colour, under its own ink.
                                val twinFill = if (classic) ArcadeFill(classicTwin, classicTwin, scheme.onSurface) else SudokuTwin
                                // A square that carries a label (a cage's total) gives its top band to it.
                                val band = topBand(i)
                                // Three rows fit under the label and above the selected square's border; a square with no label is untouched.
                                val style = BoardGlyph.NOTE.style(cell * (1f - band * 0.75f)).copy(color = ink, textAlign = TextAlign.Center)
                                // Drawn, not composed: nine Text nodes in each of up to eighty-one
                                // cells would be a lot of layout for a board that redraws on every tap.
                                // Three to a row: three rows on a 9x9, two for six digits.
                                val rows = (n + 2) / 3
                                Canvas(Modifier.fillMaxSize()) {
                                    val sub = size.width / 3f
                                    val rowHeight = (size.height * (1f - band) - if (band > 0f) 2.dp.toPx() else 0f) / rows
                                    for (d in 1..n) {
                                        if (noted and (1 shl (d - 1)) == 0) continue
                                        val laid = measurer.measure(d.toString(), style, maxLines = 1)
                                        val r = (d - 1) / 3
                                        val c = (d - 1) % 3
                                        val twin = d == selectedValue
                                        val top = band * size.height + r * rowHeight
                                        // A disc as well as the ink: a state by colour also changes shape. Its radius keeps it inside its third.
                                        if (twin) {
                                            // Clear of the selected square's border (Arcade's only: Classic draws none), which is drawn over it; a label's rows already stop short of the bottom one.
                                            val edge = if (isSelected && !classic) 2.dp.toPx() else 0f
                                            val radius = minOf(sub / 2f - edge, rowHeight / 2f - if (band > 0f) 0f else edge)
                                            val centre = Offset(c * sub + sub / 2f, top + rowHeight / 2f)
                                            drawCircle(twinFill.brush(centre - Offset(radius, radius), Size(radius * 2f, radius * 2f)), radius, centre)
                                        }
                                        drawText(
                                            laid,
                                            color = if (twin) twinFill.ink else ink,
                                            topLeft = Offset(
                                                c * sub + (sub - laid.size.width) / 2f,
                                                top + (rowHeight - laid.size.height) / 2f,
                                            ),
                                        )
                                    }
                                }
                            }
                            if (s.cells[i] != 0) {
                                Text(
                                    text = s.cells[i].toString(),
                                    // Under a label the digit sits a little low, so a two-figure cage total clears it.
                                    modifier = Modifier.padding(top = cell * topBand(i) * 0.7f),
                                    style = (if (s.givens[i]) BoardGlyph.GIVEN else BoardGlyph.ENTERED).style(cell).let {
                                        if (wrong && !classic) it.copy(fontWeight = FontWeight.Black, textDecoration = TextDecoration.Underline) else it
                                    },
                                    color = fill.ink,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                overlay(cell)
            }

            Spacer(Modifier.height(PAD_GAP))

            Row(
                Modifier.width(padWidth).keepClear(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (1..n).forEach { digit ->
                    val remaining = n - s.cells.count { it == digit }
                    val at = s.selected
                    // In notes mode a key that is already pencilled on the selected cell is tinted.
                    val pencilled = armedDigit == digit || notesMode && at != null && s.hasNote(at, digit)
                    // Notes cannot go on a filled or given cell: the keys show it rather than do nothing.
                    val inert = notesMode && at != null && !s.canNote(at)
                    PadKey(
                        padKey(digit), highlight, glow, pulse,
                        // Classic's keys are flat: the surface, hue A's tint when pencilled, the variant once a digit is used up.
                        fill = when {
                            !classic -> Color.Transparent
                            pencilled -> BoardHues.mark(hueA).copy(alpha = 0.40f)
                            remaining == 0 -> scheme.surfaceVariant
                            else -> scheme.surface
                        },
                        enabled = interactive && !inert,
                        onClick = {
                            // In notes mode the keys are dimmed and inert on a filled or given cell
                            // (above), so a refusal here is never silent.
                            notePress(digit)
                            if (at != null) onEnter(at, digit, notesMode)
                            else armed = digit.takeIf { it != armedDigit }
                        },
                        // Through modifier, not fill: a key's fill is one colour. A used-up digit lies flat and thin, a pencilled one has an edge.
                        modifier = Modifier.alpha(if (inert) 0.35f else 1f).then(
                            when {
                                classic -> Modifier
                                pencilled -> Modifier.arcadeLift(a.selected, 10.dp).background(a.selected.brush, keyShape).border(2.dp, a.selectedEdge, keyShape)
                                remaining == 0 -> Modifier.background(a.given, keyShape)
                                // The alpha's layer clips to the key, which would square off a lift.
                                inert -> Modifier.background(a.control.brush, keyShape)
                                else -> Modifier.arcadeLift(a.control, 10.dp).background(a.control.brush, keyShape)
                            }
                        ),
                    ) {
                        Text(
                            digit.toString(),
                            style = if (classic) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.titleMedium.copy(fontWeight = if (remaining == 0) FontWeight.Normal else FontWeight.Bold),
                            color = when {
                                classic -> if (remaining == 0) scheme.outline else scheme.onSurface
                                pencilled -> a.selected.ink
                                remaining == 0 -> a.givenInk
                                else -> a.control.ink
                            },
                        )
                    }
                }
                // The mode switch lives with the keys it changes. Not a state: see notesMode.
                PadKey(
                    notesKey, highlight, glow, pulse,
                    fill = if (!classic) Color.Transparent else if (notesMode) BoardHues.mark(hueA) else scheme.surface,
                    enabled = interactive,
                    onClick = {
                        notesMode = !notesMode
                        notedAt = null
                    },
                    modifier = Modifier.semantics { contentDescription = if (notesMode) "Notes on" else "Notes off" }.then(
                        if (classic) Modifier
                        else if (notesMode) Modifier.arcadeLift(a.selected, 10.dp).background(a.selected.brush, keyShape).border(2.dp, a.selectedEdge, keyShape)
                        else Modifier.background(a.given, keyShape)
                    ),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = when {
                            classic -> if (notesMode) Color.White else scheme.onSurface
                            notesMode -> a.selected.ink
                            else -> a.givenInk
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
