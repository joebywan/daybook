package com.joebywan.daybook.ui.play

import com.joebywan.daybook.ui.theme.BoardHues
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.joebywan.daybook.core.HighlightBounds
import com.joebywan.daybook.core.LocalHighlightBounds
import com.joebywan.daybook.ui.teach.HintPopover
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.plural
import com.joebywan.daybook.core.tierNotes
import com.joebywan.daybook.core.undone
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.data.fits
import com.joebywan.daybook.platform.BackButton
import com.joebywan.daybook.platform.GENERATION_ANIMATES
import com.joebywan.daybook.platform.LOADING_MESSAGE_DELAY_MS
import com.joebywan.daybook.platform.PlatformBackHandler
import com.joebywan.daybook.platform.formatClock
import com.joebywan.daybook.platform.formatDate
import com.joebywan.daybook.platform.generateBoard
import com.joebywan.daybook.platform.readyBoard
import com.joebywan.daybook.puzzles.PointersState
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.CappedFontScale
import com.joebywan.daybook.ui.LocalHaptics
import com.joebywan.daybook.ui.LocalBoardArrives
import com.joebywan.daybook.ui.LocalMoveCount
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.LocalSolveCount
import com.joebywan.daybook.ui.LocalUndoCount
import com.joebywan.daybook.ui.SOLVE_WAVE_MS
import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.teach.HintPanel
import com.joebywan.daybook.ui.teach.WatchHint
import com.joebywan.daybook.ui.teach.hintKeys
import com.joebywan.daybook.ui.teach.buttonLabel
import com.joebywan.daybook.ui.teach.rememberHintSession
import com.joebywan.daybook.ui.tutorial.LocalChime
import com.joebywan.daybook.ui.tutorial.TutorialRunner
import com.joebywan.daybook.ui.tutorial.autoWalkthrough
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/** A `DateTimeFormatter` pattern; see `formatDate` in the platform seam. */
private const val PLAY_DATE = "d MMM yyyy"

/** Long enough that holding down a Sudoku digit is one write rather than a dozen. */
private const val SAVE_DEBOUNCE_MS = 500L

/**
 * Rotation goes through the same JSON as the on-disk store: one format to get right, and a board
 * that survives a turn of the phone is proof the stored one will load too.
 */
private val SavedGameSaver = Saver<SavedGame, String>(
    save = { it.encode() },
    restore = SavedGame::decode,
)

/** The finish frame across a rotation: [praiseFor] cannot be asked again once the solve is recorded. UI state, never saved to disk. */
internal val PraiseSaver = Saver<Praise, String>(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<Praise>(it) },
)

