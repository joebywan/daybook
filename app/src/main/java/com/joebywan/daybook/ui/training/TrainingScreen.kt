package com.joebywan.daybook.ui.training

import com.joebywan.daybook.ui.theme.BoardHues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Course
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.newlyTrained
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.TitledScreen
import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.teach.HintPanel
import com.joebywan.daybook.ui.teach.HintStage
import com.joebywan.daybook.ui.teach.WatchHint
import com.joebywan.daybook.ui.teach.buttonLabel
import com.joebywan.daybook.ui.teach.hintKeys
import com.joebywan.daybook.ui.teach.rememberHintSession
import com.joebywan.daybook.ui.tutorial.LocalChime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Height of the slot under the board: the explanation, a hint or a verdict, never moving the board. Five lines of body
 * text, a little under the walkthrough's, so the board keeps more of a short phone; longer text scrolls.
 */
private val SlotHeight = 144.dp

/** How long a pass's congratulation shows before back takes the player to the list it was opened from. */
private const val PASS_BEAT_MS = 2500L

/** A card row: the tick's room kept either way so the names line up, then [title] and an optional [note]. */
@Composable
private fun TrainingRow(title: String, note: String?, ticked: Boolean, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().card(scheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ticked) Icon(Icons.Default.Check, "Passed", tint = scheme.primary, modifier = Modifier.size(20.dp))
        else Spacer(Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrainingList(title: String, subtitle: String, intro: String, onBack: () -> Unit, rows: @Composable () -> Unit) {
    TitledScreen(title, subtitle, onBack) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            rows()
        }
    }
}

/**
 * Training's front page (`docs/TRAINING_SPEC.md`): the puzzles that have it, each with how many of its techniques are
 * passed, ticked once it is finished (its final board, or every technique where it has none).
 */
@Composable
fun TrainingScreen(record: Set<String>, onOpen: (String) -> Unit, onBack: () -> Unit) {
    TrainingList(
        "Training", "Practise what the hints teach",
        "Pick a puzzle, then a technique, and practise it on real boards. Get ${Training.RUN} right in a row to pass it.",
        onBack,
    ) {
        Training.COURSES.forEach { c ->
            val done = c.techniques.count { c.passed(record, it) }
            TrainingRow(
                c.puzzle.displayName, "$done of ${c.techniques.size} passed",
                ticked = if (c.hasExam) c.passed(record, Training.EXAM) else c.allPassed(record),
            ) { onOpen(c.id) }
        }
    }
}

/** One puzzle's techniques in the ladder's order, each ticked once passed, and its final board if it has one. */
@Composable
fun CourseScreen(course: Course, record: Set<String>, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val done = course.techniques.count { course.passed(record, it) }
    TrainingList(
        "${course.puzzle.displayName} training", "$done of ${course.techniques.size} passed",
        "Each technique the hints use, with real boards to practise on. Get ${Training.RUN} right " +
            "in a row to pass it. Hints are there if you need one, but a hinted board doesn't count.",
        onBack,
    ) {
        course.techniques.forEach { t -> TrainingRow(course.titleOf(t), null, course.passed(record, t)) { onOpen(t) } }
        if (course.hasExam) {
            val open = course.allPassed(record)
            TrainingRow(
                "Final board",
                if (open) "A whole ${course.puzzle.displayName} board that needs every technique above."
                else "Opens when every technique above is passed.",
                course.passed(record, Training.EXAM),
                if (open) ({ onOpen(Training.EXAM) }) else null,
            )
        }
    }
}

private enum class Verdict { RIGHT, HINTED, WRONG }

/** The line a pass earns: its achievements, if any. */
private fun earnedLine(course: Course, record: Set<String>, technique: String) =
    newlyTrained(record, course.id, technique).joinToString(" ") { "Achievement: ${it.title}!" }

/**
 * The congratulation when a run has just reached [Training.RUN], or null: every time it does, not only the first, so
 * practising a technique already passed still ends in "Passed!" ([record] is before this pass; achievements only once).
 */
internal fun passLine(course: Course, record: Set<String>, technique: String, run: Int): String? =
    if (run != Training.RUN) null
    else "Passed! ${Training.RUN} in a row. ${if (course.passed(record, technique)) "" else earnedLine(course, record, technique)}".trim()

/**
 * One technique's practice: a board whose next hint step is [technique]. The player makes a move; [Course.judge] says
 * whether it is right (adds to the run), wrong (ends it and shows the reasoning) or neither yet (play on: one cross of a
 * step that crosses three). [Training.RUN] in a row calls [onPass] (the first time) and, after the congratulation has
 * shown for a beat, [onBack]: the owner, "send the user back to the list when they achieve the goal". The same for every
 * drill. A board where a hint was opened neither counts nor breaks the run.
 */
