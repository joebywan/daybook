package com.joebywan.daybook.ui.training

import com.joebywan.daybook.ui.theme.BoardHues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
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
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.SudokuTraining
import com.joebywan.daybook.core.TrainingRecord
import com.joebywan.daybook.core.newlyTrained
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.ui.TitledScreen
import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.teach.HintPanel
import com.joebywan.daybook.ui.teach.HintStage
import com.joebywan.daybook.ui.teach.WatchHint
import com.joebywan.daybook.ui.teach.buttonLabel
import com.joebywan.daybook.ui.teach.rememberHintSession
import com.joebywan.daybook.ui.tutorial.LocalChime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** A technique's name as a title: "A digit's only place in a box". */
private fun titleOf(technique: String): String =
    techniqueNamesOf(SudokuTraining.PUZZLE)[technique]?.replaceFirstChar { it.uppercase() } ?: "Final board"

private fun passed(record: Set<String>, technique: String) =
    TrainingRecord.entry(SudokuTraining.PUZZLE, technique) in record

/** Whether every technique is passed, which opens the final board. */
private fun allPassed(record: Set<String>) = SudokuTraining.TECHNIQUES.all { passed(record, it) }

/**
 * Height of the slot under the board: the explanation, a hint or a verdict, never moving the board. Five lines of body
 * text, a little under the walkthrough's, so the board keeps more of a short phone; longer text scrolls.
 */
private val SlotHeight = 144.dp

/**
 * Training's list (`docs/TRAINING_SPEC.md`): Sudoku's techniques in the ladder's order, each ticked once passed, and the
 * final board, open once they all are. [record] is the training record ([TrainingRecord]).
 */