@Composable
fun PlayScreen(
    puzzle: PuzzleType,
    difficulty: Difficulty,
    day: LocalDate?,
    seed: Long,
    restore: suspend () -> SavedGame?,
    persist: suspend (SavedGame?) -> Unit,
    onSolved: (seconds: Int, hints: Int) -> Unit,
    praiseFor: (seconds: Int, hints: Int, state: PuzzleState, log: String, taught: List<String>) -> Praise,
    options: List<NextOption>,
    onNext: (NextOption) -> Unit,
    onBack: () -> Unit,
    onNewGame: (() -> Unit)?,
    tutorialOffered: Boolean?,
    onTutorialOffered: () -> Unit,
    /** Whether this puzzle has ever been solved, or has a game saved on any board; null until the store has answered. */
    playedBefore: Boolean?,
    showTimer: Boolean,
    /** The level this board belongs to, if any: the subtitle says so where a daily shows its date. */
    level: Int?,
    /** A hint explained this technique (`Deduction.technique`), for the techniques page. */
    onTaught: (String) -> Unit,
    /** Opens this puzzle's Training (`docs/TRAINING_SPEC.md`) from the rules sheet; null for a puzzle without one. */
    onTrain: (() -> Unit)? = null,
) {
    // The harder boards take hundreds of milliseconds to generate, which would freeze the frame if
    // it happened during composition. Android runs it off the main thread; the web has only one
    // thread, so there it waits until "Setting out" is on screen first (see the platform seam).
    // A board the platform already holds (the web makes the day's in advance) is there on the first
    // frame, so a tap on it never shows the loading screen at all.
    val generated by produceState(readyBoard(puzzle, seed, difficulty), puzzle.id, seed, difficulty) {
        value = generateBoard(puzzle, seed, difficulty)
    }

    val initial = generated
    if (initial == null) {
        DealingBoard(puzzle)
        return
    }
    val scheme = MaterialTheme.colorScheme

    val pristine = remember(initial) { SavedGame(initial) }
    var game by rememberSaveable(initial, stateSaver = SavedGameSaver) { mutableStateOf(pristine) }
    // Saved alongside the game: without it, rotating a finished board would record the win twice.
    var recorded by rememberSaveable(initial) { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    // Saved too: rotating right after a solve would otherwise leave a bare frame. A reopened solved board shows the plain frame, with its Share card.
    var praise by rememberSaveable(initial, stateSaver = PraiseSaver) { mutableStateOf(finishPreview ?: Praise()) }
    // Finished-frame state: hidden to inspect the board, and confetti played once. Never PuzzleState.
    var resultsHidden by rememberSaveable(initial) { mutableStateOf(false) }
    var confettiPlayed by rememberSaveable(initial) { mutableStateOf(false) }
    // Set as soon as the store has been asked, and itself saved, so the answer that arrived before
    // the rotation is not thrown away by a second lookup afterwards.
    var askRestart by rememberSaveable(initial) { mutableStateOf(false) }
    var consulted by rememberSaveable(initial) { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    // The walkthrough draws over the game rather than replacing the route, so the board, its undo
    // stack and its clock are exactly where they were when the player comes back.
    var showTutorial by rememberSaveable(initial) { mutableStateOf(false) }
    val hasTutorial = puzzle.tutorial.isNotEmpty()
    val hintSession = rememberHintSession(initial)
    // Undoable: the cleared board goes on the history, so one mis-tap beside Undo costs nothing. Pointers has no Undo.
    fun restart() {
        hintSession.clear()
        game = if (puzzle.offersUndo) {
            val history = game.history + game.state
            game.copy(state = initial, history = history, restarts = game.restarts + history.size, log = game.log + PlayLog.RESTART)
        } else {
            game.copy(state = initial, history = emptyList(), log = game.log + PlayLog.RESTART)
        }
    }
    val hintScope = rememberCoroutineScope()
    // The offer returns on every visit until the player opens the walkthrough or solves this puzzle once, at any tier:
    // either one is recorded through [onTutorialOffered] (the store's set now means "no longer needs offering"). Saved, so a
    // rotation keeps the line it already showed.
    var offering by rememberSaveable(initial) { mutableStateOf(false) }
    LaunchedEffect(tutorialOffered) {
        if (tutorialOffered == false && hasTutorial) offering = true
    }
    fun openTutorial() {
        showTutorial = true
        if (offering || tutorialOffered == false) onTutorialOffered()
        offering = false
    }
    // A first board starts the walkthrough by itself (autoWalkthrough), once the store has said what was solved and seen
    // and the saved game has been read. Asked once per board (saved), and recorded as it opens, so it never comes back.
    var autoAsked by rememberSaveable(initial) { mutableStateOf(false) }
    LaunchedEffect(consulted, tutorialOffered, playedBefore) {
        if (autoAsked || !consulted || tutorialOffered == null || playedBefore == null) return@LaunchedEffect
        autoAsked = true
        val played = game.state.moves + game.restarts.size + game.history.size
        if (hasTutorial && finishPreview == null && autoWalkthrough(playedBefore, tutorialOffered, level != null, played)) openTutorial()
    }
    // Arrows has no Undo, so its Restart is asked first once a move would be lost.
    var askStartOver by rememberSaveable(initial) { mutableStateOf(false) }

    val state = game.state
    val seconds = game.seconds
    val finished = state.solved || finishPreview != null
    var reopenedSolved by rememberSaveable(initial) { mutableStateOf(false) }

    // Reads `game` rather than the values unpacked above: this runs from effects that outlive the
    // composition that started them, where those locals would be frozen at their first value.
    suspend fun flush() {
        val current = game
        when {
            // A reopened solve is already stored; rewriting it would only stretch its day.
            current.state.solved && reopenedSolved -> Unit
            // A finished daily is kept for the day so it can be looked at, not replayed; practice is not.
            current.state.solved -> persist(if (day != null) current.copy(history = emptyList(), restarts = emptyList()) else null)
            // Opened and never touched. Nothing to record, and nothing of anyone's to clear.
            current == pristine -> Unit
            // Only ever selected a cell, or restarted past Undo's reach: no longer a game in progress.
            current.state.moves == 0 && current.restarts.isEmpty() -> persist(null)
            else -> persist(current)
        }
    }

    LaunchedEffect(initial) {
        if (consulted) return@LaunchedEffect
        val stored = restore()
        // Reading the store takes a moment; a tap that landed in the meantime outranks it.
        // A stored game that does not fit this board (a crafted backup) is dropped: the board starts fresh.
        if (stored != null && game == pristine && stored.fits(initial)) {
            game = stored
            if (stored.state.solved) {
                // Already counted; just the board, with the finish frame out of the way.
                reopenedSolved = true
                recorded = true
                resultsHidden = true
                confettiPlayed = true
            }
        }
        consulted = true
    }

    LaunchedEffect(initial) {
        while (true) {
            delay(1000)
            if (!game.state.solved && !showTutorial) game = game.copy(seconds = game.seconds + 1)
        }
    }

    // Deliberately watches the board and not the clock: ticking the seconds is not progress worth
    // a write, so a game abandoned without touching it keeps the time it had at the last move.
    LaunchedEffect(initial) {
        try {
            snapshotFlow { game.state }.collectLatest {
                delay(SAVE_DEBOUNCE_MS)
                flush()
            }
        } finally {
            // The screen is going away, possibly with the process; the write has to outlive it.
            withContext(NonCancellable) { flush() }
        }
    }

    LaunchedEffect(state.solved) {
        if (!state.solved) return@LaunchedEffect
        if (tutorialOffered == false) onTutorialOffered()
        if (!recorded) {
            recorded = true
            // Asked before onSolved: the history must not yet contain this solve.
            praise = praiseFor(seconds, game.hints, game.state, game.log, game.taught)
            onSolved(seconds, game.hints)
            haptics.success()
        }
        hintSession.clear()
    }

    // Counts moves, undos and solving moves for the boards' move feel (LocalMoveCount and its neighbours); not saved, so a
    // rotation or a reopened board plays nothing.
    var pushes by remember { mutableIntStateOf(0) }
    var undos by remember { mutableIntStateOf(0) }
    var solves by remember { mutableIntStateOf(0) }
    // Saved: the board's squares fade in when it first shows, not again on a rotation.
    var arrived by rememberSaveable(initial) { mutableStateOf(false) }
    LaunchedEffect(initial) {
        withFrameNanos { }
        arrived = true
    }
    // The finished frame waits for the solve wave to cross the board, when a move solved it here; saved, so a rotation
    // shows it at once.
    val still = LocalReducedMotion.current
    var revealed by rememberSaveable(initial) { mutableStateOf(false) }
    LaunchedEffect(finished) {
        if (finished && !revealed) {
            if (solves > 0 && !still) delay(SOLVE_WAVE_MS.toLong())
            revealed = true
        }
    }

    // Reads through `game` rather than the unpacked locals so that two taps landing in the same
    // frame stack up properly instead of pushing the same board twice.
    fun push(next: PuzzleState) {
        val prev = game.state
        pushes++
        if (next.solved && !prev.solved) solves++
        val step = PlayLog.step(puzzle, prev, next)
        game = game.copy(state = next, history = game.history + prev, log = game.log + step)
        // A buzz for a move that lands (not a selection or a typed letter), a firmer one for a bump; a solve gets
        // its own, with the chime, so the move that solves stays quiet here.
        when {
            next.solved -> Unit
            step == PlayLog.WRONG.toString() ||
                (next is PointersState && prev is PointersState && next.bumps.size > prev.bumps.size) ||
                (next is MinesState && prev is MinesState && next.bumps.size > prev.bumps.size) -> haptics.bump()
            step.isNotEmpty() -> haptics.tick()
        }
    }

    // One logged hint per counted hint, exactly where the count goes up; a deduction's technique beside it, for the praise.
    fun countHint(opened: Deduction? = null) {
        val taught = if (opened == null || opened.mistake) game.taught else game.taught + opened.technique
        if (opened != null && !opened.mistake) onTaught(opened.technique)
        game = game.copy(hints = game.hints + 1, log = game.log + PlayLog.HINT, taught = taught)
    }

    WatchHint(hintSession, puzzle, state, onReached = LocalChime.current)

    /**
     * Hint, for a puzzle that teaches: nudge, then explain, then — only on a third, explicit tap —
     * "Show me". **One hint is counted per deduction opened**, on the first tap; the explanation
     * and "Show me" for the same deduction are free. A player who needed the nudge was helped,
     * and charging again for reading the reasoning would punish the very thing hints are for.
     * Puzzles that do not teach keep the old hint: the move goes straight on and costs one.
     */
    fun onHint() {
        val asked = game.state
        hintScope.launch {
            val taught = hintSession.tap(
                puzzle, asked,
                onOpened = ::countHint,
                onApply = ::push,
            )
            // The old hint lands on the board, so only on the board it was asked about.
            if (!taught && game.state == asked) {
                puzzle.hint(asked)?.let {
                    countHint()
                    push(it)
                }
            }
        }
    }

    fun undo() {
        // A hint reasoned from a board that has just been taken back may lean on a king
        // that is no longer there.
        hintSession.clear()
        undone(puzzle, game.state, game.history)?.let {
            // A restart is taken back once the history no longer reaches the board it cleared; logged as an undo each.
            val restarts = game.restarts.filter { r -> r <= it.history.size }
            // Otherwise logged only when it takes back a move (not a typed letter, a peg, or only a selection).
            val back = when {
                restarts.size < game.restarts.size -> PlayLog.UNDO.toString().repeat(game.restarts.size - restarts.size)
                PlayLog.step(puzzle, it.state, game.state).isNotEmpty() -> PlayLog.UNDO.toString()
                else -> ""
            }
            game = game.copy(state = it.state, history = it.history, restarts = restarts, log = game.log + back)
            undos++
        }
    }

    if (showTutorial) {
        PlatformBackHandler(enabled = true) { showTutorial = false }
        TutorialRunner(
            puzzle = puzzle,
            onClose = { showTutorial = false },
            // Painted before the insets are taken, so the status bar strip is the page colour
            // rather than whatever the window shows through it.
            modifier = Modifier.background(scheme.background).windowInsetsPadding(WindowInsets.safeDrawing),
        )
        return
    }

    // Below 640dp (a phone browser with its toolbars showing) the toolbar's padding gives way, to
    // leave the board the room. The hint is a popover over the content, never a slot in this
    // column, so nothing in it changes size when a hint opens, the offer shows, or the puzzle is
    // solved.
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val buttonPad = if (windowHeight < with(LocalDensity.current) { 640.dp.toPx() }) 10.dp else 22.dp
    val highlightBounds = remember { HighlightBounds() }
    LaunchedEffect(hintSession.deduction) {
        highlightBounds.named = hintSession.deduction?.let { BoardHighlight(soft = it.focus + it.cited + it.targets) } ?: BoardHighlight.None
    }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var contentTop by remember { mutableStateOf(0f) }
    var boardTop by remember { mutableStateOf(0f) }
    var toolbarTop by remember { mutableStateOf(0f) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            // Ctrl/Cmd+Z, H and Esc, as wherever a board is played. None once solved.
            .hintKeys(!state.solved, hintSession, if (puzzle.offersHints) ::onHint else null, if (puzzle.offersUndo) ::undo else null)
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
    ) {
    // A wide window (a phone on its side, a tablet, a desktop browser) puts the board beside the title, clock and
    // toolbar instead of under them, when that gives the board more room than stacking would. A guess from the
    // window alone, never from what the board or a hint is doing, so nothing in play can flip it.
    val wide = minOf(maxWidth - SidePanel, maxHeight) > minOf(maxWidth, maxHeight - StackedChrome)
    PlayLayout(
        wide,
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onGloballyPositioned { contentTop = it.positionInWindow().y }
    ) {
        // Title at the start, the one remaining action at the end. Centring what is left over
        // after removing the arrow would have hung the title 44dp off true, and padding the gap
        // back out would keep reserving room for a control that no longer exists.
        // Held to the chrome cap: at 2x the date wrapped and the header took the board's room.
        CappedFontScale {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Nothing on Android, where the system back button is the way out.
                BackButton(onBack)
                Column(Modifier.weight(1f)) {
                    Text(
                        puzzle.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onBackground,
                    )
                    Text(
                        buildString {
                            append(difficulty.label)
                            append(" · ")
                            append(level?.let { "Level $it" } ?: day?.let { formatDate(it, PLAY_DATE) } ?: "Random")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable { showRules = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, "How to play", tint = scheme.onSurfaceVariant)
                }
            }
        }

        // The clock, and on a first visit the walkthrough offer drawn over it until the first move.
        // Over the clock rather than under the board: it takes no height from the board, and covers
        // nothing the player plays on. The row keeps the clock's own height either way.
        Box(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Hidden by the player's setting: still laid out, so the row, the board and the hint
            // popover (placed from this row's bottom) are exactly where they would be with it.
            // The time keeps counting and is shown on the solved card. It sets the row's height, so it is capped too.
            CappedFontScale {
                Text(
                    text = formatClock(seconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = if (showTimer) Modifier else Modifier.alpha(0f).clearAndSetSemantics { },
                )
            }
            if (offering && game.history.isEmpty() && !hintSession.active && !state.solved) {
                Box(Modifier.matchParentSize().background(scheme.background), contentAlignment = Alignment.Center) {
                    // What the puzzle is about, then the offer: two small lines (the longest tagline fits one at 360dp),
                    // inside the clock row's height and its padding, so the board never moves.
                    Text(
                        "${puzzle.tagline}\nOne-minute walkthrough",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.joebywan.daybook.ui.theme.BoardHues.textOn(Color(puzzle.accent), scheme.background),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .requiredHeight(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { openTutorial() }
                            .padding(horizontal = 12.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
        }

        Box(
            Modifier.onGloballyPositioned { boardTop = it.positionInWindow().y },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalBoardHighlight provides hintSession.highlight,
                LocalHighlightBounds provides highlightBounds,
                LocalMoveCount provides pushes,
                LocalUndoCount provides undos,
                LocalSolveCount provides solves,
                LocalBoardArrives provides !arrived,
            ) {
                puzzle.Board(
                    state = state,
                    onState = ::push,
                    interactive = !state.solved,
                )
            }
        }

        // The toolbar is always laid out; once solved it is hidden and the solved card is drawn
        // over exactly the same box, so the board above never changes size on completion.
        Box(Modifier.onGloballyPositioned { toolbarTop = it.positionInWindow().y }) {
            val solved = state.solved
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = buttonPad)
                    .then(if (solved) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (puzzle.offersUndo) {
                    ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", Modifier.weight(1f)) {
                        if (!solved) undo()
                    }
                }
                ToolButton(Icons.Default.Refresh, "Restart", Modifier.weight(1f)) {
                    if (solved) return@ToolButton
                    // A random game can be replaced as well as restarted; a daily has only the one board.
                    when {
                        onNewGame != null -> askRestart = true
                        !puzzle.offersUndo && state.moves > 0 -> askStartOver = true
                        else -> restart()
                    }
                }
                if (puzzle.offersHints) {
                    ToolButton(Icons.Default.AutoAwesome, hintSession.buttonLabel(), Modifier.weight(1f)) {
                        if (!solved) onHint()
                    }
                }
            }
        }

        // Wide only: the hint sits in the side panel, between the clock and the toolbar, so it never covers the board.
        // With no hint open the space holds the rules, quietly; a hint takes it over rather than stacking on them.
        Box {
            if (wide && hintSession.active && !state.solved) {
                HintPanel(
                    hintSession, Color(puzzle.accent), ::onHint,
                    modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                    fill = false,
                )
            } else if (wide && !finished) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text("How to play", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    // With the keys: a wide window is the desktop, where they are most likely used and least likely guessed.
                    RulesList(puzzle, MaterialTheme.typography.bodySmall, scheme.onSurfaceVariant, gap = 6.dp)
                }
            }
        }
    }

    // In a wide window the frame and the pill keep to the side panel, beside the solved board rather than over it.
    val finishArea = if (wide) Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing).width(SidePanel)
        else Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)

    // The finish: a compact frame docked over the play screen. An overlay, so the board keeps
    // exactly the box it had; the toolbar above stays laid out (hidden) for the same reason.
    if (finished && revealed) {
        val accent = Color(puzzle.accent)
        // Tapping anywhere but a button hides the frame so the solved board can be looked at; the pill
        // brings it back. The scrim only exists while the frame is up, and a hidden frame covers nothing.
        if (!resultsHidden) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { resultsHidden = true })
        }
        AnimatedVisibility(
            visible = !resultsHidden,
            // Full-screen overlay: banner on top, buttons at the bottom, the centre open (the earned badge, if
            // any, sits there). Empty space passes taps to the scrim beneath, which hides the frame.
            modifier = finishArea.padding(vertical = 10.dp),
            enter = fadeIn(tween(300)),
            exit = ExitTransition.None,
        ) {
            FinishedFrame(
                seconds = seconds,
                hints = game.hints,
                praise = praise,
                // The milestones animate the first time the frame shows, as the confetti plays: once per finish.
                celebrate = !confettiPlayed && !still,
                accent = accent,
                options = options,
                onOption = onNext,
                onHide = { resultsHidden = true },
            )
        }
        if (resultsHidden) {
            ResultsPill(
                accent = accent,
                next = pillOption(options),
                done = options.last(),
                onShow = {
                    // A reopened solve's Share card, drawn from the saved board and log when asked for, so the
                    // streak it reads is from completions that have loaded by then.
                    if (reopenedSolved) praise = Praise(share = praiseFor(seconds, game.hints, state, game.log, game.taught).share)
                    resultsHidden = false
                },
                onOption = onNext,
                modifier = Modifier.align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing).then(if (wide) Modifier.width(SidePanel) else Modifier)
                    .padding(bottom = 10.dp),
            )
        }
        Confetti(played = confettiPlayed, full = praise.full, onPlayed = { confettiPlayed = true })
    }

    if (hintSession.active && !state.solved && !wide) {
        HintPopover(
            session = hintSession,
            accent = Color(puzzle.accent),
            onNext = ::onHint,
            highlight = highlightBounds.rect,
            keepClear = highlightBounds.keepClear,
            origin = overlayOrigin,
            // Where the popover may sit: from just under the clock (or, to clear a highlight, the
            // status bar) down to just above the toolbar, which it never covers.
            safeTop = contentTop,
            boardTop = boardTop,
            toolbarTop = toolbarTop,
            windowHeight = windowHeight,
        )
    }
    }


    if (askRestart && onNewGame != null) {
        AlertDialog(
            onDismissRequest = { askRestart = false },
            title = { Text("Start over?") },
            text = { Text("Clear this board and try it again, or deal a new random one.") },
            confirmButton = {
                TextButton(onClick = {
                    askRestart = false
                    // Back to the untouched board first, so leaving writes nothing under the old key.
                    game = pristine
                    onNewGame()
                }) { Text("New board") }
            },
            dismissButton = {
                TextButton(onClick = { askRestart = false; restart() }) { Text("Restart this one") }
            },
        )
    }

    if (askStartOver) {
        AlertDialog(
            onDismissRequest = { askStartOver = false },
            title = { Text("Start over?") },
            // Only Arrows has lives; Mines (no Undo either) ends at its first mine, so it says nothing of them.
            text = { Text(if (state is PointersState) "The board resets and your lives come back." else "The board goes back to how it began.") },
            confirmButton = { TextButton(onClick = { askStartOver = false; restart() }) { Text("Start over") } },
            dismissButton = { TextButton(onClick = { askStartOver = false }) { Text("Keep playing") } },
        )
    }

    if (showRules) {
        RulesDialog(
            "How to play ${puzzle.displayName}", puzzle, difficulty,
            onWalkthrough = if (hasTutorial) ({ showRules = false; openTutorial() }) else null,
            onTrain = onTrain?.let { train -> { showRules = false; train() } },
        ) { showRules = false }
    }
}