@Composable
fun TechniquePractice(course: Course, technique: String, record: Set<String>, onPass: (String) -> Unit, onBack: () -> Unit) {
    val puzzle = course.puzzle
    val scheme = MaterialTheme.colorScheme
    val accent = Color(puzzle.accent)
    val chime = LocalChime.current
    val scope = rememberCoroutineScope()
    val key = "${course.id}|$technique"
    var index by rememberSaveable(key) { mutableIntStateOf(0) }
    var run by rememberSaveable(key) { mutableIntStateOf(0) }
    // The pass line ([passLine]) of this board, kept until the next board.
    var earned by remember(key) { mutableStateOf<String?>(null) }
    val practice by produceState<Course.Practice?>(null, key, index) {
        value = null
        value = withContext(Dispatchers.Default) { course.practice(technique, index) }
    }
    val p = practice
    var board by remember(p) { mutableStateOf(p?.board) }
    var verdict by remember(p) { mutableStateOf<Verdict?>(null) }
    var hinted by remember(p) { mutableStateOf(false) }
    val hints = rememberHintSession("$key|$index")
    // A pass: the line shows with the board locked, then back to where the practice was opened from.
    LaunchedEffect(earned) { if (earned != null) { delay(PASS_BEAT_MS); onBack() } }
    val subtitle = if (course.passed(record, technique)) "Passed" else "$run of ${Training.RUN} in a row"

    fun settle(right: Boolean) {
        hints.clear()
        verdict = when {
            !right -> Verdict.WRONG
            hinted -> Verdict.HINTED
            else -> Verdict.RIGHT
        }
        when (verdict) {
            Verdict.WRONG -> run = 0
            Verdict.RIGHT -> {
                run++
                chime()
                earned = passLine(course, record, technique, run)
                if (earned != null && !course.passed(record, technique)) onPass(technique)
            }
            else -> Unit
        }
    }

    // Every state the board emits is judged off the main thread (a move drill asks the teacher, which can take a while);
    // a verdict for a state the player has already moved on from is dropped, and the newer state judged instead.
    fun judge(next: PuzzleState) {
        if (board == null || p == null || verdict != null) return
        board = next
        scope.launch {
            val right = withContext(Dispatchers.Default) { course.judge(p, next) } ?: return@launch
            if (!right) delay(course.drill.settleMillis)
            if (board === next && verdict == null) settle(right)
        }
    }

    // The Hint button's and the hint panel's next step: one path.
    fun onHint() {
        val now = board ?: return
        scope.launch { hints.tap(puzzle, now, onOpened = { hinted = true }, onApply = { judge(it) }) }
    }

    TitledScreen(course.titleOf(technique), subtitle, onBack) {
        val b = board
        if (p == null || b == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Finding a board...", color = scheme.onSurfaceVariant)
            }
        } else {
            if (verdict == null) WatchHint(hints, puzzle, b)
            val highlight = when (verdict) {
                null -> hints.highlight
                else -> BoardHighlight(strong = p.step.targets, soft = p.step.cited - p.step.targets, warning = false)
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().hintKeys(verdict == null, hints, if (puzzle.offersHints) ::onHint else null),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalBoardHighlight provides highlight) {
                    puzzle.Board(b, { judge(it) }, interactive = verdict == null)
                }
            }
            Box(Modifier.fillMaxWidth().height(SlotHeight).padding(horizontal = 18.dp)) {
                if (verdict == null && hints.active) HintPanel(hints, accent, ::onHint, Modifier.fillMaxSize())
                else {
                    val step = p.step
                    val text = when (verdict) {
                        null -> course.explanations.getValue(technique)
                        Verdict.WRONG -> "Not quite. ${step.explanation}"
                        Verdict.HINTED -> "That's it, with a hint, so it doesn't count. ${step.explanation}"
                        Verdict.RIGHT -> earned ?: "That's it. ${step.explanation}"
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onBackground,
                        modifier = Modifier.fillMaxSize().verticalScroll(remember(text) { ScrollState(0) }),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = ::onHint,
                enabled = board != null && verdict == null,
                modifier = Modifier.weight(1f),
            ) { Text(hints.buttonLabel()) }
            Button(
                onClick = {
                    earned = null
                    index++
                },
                enabled = p != null && earned == null,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = BoardHues.onFill(accent)),
                modifier = Modifier.weight(1f),
            ) { Text(if (verdict == null) "Another board" else "Next board") }
        }
    }
}

