package com.joebywan.daybook.ui.play

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.undone
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.platform.BackButton
import com.joebywan.daybook.platform.PlatformBackHandler
import com.joebywan.daybook.platform.formatClock
import com.joebywan.daybook.platform.formatDate
import com.joebywan.daybook.platform.generateBoard
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.teach.HintPanel
import com.joebywan.daybook.ui.teach.WatchHint
import com.joebywan.daybook.ui.teach.buttonLabel
import com.joebywan.daybook.ui.teach.rememberHintSession
import com.joebywan.daybook.ui.tutorial.TutorialRunner
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

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

@Composable
fun PlayScreen(
    puzzle: PuzzleType,
    difficulty: Difficulty,
    day: LocalDate?,
    seed: Long,
    restore: suspend () -> SavedGame?,
    persist: suspend (SavedGame?) -> Unit,
    onSolved: (seconds: Int, hints: Int) -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
    tutorialOffered: Boolean? = null,
    onTutorialOffered: () -> Unit = {},
    showTimer: Boolean = true,
) {
    // The harder boards take hundreds of milliseconds to generate, which would freeze the frame if
    // it happened during composition. Android runs it off the main thread; the web has only one
    // thread, so there it waits until "Setting out" is on screen first (see the platform seam).
    val generated by produceState<PuzzleState?>(null, puzzle.id, seed, difficulty) {
        value = generateBoard(puzzle, seed, difficulty)
    }

    val ready = generated
    if (ready == null) {
        DealingBoard(puzzle)
    } else {
        PlayBoard(
            puzzle, difficulty, day, ready, restore, persist, onSolved, onAgain, onBack,
            tutorialOffered, onTutorialOffered, showTimer,
        )
    }
}