/**
 * Silent for [LOADING_MESSAGE_DELAY_MS], so a board that arrives straight away does not flash a
 * message. The box fills the screen either way and the board replaces it, so nothing resizes when
 * the board arrives. Where generation runs off the main thread ([GENERATION_ANIMATES]) a spinner
 * turns above the line; on the web the thread is busy generating, so a spinner would sit frozen
 * and is left out (and the message has to be painted *before* generating starts, which is why the
 * web's delay is zero: nothing can be painted once the board is underway).
 *
 * No header at all: the back arrow was the only thing in it, and a lone "How to play" button for
 * rules you cannot yet see would be worse than the empty strip it would fill. Back out of a board
 * still being dealt with the system back button, same as anywhere else.
 */
@Composable
private fun DealingBoard(puzzle: PuzzleType) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        var due by remember { mutableStateOf(LOADING_MESSAGE_DELAY_MS <= 0L) }
        LaunchedEffect(Unit) {
            delay(LOADING_MESSAGE_DELAY_MS)
            due = true
        }
        if (due) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (GENERATION_ANIMATES) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    "Setting out ${puzzle.displayName}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}


/** How long an extended streak takes to count up in the finished frame, and the scale a milestone's title stamps in from. */
private const val COUNT_MS = 600
private const val STAMP_FROM = 1.3f

