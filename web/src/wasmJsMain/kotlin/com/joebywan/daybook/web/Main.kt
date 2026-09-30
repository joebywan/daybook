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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.DaybookTypography
import com.joebywan.daybook.ui.theme.LightScheme

/**
 * The device's local calendar date as yyyymmdd, read from one `Date` so the three fields cannot
 * straddle midnight. Local, not UTC, because the app's `LocalDate.now()` is the phone's local date.
 */
private fun localDateCode(): Int =
    js("(() => { const d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate(); })()")

private fun locationSearch(): String = js("window.location.search")

private fun removeLoadingNote(): Unit = js("document.getElementById('loading')?.remove()")

/** A calendar date the way the app's `LocalDate` would print it. */
private data class Day(val year: Int, val month: Int, val day: Int) {
    val epochDay: Long get() = SeedHash.epochDay(year, month, day)
    override fun toString(): String =
        "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {
        fun parse(text: String): Day? {
            val parts = text.split('-').map { it.toIntOrNull() ?: return null }
            if (parts.size != 3 || parts[1] !in 1..12 || parts[2] !in 1..31) return null
            return Day(parts[0], parts[1], parts[2])
        }

        fun today(): Day = localDateCode().let { Day(it / 10000, it / 100 % 100, it % 100) }

        /** The inverse of [SeedHash.epochDay] (Hinnant's civil-from-days), for the parity dump. */
        fun ofEpochDay(epochDay: Long): Day {
            val z = epochDay + 719468
            val era = z.floorDiv(146097L)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return Day(y, m, d)
        }
    }
}

private fun query(): Map<String, String> =
    locationSearch().removePrefix("?").split('&').filter { it.isNotEmpty() }.associate {
        val eq = it.indexOf('=')
        if (eq < 0) it to "" else it.substring(0, eq) to it.substring(eq + 1)
    }

/** Same line format as WebParityTest in app/, so the two can be diffed. */
private fun fingerprint(day: Day, tier: Difficulty): String {
    val seed = SeedHash.daily(day.epochDay, Kings.id, tier)
    val s = Kings.generate(seed, tier) as KingsState
    return "$day ${tier.name} seed=$seed regions=${s.region.joinToString("")} " +
        "kings=${s.solution.sorted().joinToString(",")}"
}

private val PARITY_DAYS = listOf(Day(2026, 1, 1), Day(2026, 9, 30), Day(2027, 2, 28))

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val params = query()
    val day = params["date"]?.let(Day::parse) ?: Day.today()
    val tier = params["tier"]?.let { Difficulty.fromKey(it.uppercase()) } ?: Difficulty.STANDARD
    val debug = "dump" in params

    println("daybook: date=$day epochDay=${day.epochDay}")
    if (debug) {
        for (d in PARITY_DAYS) for (t in Difficulty.entries) println("PARITY ${fingerprint(d, t)}")
        println("TODAY ${fingerprint(day, tier)}")
        // ?dump&range=N: every tier for N days from 2026-01-01, to diff against the JVM.
        val range = params["range"]?.toIntOrNull() ?: 0
        val start = Day(2026, 1, 1).epochDay
        for (i in 0 until range) {
            for (t in Difficulty.entries) println("RANGE ${fingerprint(Day.ofEpochDay(start + i), t)}")
        }
    }

    ComposeViewport(viewportContainerId = "app") {
        KingsPage(day, tier, debug)
    }
    removeLoadingNote()
}

@Composable
private fun KingsPage(day: Day, startTier: Difficulty, debug: Boolean) {
    val scheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = DaybookTypography) {
        var tier by remember { mutableStateOf(startTier) }
        val initial = remember(tier) {
            Kings.generate(SeedHash.daily(day.epochDay, Kings.id, tier), tier) as KingsState
        }
        // Undo mirrors the app's PlayScreen: every state the board hands over is one entry. The
        // board's own transient gesture state lives inside Kings.Board, so this never sees a
        // half-finished drag.
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
                "Kings",
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

            // Sized from both axes: the board is a square no bigger than either the width or the
            // height left over, so a short landscape window shrinks it instead of clipping it.
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
                                        "pad=14 n=${initial.size}",
                                )
                            }
                        },
                ) {
                    Kings.Board(
                        state = state,
                        onState = { history = history + it },
                        interactive = !state.solved,
                    )
                }
            }

            // Reserved at a fixed two lines, so the message appearing never moves the board.
            Text(
                if (state.solved) {
                    "Solved in ${state.moves} moves."
                } else {
                    "Tap to cross out, double-tap to crown. One king per row, column and colour; no two touching."
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
