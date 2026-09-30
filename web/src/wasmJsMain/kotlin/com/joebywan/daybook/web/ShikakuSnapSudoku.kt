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
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.SnapState
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.DaybookTypography
import com.joebywan.daybook.ui.theme.LightScheme
import kotlin.time.TimeSource

/*
 * Shikaku, Snap and Sudoku on the web: `?puzzle=shikaku|snap|sudoku` picks one (Kings stays the
 * default), and `?dump` prints their boards for diffing against WebParityShikakuSnapSudokuTest.
 */

private val SHIKAKU_SNAP_SUDOKU: List<PuzzleType> = listOf(Shikaku, Snap, Sudoku)

/** The puzzle a `?puzzle=` value names, if it is one of these three. */
internal fun shikakuSnapSudoku(id: String): PuzzleType? = SHIKAKU_SNAP_SUDOKU.firstOrNull { it.id == id }

/** Same line format as WebParityShikakuSnapSudokuTest in app/, so the two can be diffed. */
private fun fingerprint(p: PuzzleType, date: String, epochDay: Long, tier: Difficulty): String {
    val seed = SeedHash.daily(epochDay, p.id, tier)
    val body = when (val s = p.generate(seed, tier)) {
        is ShikakuState -> "${s.width}x${s.height} clues=" +
            s.clues.joinToString(",") { it?.toString() ?: "." } +
            " blocks=" + s.solution.joinToString(";") { "${it.r0},${it.c0},${it.r1},${it.c1}" }
        is SnapState -> "${s.width}x${s.height} waypoints=${s.waypoints.joinToString(",")}"
        is SudokuState -> "givens=" + s.cells.joinToString("") + " solution=" + s.solution.joinToString("")
        else -> error("unexpected state $s")
    }
    return "${p.id} $date ${tier.name} seed=$seed $body"
}

/**
 * Prints `PARITY3` lines for [parity] and `RANGE3` lines for [range] (date text to epoch day),
 * every puzzle and tier, then one `GENTIME` line per puzzle and tier with how long generation took.
 */
internal fun dumpShikakuSnapSudoku(parity: List<Pair<String, Long>>, range: List<Pair<String, Long>>) {
    for ((date, epochDay) in parity) for (p in SHIKAKU_SNAP_SUDOKU) for (t in Difficulty.entries) {
        println("PARITY3 ${fingerprint(p, date, epochDay, t)}")
    }
    if (range.isEmpty()) return
    val times = mutableMapOf<String, MutableList<Double>>()
    for ((date, epochDay) in range) for (p in SHIKAKU_SNAP_SUDOKU) for (t in Difficulty.entries) {
        val mark = TimeSource.Monotonic.markNow()
        val line = fingerprint(p, date, epochDay, t)
        times.getOrPut("${p.id} ${t.name}") { mutableListOf() } += mark.elapsedNow().inWholeMicroseconds / 1000.0
        println("RANGE3 $line")
    }
    for ((key, ms) in times) {
        val sorted = ms.sorted()
        println(
            "GENTIME $key n=${ms.size} mean=${(ms.sum() / ms.size).roundTo1()} " +
                "median=${sorted[sorted.size / 2].roundTo1()} p95=${sorted[sorted.size * 95 / 100].roundTo1()} " +
                "max=${sorted.last().roundTo1()} total=${ms.sum().roundTo1()}",
        )
    }
}

private fun Double.roundTo1(): String = ((this * 10).toLong() / 10.0).toString()

@Composable
internal fun ShikakuSnapSudokuPage(
    puzzle: PuzzleType,
    date: String,
    epochDay: Long,
    startTier: Difficulty,
    debug: Boolean,
) {
    val scheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = DaybookTypography) {
        var tier by remember { mutableStateOf(startTier) }
        val initial = remember(tier) {
            val mark = TimeSource.Monotonic.markNow()
            val s = puzzle.generate(SeedHash.daily(epochDay, puzzle.id, tier), tier)
            if (debug) println("GENERATED ${puzzle.id} ${tier.name} ms=${mark.elapsedNow().inWholeMilliseconds}")
            s
        }
        // Undo mirrors the app's PlayScreen: every state the board hands over is one entry.
        var history by remember(initial) { mutableStateOf(listOf(initial)) }
        val state: PuzzleState = history.last()
        val density = LocalDensity.current

        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                puzzle.displayName,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                date,
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

            // Hosted as the app's PlayScreen hosts it: the leftover height, board centred in it.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.onGloballyPositioned {
                        if (debug) {
                            val r = it.boundsInWindow()
                            val d = density.density
                            println(
                                "BOARD3 ${puzzle.id} ${tier.name} x=${r.left / d} y=${r.top / d} " +
                                    "w=${r.width / d} h=${r.height / d}",
                            )
                        }
                    },
                ) {
                    puzzle.Board(
                        state = state,
                        onState = { history = history + it },
                        interactive = !state.solved,
                    )
                }
            }

            // Reserved at a fixed two lines, so the message appearing never moves the board.
            Text(
                if (state.solved) "Solved in ${state.moves} moves." else puzzle.tagline,
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
            if (state.solved && debug) println("SOLVED ${puzzle.id} moves=${state.moves}")
        }
    }
}