/** The side panel's width in a wide window: the title, clock, hint and three tool buttons, with the walkthrough offer on one line. */
private val SidePanel = 360.dp

/** About what the title, clock and toolbar take from a stacked layout's height; only used to choose between the two. */
private val StackedChrome = 180.dp

/**
 * The play screen's four pieces, [content] in this order: title, clock, board, toolbar, then the hint slot. Stacked, it
 * is the column it always was (the board takes what the rest leaves, and the hint slot is not placed: the hint is a
 * popover). [wide] puts the first two at the top of a [SidePanel], the toolbar at its foot and the hint slot (the hint, else
 * the rules) between,
 * with the board filling the rest of the window. One layout for both, so a rotation keeps every board's own saved state
 * (Sudoku's notes mode, Nonogram's pen) where a Row/Column switch would compose the board somewhere new and lose it.
 */
@Composable
private fun PlayLayout(wide: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { parts, c ->
        val (title, clock, board, tools, slot) = parts
        val w = c.maxWidth
        val h = c.maxHeight
        val panel = if (wide) SidePanel.roundToPx().coerceAtMost(w) else w
        val loose = Constraints(maxWidth = panel, maxHeight = h)
        val t = title.measure(loose)
        val k = clock.measure(loose)
        val b = tools.measure(loose)
        val rest = (h - t.height - k.height - b.height).coerceAtLeast(0)
        if (wide) {
            val g = board.measure(Constraints.fixed(w - panel, h))
            val s = slot.measure(Constraints.fixed(panel, rest))
            layout(w, h) {
                t.placeRelative(0, 0)
                k.placeRelative(0, t.height)
                s.placeRelative(0, t.height + k.height)
                b.placeRelative(0, h - b.height)
                g.placeRelative(panel, 0)
            }
        } else {
            // As the Column with a weighted board was: the board gets exactly the height left, any width up to the window.
            val g = board.measure(Constraints(maxWidth = w, minHeight = rest, maxHeight = rest))
            layout(w, h) {
                t.placeRelative(0, 0)
                k.placeRelative(0, t.height)
                g.placeRelative(0, t.height + k.height)
                b.placeRelative(0, t.height + k.height + rest)
            }
        }
    }
}

