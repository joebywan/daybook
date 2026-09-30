package com.joebywan.daybook.web

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.DaybookTypography
import com.joebywan.daybook.ui.theme.LightScheme
import kotlin.time.TimeSource

/** Same line format as LitsWebParityTest in app/, so the two can be diffed. */
private fun litsFingerprint(day: Day, tier: Difficulty): String {
    val seed = SeedHash.daily(day.epochDay, Lits.id, tier)
    val s = Lits.generate(seed, tier) as LitsState
    return "$day ${tier.name} seed=$seed regions=${s.region.joinToString("") { it.toString(36) }} " +
        "shading=${s.solution.joinToString("") { if (it) "1" else "0" }}"
}

/**
 * The LITS lines of `?dump`: `LITS-PARITY` for the days `LitsWebParityTest` pins, `LITS-TODAY`
 * with how long the board took to generate, and with `&range=N` a `LITS-RANGE` line per tier for
 * N days from 2026-01-01.
 */
internal fun dumpLits(today: Day, tier: Difficulty, range: Int) {
    for (d in PARITY_DAYS) for (t in Difficulty.entries) println("LITS-PARITY ${litsFingerprint(d, t)}")
    val mark = TimeSource.Monotonic.markNow()
    val line = litsFingerprint(today, tier)
    println("LITS-TODAY $line ms=${mark.elapsedNow().inWholeMilliseconds}")
    val start = Day(2026, 1, 1).epochDay
    for (i in 0 until range) {
        for (t in Difficulty.entries) println("LITS-RANGE ${litsFingerprint(Day.ofEpochDay(start + i), t)}")
    }
}

/** `?puzzle=lits`: the same page as Kings', holding a LITS board. */
@Composable
internal fun LitsPage(day: Day, startTier: Difficulty, debug: Boolean) {
    val scheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = DaybookTypography) {
        var tier by remember { mutableStateOf(startTier) }
        val initial = remember(tier) {
            val mark = TimeSource.Monotonic.markNow()
            val s = Lits.generate(SeedHash.daily(day.epochDay, Lits.id, tier), tier) as LitsState
            if (debug) println("LITS-GENERATED ${tier.name} ms=${mark.elapsedNow().inWholeMilliseconds}")
            s
        }
        var history by remember(initial) { mutableStateOf(listOf<PuzzleState>(initial)) }
        val state = history.last()
        val density = LocalDensity.current

        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Lits.displayName,
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

            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                Box(
                    Modifier
                        .size(side)
                        .onGloballyPositioned {
                            if (debug) {
                                val r = it.boundsInWindow()
                                val d = density.density
                                println(
                                    "BOARD x=${r.left / d} y=${r.top / d} side=${r.width / d} " +
                                        "pad=18 n=${initial.width}",
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Lits.Board(
                        state = state,
                        onState = { history = history + it },
                        interactive = !state.solved,
                    )
                }
            }

            Text(
                if (state.solved) {
                    "Solved in ${state.moves} moves."
                } else {
                    "Shade a tetromino in every region: no 2x2, no two same letters touching."
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
