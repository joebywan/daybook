package com.joebywan.daybook.ui.teach

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.puzzles.PuzzleState
import kotlinx.coroutines.delay

/**
 * Height of the walkthrough's fixed slot under its board (the play screen has no slot: its hint is
 * a popover, [HintPopover]). Sized for the longest hint text:
 *
 * Five lines of body text plus a row of buttons: at 390dp that holds about 210 characters, which
 * covers every explanation the teachers produce bar Pipes' and Atoms' longest (measured, up to
 * 230). Those few scroll inside the slot rather than being cut off, with a fade to say there is
 * more. Four lines (132dp) clipped at about 145 characters, and half the teachers run past that.
 */
val HintSlotHeight = 156.dp

/** How long "That's it" stays before the hint clears itself. */
private const val CONFIRM_MS = 1400L

enum class HintStage { NUDGE, EXPLAIN, CONFIRMED }

/**
 * The hint in progress: which deduction, and how far into it the player has asked.
 *
 * Lives in `remember`, never in the puzzle state — the play screen pushes an undo entry for every
 * state it is handed, and "the hint panel opened" is not a move. Across a rotation only the stage
 * is saved; [WatchHint] reasons the deduction out again from the board, which is the same board
 * and so gives the same deduction (teachers are pure), and that costs no second hint.
 */
@Stable
class HintSession internal constructor(restoring: HintStage? = null) {
    var deduction by mutableStateOf<Deduction?>(null)
        private set
    var stage by mutableStateOf(HintStage.NUDGE)
        private set

    /** A teacher is working out the next deduction off the main thread. Further taps wait. */
    var thinking by mutableStateOf(false)
        private set

    /** The stage an open hint had before the activity was recreated, until [WatchHint] rebuilds it. */
    internal var restoring by mutableStateOf(restoring)
        private set

    /** The board the open deduction was reasoned from; a different one gets re-checked. */
    private var reasonedFrom: PuzzleState? = null

    val active: Boolean get() = deduction != null

    fun open(d: Deduction, from: PuzzleState) {
        deduction = d
        stage = HintStage.NUDGE
        reasonedFrom = from
    }

    fun explain() {
        if (deduction != null) stage = HintStage.EXPLAIN
    }

    fun confirm() {
        stage = HintStage.CONFIRMED
    }

    fun clear() {
        deduction = null
        stage = HintStage.NUDGE
        reasonedFrom = null
        restoring = null
    }

    /** First tap: where to look. Second tap: what changes, and what the reasoning leans on. */
    val highlight: BoardHighlight
        get() {
            val d = deduction ?: return BoardHighlight.None
            return when (stage) {
                HintStage.NUDGE -> BoardHighlight(strong = d.focus, warning = d.mistake)
                HintStage.EXPLAIN -> BoardHighlight(strong = d.targets, soft = d.cited - d.targets, warning = d.mistake)
                HintStage.CONFIRMED -> BoardHighlight.None
            }
        }

    /**
     * One tap on Hint, or on the panel's own button: open a new deduction, then explain it, then
     * — only on this third, explicit tap — make the move. [onOpened] fires when a new deduction is
     * opened, which is what a hint costs. Returns false when [puzzle] does not teach, so the caller
     * can fall back to the old [PuzzleType.hint].
     *
     * Suspends while the teacher reasons, on [Dispatchers.Default]: Mosaic's hardest hint is a
     * search that took ~250 ms on a desktop JVM and 1.2 s on the emulator (2026-03-05 Expert), far
     * too long to hold a frame.
     * (On the web Default is the one thread, so there it is no worse than before.)
     */
    suspend fun tap(
        puzzle: PuzzleType,
        state: PuzzleState,
        onOpened: () -> Unit,
        onApply: (PuzzleState) -> Unit,
    ): Boolean {
        if (thinking) return true
        val d = deduction
        when {
            d == null || stage == HintStage.CONFIRMED -> {
                thinking = true
                val next = try {
                    withContext(Dispatchers.Default) { puzzle.teach(state) }
                } finally {
                    thinking = false
                }
                next ?: return false
                open(next, state)
                onOpened()
            }
            stage == HintStage.NUDGE -> explain()
            else -> showMe(state, onApply)
        }
        return true
    }

    /** "Show me": the move, made on the board as it is now. Clears at once — no "that's it". */
    fun showMe(state: PuzzleState, onApply: (PuzzleState) -> Unit) {
        val d = deduction ?: return
        clear()
        val next = d.apply(state)
        if (next !== state) onApply(next)
    }