/**
 * The finished overlay: a banner on top ("Congratulations!" or the streak title between two party poppers, the time,
 * one line, and the to-go line when nothing was earned), next-step buttons at the bottom (two columns; an odd last
 * tile takes the full row), and the centre left open so the solved board shows. Only when the solve earned
 * achievements does the centre hold a badge card. On a short screen the badge shrinks; the buttons never do.
 */
@Composable
private fun FinishedFrame(
    seconds: Int,
    hints: Int,
    praise: Praise,
    celebrate: Boolean,
    accent: Color,
    options: List<NextOption>,
    onOption: (NextOption) -> Unit,
    onHide: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // [celebrate]: an extended streak counts up to its new length, a milestone's title stamps in (from 130%, settling with
    // a small bounce). Otherwise, and under reduced motion, the final values at once. Drawn over the final title's own
    // box, so the banner never changes size while it runs.
    val count = remember(praise.streak) { Animatable(if (celebrate && praise.streak > 1) 1f else praise.streak.toFloat()) }
    val stamp = remember(praise.big) { Animatable(if (celebrate && praise.big) STAMP_FROM else 1f) }
    LaunchedEffect(praise.streak) { count.animateTo(praise.streak.toFloat(), tween(COUNT_MS, easing = FastOutSlowInEasing)) }
    LaunchedEffect(praise.big) { stamp.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    val title = praise.title ?: "Congratulations!"
    val shown = count.value.toInt()
    val counting = praise.streak > 0 && shown < praise.streak
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val badge = if (maxHeight < 640.dp) 44.dp else 72.dp
        val hide = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onHide)
        Column(
            Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp).widthIn(max = 380.dp).fillMaxWidth()
                .frameCard(scheme.surface).background(accent.copy(alpha = 0.22f)).then(hide)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PartyPopper(Modifier.size(36.dp))
                val style = if (praise.big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall
                val weight = if (praise.big) FontWeight.Bold else null
                val titleInk = BoardHues.onTint(accent, scheme.surface)
                Box(Modifier.weight(1f, fill = false).graphicsLayer { scaleX = stamp.value; scaleY = stamp.value }, contentAlignment = Alignment.Center) {
                    Text(title, style = style, color = titleInk, fontWeight = weight, maxLines = 1,
                        modifier = if (counting) Modifier.alpha(0f) else Modifier)
                    if (counting) Text(title.replaceFirst("${praise.streak}", "$shown"), style = style, color = titleInk, fontWeight = weight, maxLines = 1)
                }
                PartyPopper(Modifier.size(36.dp).graphicsLayer(scaleX = -1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    buildString {
                        append(formatClock(seconds))
                        if (hints > 0) append("  ·  ${plural(hints, "hint")}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                praise.share?.let { ShareChip(it, accent) }
                praise.setShare?.let { ShareChip(it, accent, praise.setShareLabel) }
            }
            praise.lines.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (praise.achievements.isEmpty()) praise.toGo?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            }
        }
        if (praise.achievements.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.Center).padding(horizontal = 32.dp).widthIn(max = 340.dp).fillMaxWidth()
                    .frameCard(scheme.surface).padding(horizontal = 12.dp, vertical = 10.dp),
            ) { ResultsBlock(praise, accent, badge, celebrate) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).widthIn(max = 380.dp).fillMaxWidth()
                .frameCard(scheme.surface).then(hide).padding(10.dp),
        ) {
            val main = mainOption(options)
            options.chunked(2).forEachIndexed { row, pair ->
                if (row > 0) Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { option ->
                        NextTile(option, option == main, accent, Modifier.weight(1f)) { onOption(option) }
                    }
                }
            }
        }
    }
}

