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
 * Height of the hint panel's slot under the board, while a hint is open (the play screen eases it
 * open and shut; the board gives up the room only then). The walkthrough's own screen still keeps
 * it permanently.
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
 * The hint itself, drawn into the reserved slot: the nudge, then the reasoning, then a brief "that's
 * it". Fills whatever height it is given and never asks for more, so the caller's fixed slot is the
 * whole of its layout contract.
 */
@Composable
fun HintPanel(
    session: HintSession,
    accent: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = session.deduction ?: return
    val scheme = MaterialTheme.colorScheme
    val ink = if (d.mistake) scheme.error else accent
    // The panel's own colour, solid, for the fade over text that scrolls on.
    val fade = ink.copy(alpha = 0.12f).compositeOver(scheme.background)
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(ink.copy(alpha = 0.12f))
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
        Box(Modifier.weight(1f).fillMaxWidth().padding(end = 8.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier.fillMaxSize().verticalScroll(scroll),
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

/** The one-line walkthrough offer's height, reserved only on a puzzle's first visit. */
val OfferLineHeight = 40.dp