    /** Puts back the hint a rotation interrupted, at the stage it had reached. */
    internal fun restore(d: Deduction?, from: PuzzleState) {
        val at = restoring ?: return
        restoring = null
        if (d == null) return
        open(d, from)
        if (at == HintStage.EXPLAIN) explain()
    }

    /**
     * Whether an open hint still stands on [state], a board the player made some other way than
     * the hint's own move. Kept when the teacher's next step on that board is the same kind of
     * thing — a mistake for a mistake, a step for a step — and touches the same cells: the player
     * has done part of a multi-cell move, or moved somewhere that did not change what comes next.
     * Anything else closes it (see [WatchHint]).
     */
    internal fun standsOn(state: PuzzleState, next: Deduction?): Boolean {
        val d = deduction ?: return false
        if (state == reasonedFrom) return true
        if (next == null || next.mistake != d.mistake) return false
        val same = if (d.targets.isEmpty()) {
            next.technique == d.technique && next.focus == d.focus
        } else {
            (next.targets intersect d.targets).isNotEmpty()
        }
        if (same) reasonedFrom = state
        return same
    }

    internal fun needsCheck(state: PuzzleState): Boolean = deduction != null && state != reasonedFrom

    companion object {
        /** Saves the stage of an open hint, or -1; the deduction itself is re-derived. */
        internal val Saver: Saver<HintSession, Int> = Saver(
            save = { s ->
                when {
                    s.restoring != null -> s.restoring!!.ordinal
                    s.deduction != null && s.stage != HintStage.CONFIRMED -> s.stage.ordinal
                    else -> -1
                }
            },
            restore = { HintSession(HintStage.entries.getOrNull(it)) },
        )
    }
}

@Composable
fun rememberHintSession(key: Any?): HintSession =
    rememberSaveable(key, saver = HintSession.Saver) { HintSession() }

/**
 * Keeps an open hint honest as the board changes under it.
 *
 * - The board contains the hint's result, however the player got there: "that's it" briefly, then
 *   clear. Keyed on the board, so a move made during the confirmation just restarts its timer.
 * - The player moved some other way: the teacher looks at the new board (off the main thread). If
 *   its next step is the same one, or the same cells partly done, the hint stays as it was. If not,
 *   the hint **closes quietly** — the player has gone their own way, and a panel that swapped its
 *   text and glow for a different square they never asked about would be more surprising than one
 *   that steps aside. No new hint is opened or charged; the next tap of Hint reasons afresh.
 * - After a rotation, the hint that was open is rebuilt from the board, at the stage it had.
 */
@Composable
fun WatchHint(session: HintSession, puzzle: PuzzleType, state: PuzzleState) {
    val board by rememberUpdatedState(state)
    LaunchedEffect(session.restoring) {
        if (session.restoring == null) return@LaunchedEffect
        val from = board
        session.restore(withContext(Dispatchers.Default) { puzzle.teach(from) }, from)
    }
    LaunchedEffect(state, session.deduction) {
        val d = session.deduction ?: return@LaunchedEffect
        if (session.stage != HintStage.CONFIRMED) {
            if (!d.isReached(state)) {
                if (!session.needsCheck(state)) return@LaunchedEffect
                val next = withContext(Dispatchers.Default) { puzzle.teach(state) }
                // A tap or another move may have replaced the hint while the teacher thought.
                if (session.deduction === d && !session.standsOn(state, next)) session.clear()
                return@LaunchedEffect
            }
            session.confirm()
        }
        delay(CONFIRM_MS)
        session.clear()
    }
}

/** The label a Hint button should carry for what its next tap does. */
fun HintSession.buttonLabel(): String = when {
    // Mosaic's hardest hint took ~1.2 s on the emulator; a button that said nothing for that long
    // would read as a missed tap.
    thinking -> "Thinking..."
    !active || stage == HintStage.CONFIRMED -> "Hint"
    stage == HintStage.NUDGE -> "Why?"
    else -> "Show me"
}

/**
 * The hint itself: the nudge, then the reasoning, then a brief "that's it". With [fill] it takes
 * whatever height it is given and never asks for more (the walkthrough's fixed slot); without, it
 * wraps its text up to the height it is offered, and scrolls behind a fade beyond that (the play
 * screen's popover, see [HintPopover]). [raised] draws it as a card that floats over a board: a
 * solid ground, an outline and a shadow, where the slot's wash would let the board show through.
 */
