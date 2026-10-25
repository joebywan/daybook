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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.digitKeyAction
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.ui.PAD_GAP
import com.joebywan.daybook.ui.PAD_HEIGHT
import com.joebywan.daybook.ui.PadKey
import com.joebywan.daybook.ui.dimmed
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.ring
import com.joebywan.daybook.ui.theme.BoardHues


/**
 * The Sudoku grid and its digit pad, shared by Sudoku and Cage Sudoku: both draw exactly this, and differ in
 * what a tap, a key and a digit do to their own state (the callbacks), which squares clash ([conflicts]),
 * and what is drawn over the grid ([overlay]: Cage Sudoku's dashed cages). [topBand] is the share of a
 * square's height that a label of the overlay takes from the top, which the pencil marks leave clear.
 */

/** A note's size as a share of its cell's side, and how strongly it is inked. Tuned by rendering. */
private const val SUDOKU_NOTE_SIZE = 0.34f
private const val SUDOKU_NOTE_ALPHA = 0.85f

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
    hueSeed: Int,
    interactive: Boolean,
    onSelect: (Int) -> Unit,
    onEnter: (cell: Int, digit: Int, notesMode: Boolean) -> Unit,
    onKey: (DigitKeyAction, notesMode: Boolean) -> Unit,
    accent: Color,
    topBand: (Int) -> Float = { 0f },
    units: List<IntArray> = SUDOKU_UNITS,
    complete: (Int) -> Boolean = { s.cells.holdsOneToNine(SUDOKU_UNITS[it]) },
    overlay: @Composable BoxScope.(cell: Dp) -> Unit = {},
) {
    val feel = rememberMoveFeel(s.cells, units, complete)
    val scheme = MaterialTheme.colorScheme
    // Colour is derived per render from the solution, never stored (docs/COLOUR.md "Roles"): A is the
    // structure (box tint, selection, pencil), B is the player's digits and their twins.
    val dark = BoardHues.isDark(scheme)
    val (hueA, hueB) = BoardHues.pair(hueSeed)
    val boxTint = BoardHues.fill(hueA, dark).copy(alpha = if (dark) 0.55f else 0.30f).compositeOver(scheme.surface)
    val selectedValue = s.selected?.let { s.cells[it] } ?: 0
    val highlight = LocalBoardHighlight.current
    val glow = if (highlight.warning) scheme.error else scheme.onBackground
    // Which way the digit keys act. Transient UI state, so it lives here and not in the state:
    // PlayScreen would otherwise make every flip of it an undo step.
    var notesMode by rememberSaveable { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    // The keyboard (web, or a hardware keyboard on Android): digits, clear and arrows, mapped by
    // digitKeyAction and applied through the same state functions the pad and a tap use. See boardKeys.
    // Breathes only while something glows, as on Kings.
    val pulse = rememberHintPulse(highlight.strong.isNotEmpty())

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
        val side = if (constraints.hasBoundedHeight) {
            minOf(maxWidth, (maxHeight - PAD_GAP - PAD_HEIGHT).coerceAtLeast(0.dp))
        } else {
            maxWidth
        }
        val cell = side / 9
        val padWidth = maxWidth
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(side).highlightGrid(9, 9)) {
                for (r in 0 until 9) {
                    for (c in 0 until 9) {
                        val i = r * 9 + c
                        val isSelected = s.selected == i
                        val sameValue = selectedValue != 0 && s.cells[i] == selectedValue
                        val look = highlight.look(i)
                        Box(
                            Modifier
                                .padding(start = cell * c, top = cell * r)
                                .size(cell)
                                .moveFeel(feel, i, accent)
                                .padding(1.dp)
                                .dimmed(look)
                                .ring(look, glow, pulse, corner = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                // The selected cell's row, column and box are deliberately
                                // left alone. Shading them restyled twenty of the eighty-one
                                // squares on every tap, so the board had to be re-read after
                                // each selection to find the 3x3 structure again -- the cost
                                // outweighed the help. Only the selection itself and cells
                                // holding the same digit react.
                                .background(
                                    when {
                                        isSelected -> BoardHues.mark(hueA).copy(alpha = if (dark) 0.55f else 0.40f).compositeOver(scheme.surface)
                                        sameValue -> BoardHues.fill(hueB, dark).copy(alpha = 0.8f).compositeOver(scheme.surface)
                                        (r / 3 + c / 3) % 2 == 0 -> boxTint
                                        else -> scheme.surface
                                    }
                                )
                                .clickable(enabled = interactive) { onSelect(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val noted = s.visibleNotes(i)
                            if (noted != 0) {
                                val ink = scheme.onSurface.copy(alpha = SUDOKU_NOTE_ALPHA)
                                // A square that carries a label (a cage's total) gives its top band to it.
                                val band = topBand(i)
                                val style = TextStyle(
                                    fontSize = (cell.value * SUDOKU_NOTE_SIZE * (1f - band * 0.5f)).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ink,
                                    textAlign = TextAlign.Center,
                                )
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
                                    fontSize = (cell.value * 0.52f).sp,
                                    fontWeight = if (s.givens[i]) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        i in conflicts -> scheme.error
                                        s.givens[i] -> scheme.onSurface
                                        else -> BoardHues.ink(hueB, dark)
                                    },
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
                    val pencilled = notesMode && at != null && s.hasNote(at, digit)
                    // Notes cannot go on a filled or given cell: the keys show it rather than do nothing.
                    val inert = notesMode && at != null && !s.canNote(at)
                    PadKey(
                        SudokuTeacher.pad(digit), highlight, glow, pulse,
                        fill = when {
                            pencilled -> BoardHues.mark(hueA).copy(alpha = 0.40f)
                            remaining == 0 -> scheme.surfaceVariant
                            else -> scheme.surface
                        },
                        enabled = interactive && at != null && !inert,
                        onClick = {
                            // In notes mode the keys are dimmed and inert on a filled or given cell
                            // (above), so a refusal here is never silent.
                            if (at != null) onEnter(at, digit, notesMode)
                        },
                        modifier = Modifier.alpha(if (inert) 0.35f else 1f),
                    ) {
                        Text(
                            digit.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (remaining == 0) scheme.outline else scheme.onSurface,
                        )
                    }
                }
                // The mode switch lives with the keys it changes. Not a state: see notesMode.
                PadKey(
                    SudokuTeacher.NOTES_KEY, highlight, glow, pulse,
                    fill = if (notesMode) BoardHues.mark(hueA) else scheme.surface,
                    enabled = interactive,
                    onClick = { notesMode = !notesMode },
                    modifier = Modifier.semantics { contentDescription = if (notesMode) "Notes on" else "Notes off" },
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (notesMode) Color.White else scheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