private val FrameShape = RoundedCornerShape(20.dp)

/** One of the finished frame's raised cards. */
private fun Modifier.frameCard(color: Color): Modifier = shadow(6.dp, FrameShape).clip(FrameShape).background(color)

/** Opens the share preview ([SharePreview]) for the result's card ([Praise.share]). */
@Composable
private fun ShareChip(card: ShareCard, accent: Color, label: String = "Share") {
    // Saveable, so turning the device keeps the preview open.
    // The render hook opens the solve's own card, never the set's over it.
    var open by rememberSaveable { mutableStateOf(sharePreviewOpen && label == "Share") }
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.22f))
            .clickable { open = true }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
    if (open) SharePreview(card) { open = false }
}

/** What stays on screen while the frame is hidden: bring it back, or move on. */
@Composable
private fun ResultsPill(accent: Color, next: NextOption?, done: NextOption, onShow: () -> Unit, onOption: (NextOption) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.padding(horizontal = 18.dp).widthIn(max = 360.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.weight(1.4f).heightIn(min = 48.dp).shadow(4.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                .background(scheme.surface).background(accent.copy(alpha = 0.22f)).clickable(onClick = onShow),
            contentAlignment = Alignment.Center,
        ) { Text("Show results", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface) }
        val shown = listOfNotNull(next, done)
        val main = mainOption(shown)
        shown.forEach {
            NextTile(it, it == main, accent, Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(scheme.surface)) { onOption(it) }
        }
    }
}