@Composable
fun HintPanel(
    session: HintSession,
    accent: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    raised: Boolean = false,
) {
    val d = session.deduction ?: return
    val scheme = MaterialTheme.colorScheme
    val ink = if (d.mistake) scheme.error else accent
    // The panel's own colour, solid, for the fade over text that scrolls on.
    val fade = ink.copy(alpha = 0.12f).compositeOver(if (raised) scheme.surface else scheme.background)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .then(if (raised) Modifier.shadow(10.dp, shape).border(1.dp, ink.copy(alpha = 0.45f), shape) else Modifier)
            .clip(shape)
            .background(if (raised) fade else ink.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 0.dp),
    ) {
        val text = when (session.stage) {
            HintStage.NUDGE -> d.nudge
            HintStage.EXPLAIN -> d.explanation
            HintStage.CONFIRMED -> if (d.mistake) "Fixed." else "That's it."
        }
        // Scrolls rather than clipping, for the rare explanation past five lines; the slot never
        // grows. A new text starts at the top again.
        val scroll = remember(text) { ScrollState(0) }
        Box(Modifier.weight(1f, fill = fill).fillMaxWidth().padding(end = 8.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = (if (fill) Modifier.fillMaxSize() else Modifier).verticalScroll(scroll),
            )
            if (scroll.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, fade))),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(38.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (session.stage != HintStage.CONFIRMED) {
                TextButton(onClick = { session.clear() }) {
                    Text("Close", color = scheme.onSurfaceVariant)
                }
                TextButton(onClick = onAction) {
                    Text(if (session.stage == HintStage.NUDGE) "Why?" else "Show me", color = ink)
                }
            }
        }
    }
}

/**
 * The hint as a popover over the play screen, so that a hint never takes height from the board —
 * the board is the same size before, during and after (CLAUDE.md: feedback must not move it).
 *
 * It sits on the opposite side of the board from what the hint points at: highlight in the lower
 * half, popover above (hugging the top of the board area, just under the clock); highlight in the
 * upper half, popover below (hugging the top of the toolbar, which stays uncovered). Where the
 * highlight spans both halves the side with less overlap wins, ties going below. A control the
 * player needs ([keepClear]: a digit pad, a palette) is kept clear above all: when "below" would
 * land on one, a third place, just above it, is tried (see [placePopover]). If the chosen side still
 * overlaps, the popover first slides as far as it can (an upper popover may rise over the header,
 * to the status bar) and then shrinks to [COMPACT] height, its text scrolling. All of this is
 * pixels in window coordinates: [highlight] comes from the boards ([HighlightBounds]), and unknown
 * (a board that reports nothing) means below.
 *
 * It does not block the board: taps outside the card reach the cells, so the player makes the
 * move with the explanation still up. A change of side or of highlight glides rather than jumps.
 */
@Composable
fun HintPopover(
    session: HintSession,
    accent: Color,
    highlight: Rect?,
    keepClear: Collection<Rect>,
    origin: Offset,
    safeTop: Float,
    boardTop: Float,
    toolbarTop: Float,
    windowHeight: Float,
    onAction: () -> Unit,
) {
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.toPx() }
    val pad = with(density) { 6.dp.toPx() }
    val compactPx = with(density) { COMPACT.toPx() }
    val fullMax = windowHeight * 0.4f

    // The height the text wants at full size, re-measured for each stage's new text. Measured only
    // while the popover is at full size, so shrinking it cannot feed back into the choice.
    var natural by remember(session.deduction, session.stage) { mutableFloatStateOf(0f) }
    val h = natural
    val hl = highlight?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
    // Everything below is in window coordinates; the overlay's own origin is taken off at the end.
    val minTop = safeTop + gap
    val maxBottom = toolbarTop - gap

    val spot = placePopover(
        natural = h,
        compact = compactPx,
        highlight = hl,
        keepClear = keepClear,
        minTop = minTop,
        maxBottom = maxBottom,
        boardTop = boardTop,
        gap = gap,
        windowHeight = windowHeight,
    )
    val limited = spot.limited
    val target = spot.y

    val y = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(target, natural > 0f) {
        if (natural <= 0f) return@LaunchedEffect
        if (!placed) {
            y.snapTo(target)
            placed = true
        } else {
            y.animateTo(target, tween(220))
        }
    }

    HintPanel(
        session, accent, onAction,
        modifier = Modifier
            .offset { IntOffset(0, (y.value - origin.y).roundToInt()) }
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .heightIn(max = with(density) { (if (limited) compactPx else fullMax).toDp() })
            .alpha(if (placed) 1f else 0f)
            .onSizeChanged { if (!limited) natural = it.height.toFloat() },
        fill = false,
        raised = true,
    )
}

/** The popover's height when it has to squeeze past the highlight; the text scrolls inside it. */
private val COMPACT = 104.dp
