package com.joebywan.daybook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.KeptCard
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.data.LevelProgress
import com.joebywan.daybook.data.LevelSolve
import com.joebywan.daybook.data.ProgressStore
import com.joebywan.daybook.data.PuzzleOrder
import com.joebywan.daybook.data.Stats
import com.joebywan.daybook.data.savedGameKey
import com.joebywan.daybook.platform.PlatformBackHandler
import com.joebywan.daybook.platform.currentDate
import com.joebywan.daybook.platform.formatClock
import com.joebywan.daybook.platform.formatDate
import com.joebywan.daybook.platform.prepareBoards
import com.joebywan.daybook.platform.rememberHaptics
import com.joebywan.daybook.platform.rememberKeyValueStore
import com.joebywan.daybook.platform.rememberReducedMotion
import com.joebywan.daybook.platform.rememberSolveSoundPlayer
import com.joebywan.daybook.ui.tutorial.LocalChime
import com.joebywan.daybook.ui.archive.ArchiveScreen
import com.joebywan.daybook.ui.home.HomeScreen
import com.joebywan.daybook.ui.home.LaunchMode
import com.joebywan.daybook.ui.home.LocalJustSolved
import androidx.compose.runtime.DisposableEffect
import com.joebywan.daybook.ui.home.LaunchPreferences
import com.joebywan.daybook.ui.home.LevelsScreen
import com.joebywan.daybook.ui.home.Welcome
import com.joebywan.daybook.ui.play.NextKind
import com.joebywan.daybook.puzzles.WordWheelState
import com.joebywan.daybook.ui.play.PlayLog
import com.joebywan.daybook.ui.play.PlayScreen
import com.joebywan.daybook.ui.play.finishPraise
import com.joebywan.daybook.ui.play.levelNextOptions
import com.joebywan.daybook.ui.play.nextOptions
import com.joebywan.daybook.ui.play.shareCard
import com.joebywan.daybook.ui.play.tiersDoneOn
import com.joebywan.daybook.ui.settings.SettingsScreen
import com.joebywan.daybook.ui.stats.AchievementsScreen
import com.joebywan.daybook.ui.stats.StatsScreen
import com.joebywan.daybook.ui.theme.Appearance
import com.joebywan.daybook.ui.theme.DaybookTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier

/** A shared daily's date: day and month, as the owner's share example has it. */
private const val SHARE_DATE = "d MMM"

/** Where the app currently is. Hand-rolled because four screens do not need a nav graph. */
sealed interface Route {
    data object Home : Route
    data object Stats : Route
    data object Settings : Route
    /** [fromHome]: back returns to where it was opened from. */
    data class Achievements(val fromHome: Boolean = false) : Route
    /** [day]: the day last played from it, which the list opens at. */
    data class Archive(val puzzleId: String, val day: LocalDate? = null) : Route
    /** The levels list; [level] shows that level's five boards instead. */
    data class Levels(val level: Int? = null) : Route
    /**
     * [fromArchive]: back returns to the puzzle's archive, at this day, rather than Home.
     * [level]: a board of that level, seeded from its plan (then [day] is null and [nonce] unused).
     */
    data class Play(
        val puzzleId: String,
        val difficulty: Difficulty,
        val day: LocalDate?,
        val nonce: Long = 0L,
        val fromArchive: Boolean = false,
        val level: Int? = null,
    ) : Route
}

/** Where back goes from [r]: the screen it was opened from. One level's boards go back to the list of levels. */
internal fun backFrom(r: Route): Route = when {
    r is Route.Achievements && !r.fromHome -> Route.Stats
    r is Route.Play && r.fromArchive -> Route.Archive(r.puzzleId, r.day)
    r is Route.Levels && r.level != null -> Route.Levels()
    else -> Route.Home
}

/** A route as one string, for rotation and process death. */
private val RouteSaver = Saver<Route, String>(save = { encodeRoute(it) }, restore = { decodeRoute(it) })

/** [RouteSaver]'s format. A field is only ever added at the end, so a string written before it existed still reads. */
internal fun encodeRoute(r: Route): String =
    when (r) {
        Route.Home -> "home"
        Route.Stats -> "stats"
        Route.Settings -> "settings"
        is Route.Achievements -> "achievements|${r.fromHome}"
        is Route.Archive -> "archive|${r.puzzleId}|${r.day ?: ""}"
        is Route.Levels -> "levels|${r.level ?: ""}"
        is Route.Play -> "play|${r.puzzleId}|${r.difficulty.name}|${r.day ?: ""}|${r.nonce}|${r.fromArchive}|${r.level ?: ""}"
    }

