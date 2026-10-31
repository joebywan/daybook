package com.joebywan.daybook.ui.tutorial

import com.joebywan.daybook.ui.theme.BoardHues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.HighlightBounds
import com.joebywan.daybook.core.LocalHighlightBounds
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.ui.play.RulesDialog
import com.joebywan.daybook.ui.teach.HintPopover
import com.joebywan.daybook.ui.teach.WatchHint
import com.joebywan.daybook.ui.teach.buttonLabel
import com.joebywan.daybook.ui.teach.rememberHintSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A confirmation (a no-op when sound is off): the unit blip for a hint or a walkthrough step reached, the win for Training's right answer and pass. */
val LocalChime = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The most the caption slot under the board may take. The slot is sized from the walkthrough's own text (the tallest
 * caption plus the feedback line, measured once at the screen's width), so a short walkthrough leaves its board the room
 * it gets in play; a caption past this scrolls. A free-play frame's hint is a popover over the board, as in play, never
 * this slot.
 */
private val MaxCaptionSlot = 156.dp

/** Why the caption area says what it says. */
private enum class Status { WAITING, RETRY, DONE }

/**
 * How long a finished move stays on screen, with its done line, before the next frame: long enough
 * to see the move land and read a short line. Not an animation, so reduced motion does not shorten it.
 */
internal const val TUTORIAL_HOLD_MS = 1000L

/**
 * Whether opening a board starts its walkthrough by itself: the first board of a puzzle the player has never solved
 * nor left a game of on any board (any tier, any mode: [played]) and whose walkthrough was never opened or auto-started ([seen], the store's
 * `tutorials_offered`, which a solve also sets). Never on a level board ([level]; Training has its own screen), and
 * never over a game already under way ([savedMoves]: a restored save's moves and restarts). Shown once per puzzle;
 * the offer line and the "?" sheet stay as the way back. A player from before this rule who has played a puzzle has
 * a completion or a saved game for it, so neither starts.
 */
fun autoWalkthrough(played: Boolean, seen: Boolean, level: Boolean, savedMoves: Int): Boolean =
    !played && !seen && !level && savedMoves == 0

/**
 * Plays [puzzle]'s [PuzzleType.tutorial] on its own real board.
 *
 * Nothing here knows which puzzle it is: each frame brings its board, its caption, the squares to
 * glow and a predicate for the move it wants, and the puzzle's own `Board` draws and reads the
 * gestures exactly as it does in a game — which is the point, since the gestures are what is being
 * taught. A move the frame does not want is not applied, so the board can never drift away from
 * what the next caption describes; the player is just asked, gently, to try the glowing square.
 * A finished move holds for [TUTORIAL_HOLD_MS] and then moves on by itself, its done line carried
 * into the next frame, so Next is only ever pressed on a frame that has nothing to do but read.
 *
 * Frame, board and hint state are all transient and live here, never in a saved game. Skip is
 * always on screen. Free of `android.*`, like the puzzle files, so the web build can use it too.
 */
