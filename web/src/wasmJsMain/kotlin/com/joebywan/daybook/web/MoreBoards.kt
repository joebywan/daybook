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
import androidx.compose.runtime.LaunchedEffect
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
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.Sym
import com.joebywan.daybook.puzzles.Tower
import com.joebywan.daybook.puzzles.TowerState
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.DaybookTypography
import com.joebywan.daybook.ui.theme.LightScheme
import kotlin.time.TimeSource

/** Boards reachable with `?puzzle=<id>`; Kings, with no parameter, stays on its own page. */
private val MORE_BOARDS: List<PuzzleType> = listOf(Mambo, Pipes, Sets, Tower)

fun moreBoard(key: String?): PuzzleType? = MORE_BOARDS.firstOrNull { it.id == key }

/**
 * Same line format as `WebParityMamboPipesSetsTowerTest.fingerprint` in app/, so the two can be
 * diffed.
 */
fun boardFingerprint(puzzle: PuzzleType, date: String, tier: Difficulty, seed: Long): String {
    val head = "${puzzle.id} $date ${tier.name} seed=$seed"
    return when (val s = puzzle.generate(seed, tier)) {
        is MamboState ->
            "$head n=${s.size} givens=${s.givens.joinToString("") { if (it) "1" else "0" }} " +
                "links=${s.links.joinToString(",") { "${it.a}${if (it.same) "=" else "x"}${it.b}" }} " +
                "solution=${s.solution.joinToString("") { if (it == Sym.SUN) "S" else "M" }}"
        is PipesState ->
            "$head w=${s.width} h=${s.height} source=${s.source} " +
                "cells=${s.cells.joinToString("") { it.toString(16) }}"
        is SetsState ->
            "$head target=${s.target} " +
                "cards=${s.cards.joinToString(",") { c -> c.traits.joinToString("") }} " +
                "sets=${Sets.allSets(s.cards).joinToString(";") { it.joinToString(",") }}"
        is TowerState ->
            "$head slots=${s.slots} colours=${s.colours} max=${s.maxGuesses} " +
                "secret=${s.secret.joinToString("")}"
        else -> error("no fingerprint for ${puzzle.id}")
    }
}

/**
 * `?dump&time`: how long [PuzzleType.generate] takes per tier, over [epochDays]. Printed as
 * `TIME <id> <tier> n=… mean=…ms max=…ms total=…ms`.
 */
fun timeGeneration(puzzle: PuzzleType, epochDays: List<Long>) {
    for (tier in Difficulty.entries) {
        var total = 0.0
        var worst = 0.0
        for (epochDay in epochDays) {
            val seed = SeedHash.daily(epochDay, puzzle.id, tier)
            val mark = TimeSource.Monotonic.markNow()
            puzzle.generate(seed, tier)
            val ms = mark.elapsedNow().inWholeMicroseconds / 1000.0
            total += ms
            if (ms > worst) worst = ms
        }
        println(
            "TIME ${puzzle.id} ${tier.name} n=${epochDays.size} " +
                "mean=${(total / epochDays.size).fmt()}ms max=${worst.fmt()}ms total=${total.fmt()}ms",
        )
    }
}

private fun Double.fmt(): String = ((this * 100).toLong() / 100.0).toString()

/**
 * Any registered board, hosted the way the app's PlayScreen hosts it: a weighted, centred box,
 * with undo pushing one entry per state the board hands over.
 */
@Composable
fun MoreBoardPage(puzzle: PuzzleType, dayLabel: String, epochDay: Long, startTier: Difficulty, debug: Boolean) {
    val scheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = DaybookTypography) {
        var tier by remember { mutableStateOf(startTier) }
        val initial = remember(tier) { puzzle.generate(SeedHash.daily(epochDay, puzzle.id, tier), tier) }
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
                dayLabel,
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

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Wraps the board so its real bounds can be reported to a test harness; the
                // board's own layout decides the size.
                Box(
                    Modifier.onGloballyPositioned {
                        if (debug) {
                            val r = it.boundsInWindow()
                            val d = density.density
                            println(
                                "BOARD x=${r.left / d} y=${r.top / d} w=${r.width / d} " +
                                    "h=${r.height / d} tier=${tier.name}",
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

            Text(
                when {
                    state.solved -> "Solved in ${state.moves} moves."
                    state.failed -> "Out of guesses."
                    else -> puzzle.tagline
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.solved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                minLines = 1,
                maxLines = 1,
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
            if (debug) {
                LaunchedEffect(state) {
                    println("STATE moves=${state.moves} solved=${state.solved} failed=${state.failed} undo=${history.size - 1}")
                }
            }
        }
    }
}
