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
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
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
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.style


/**
 * The Sudoku grid and its digit pad, shared by Sudoku and Cage Sudoku: both draw exactly this, and differ in
 * what a tap, a key and a digit do to their own state (the callbacks), which squares clash ([conflicts]),
 * and what is drawn over the grid ([overlay]: Cage Sudoku's dashed cages). [topBand] is the share of a
 * square's height that a label of the overlay takes from the top, which the pencil marks leave clear.
 */

/** Squares holding the selected square's digit: apart from the entered blue-violet, the selected amber and the error red. */
internal val SudokuTwin = ArcadeFill(Color(0xFF5CF2C8), Color(0xFF14D3A6), Color(0xFF06281F))

/** The hint's ring in light, seen on both a white square and the indigo frame (amber misses on white, the frame's ink on the frame). */
internal val SudokuHintLight = Color(0xFFE6007E)

/** Every other box's empty squares. */
internal fun ArcadeBoard.boxShade() = lerp(square, given, 0.35f)

/** The frame showing between two squares, half each side: a box's edge and the board's are the heavy ones. */
private fun gap(line: Int) = when {
    line == 0 || line == 9 -> 2.dp
    line % 3 == 0 -> 1.5.dp
    else -> 0.5.dp
}

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

@Composable
internal fun SudokuGridBoard(
    s: SudokuState,
    conflicts: Set<Int>,
    interactive: Boolean,
    onSelect: (Int) -> Unit,
    onDeselect: () -> Unit,
    onEnter: (cell: Int, digit: Int, notesMode: Boolean) -> Unit,
    onKey: (DigitKeyAction, notesMode: Boolean) -> Unit,
    topBand: (Int) -> Float = { 0f },
    units: List<IntArray> = SUDOKU_UNITS,
    complete: (Int) -> Boolean = { s.cells.holdsOneToNine(SUDOKU_UNITS[it]) },
    overlay: @Composable BoxScope.(cell: Dp) -> Unit = {},
) {
    val feel = rememberMoveFeel(s.cells, units, complete)
    val a = ArcadeBoard.current
    val dark = BoardHues.isDark(MaterialTheme.colorScheme)
    val boxShade = a.boxShade()
    val keyShape = RoundedCornerShape(10.dp)
    val (highlight, glow, pulse) = rememberHintPaint(if (dark) a.selected.from else SudokuHintLight)
    // Which way the digit keys act. Transient UI state, so it lives here and not in the state:
    // PlayScreen would otherwise make every flip of it an undo step.
    var notesMode by rememberSaveable { mutableStateOf(false) }
    // The digit picked first (issue #384): with no square selected, a key arms and each tap on a square enters it. Not a state, as notesMode.
    var armed by rememberSaveable { mutableStateOf<Int?>(null) }
    val armedDigit = armed.takeIf { s.selected == null }
    val selectedValue = armedDigit ?: s.selected?.let { s.cells[it] } ?: 0
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
                val action = digitKeyAction(key, 9) ?: return@boardKeys false
                if (!repeat || action is DigitKeyAction.Move) onKey(action, notesMode)
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        val side = padBoardSide()
        val cell = side / 9
        val padWidth = maxWidth
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(side).highlightGrid(9, 9).background(a.frame, RoundedCornerShape(8.dp)).drawBehind {
                    // The frame again over a box's edge: dark's is translucent, and one layer of it hardly parts the boxes.
                    for (k in 1..2) {
                        val at = size.width * k / 3f
                        drawLine(a.frame, Offset(at, 0f), Offset(at, size.height), 3.dp.toPx())
                        drawLine(a.frame, Offset(0f, at), Offset(size.width, at), 3.dp.toPx())
                    }
                },
            ) {
                for (r in 0 until 9) {
                    for (c in 0 until 9) {
                        val i = r * 9 + c
                        val isSelected = s.selected == i
                        val sameValue = selectedValue != 0 && s.cells[i] == selectedValue
                        val look = highlight.look(i)
                        val wrong = i in conflicts
                        // A state by colour also changes shape or weight: wrong is underlined and heavy, selected and twins have an edge.
                        val fill = when {
                            wrong -> ArcadeFill(a.error, a.error, a.errorInk)
                            isSelected -> a.selected
                            sameValue -> SudokuTwin
                            s.cells[i] != 0 && !s.givens[i] -> a.control
                            s.givens[i] -> ArcadeFill(a.given, a.given, a.givenInk)
                            (r / 3 + c / 3) % 2 == 0 -> ArcadeFill(boxShade, boxShade, a.squareInk)
                            else -> ArcadeFill(a.square, a.square, a.squareInk)
                        }
                        Box(
                            Modifier
                                // Above its neighbours while a hint names it, which would otherwise cover the ring.
                                .then(if (look.strong || look.soft) Modifier.zIndex(1f) else Modifier)
                                .padding(start = cell * c, top = cell * r)
                                .size(cell)
                                .moveFeel(feel, i, a.selected.from)
                                .padding(start = gap(c), end = gap(c + 1), top = gap(r), bottom = gap(r + 1))
                                .then(a.selected.glow?.takeIf { isSelected }?.let { Modifier.arcadeGlow(it, 4.dp, 1.5.dp) } ?: Modifier)
                                // Under the dimming, so a dimmed square fades to itself and not to the frame.
                                .background(a.square, RoundedCornerShape(4.dp))
                                .dimmed(look)
                                .ring(look, glow, pulse, corner = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                // The selected cell's row, column and box are deliberately
                                // left alone. Shading them restyled twenty of the eighty-one
                                // squares on every tap, so the board had to be re-read after
                                // each selection to find the 3x3 structure again -- the cost
                                // outweighed the help. Only the selection itself and cells
                                // holding the same digit react.
                                .background(fill.brush)
                                .then(
                                    when {
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
                                val ink = if (isSelected) a.selected.ink else a.givenInk
                                // A square that carries a label (a cage's total) gives its top band to it.
                                val band = topBand(i)
                                val style = BoardGlyph.NOTE.style(cell * (1f - band * 0.5f)).copy(color = ink, textAlign = TextAlign.Center)
                                // Drawn, not composed: nine Text nodes in each of up to eighty-one
                                // cells would be a lot of layout for a board that redraws on every tap.
                                Canvas(Modifier.fillMaxSize()) {
                                    val sub = size.width / 3f
                                    for (d in 1..9) {
                                        if (noted and (1 shl (d - 1)) == 0) continue
                                        val laid = measurer.measure(d.toString(), style, maxLines = 1)
                                        val r = (d - 1) / 3
                                        val c = (d - 1) % 3
                                        drawText(
                                            laid,
                                            topLeft = Offset(
                                                c * sub + (sub - laid.size.width) / 2f,
                                                band * size.height + r * (size.height * (1f - band) / 3f) +
                                                    (size.height * (1f - band) / 3f - laid.size.height) / 2f,
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
                                        if (wrong) it.copy(fontWeight = FontWeight.Black, textDecoration = TextDecoration.Underline) else it
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
                (1..9).forEach { digit ->
                    val remaining = 9 - s.cells.count { it == digit }
                    val at = s.selected
                    // In notes mode a key that is already pencilled on the selected cell is tinted.
                    val pencilled = armedDigit == digit || notesMode && at != null && s.hasNote(at, digit)
                    // Notes cannot go on a filled or given cell: the keys show it rather than do nothing.
                    val inert = notesMode && at != null && !s.canNote(at)
                    PadKey(
                        SudokuTeacher.pad(digit), highlight, glow, pulse,
                        fill = Color.Transparent,
                        enabled = interactive && !inert,
                        onClick = {
                            // In notes mode the keys are dimmed and inert on a filled or given cell
                            // (above), so a refusal here is never silent.
                            if (at != null) onEnter(at, digit, notesMode)
                            else armed = digit.takeIf { it != armedDigit }
                        },
                        // Through modifier, not fill: a key's fill is one colour. A used-up digit lies flat and thin, a pencilled one has an edge.
                        modifier = Modifier.alpha(if (inert) 0.35f else 1f).then(
                            when {
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
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (remaining == 0) FontWeight.Normal else FontWeight.Bold),
                            color = when {
                                pencilled -> a.selected.ink
                                remaining == 0 -> a.givenInk
                                else -> a.control.ink
                            },
                        )
                    }
                }
                // The mode switch lives with the keys it changes. Not a state: see notesMode.
                PadKey(
                    SudokuTeacher.NOTES_KEY, highlight, glow, pulse,
                    fill = Color.Transparent,
                    enabled = interactive,
                    onClick = { notesMode = !notesMode },
                    modifier = Modifier.semantics { contentDescription = if (notesMode) "Notes on" else "Notes off" }.then(
                        if (notesMode) Modifier.arcadeLift(a.selected, 10.dp).background(a.selected.brush, keyShape).border(2.dp, a.selectedEdge, keyShape)
                        else Modifier.background(a.given, keyShape)
                    ),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (notesMode) a.selected.ink else a.givenInk,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
