package com.joebywan.daybook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.data.ProgressStore
import com.joebywan.daybook.data.savedGameKey
import com.joebywan.daybook.platform.PlatformBackHandler
import com.joebywan.daybook.platform.currentDate
import com.joebywan.daybook.platform.prepareBoards
import com.joebywan.daybook.platform.rememberKeyValueStore
import com.joebywan.daybook.platform.rememberSolveSoundPlayer
import com.joebywan.daybook.ui.tutorial.LocalChime
import com.joebywan.daybook.ui.archive.ArchiveScreen
import com.joebywan.daybook.ui.home.HomeScreen
import com.joebywan.daybook.ui.home.LaunchMode
import com.joebywan.daybook.ui.home.LaunchPreferences
import com.joebywan.daybook.ui.play.NextKind
import com.joebywan.daybook.ui.play.PlayScreen
import com.joebywan.daybook.ui.play.finishPraise
import com.joebywan.daybook.ui.play.tiersDoneOn
import com.joebywan.daybook.ui.settings.SettingsScreen
import com.joebywan.daybook.ui.stats.AchievementsScreen
import com.joebywan.daybook.ui.stats.StatsScreen
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Where the app currently is. Hand-rolled because four screens do not need a nav graph. */
sealed interface Route {
    data object Home : Route
    data object Stats : Route
    data object Settings : Route
    /** [fromHome]: back returns to where it was opened from. */
    data class Achievements(val fromHome: Boolean = false) : Route
    data class Archive(val puzzleId: String) : Route
    data class Play(
        val puzzleId: String,
        val difficulty: Difficulty,
        val day: LocalDate?,
        val nonce: Long = 0L,
    ) : Route
}

/** A route as one string, for rotation and process death. */
private val RouteSaver = Saver<Route, String>(
    save = { r ->
        when (r) {
            Route.Home -> "home"
            Route.Stats -> "stats"
            Route.Settings -> "settings"
            is Route.Achievements -> "achievements|${r.fromHome}"
            is Route.Archive -> "archive|${r.puzzleId}"
            is Route.Play -> "play|${r.puzzleId}|${r.difficulty.name}|${r.day ?: ""}|${r.nonce}"
        }
    },
    restore = { text ->
        val f = text.split('|')
        when (f[0]) {
            "stats" -> Route.Stats
            "settings" -> Route.Settings
            "achievements" -> Route.Achievements(f.getOrNull(1) == "true")
            "archive" -> Route.Archive(f[1])
            "play" -> Route.Play(
                puzzleId = f[1],
                difficulty = Difficulty.valueOf(f[2]),
                day = f[3].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                nonce = f[4].toLong(),
            )
            else -> Route.Home
        }
    },
)

/**
 * The whole app. [startAt] is where it opens — Home unless something asked for a particular
 * screen, which on the web is a `?puzzle=` link.
 */
