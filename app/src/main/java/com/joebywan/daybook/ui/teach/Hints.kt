package com.joebywan.daybook.ui.teach

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.puzzles.PuzzleState
import kotlinx.coroutines.delay

/**
 * Height reserved under the board for the hint panel, the walkthrough offer and the "that's it"
 * line. Fixed, and reserved whether or not anything is showing, because feedback must never move
 * the board (CLAUDE.md) — a panel that grew into place would shift every square under a finger
 * that was already on its way to one. Four lines of body text plus a row of buttons.
 */
val HintSlotHeight = 132.dp

/** How long "That's it" stays before the hint clears itself. */
private const val CONFIRM_MS = 1400L

enum class HintStage { NUDGE, EXPLAIN, CONFIRMED }

/**
 * The hint in progress: which deduction, and how far into it the player has asked.
 *
 * Lives in `remember`, never in the puzzle state — the play screen pushes an undo entry for every
 * state it is handed, and "the hint panel opened" is not a move.
 */
@Stable
class HintSession {
    var deduction by mutableStateOf<Deduction?>(null)
        private set
    var stage by mutableStateOf(HintStage.NUDGE)
        private set

    val active: Boolean get() = deduction != null

    fun open(d: Deduction) {
        deduction = d
        stage = HintStage.NUDGE
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
     */
    fun tap(
        puzzle: PuzzleType,
        state: PuzzleState,
        onOpened: () -> Unit,
        onApply: (PuzzleState) -> Unit,
    ): Boolean {
        val d = deduction
        when {
            d == null || stage == HintStage.CONFIRMED -> {
                val next = puzzle.teach(state) ?: return false
                open(next)
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
}

@Composable
fun rememberHintSession(key: Any?): HintSession = remember(key) { HintSession() }

/**
 * Watches the player's boards for the one that contains the open deduction's result, then shows
 * "that's it" briefly and clears. Keyed on the board, so a move made during the confirmation just
 * restarts its timer rather than stranding it.
 */
@Composable
fun WatchHint(session: HintSession, state: PuzzleState) {
    LaunchedEffect(state, session.deduction) {
        val d = session.deduction ?: return@LaunchedEffect
        if (session.stage != HintStage.CONFIRMED) {
            if (!d.isReached(state)) return@LaunchedEffect
            session.confirm()
        }
        delay(CONFIRM_MS)
        session.clear()
    }
}

/** The label a Hint button should carry for what its next tap does. */
fun HintSession.buttonLabel(): String = when {
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
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(ink.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
    ) {
        val text = when (session.stage) {
            HintStage.NUDGE -> d.nudge
            HintStage.EXPLAIN -> d.explanation
            HintStage.CONFIRMED -> if (d.mistake) "Fixed." else "That's it."
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(end = 8.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.fillMaxWidth().height(40.dp),
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