@Composable
fun TrainingScreen(record: Set<String>, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val done = SudokuTraining.TECHNIQUES.count { passed(record, it) }
    TitledScreen("Training", "Sudoku: $done of ${SudokuTraining.TECHNIQUES.size} passed", onBack) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Each technique the hints use, with real boards to practise on. Get ${SudokuTraining.RUN} right " +
                        "in a row to pass it. Hints are there if you need one, but a hinted board doesn't count.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            items(SudokuTraining.TECHNIQUES, key = { it }) { t ->
                val got = passed(record, t)
                Row(
                    Modifier.fillMaxWidth().card(scheme.surface).clickable { onOpen(t) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The tick's room is kept either way, so the names line up.
                    if (got) Icon(Icons.Default.Check, "Passed", tint = scheme.primary, modifier = Modifier.size(20.dp))
                    else Spacer(Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(titleOf(t), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                }
            }
            item {
                val open = allPassed(record)
                Row(
                    Modifier.fillMaxWidth().card(scheme.surface)
                        .then(if (open) Modifier.clickable { onOpen(SudokuTraining.EXAM) } else Modifier)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (passed(record, SudokuTraining.EXAM)) {
                        Icon(Icons.Default.Check, "Passed", tint = scheme.primary, modifier = Modifier.size(20.dp))
                    } else Spacer(Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Final board", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                        Text(
                            if (open) "A whole Sudoku that needs every technique above."
                            else "Opens when every technique above is passed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private enum class Verdict { RIGHT, HINTED, WRONG }

/**
 * One technique's practice: a board whose next hint step is [technique]. The player places a digit; a right one (the
 * step's, or any correct digit, since the board has no simpler step anywhere) adds to the run, a wrong one ends it and
 * shows the reasoning. [SudokuTraining.RUN] in a row calls [onPass]. A board where a hint was opened neither counts nor
 * breaks the run.
 */
@Composable
fun TechniquePractice(technique: String, record: Set<String>, onPass: (String) -> Unit, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = Color(Sudoku.accent)
    val chime = LocalChime.current
    val scope = rememberCoroutineScope()
    var index by rememberSaveable(technique) { mutableIntStateOf(0) }
    var run by rememberSaveable(technique) { mutableIntStateOf(0) }
    // The line earned by the pass this session, kept until the next board.
    var earned by remember(technique) { mutableStateOf<String?>(null) }
    val practice by produceState<SudokuTraining.Practice?>(null, technique, index) {
        value = null
        value = withContext(Dispatchers.Default) { SudokuTraining.practice(technique, index) }
    }
    val p = practice
    var board by remember(p) { mutableStateOf(p?.board) }
    var verdict by remember(p) { mutableStateOf<Verdict?>(null) }
    var hinted by remember(p) { mutableStateOf(false) }
    val hints = rememberHintSession("$technique|$index")
    val subtitle = if (passed(record, technique)) "Passed" else "$run of ${SudokuTraining.RUN} in a row"

    fun judge(next: SudokuState) {
        val now = board ?: return
        val placed = next.cells.indices.firstOrNull { next.cells[it] != 0 && now.cells[it] == 0 }
        board = next
        if (placed == null || p == null) return
        hints.clear()
        val right = next.cells[placed] == p.board.solution[placed]
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
                if (run >= SudokuTraining.RUN && !passed(record, technique)) {
                    earned = newlyTrained(record, technique).joinToString(" ") { "Achievement: ${it.title}!" }
                    onPass(technique)
                }
            }
            else -> Unit
        }
    }

    // The Hint button's and the hint panel's next step: one path.
    fun onHint() {
        val now = board ?: return
        scope.launch { hints.tap(Sudoku, now, onOpened = { hinted = true }, onApply = { judge(it as SudokuState) }) }
    }

    TitledScreen(titleOf(technique), subtitle, onBack) {
        val b = board
        if (p == null || b == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Finding a board...", color = scheme.onSurfaceVariant)
            }
        } else {
            if (verdict == null) WatchHint(hints, Sudoku, b)
            val highlight = when (verdict) {
                null -> hints.highlight
                else -> BoardHighlight(strong = setOf(p.step.cell), soft = p.step.cited - p.step.cell, warning = false)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalBoardHighlight provides highlight) {
                    Sudoku.Board(b, { judge(it as SudokuState) }, interactive = verdict == null)
                }
            }
            Box(Modifier.fillMaxWidth().height(SlotHeight).padding(horizontal = 18.dp)) {
                if (verdict == null && hints.active) HintPanel(hints, accent, ::onHint, Modifier.fillMaxSize())
                else {
                    val step = p.step
                    val text = when (verdict) {
                        null -> SudokuTraining.EXPLANATIONS.getValue(technique)
                        Verdict.WRONG -> "Not quite. ${step.explanation}"
                        Verdict.HINTED -> "That's it, with a hint, so it doesn't count. ${step.explanation}"
                        Verdict.RIGHT -> when {
                            run >= SudokuTraining.RUN && earned != null ->
                                "Passed! ${SudokuTraining.RUN} in a row. ${earned.orEmpty()}".trim()
                            else -> "That's it. ${step.explanation}"
                        }
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
                enabled = p != null,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = BoardHues.onFill(accent)),
                modifier = Modifier.weight(1f),
            ) { Text(if (verdict == null) "Another board" else "Next board") }
        }
    }
}

private val StateJson = Json { ignoreUnknownKeys = true }

private val BoardSaver = Saver<SudokuState, String>(
    save = { StateJson.encodeToString(SudokuState.serializer(), it) },
    restore = { runCatching { StateJson.decodeFromString(SudokuState.serializer(), it) }.getOrNull() },
)

/**
 * The final board: a whole Expert Sudoku whose walk needs every technique ([SudokuTraining.exam]). Solving it without
 * "Show me" calls [onPass]; hints are allowed. Kept across a rotation, not across leaving: it is one board, not a save.
 */
@Composable
fun FinalBoard(record: Set<String>, onPass: (String) -> Unit, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = Color(Sudoku.accent)
    val chime = LocalChime.current
    val scope = rememberCoroutineScope()
    val start = remember { SudokuTraining.exam() }
    var board by rememberSaveable(stateSaver = BoardSaver) { mutableStateOf(start) }
    var shown by rememberSaveable { mutableStateOf(false) }
    var earned by remember { mutableStateOf<String?>(null) }
    val hints = rememberHintSession("exam")
    WatchHint(hints, Sudoku, board)
    fun onHint() {
        val now = board
        scope.launch { hints.tap(Sudoku, now, onOpened = {}, onApply = { shown = true; board = it as SudokuState }) }
    }
    LaunchedEffect(board.solved) {
        if (!board.solved) return@LaunchedEffect
        hints.clear()
        chime()
        if (!shown && !passed(record, SudokuTraining.EXAM)) {
            earned = newlyTrained(record, SudokuTraining.EXAM).joinToString(" ") { "Achievement: ${it.title}!" }
            onPass(SudokuTraining.EXAM)
        }
    }
    TitledScreen("Final board", if (passed(record, SudokuTraining.EXAM)) "Passed" else "Every technique, in one board", onBack) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalBoardHighlight provides hints.highlight) {
                Sudoku.Board(board, { board = it as SudokuState }, interactive = !board.solved)
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
 * "Practise this" beside a Sudoku hint's Close, once it explains a technique Training has (not a mistake, not the
 * fallback). Provided to the play screen's hint panel through `LocalHintAction` by the app, for Sudoku only.
 */
@Composable
fun PractiseLink(d: Deduction, stage: HintStage, onPractise: (String) -> Unit) {
    if (stage != HintStage.EXPLAIN || d.mistake || d.fallback || d.technique !in SudokuTraining.TECHNIQUES) return
    TextButton(onClick = { onPractise(d.technique) }) { Text("Practise this", color = MaterialTheme.colorScheme.primary) }
}