/**
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
        Text(
            "Setting out ${puzzle.displayName}...",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayBoard(
    puzzle: PuzzleType,
    difficulty: Difficulty,
    day: LocalDate?,
    initial: PuzzleState,
    restore: suspend () -> SavedGame?,
    persist: suspend (SavedGame?) -> Unit,
    onSolved: (seconds: Int, hints: Int) -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
    tutorialOffered: Boolean?,
    onTutorialOffered: () -> Unit,
    showTimer: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    val pristine = remember(initial) { SavedGame(initial) }
    var game by rememberSaveable(initial, stateSaver = SavedGameSaver) { mutableStateOf(pristine) }
    // Saved alongside the game: without it, rotating a finished board would record the win twice.
    var recorded by rememberSaveable(initial) { mutableStateOf(false) }
    // Set as soon as the store has been asked, and itself saved, so the answer that arrived before
    // the rotation is not thrown away by a second lookup afterwards.
    var consulted by rememberSaveable(initial) { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    // The walkthrough draws over the game rather than replacing the route, so the board, its undo
    // stack and its clock are exactly where they were when the player comes back.
    var showTutorial by rememberSaveable(initial) { mutableStateOf(false) }
    val hasTutorial = puzzle.tutorial.isNotEmpty()
    // Whether this puzzle gets the space under the board at all. Fixed per puzzle, so it can never
    // be the thing that moves a board; puzzles that neither teach nor have a walkthrough keep the
    // layout they always had.
    val teaches = remember(initial) { hasTutorial || puzzle.teach(initial) != null }
    val hintSession = rememberHintSession(initial)
    val hintScope = rememberCoroutineScope()
    // Offered on this visit, and only this one. Saved, so a rotation keeps the line it already
    // showed rather than losing it to the store's "already offered".
    var offering by rememberSaveable(initial) { mutableStateOf(false) }
    LaunchedEffect(tutorialOffered) {
        if (tutorialOffered == false && hasTutorial && !offering) {
            offering = true
            onTutorialOffered()
        }
    }

    val state = game.state
    val seconds = game.seconds

    // Reads `game` rather than the values unpacked above: this runs from effects that outlive the
    // composition that started them, where those locals would be frozen at their first value.
    suspend fun flush() {
        val current = game
        when {
            // A finished board must never come back as "in progress".
            current.state.solved -> persist(null)
            // Opened and never touched. Nothing to record, and nothing of anyone's to clear.
            current == pristine -> Unit
            // Restarted, or only ever selected a cell: no longer a game in progress.
            current.state.moves == 0 -> persist(null)
            else -> persist(current)
        }
    }

    LaunchedEffect(initial) {
        if (consulted) return@LaunchedEffect
        val stored = restore()
        // Reading the store takes a moment; a tap that landed in the meantime outranks it.
        if (stored != null && game == pristine) game = stored
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
        if (state.solved && !recorded) {
            recorded = true
            onSolved(seconds, game.hints)
        }
    }

    // Reads through `game` rather than the unpacked locals so that two taps landing in the same
    // frame stack up properly instead of pushing the same board twice.
    fun push(next: PuzzleState) {
        game = game.copy(state = next, history = game.history + game.state)
    }

    WatchHint(hintSession, puzzle, state)
    LaunchedEffect(state.solved) { if (state.solved) hintSession.clear() }

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
                onOpened = { game = game.copy(hints = game.hints + 1) },
                onApply = ::push,
            )
            // The old hint lands on the board, so only on the board it was asked about.
            if (!taught && game.state == asked) {
                puzzle.hint(asked)?.let {
                    game = game.copy(hints = game.hints + 1)
                    push(it)
                }
            }
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
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val buttonPad = if (screenHeight < 640.dp) 10.dp else 22.dp
    val highlightBounds = remember { HighlightBounds() }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var contentTop by remember { mutableStateOf(0f) }
    var boardTop by remember { mutableStateOf(0f) }
    var toolbarTop by remember { mutableStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onGloballyPositioned { contentTop = it.positionInWindow().y }
    ) {
        // Title at the start, the one remaining action at the end. Centring what is left over
        // after removing the arrow would have hung the title 44dp off true, and padding the gap
        // back out would keep reserving room for a control that no longer exists.
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
                        append(day?.let { formatDate(it, PLAY_DATE) } ?: "Random")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            IconCircle(Icons.AutoMirrored.Filled.HelpOutline, "How to play") {
                if (hasTutorial) showTutorial = true else showRules = true
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
            // The time keeps counting and is shown on the solved card.
            Text(
                text = formatClock(seconds),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                modifier = if (showTimer) Modifier else Modifier.alpha(0f).clearAndSetSemantics { },
            )
            if (teaches && offering && game.history.isEmpty() && !hintSession.active && !state.solved) {
                Box(Modifier.matchParentSize().background(scheme.background), contentAlignment = Alignment.Center) {
                    Text(
                        "New to ${puzzle.displayName}? One-minute walkthrough",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(puzzle.accent),
                        maxLines = 1,
                        modifier = Modifier
                            .requiredHeight(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTutorial = true }
                            .padding(horizontal = 12.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
        }

        Box(
            Modifier.weight(1f).onGloballyPositioned { boardTop = it.positionInWindow().y },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalBoardHighlight provides hintSession.highlight,
                LocalHighlightBounds provides highlightBounds,
            ) {
                puzzle.Board(
                    state = state,
                    onState = { next -> push(next) },
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
                ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", Modifier.weight(1f)) {
                    if (solved) return@ToolButton
                    // A hint reasoned from a board that has just been taken back may lean on a king
                    // that is no longer there.
                    hintSession.clear()
                    undone(puzzle, game.state, game.history)?.let {
                        game = game.copy(state = it.state, history = it.history)
                    }
                }
                ToolButton(Icons.Default.Refresh, "Restart", Modifier.weight(1f)) {
                    if (solved) return@ToolButton
                    hintSession.clear()
                    game = game.copy(state = initial, history = emptyList())
                }
                if (puzzle.offersHints) {
                    ToolButton(Icons.Default.AutoAwesome, hintSession.buttonLabel(), Modifier.weight(1f)) {
                        if (!solved) onHint()
                    }
                }
            }
            if (solved) {
                SolvedBar(
                    seconds = seconds,
                    hints = game.hints,
                    accent = Color(puzzle.accent),
                    onAgain = onAgain,
                    onBack = onBack,
                    modifier = Modifier.matchParentSize().padding(horizontal = 18.dp, vertical = buttonPad / 2),
                )
            }
        }
    }

    if (hintSession.active && !state.solved) {
        HintPopover(
            session = hintSession,
            accent = Color(puzzle.accent),
            highlight = highlightBounds.rect,
            keepClear = highlightBounds.keepClear,
            origin = overlayOrigin,
            // Where the popover may sit: from just under the clock (or, to clear a highlight, the
            // status bar) down to just above the toolbar, which it never covers.
            safeTop = contentTop,
            boardTop = boardTop,
            toolbarTop = toolbarTop,
            windowHeight = with(LocalDensity.current) { screenHeight.toPx() },
            onAction = ::onHint,
        )
    }
    }


    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            confirmButton = {
                TextButton(onClick = { showRules = false }) { Text("Got it") }
            },
            title = { Text("How to play ${puzzle.displayName}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    puzzle.rules.forEach { rule ->
                        Text("•  $rule", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
        )
    }
}

@Composable
private fun SolvedBar(
    seconds: Int,
    hints: Int,
    accent: Color,
    onAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // One row, sized by the toolbar it covers, so it never costs the board any height.
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.18f))
            .pointerInput(Unit) {}
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Solved", style = MaterialTheme.typography.titleLarge, color = accent)
            Text(
                buildString {
                    append(formatClock(seconds))
                    if (hints > 0) append("  ·  $hints hint${if (hints == 1) "" else "s"}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
        }
        TextButton(onClick = onAgain) { Text("Another") }
        TextButton(onClick = onBack) { Text("Done") }
    }
}

@Composable
private fun IconCircle(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}
