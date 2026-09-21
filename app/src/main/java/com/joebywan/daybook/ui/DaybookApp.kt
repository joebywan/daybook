package com.joebywan.daybook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.ProgressStore
import com.joebywan.daybook.data.savedGameKey
import com.joebywan.daybook.ui.archive.ArchiveScreen
import com.joebywan.daybook.ui.home.HomeScreen
import com.joebywan.daybook.ui.play.PlayScreen
import com.joebywan.daybook.ui.stats.StatsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Where the app currently is. Hand-rolled because four screens do not need a nav graph. */
sealed interface Route {
    data object Home : Route
    data object Stats : Route
    data class Archive(val puzzleId: String) : Route
    data class Play(
        val puzzleId: String,
        val difficulty: Difficulty,
        val day: LocalDate?,
        val nonce: Long = 0L,
    ) : Route
}

@Composable
fun DaybookApp() {
    val context = LocalContext.current
    val store = remember { ProgressStore(context) }
    val scope = rememberCoroutineScope()
    val completions by store.completions.collectAsState(initial = emptyList())

    var route by remember { mutableStateOf<Route>(Route.Home) }
    var today by remember { mutableStateOf(LocalDate.now()) }

    // Roll the date over without needing the app to be restarted at midnight.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            val now = LocalDate.now()
            if (now != today) today = now
        }
    }

    when (val current = route) {
        Route.Home -> HomeScreen(
            today = today,
            completions = completions,
            onPlay = { puzzleId, difficulty -> route = Route.Play(puzzleId, difficulty, today) },
            onPractice = { puzzleId, difficulty ->
                route = Route.Play(puzzleId, difficulty, null, System.nanoTime())
            },
            onArchive = { puzzleId -> route = Route.Archive(puzzleId) },
            onStats = { route = Route.Stats },
        )

        Route.Stats -> StatsScreen(
            today = today,
            completions = completions,
            onBack = { route = Route.Home },
        )

        is Route.Archive -> ArchiveScreen(
            puzzleId = current.puzzleId,
            today = today,
            completions = completions,
            onPlay = { day, difficulty ->
                route = Route.Play(current.puzzleId, difficulty, day)
            },
            onBack = { route = Route.Home },
        )

        is Route.Play -> {
            val puzzle = PuzzleRegistry.byId(current.puzzleId)
            if (puzzle == null) {
                route = Route.Home
            } else {
                val seed = if (current.day != null) {
                    DailySeed.seedFor(current.day, puzzle.id, current.difficulty)
                } else {
                    DailySeed.practiceSeed(puzzle.id, current.difficulty, current.nonce)
                }
                val gameKey = savedGameKey(puzzle.id, current.difficulty, seed)
                PlayScreen(
                    puzzle = puzzle,
                    difficulty = current.difficulty,
                    day = current.day,
                    seed = seed,
                    restore = { store.savedGame(gameKey) },
                    persist = { game ->
                        if (game == null) store.clearSavedGame(gameKey)
                        else store.saveGame(gameKey, game)
                    },
                    onSolved = { seconds, hints ->
                        scope.launch {
                            store.record(
                                Completion(
                                    puzzleId = puzzle.id,
                                    difficulty = current.difficulty,
                                    day = current.day,
                                    seconds = seconds,
                                    hints = hints,
                                )
                            )
                        }
                    },
                    onAgain = {
                        route = Route.Play(puzzle.id, current.difficulty, null, System.nanoTime())
                    },
                    onBack = { route = Route.Home },
                )
            }
        }
    }
}