@Composable
private fun NextTile(option: NextOption, main: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (main) scheme.primary else accent.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(option.label, style = MaterialTheme.typography.titleSmall, color = if (main) scheme.onPrimary else scheme.onSurface, maxLines = 1)
        (option.note ?: option.difficulty?.label)?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = if (main) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .card(scheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}

/**
 * A puzzle's rules, scrolling, under [title]: the tagline first, then the bullets, then what each tier changes (the board's
 * own [difficulty] marked), with a "Got it" and, given [onWalkthrough], a way into the walkthrough (the play screen's "?":
 * the rules are a tap away, the walkthrough one more).
 */
@Composable
internal fun RulesDialog(
    title: String,
    puzzle: PuzzleType,
    difficulty: Difficulty? = null,
    onWalkthrough: (() -> Unit)? = null,
    onTrain: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(puzzle.tagline, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                RulesList(puzzle)
                TierList(puzzle, difficulty)
                // In the text, not the button row: beside "Got it" a label this long wraps the row onto two lines.
                onWalkthrough?.let {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Play the one-minute walkthrough") }
                }
                // Training: each technique the hints use, practised on real boards.
                onTrain?.let {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Train: practise the techniques") }
                }
            }
        },
    )
}

/**
 * "Standard · Hard · Expert", then a line each saying what that tier changes ([tierNotes]); the [current] tier's line is
 * bold, in the primary colour, and says "this board", so it is marked by more than colour.
 */