@Composable
fun DaybookApp(startAt: Route = Route.Home) {
    val progressFile = rememberKeyValueStore(KeyValueStore.PROGRESS)
    val store = remember(progressFile) { ProgressStore(progressFile) }
    val scope = rememberCoroutineScope()
    val completions by store.completions.collectAsState(initial = emptyList())
    // Null until the store has answered, so a returning player never sees the walkthrough offer
    // flash up for the instant before their "already offered" loads.
    val tutorialsOffered by store.tutorialsOffered.collectAsState(initial = null)

    // Saveable, so turning the phone keeps the player on their board. Under plain `remember` a
    // rotation dropped them back on Home, which also made every rememberSaveable below Play
    // (the game, the open hint, the walkthrough) unreachable.
    var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf(startAt) }
    var today by remember { mutableStateOf(currentDate()) }

    // The home grid's two selectors live here, not on the home screen: navigating into a puzzle
    // destroys that screen, and a difficulty that reset after every game would be worse than the
    // per-card pills it replaced.
    val launchFile = rememberKeyValueStore(KeyValueStore.LAUNCH)
    val launchPrefs = remember(launchFile) { LaunchPreferences(launchFile) }
    val storedDifficulty by launchPrefs.difficulty.collectAsState(initial = null)
    // A tap has to win over the store immediately. Reading the selection back out of DataStore
    // would leave a window — however short — in which the grid is still set to the old tier and a
    // tapped tile starts the wrong puzzle.
    var pickedDifficulty by remember { mutableStateOf<Difficulty?>(null) }
    val difficulty = pickedDifficulty ?: storedDifficulty ?: Difficulty.STANDARD
    // Same reasoning as the difficulty: a flip of the switch shows at once, not after the store answers.
    val storedShowTimer by launchPrefs.showTimer.collectAsState(initial = true)
    var pickedShowTimer by remember { mutableStateOf<Boolean?>(null) }
    val showTimer = pickedShowTimer ?: storedShowTimer
    val storedPlaySound by launchPrefs.playSound.collectAsState(initial = true)
    var pickedPlaySound by remember { mutableStateOf<Boolean?>(null) }
    val playSound = pickedPlaySound ?: storedPlaySound
    // Armed while the switch is on (the web wakes its audio on the first taps); a no-op when off.
    val playSolveSound = rememberSolveSoundPlayer(playSound)
    // Saveable rather than stored: it survives rotation and process death, but a cold start comes
    // back to Daily, because that is what the app is for.
    var mode by rememberSaveable { mutableStateOf(LaunchMode.DAILY) }

    // Roll the date over without needing the app to be restarted at midnight.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            val now = currentDate()
            if (now != today) today = now
        }
    }

    // The screens have no back arrow of their own any more, so this is the only way out of one.
    // It belongs here rather than on each screen because `route` lives here, and a screen that
    // could send itself home would be a second, quieter copy of the navigation rules.
    //
    // Disabled on Home so the press falls through to the activity and closes the app: Home is the
    // top of the task, and a back button that does nothing there is a trap. Nothing is lost by
    // leaving a game this way — the board is saved on the way out and restored on the way back in.
    //
    // A dialog is a window of its own and takes the back press before the activity ever sees it,
    // so this cannot fire while the archive picker or the rules sheet is open.
    //
    // On the web the same handler is the browser's back button; see platform/WebPlatform.kt.
    PlatformBackHandler(enabled = route != Route.Home) { route = Route.Home }

    when (val current = route) {
        Route.Home -> {
            // Nothing on Android. On the web, today's boards are generated while the grid is being
            // read, so the tap that opens one does not have to wait for it; see the platform seam.
            LaunchedEffect(today, difficulty) { prepareBoards(today, difficulty) }
            HomeScreen(
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
                    when (mode) {
                        LaunchMode.DAILY -> route = Route.Play(puzzleId, difficulty, today)
                        // The tier's current random board: resumed until solved or replaced in the game.
                        LaunchMode.RANDOM -> scope.launch {
                            route = Route.Play(puzzleId, difficulty, null, store.randomNonce(puzzleId, difficulty))
                        }
                    }
                },
                onArchive = { puzzleId -> route = Route.Archive(puzzleId) },
                onStats = { route = Route.Stats },
                onAchievements = { route = Route.Achievements(fromHome = true) },
                onSettings = { route = Route.Settings },
            )
        }

        Route.Stats -> StatsScreen(
            today = today,
            completions = completions,
            onAchievements = { route = Route.Achievements() },
            onBack = { route = Route.Home },
        )

        is Route.Achievements -> AchievementsScreen(
            today = today,
            completions = completions,
            onBack = { route = if (current.fromHome) Route.Home else Route.Stats },
        )

        Route.Settings -> SettingsScreen(
            showTimer = showTimer,
            onShowTimer = { show ->
                pickedShowTimer = show
                scope.launch { launchPrefs.setShowTimer(show) }
            },
            playSound = playSound,
            onPlaySound = { play ->
                pickedPlaySound = play
                scope.launch { launchPrefs.setPlaySound(play) }
            },
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
                    DailySeed.randomSeed(puzzle.id, current.difficulty, current.nonce)
                }
                val gameKey = savedGameKey(puzzle.id, current.difficulty, seed)
                CompositionLocalProvider(LocalChime provides playSolveSound) {
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
                    onNewGame = if (current.day != null) null else {
                        {
                            scope.launch {
                                store.clearSavedGame(gameKey)
                                route = Route.Play(
                                    puzzle.id, current.difficulty, null,
                                    store.newRandomNonce(puzzle.id, current.difficulty),
                                )
                            }
                        }
                    },
                    onSolved = { seconds, hints ->
                        // The slot moves on, so leaving and tapping the tile again is a new board.
                        if (current.day == null) scope.launch { store.newRandomNonce(puzzle.id, current.difficulty) }
                        // PlayScreen calls this once per solve (its `recorded` flag survives
                        // recreation), so the chime cannot repeat on a rotation.
                        playSolveSound()
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
                    praiseFor = { seconds, hints ->
                        finishPraise(
                            completions,
                            Completion(puzzle.id, current.difficulty, current.day, seconds, hints),
                            today,
                            puzzle.displayName,
                        )
                    },
                    // Only what the store already holds; the board being solved is counted by the
                    // options themselves, since it reaches the store a moment after the win.
                    otherTiersDone = tiersDoneOn(completions, puzzle.id, current.day),
                    onNext = { option ->
                        val tier = option.difficulty
                        when {
                            tier == null -> route = Route.Home
                            // The same date, so on an archive day it is that past date's board.
                            option.kind == NextKind.DAILY -> route = Route.Play(puzzle.id, tier, current.day)
                            // A solved random board has already had its slot renewed (see onSolved).
                            else -> scope.launch {
                                route = Route.Play(puzzle.id, tier, null, store.randomNonce(puzzle.id, tier))
                            }
                        }
                    },
                    tutorialOffered = tutorialsOffered?.let { puzzle.id in it },
                    onTutorialOffered = { scope.launch { store.markTutorialOffered(puzzle.id) } },
                    showTimer = showTimer,
                    onBack = { route = Route.Home },
                )
                }
            }
        }
    }
}