private val StateJson = Json { ignoreUnknownKeys = true }

private val BoardSaver = Saver<PuzzleState, String>(
    save = { StateJson.encodeToString(PuzzleState.serializer(), it) },
    restore = { runCatching { StateJson.decodeFromString(PuzzleState.serializer(), it) }.getOrNull() },
)

/**
 * The final board: a whole Expert board whose walk needs every technique ([Course.exam]). Solving it without
 * "Show me" calls [onPass], then [onBack] after a beat; hints are allowed. Kept across a rotation, not across leaving:
 * it is one board, not a save.
 */
@Composable
fun FinalBoard(course: Course, record: Set<String>, onPass: (String) -> Unit, onBack: () -> Unit) {
    // An Expert board can take a while to generate (Regions' up to a second or two in a browser), so off the main thread.
    val start by produceState<PuzzleState?>(null, course) { value = withContext(Dispatchers.Default) { course.exam() } }
    val s = start
    if (s == null) TitledScreen("Final board", "Every technique, in one board", onBack) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Finding a board...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else FinalBoardOn(course, s, record, onPass, onBack)
}

@Composable
private fun FinalBoardOn(course: Course, start: PuzzleState, record: Set<String>, onPass: (String) -> Unit, onBack: () -> Unit) {
    val puzzle = course.puzzle
    val scheme = MaterialTheme.colorScheme
    val accent = Color(puzzle.accent)
    val chime = LocalChime.current
    val scope = rememberCoroutineScope()
    var board by rememberSaveable(course.id, stateSaver = BoardSaver) { mutableStateOf(start) }
    var shown by rememberSaveable(course.id) { mutableStateOf(false) }
    var earned by remember { mutableStateOf<String?>(null) }
    val hints = rememberHintSession("${course.id}|exam")
    WatchHint(hints, puzzle, board)
    fun onHint() {
        val now = board
        scope.launch { hints.tap(puzzle, now, onOpened = {}, onApply = { shown = true; board = it }) }
    }
    LaunchedEffect(board.solved) {
        if (!board.solved) return@LaunchedEffect
        hints.clear()
        chime()
        if (!shown && !course.passed(record, Training.EXAM)) {
            earned = earnedLine(course, record, Training.EXAM)
            onPass(Training.EXAM)
        }
        // Passed (again or for the first time): the line shows for a beat, then back to the list, as a technique does.
        if (!shown) { delay(PASS_BEAT_MS); onBack() }
    }
    TitledScreen("Final board", if (course.passed(record, Training.EXAM)) "Passed" else "Every technique, in one board", onBack) {
        Box(
            Modifier.weight(1f).fillMaxWidth().hintKeys(!board.solved, hints, if (puzzle.offersHints) ::onHint else null),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalBoardHighlight provides hints.highlight) {
                puzzle.Board(board, { board = it }, interactive = !board.solved)
            }
        }
        Box(Modifier.fillMaxWidth().height(SlotHeight).padding(horizontal = 18.dp)) {
            if (hints.active) HintPanel(hints, accent, ::onHint, Modifier.fillMaxSize())
            else Text(
                when {
                    board.solved && shown -> "Solved, with a Show me along the way. Start again to solve it on your own."
                    board.solved -> "Solved! ${earned.orEmpty()}".trim()
                    else -> "This board needs every technique you've practised. Hints are here if you're stuck; " +
                        "solve it without a Show me to pass."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onBackground,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = ::onHint,
                enabled = !board.solved,
                modifier = Modifier.weight(1f),
            ) { Text(hints.buttonLabel()) }
            Button(
                onClick = {
                    hints.clear()
                    board = start
                    shown = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = BoardHues.onFill(accent)),
                modifier = Modifier.weight(1f),
            ) { Text("Start again") }
        }
    }
}

/**
 * "Practise this" beside a hint's Close, once it explains a technique [course] trains (not a mistake, not the fallback).
 * Provided to the play screen's hint panel through `LocalHintAction` by the app, for puzzles with a course.
 */
@Composable
fun PractiseLink(course: Course, d: Deduction, stage: HintStage, onPractise: (String) -> Unit) {
    if (stage != HintStage.EXPLAIN || d.mistake || d.fallback || d.technique !in course.techniques) return
    TextButton(onClick = { onPractise(d.technique) }) { Text("Practise this", color = MaterialTheme.colorScheme.primary) }
}