@Composable
private fun TierList(puzzle: PuzzleType, current: Difficulty?) {
    val notes = tierNotes(puzzle.id)
    if (notes.size != Difficulty.entries.size) return
    Spacer(Modifier.height(14.dp))
    Text(Difficulty.entries.joinToString(" · ") { it.label }, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Difficulty.entries.forEachIndexed { i, d ->
        val here = d == current
        Text(
            "${d.label}: ${notes[i]}" + if (here) " (this board)" else "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (here) FontWeight.Bold else null,
            color = if (here) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * The rules as bullets, [gap] under each, then the "Keyboard" line: the board's keys, then the play screen's (nothing for
 * a board with none).
 */
@Composable
private fun RulesList(
    puzzle: PuzzleType,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    gap: Dp = 8.dp,
) {
    puzzle.rules.forEach { rule ->
        Text("•  $rule", style = style, color = color)
        Spacer(Modifier.height(gap))
    }
    if (puzzle.keyboardHelp.isEmpty()) return
    Text(
        "Keyboard: " + puzzle.keyboardHelp.joinToString(" ") +
            listOfNotNull(
                "Ctrl+Z undoes".takeIf { puzzle.offersUndo },
                "H asks for a hint, Esc closes it".takeIf { puzzle.offersHints },
            ).joinToString(", ", prefix = " ").let { if (it.isBlank()) "" else "$it." },
        style = style,
        color = color,
    )
}
