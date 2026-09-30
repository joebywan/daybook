package com.joebywan.daybook.web

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.DaybookTypography
import com.joebywan.daybook.ui.theme.LightScheme
import kotlin.time.TimeSource

/**
 * Mosaic and Atoms on the web: `?puzzle=mosaic` / `?puzzle=atoms` (Kings stays the default).
 * Kept out of Main.kt so each puzzle slice adds a file rather than rewriting a shared one.
 */
internal val mosaicAtomsPuzzles: Map<String, PuzzleType> = mapOf(Mosaic.id to Mosaic, Atoms.id to Atoms)

/** Same line format as `MosaicAtomsWebParityTest` in app/, so the two can be diffed. */
private fun fingerprint(type: PuzzleType, day: Day, tier: Difficulty): String {
    val seed = SeedHash.daily(day.epochDay, type.id, tier)
    return fingerprintOf(day, tier, seed, type.generate(seed, tier))
}

private fun fingerprintOf(day: Day, tier: Difficulty, seed: Long, state: PuzzleState): String =
    "$day ${tier.name} seed=$seed " + when (state) {
        is MosaicState ->
            "${state.width}x${state.height} colours=${state.colours} limit=${state.limit} " +
                "cells=${state.cells.joinToString("")}"
        is AtomsState ->
            "n=${state.size} atoms=${state.atoms.joinToString(";") { "${it.row},${it.col},${it.bonds}" }} " +
                "pairs=${state.pairs.joinToString(";") { "${it.a}-${it.b}${if (it.horizontal) "h" else "v"}" }} " +
                "solution=${state.solution.joinToString("")}"
        else -> error("not a Mosaic or Atoms board")
    }

private val MOSAIC_ATOMS_PARITY_DAYS = listOf(Day(2026, 1, 1), Day(2026, 9, 30), Day(2027, 2, 28))

/**
 * `?dump` for these two: PARITY and RANGE lines (the latter with `&range=N`), plus a TIMING line
 * per tier over the range, since generation runs on the page's only thread.
 */
internal fun dumpMosaicAtoms(type: PuzzleType, day: Day, tier: Difficulty, range: Int) {
    for (d in MOSAIC_ATOMS_PARITY_DAYS) for (t in Difficulty.entries) {
        println("PARITY ${type.id} ${fingerprint(type, d, t)}")
    }
    println("TODAY ${fingerprint(type, day, tier)}")
    val start = Day(2026, 1, 1).epochDay
    val times = Difficulty.entries.associateWith { ArrayList<Double>() }
    for (i in 0 until range) {
        val d = Day.ofEpochDay(start + i)
        for (t in Difficulty.entries) {
            val seed = SeedHash.daily(d.epochDay, type.id, t)
            val mark = TimeSource.Monotonic.markNow()
            val state = type.generate(seed, t)
            times.getValue(t) += mark.elapsedNow().inWholeMicroseconds / 1000.0
            println("RANGE ${fingerprintOf(d, t, seed, state)}")
        }
    }
    if (range > 0) for ((t, ms) in times) {
        val sorted = ms.sorted()
        fun at(q: Double) = sorted[((sorted.size - 1) * q).toInt()]
        println(
            "TIMING ${type.id} ${t.name} n=${sorted.size} mean=${(sorted.sum() / sorted.size).fmt()} " +
                "p50=${at(0.5).fmt()} p95=${at(0.95).fmt()} max=${sorted.last().fmt()} " +
                "total=${sorted.sum().fmt()}",
        )
    }
}

private fun Double.fmt(): String = ((this * 10).toLong() / 10.0).toString()

/** A debug line for the touch tests: enough of the board to check a tap landed. */
private fun describe(state: PuzzleState): String = when (state) {
    is MosaicState -> "cells=${state.cells.joinToString("")} failed=${state.failed}"
    is AtomsState -> "counts=${state.counts.joinToString("")}"
    else -> ""
}

@Composable
internal fun MosaicAtomsPage(type: PuzzleType, day: Day, startTier: Difficulty, debug: Boolean) {
    val scheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = DaybookTypography) {
        var tier by remember { mutableStateOf(startTier) }
        val initial = remember(tier) {
            val seed = SeedHash.daily(day.epochDay, type.id, tier)
            val mark = TimeSource.Monotonic.markNow()
            val board = type.generate(seed, tier)
            if (debug) {
                println("GENERATED ${type.id} ${tier.name} ms=${mark.elapsedNow().inWholeMilliseconds}")
                if (board is MosaicState) {
                    // The proven shortest line, for the touch test to play back.
                    val line = Mosaic.solve(board).orEmpty()
                    println("LINE ${line.joinToString(",") { "${it.cell}:${it.colour}" }}")
                }
            }
            board
        }
        // Undo as in the app's PlayScreen: one entry per state the board hands over.
        var history by remember(initial) { mutableStateOf(listOf(initial)) }
        val state = history.last()
        val density = LocalDensity.current
        if (debug) {
            println("STATE moves=${state.moves} solved=${state.solved} undo=${history.size - 1} ${describe(state)}")
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                type.displayName,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in Difficulty.entries) {
                    val selected = t == tier
                    TextButton(onClick = { tier = t }) {
                        Text(
                            t.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        if (debug) {
                            val r = it.boundsInWindow()
                            val d = density.density
                            println(
                                "BOARD x=${r.left / d} y=${r.top / d} w=${r.width / d} h=${r.height / d} " +
                                    "puzzle=${type.id} tier=${tier.name}",
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                type.Board(
                    state = state,
                    onState = { history = history + it },
                    interactive = !state.solved && !state.failed,
                )
            }

            // Fixed at two lines, so the message changing never moves the board.
            Text(
                when {
                    state.solved -> "Solved in ${state.moves} moves."
                    type === Mosaic -> "Pick a colour, then tap an area to flood it. One colour before the fills run out."
                    else -> "Tap between two atoms to cycle none, one, two bonds, or drag from one atom to another."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.solved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { history = history.dropLast(1) },
                    enabled = history.size > 1,
                ) { Text("Undo") }
                OutlinedButton(
                    onClick = { history = history + initial },
                    enabled = state !== initial,
                ) { Text("Restart") }
            }
            if (state.solved && debug) println("SOLVED moves=${state.moves}")
        }
    }
}
