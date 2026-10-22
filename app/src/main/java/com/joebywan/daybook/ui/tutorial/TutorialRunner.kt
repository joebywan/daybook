package com.joebywan.daybook.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.ui.play.KeyboardHelpLine
import com.joebywan.daybook.ui.teach.HintPanel
import com.joebywan.daybook.ui.teach.HintSlotHeight
import com.joebywan.daybook.ui.teach.WatchHint
import com.joebywan.daybook.ui.teach.buttonLabel
import com.joebywan.daybook.ui.teach.rememberHintSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The solve chime (a no-op when sound is off), so a finished step in a tutorial announces its new caption. */
val LocalChime = staticCompositionLocalOf<() -> Unit> { {} }

/** Why the caption area says what it says. */
private enum class Status { WAITING, RETRY, DONE }

/**
 * How long a finished move stays on screen, with its done line, before the next frame: long enough
 * to see the move land and read a short line. Not an animation, so reduced motion does not shorten it.
 */
internal const val TUTORIAL_HOLD_MS = 1000L

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
    val frames = puzzle.tutorial
    if (frames.isEmpty()) {
        // Closing is navigation, which must not happen mid-composition.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val scheme = MaterialTheme.colorScheme
    val accent = Color(puzzle.accent)

    var index by rememberSaveable { mutableIntStateOf(0) }
    val frame = frames[index.coerceIn(0, frames.lastIndex)]
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
        WatchHint(hints, puzzle, board)
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

    Column(modifier.fillMaxSize().background(scheme.background)) {
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

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalBoardHighlight provides highlight) {
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

        // The same fixed slot the play screen reserves, so the board sits where it will in a game
        // and nothing under it moves as captions change length.
        Box(
            Modifier.fillMaxWidth().height(HintSlotHeight).padding(horizontal = 18.dp),
        ) {
            if (frame.freePlay && hints.active) {
                HintPanel(hints, accent, onAction = ::onHint, modifier = Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        frame.caption,
                        style = MaterialTheme.typography.bodyLarge,
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
                    if (judge != null || frame.freePlay || carried != null) Text(
                        feedback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status == Status.RETRY) scheme.onSurfaceVariant else accent,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
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
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.weight(1f).alpha(if (showNext) 1f else 0f),
            ) { Text(if (last) "Play" else "Next") }
        }
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("Got it") } },
            title = { Text("${puzzle.displayName} in short") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    puzzle.rules.forEach { rule ->
                        Text("•  $rule", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    KeyboardHelpLine(puzzle)
                }
            },
        )
    }
}