@Composable
fun TutorialRunner(
    puzzle: PuzzleType,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Never empty: the play screen offers a walkthrough only to a puzzle that has one.
    val frames = puzzle.tutorial
    val scheme = MaterialTheme.colorScheme
    val accent = Color(puzzle.accent)

    var index by rememberSaveable { mutableIntStateOf(0) }
    val frame = frames[index]
    var board by remember(index) { mutableStateOf(frame.state) }
    var moved by remember(index) { mutableStateOf(Status.WAITING) }
    // The done line of the move that brought us here, shown until this frame has news of its own.
    // Back clears it: a frame reached backwards starts fresh.
    var carried by rememberSaveable { mutableStateOf<String?>(null) }
    val judge = frame.judge
    // Free play is done when the board is, which is read off the board rather than stored.
    val status = if (frame.freePlay && board.solved) Status.DONE else moved
    var showRules by remember { mutableStateOf(false) }
    val hints = rememberHintSession(index)
    val last = index == frames.lastIndex
    val scope = rememberCoroutineScope()
    val chime = LocalChime.current
    fun onHint() {
        val asked = board
        scope.launch { hints.tap(puzzle, asked, onOpened = {}, onApply = { board = it }) }
    }

    if (frame.freePlay) {
        WatchHint(hints, puzzle, board, onReached = chime)
        LaunchedEffect(board.solved) {
            if (board.solved) {
                hints.clear()
                chime()
            }
        }
    }

    // A finished move holds, then moves on. Keyed on the index too, so Back during the hold cancels it,
    // and the board is not interactive meanwhile, so a quick extra tap can't count twice or land on
    // the next frame's board. The last frame waits for Play instead.
    if (status == Status.DONE && judge != null && !last) {
        LaunchedEffect(index) {
            delay(TUTORIAL_HOLD_MS)
            carried = frame.done
            index++
        }
    }

    val highlight = when {
        frame.freePlay -> hints.highlight
        status == Status.DONE -> BoardHighlight.None
        else -> frame.glow?.invoke(board) ?: frame.highlight
    }
    val interactive = when {
        frame.freePlay -> !board.solved
        judge != null -> status != Status.DONE
        else -> false
    }
    // A move frame advances by itself; its Next would only invite a tap that does nothing.
    val showNext = judge == null || last
    val canAdvance = judge == null || status == Status.DONE

    // A free-play frame's hint is a popover placed as the play screen places it; this is what it must stay off.
    val highlightBounds = remember { HighlightBounds() }
    LaunchedEffect(hints.deduction) {
        highlightBounds.named = hints.deduction?.let { BoardHighlight(soft = it.focus + it.cited + it.targets) } ?: BoardHighlight.None
    }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var contentTop by remember { mutableFloatStateOf(0f) }
    var boardTop by remember { mutableFloatStateOf(0f) }
    var slotTop by remember { mutableFloatStateOf(0f) }
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val density = LocalDensity.current
    val short = windowHeight < with(density) { 640.dp.toPx() }
    // A short window (a phone browser with its toolbars showing) takes the caption a size down, to leave the board its room.
    val captionStyle = if (short) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val feedbackStyle = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier.fillMaxSize().background(scheme.background).onGloballyPositioned { overlayOrigin = it.positionInWindow() },
    ) {
    // The slot under the board: the tallest frame's caption plus the feedback it can show (its own retry and done lines,
    // the last frame's done line carried in), at most [MaxCaptionSlot]. Measured over every frame, so the slot, and the
    // board above it, is one size for the whole walkthrough; each frame's feedback keeps its own fixed line count, so a
    // line appearing never moves the caption.
    val textWidth = with(density) { (maxWidth - 36.dp).roundToPx() }.coerceAtLeast(1)
    val (slotHeight, feedbackLines) = remember(puzzle.id, textWidth, captionStyle, feedbackStyle, density) {
        val c = Constraints(maxWidth = textWidth)
        fun lines(text: String) = measurer.measure(text, feedbackStyle, maxLines = 2, constraints = c).lineCount
        val perFrame = frames.mapIndexed { i, f ->
            val own = if (f.judge != null || f.freePlay) listOf(f.retry, f.done) else emptyList()
            val carried = frames.getOrNull(i - 1)?.takeIf { it.judge != null }?.let { listOf("\u2713 ${it.done}") }.orEmpty()
            (own + carried).maxOfOrNull(::lines) ?: 0
        }
        val tallest = frames.indices.maxOf { i ->
            val caption = measurer.measure(frames[i].caption, captionStyle, constraints = c).size.height
            val n = perFrame[i]
            caption + if (n == 0) 0 else measurer.measure("\n".repeat(n - 1), feedbackStyle, constraints = c).size.height
        }
        with(density) { minOf(tallest.toDp(), MaxCaptionSlot) } to perFrame
    }
    Column(Modifier.fillMaxSize().onGloballyPositioned { contentTop = it.positionInWindow().y }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "How to play ${puzzle.displayName}",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onBackground,
                )
                Text(
                    "${index + 1} of ${frames.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showRules = true }) { Text("Rules", color = scheme.onSurfaceVariant) }
            // Skip is always there, and always the same size and place, so leaving never has to be
            // hunted for.
            TextButton(onClick = onClose) { Text(if (last) "Close" else "Skip", color = scheme.onSurfaceVariant) }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { boardTop = it.positionInWindow().y },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalBoardHighlight provides highlight, LocalHighlightBounds provides highlightBounds) {
                puzzle.Board(
                    state = board,
                    onState = { next ->
                        when {
                            frame.freePlay -> board = next
                            judge == null || status == Status.DONE -> Unit
                            else -> when (judge(next)) {
                                TutorialStep.DONE -> {
                                    board = next
                                    moved = Status.DONE
                                    // The done line shows at once; the chime says the move landed.
                                    chime()
                                }
                                TutorialStep.PROGRESS -> {
                                    board = next
                                    moved = Status.WAITING
                                }
                                // Not applied: the progress so far stays on the board.
                                TutorialStep.REJECT -> moved = Status.RETRY
                            }
                        }
                    },
                    interactive = interactive,
                )
            }
        }

        // One height for every frame, so nothing under the board moves as captions change length.
        Box(
            Modifier.fillMaxWidth().height(slotHeight).padding(horizontal = 18.dp)
                .onGloballyPositioned { slotTop = it.positionInWindow().y },
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    frame.caption,
                    style = captionStyle,
                    color = scheme.onBackground,
                    // Never cut a word: a caption longer than the slot scrolls instead.
                    modifier = Modifier.weight(1f).verticalScroll(androidx.compose.runtime.key(index) { rememberScrollState() }),
                )
                // Only a frame that asks for a move, or that carries the last move's done line,
                // has feedback to show; the others give the caption the whole slot.
                val feedback = when {
                    status == Status.RETRY -> frame.retry
                    status == Status.DONE -> frame.done
                    carried != null -> "\u2713 $carried"
                    else -> ""
                }
                if (feedbackLines[index] > 0) Text(
                    feedback,
                    style = feedbackStyle,
                    color = if (status == Status.RETRY) scheme.onSurfaceVariant else BoardHues.textOn(accent, scheme.background),
                    maxLines = feedbackLines[index],
                    minLines = feedbackLines[index],
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // On a short window (a phone browser with its toolbars showing) the padding gives way, as the play screen's does.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (short) 8.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    carried = null
                    index--
                },
                enabled = index > 0,
                modifier = Modifier.weight(1f),
            ) { Text("Back") }
            // Only where a hint can answer: Snap's teaching is its walkthrough, with no hints.
            if (frame.freePlay && !board.solved && puzzle.offersHints) {
                OutlinedButton(
                    onClick = ::onHint,
                    modifier = Modifier.weight(1f),
                ) { Text(hints.buttonLabel()) }
            }
            // Invisible rather than gone on a move frame, so the row keeps its shape and Back its place.
            Button(
                onClick = {
                    carried = null
                    if (last) onClose() else index++
                },
                enabled = showNext && canAdvance,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = BoardHues.onFill(accent)),
                modifier = Modifier.weight(1f).alpha(if (showNext) 1f else 0f),
            ) { Text(if (last) "Play" else "Next") }
        }
    }

    if (frame.freePlay && hints.active && !board.solved) {
        HintPopover(
            session = hints,
            accent = accent,
            onNext = ::onHint,
            highlight = highlightBounds.rect,
            keepClear = highlightBounds.keepClear,
            origin = overlayOrigin,
            safeTop = contentTop,
            boardTop = boardTop,
            toolbarTop = slotTop,
            windowHeight = windowHeight,
        )
    }
    }

    if (showRules) RulesDialog("${puzzle.displayName} in short", puzzle) { showRules = false }
}