internal fun decodeRoute(text: String): Route {
    val f = text.split('|')
    return when (f[0]) {
        "stats" -> Route.Stats
        "settings" -> Route.Settings
        "achievements" -> Route.Achievements(f.getOrNull(1) == "true")
        // The fields after the first few came later: a string saved before them still decodes.
        "archive" -> Route.Archive(f[1], f.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse))
        "levels" -> Route.Levels(f.getOrNull(1)?.toIntOrNull())
        "play" -> Route.Play(
            puzzleId = f[1],
            difficulty = Difficulty.valueOf(f[2]),
            day = f[3].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            nonce = f[4].toLong(),
            fromArchive = f.getOrNull(5) == "true",
            level = f.getOrNull(6)?.toIntOrNull(),
        )
        // Anything else, such as "arrange|..." from the Arrange screen that the drag on the grid replaced, goes home.
        else -> Route.Home
    }
}

/**
 * The whole app, in its theme. [startAt] is where it opens — Home unless something asked for a
 * particular screen, which on the web is a `?puzzle=` link. [startMode] is the home grid's mode:
 * Daily on every launch, except where the web's render harness asks for another (`?mode=`).
 *
 * Nothing is drawn until the light/dark choice is known, so a player who picked Dark on a light
 * phone never sees one light frame. Saveable, so a rotation does not wait for the store again; it
 * is read once, since Settings (below) is the only thing that changes it.
 */
@Composable
fun DaybookApp(startAt: Route = Route.Home, startMode: LaunchMode = LaunchMode.DAILY) {
    val launchFile = rememberKeyValueStore(KeyValueStore.LAUNCH)
    val launchPrefs = remember(launchFile) { LaunchPreferences(launchFile) }
    val scope = rememberCoroutineScope()
    var appearance by rememberSaveable { mutableStateOf<Appearance?>(null) }
    LaunchedEffect(Unit) { if (appearance == null) appearance = launchPrefs.appearance.first() }
    val current = appearance ?: return
    DaybookTheme(current) {
        DaybookScreens(startAt, startMode, current) { picked ->
            appearance = picked
            scope.launch { launchPrefs.setAppearance(picked) }
        }
    }
}

