package com.joebywan.daybook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.joebywan.daybook.ui.home.LaunchMode
import com.joebywan.daybook.ui.home.LaunchPreferences
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

    // The home grid's two selectors live here, not on the home screen: navigating into a puzzle
    // destroys that screen, and a difficulty that reset after every game would be worse than the
    // per-card pills it replaced.
    val launchPrefs = remember { LaunchPreferences(context) }
    val storedDifficulty by launchPrefs.difficulty.collectAsState(initial = null)
    // A tap has to win over the store immediately. Reading the selection back out of DataStore
    // would leave a window — however short — in which the grid is still set to the old tier and a
    // tapped tile starts the wrong puzzle.
    var pickedDifficulty by remember { mutableStateOf<Difficulty?>(null) }
    val difficulty = pickedDifficulty ?: storedDifficulty ?: Difficulty.STANDARD
    // Saveable rather than stored: it survives rotation and process death, but a cold start comes
    // back to Daily, because that is what the app is for.
    var mode by rememberSaveable { mutableStateOf(LaunchMode.DAILY) }

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
            difficulty = difficulty,
            onDifficulty = { picked ->
                pickedDifficulty = picked
                scope.launch { launchPrefs.setDifficulty(picked) }
            },
            mode = mode,
            onMode = { mode = it },
            onLaunch = { puzzleId ->
                route = when (mode) {
                    LaunchMode.DAILY -> Route.Play(puzzleId, difficulty, today)
                    // A fresh nonce every tap is what makes a second practice game a new board
                    // rather than the one just finished.
                    LaunchMode.PRACTICE ->
                        Route.Play(puzzleId, difficulty, null, System.nanoTime())
                }
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
                // A saved route can name a puzzle that no longer exists. Navigating has to happen
                // in an effect rather than inline: writing state while composing is what starts a
                // recomposition loop.
                LaunchedEffect(current.puzzleId) { route = Route.Home }
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