@Composable
private fun DaybookScreens(startAt: Route, startMode: LaunchMode, appearance: Appearance, onAppearance: (Appearance) -> Unit) {
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
    // The puzzle and tier just solved, for its home tile's mark to pop in on the way back (LocalJustSolved). Never stored.
    var justSolved by remember { mutableStateOf<Pair<String, Difficulty>?>(null) }

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
    val storedShowTimer by launchPrefs.showTimer.collectAsState(initial = false)
    LaunchedEffect(Unit) { launchPrefs.settleShowTimer { store.hasPlayed() } }
    // Null until known, so a returning player never sees a frame of the welcome and a new one never sees the grid first.
    var welcome by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { welcome = launchPrefs.needsWelcome { store.hasPlayed() } }
    var pickedShowTimer by remember { mutableStateOf<Boolean?>(null) }
    val showTimer = pickedShowTimer ?: storedShowTimer
    val storedPlaySound by launchPrefs.playSound.collectAsState(initial = true)
    var pickedPlaySound by remember { mutableStateOf<Boolean?>(null) }
    val playSound = pickedPlaySound ?: storedPlaySound
    // Armed while the switch is on (the web wakes its audio on the first taps); a no-op when off.
    val playSolveSound = rememberSolveSoundPlayer(playSound)
    val storedVibrate by launchPrefs.vibrate.collectAsState(initial = true)
    var pickedVibrate by remember { mutableStateOf<Boolean?>(null) }
    val vibrate = pickedVibrate ?: storedVibrate
    val haptics = rememberHaptics(vibrate)
    val reducedMotion = rememberReducedMotion()
    // Saveable rather than stored: it survives rotation and process death, but a cold start comes
    // back to Daily, because that is what the app is for.
    var mode by rememberSaveable { mutableStateOf(startMode) }

    // Null until the store answers: a level board's seed depends on the pool size its level was opened with,
    // so the play route waits for it rather than deal a board from a guess.
    val loadedLevels by store.levelProgress.collectAsState(initial = null)
    val levels = loadedLevels ?: LevelProgress.FRESH

    /** Opens [level] (recording its pool size the first time) and plays [puzzleId], or its first unsolved board. */
    fun playLevel(level: Int, puzzleId: String?) {
        scope.launch {
            val plan = Levels.plan(level, store.openLevel(level))
            val solved = levels.solved(level)
            val board = plan.firstOrNull { it.puzzleId == puzzleId }
                ?: plan.firstOrNull { it.puzzleId !in solved && PuzzleRegistry.byId(it.puzzleId) != null }
                ?: plan.first()
            mode = LaunchMode.LEVELS
            route = Route.Play(board.puzzleId, board.difficulty, null, level = level)
        }
    }

    // The grid's order (PuzzleOrder). The solves are read once a day, not collected: a tile must not move under the
    // player's finger because the game they just left was solved. An empty stored order means most played first.
    val ids = remember { PuzzleOrder.gentlest(PuzzleRegistry.all.map { it.id }) }
    val playedBasis by produceState<List<Completion>?>(null, today) { value = store.completions.first() }
    val mostPlayed = remember(playedBasis, today) { PuzzleOrder.mostPlayed(playedBasis.orEmpty(), today, ids) }
    val pick = remember(playedBasis, today) { PuzzleOrder.pick(playedBasis.orEmpty(), today) }
    val storedOrder by launchPrefs.puzzleOrder.collectAsState(initial = emptyList())
    var pickedOrder by remember { mutableStateOf<List<String>?>(null) }
    val customOrder = pickedOrder ?: storedOrder
    val puzzles = remember(customOrder, mostPlayed) {
        (if (customOrder.isEmpty()) mostPlayed else PuzzleOrder.arranged(customOrder, ids)).mapNotNull(PuzzleRegistry::byId)
    }
    fun setOrder(order: List<String>) {
        pickedOrder = order
        scope.launch { launchPrefs.setPuzzleOrder(order) }
    }

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
    PlatformBackHandler(enabled = route != Route.Home) { route = backFrom(route) }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion, LocalHaptics provides haptics) {
    // A short fade between screens (none under reduced motion). Each route is its own keyed content, so a screen's
    // rememberSaveable state is kept across a rotation as before; the screen going out stays composed for the fade.
    // On the page colour: fading two screens over the bare window would dim the middle of the fade.
    Crossfade(route, Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), animationSpec = if (reducedMotion) snap() else tween(ROUTE_FADE_MS), label = "route") { current ->
    when (current) {
        Route.Home -> when (welcome) {
            null -> Unit
            true -> Welcome(onStart = {
                welcome = false
                scope.launch { launchPrefs.setWelcomed() }
            })
            false -> {
            // Nothing on Android. On the web, today's boards are generated while the grid is being
            // read, so the tap that opens one does not have to wait for it; see the platform seam.
            LaunchedEffect(today, difficulty) { prepareBoards(today, difficulty) }
            // The board each tile would open, by save key; then only those saves are read (ProgressStore.inProgress).
            // In Levels mode, the current level's five boards, at the tier the level gives each.
            val levelBoards = if (mode == LaunchMode.LEVELS) loadedLevels?.let { it.plan(it.current) } else null
            val tileKeys by produceState(emptyMap<String, String>(), today, difficulty, mode, levelBoards) {
                value = if (mode == LaunchMode.LEVELS) {
                    levelBoards.orEmpty().associate { savedGameKey(it.puzzleId, it.difficulty, it.seed) to it.puzzleId }
                } else PuzzleRegistry.all.mapNotNull { p ->
                    val seed = when (mode) {
                        LaunchMode.RANDOM -> DailySeed.randomSeed(
                            p.id, difficulty, store.currentRandomNonce(p.id, difficulty) ?: return@mapNotNull null,
                        )
                        else -> DailySeed.seedFor(today, p.id, difficulty)
                    }
                    savedGameKey(p.id, difficulty, seed) to p.id
                }.toMap()
            }
            val savesInProgress by remember(tileKeys) { store.inProgress(tileKeys.keys) }.collectAsState(initial = emptySet())
            // Handed down as a state read in the tile itself: passed as a plain set it reached the tile stale on the web
            // about half the time when it landed during the first frames (the pick kept "Try me!" over its saved game).
            val inProgress = remember { derivedStateOf { savesInProgress.mapNotNull(tileKeys::get).toSet() } }
            // The most recently saved of them, for the "Continue" row; a key's second field is its tier (savedGameKey).
            val resume = remember {
                derivedStateOf {
                    savesInProgress.firstNotNullOfOrNull { key ->
                        PuzzleRegistry.byId(tileKeys[key] ?: return@firstNotNullOfOrNull null)?.let { it to Difficulty.fromKey(key.split('|')[1]) }
                    }
                }
            }
            // The tick pop is for the first Home after the solve only.
            DisposableEffect(Unit) { onDispose { justSolved = null } }
            CompositionLocalProvider(LocalJustSolved provides justSolved) {
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
                        LaunchMode.LEVELS -> playLevel(levels.current, puzzleId)
                        // The tier's current random board: resumed until solved or replaced in the game.
                        LaunchMode.RANDOM -> scope.launch {
                            route = Route.Play(puzzleId, difficulty, null, store.randomNonce(puzzleId, difficulty))
                        }
                    }
                },
                levels = levels,
                onLevels = { route = Route.Levels() },
                onArchive = { puzzleId -> route = Route.Archive(puzzleId) },
                onStats = { route = Route.Stats },
                onAchievements = { route = Route.Achievements(fromHome = true) },
                onSettings = { route = Route.Settings },
                pick = pick,
                puzzles = puzzles,
                inProgress = { inProgress.value },
                customOrder = customOrder.isNotEmpty(),
                onOrder = ::setOrder,
                resume = { resume.value },
            )
            }
            }
        }

        Route.Stats -> StatsScreen(
            today = today,
            completions = completions,
            keptCards = store.keptCards.collectAsState(initial = emptyList()).value,
            puzzles = puzzles,
            onAchievements = { route = Route.Achievements() },
            onBack = { route = Route.Home },
        )

        is Route.Achievements -> AchievementsScreen(
            today = today,
            completions = completions,
            onBack = { route = backFrom(current) },
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
            vibrate = vibrate,
            onVibrate = { on ->
                pickedVibrate = on
                scope.launch { launchPrefs.setVibrate(on) }
            },
            appearance = appearance,
            onAppearance = onAppearance,
            customOrder = customOrder.isNotEmpty(),
            onResetOrder = { setOrder(emptyList()) },
            onBack = { route = Route.Home },
        )

        is Route.Levels -> LevelsScreen(
            progress = levels,
            selected = current.level,
            onSelect = { route = Route.Levels(it) },
            onPlay = { level, puzzleId -> playLevel(level, puzzleId) },
            onBack = { route = backFrom(current) },
        )

        is Route.Archive -> ArchiveScreen(
            puzzleId = current.puzzleId,
            today = today,
            completions = completions,
            onPlay = { day, difficulty ->
                route = Route.Play(current.puzzleId, difficulty, day, fromArchive = true)
            },
            inProgress = { day ->
                val keys = Difficulty.entries.associateBy { savedGameKey(current.puzzleId, it, DailySeed.seedFor(day, current.puzzleId, it)) }
                store.inProgress(keys.keys).map { saved -> saved.mapNotNullTo(HashSet(), keys::get) }
            },
            openAt = current.day,
            onBack = { route = Route.Home },
        )

        is Route.Play -> {
            val puzzle = PuzzleRegistry.byId(current.puzzleId)
            val level = current.level
            // A level board's tier and seed come from its level's plan, read with the pool size it was opened with.
            val levelBoard = level?.let { n -> loadedLevels?.plan(n)?.firstOrNull { it.puzzleId == current.puzzleId } }
            if (puzzle == null || (level != null && loadedLevels != null && levelBoard == null)) {
                // A saved route can name a puzzle that no longer exists. Navigating has to happen
                // in an effect rather than inline: writing state while composing is what starts a
                // recomposition loop.
                LaunchedEffect(current.puzzleId) { route = Route.Home }
            } else if (level == null || levelBoard != null) {
                val difficulty = levelBoard?.difficulty ?: current.difficulty
                val seed = when {
                    levelBoard != null -> levelBoard.seed
                    current.day != null -> DailySeed.seedFor(current.day, puzzle.id, difficulty)
                    else -> DailySeed.randomSeed(puzzle.id, difficulty, current.nonce)
                }
                val gameKey = savedGameKey(puzzle.id, difficulty, seed)
                val options = if (level != null) {
                    // The board just solved is counted by the options themselves, since it reaches the store a moment after the win.
                    levelNextOptions(level, levels.plan(level), levels.solved(level), puzzle.id, levels.current)
                } else {
                    // Only what the store already holds, for the same reason.
                    nextOptions(daily = current.day != null, tier = difficulty, doneTiers = tiersDoneOn(completions, puzzle.id, current.day))
                }
                // Keyed on the board: a level's next step goes straight to another puzzle, and PlayScreen's
                // produced board would otherwise be the old puzzle's state for a frame (a ClassCastException in Board).
                CompositionLocalProvider(LocalChime provides playSolveSound) { key(puzzle.id, seed) {
                PlayScreen(
                    puzzle = puzzle,
                    difficulty = difficulty,
                    day = current.day,
                    seed = seed,
                    restore = { store.savedGame(gameKey) },
                    persist = { game ->
                        if (game == null) store.clearSavedGame(gameKey)
                        else store.saveGame(gameKey, game)
                    },
                    // A daily and a level board have only the one board each.
                    onNewGame = if (current.day != null || level != null) null else {
                        {
                            scope.launch {
                                store.clearSavedGame(gameKey)
                                route = Route.Play(
                                    puzzle.id, difficulty, null,
                                    store.newRandomNonce(puzzle.id, difficulty),
                                )
                            }
                        }
                    },
                    onSolved = { seconds, hints ->
                        // The slot moves on, so leaving and tapping the tile again is a new board.
                        if (current.day == null && level == null) scope.launch { store.newRandomNonce(puzzle.id, difficulty) }
                        // PlayScreen calls this once per solve (its `recorded` flag survives
                        // recreation), so the chime cannot repeat on a rotation.
                        playSolveSound()
                        // Only a solve that marks a tile: today's daily or a level board, never a random or archive one.
                        if (current.day == today || level != null) justSolved = puzzle.id to difficulty
                        scope.launch {
                            // A level solve is also a practice solve (day = null), for totals and best times.
                            if (level != null) store.recordLevelSolve(LevelSolve(level, puzzle.id, seconds, hints))
                            store.record(
                                Completion(
                                    puzzleId = puzzle.id,
                                    difficulty = difficulty,
                                    day = current.day,
                                    seconds = seconds,
                                    hints = hints,
                                )
                            )
                        }
                    },
                    praiseFor = { seconds, hints, state, log, taught ->
                        val solve = Completion(puzzle.id, difficulty, current.day, seconds, hints)
                        // The streak is told only for today's own daily: an archive day, a level or a random board does not extend it.
                        val streak = if (current.day == today) Stats.streak(completions + solve, today).current else 0
                        // Asked of the board only when no hint was opened: a hint solve names what the hints showed.
                        val needed = if (hints == 0) puzzle.neededTechnique(state) else null
                        val praise = finishPraise(completions, solve, today, puzzle.displayName, taught, needed)
                        val card = shareCard(
                            puzzle, difficulty, current.day?.let { formatDate(it, SHARE_DATE) },
                            formatClock(seconds), streak, state, log, hints, level,
                        )
                        // Kept for the stats screen: a random board's save goes when it is solved, and with it this card.
                        scope.launch {
                            val kept = KeptCard(
                                puzzle.id, difficulty, best = true, seconds, hints, card,
                                undos = PlayLog.played(log).count { it.undone },
                                bonus = (state as? WordWheelState)?.bonus?.size ?: 0,
                            )
                            store.keepCard(kept)
                            if (current.day == null && level == null) store.keepCard(kept.copy(best = false))
                        }
                        praise.copy(
                            title = if (level != null && levelFinishedBy(levels, level, puzzle.id)) "Level $level done" else praise.title,
                            share = card,
                        )
                    },
                    options = options,
                    onNext = { option ->
                        val tier = option.difficulty
                        when {
                            option.kind == NextKind.LEVEL -> playLevel(option.level ?: levels.current, option.puzzleId)
                            tier == null -> route = backFrom(current)
                            // The same date, so on an archive day it is that past date's board (and back is still the archive).
                            option.kind == NextKind.DAILY -> route = current.copy(difficulty = tier)
                            // A solved random board has already had its slot renewed (see onSolved).
                            else -> scope.launch {
                                route = Route.Play(puzzle.id, tier, null, store.randomNonce(puzzle.id, tier))
                            }
                        }
                    },
                    tutorialOffered = tutorialsOffered?.let { puzzle.id in it },
                    onTutorialOffered = { scope.launch { store.markTutorialOffered(puzzle.id) } },
                    showTimer = showTimer,
                    level = level,
                    onBack = { route = backFrom(current) },
                )
                } }
            }
            // Otherwise a level board waits the moment it takes the store to say how its level was opened.
        }
    }
    }
    }
}

private const val ROUTE_FADE_MS = 200

/** Whether solving [puzzleId] is what finishes [level]: it was not done before, and is with this board. */
internal fun levelFinishedBy(progress: LevelProgress, level: Int, puzzleId: String): Boolean {
    if (level in progress.done) return false
    val solved = progress.solved(level) + puzzleId
    return progress.plan(level).all { it.puzzleId in solved || PuzzleRegistry.byId(it.puzzleId) == null }
}
