package com.joebywan.daybook.ui.training

import com.joebywan.daybook.ui.theme.BoardHues
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.joebywan.daybook.core.TrainingRecord
import com.joebywan.daybook.ui.LocalHaptics
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.play.Confetti
import com.joebywan.daybook.ui.play.EarnedAchievement
import com.joebywan.daybook.ui.play.FinishedFrame
import com.joebywan.daybook.ui.play.NextKind
import com.joebywan.daybook.ui.play.NextOption
import com.joebywan.daybook.ui.play.Praise
import com.joebywan.daybook.ui.stats.AchievementBadge
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Course
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.TeachStep
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Height of the slot under the board: the explanation, a hint or a verdict, never moving the board. Five lines of body
 * text, a little under the walkthrough's, so the board keeps more of a short phone; longer text scrolls. Below 700 dp of
 * window it gives way in proportion (110 dp at 537, a phone browser with both toolbars), as the play screen's hint slot
 * once did; fixed for a given window, so feedback never moves the board.
 */
@Composable
private fun slotHeight(): Dp {
    val window = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    return (SlotHeight * (window / 700.dp)).coerceIn(96.dp, SlotHeight)
}

private val SlotHeight = 144.dp

/** Render-harness hooks (`&run=N`, `&pass` on the web build's training practice): the run a practice opens on, and a pass's celebration showing. */
var runPreview = 0
var passPreview = false

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

internal enum class Verdict { RIGHT, HINTED, WRONG, ASIDE }

/**
 * The slot's line after a move: a wrong one shows [step], the reasoning there was to find; a right one explains [made],
 * the step the player made ([Course.made]), which may be another square or instance than [step]'s. The owner read
 * "That's it" followed by the explanation of a different square as being told they were wrong and should have done that.
 */
internal fun verdictLine(verdict: Verdict, step: TeachStep, made: TeachStep): String = when (verdict) {
    Verdict.ASIDE -> "" // its own line, [Course.aside]
    Verdict.WRONG -> "Not quite. ${step.explanation}"
    Verdict.HINTED -> "That's it, with a hint, so it doesn't count. ${made.explanation}"
    Verdict.RIGHT -> "That's it. ${made.explanation}"
}

/** The achievements passing [technique] earns: only the first time it is passed ([record] is from before the pass). */
private fun firstTime(course: Course, record: Set<String>, technique: String): List<EarnedAchievement> =
    if (course.passed(record, technique)) emptyList()
    else newlyTrained(record, course.id, technique).map { EarnedAchievement(it.id, it.title, it.description) }

/**
 * What a pass's popup shows: [praise] for the game's finished frame ([FinishedFrame]); with nothing earned, its centre
 * is achievement [badge] ([badgeEarned] or a silhouette) over [passed] of [of] ticks (none when [of] is 0), the last
 * ticking in when [fresh] (this pass added it), then `praise.toGo`, the progress line. Saved whole, so a recreation
 * neither drops it nor re-earns it.
 */
@Serializable
internal data class Cheer(
    val praise: Praise,
    val badge: String,
    val badgeEarned: Boolean,
    val passed: Int = 0,
    val of: Int = 0,
    val fresh: Boolean = false,
)

/**
 * The celebration when a run has just reached [Training.RUN], or null: every time it does, not only the first, so
 * practising a technique already passed still ends in "Passed!" ([record] is before this pass; achievements only once).
 * Always with the progress to the course's next achievement (the owner: "show their progress to the achievement"):
 * "<Puzzle> trained: n of m techniques" under its badge; the pass that earns it shows it as the game shows a new
 * achievement, then points on to the final board.
 */
internal fun passCheer(course: Course, record: Set<String>, technique: String, run: Int): Cheer? {
    if (run != Training.RUN) return null
    val after = record + TrainingRecord.entry(course.id, technique)
    val name = course.puzzle.displayName
    val first = !course.passed(record, technique)
    val done = course.passedCount(after)
    val all = course.allPassed(after)
    val progress = "$name trained: $done of ${course.techniques.size} techniques"
    val examOpen = course.hasExam && !course.passed(after, Training.EXAM)
    val toGo = when {
        all && first -> if (examOpen) "Final board unlocked. Solve it to become a $name graduate." else null
        all && examOpen -> "$progress. The final board is open: solve it to become a $name graduate."
        else -> progress
    }
    return Cheer(
        Praise(
            title = "Passed!",
            lines = listOf(course.titleOf(technique), "${Training.RUN} in a row"),
            achievements = firstTime(course, record, technique),
            toGo = toGo,
        ),
        badge = "${course.id}Trained", badgeEarned = all, passed = done, of = course.techniques.size, fresh = first,
    )
}

/**
 * The celebration for solving the final board without a Show me, every time; its achievement only the first. The
 * progress beyond it is Training's as a whole: courses graduated, this one included.
 */
internal fun graduateCheer(course: Course, record: Set<String>): Cheer {
    val tally = Training.tally(record + TrainingRecord.entry(course.id, Training.EXAM))
    return Cheer(
        Praise(
            title = "Graduated!",
            big = true,
            lines = listOf("${course.puzzle.displayName} graduate", "The final board, solved"),
            achievements = firstTime(course, record, Training.EXAM),
            toGo = "Training: ${tally.graduated} of ${tally.courses} courses graduated",
        ),
        badge = "${course.id}Graduate", badgeEarned = true,
    )
}

private fun Cheer.json() = StateJson.encodeToString(Cheer.serializer(), this)
private fun cheerOf(json: String?) = json?.let { runCatching { StateJson.decodeFromString(Cheer.serializer(), it) }.getOrNull() }

/**
 * A pass's celebration, as a solved game's: the finished frame over the screen (the board keeps its box beneath), the
 * confetti once ([played], saved by the caller), the badge flipping in, all as reduced motion allows. Tapping outside,
 * the banner or [done]'s button, or back, all return ([onDone]), as the old timed return did.
 */
@Composable
private fun Celebration(cheer: Cheer, accent: Color, done: String, played: Boolean, onPlayed: () -> Unit, onDone: () -> Unit) {
    val praise = cheer.praise
    val celebrate = !played && !LocalReducedMotion.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone))
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(vertical = 10.dp)) {
            FinishedFrame(
                seconds = null,
                hints = 0,
                praise = praise,
                celebrate = celebrate,
                accent = accent,
                options = listOf(NextOption(done, NextKind.DONE, null)),
                onOption = { onDone() },
                onHide = onDone,
            ) { size ->
                AchievementBadge(cheer.badge, cheer.badgeEarned, Modifier.width(size))
                if (cheer.of > 0) {
                    Spacer(Modifier.height(8.dp))
                    TickBoxes(cheer.passed, cheer.of, accent, "${cheer.passed} of ${cheer.of} techniques passed",
                        fresh = cheer.fresh && celebrate, box = 16.dp)
                }
            }
        }
        Confetti(played = played, full = praise.full, onPlayed = onPlayed)
    }
}

/** How long a pass's popup waits before the technique it added ticks in. */
private const val FRESH_MS = 450L

/**
 * [done] of [of] boxes: ticked for each done, hollow for each still to go, read as [label]. A box pops as it ticks (not
 * under reduced motion); fixed in dp, so they never move the board. [fresh]: the last one ticks in just after they show.
 * The run in a practice's header ([Training.RUN] of them) and a course's techniques in a pass's popup.
 */
@Composable
internal fun TickBoxes(done: Int, of: Int, accent: Color, label: String, modifier: Modifier = Modifier, fresh: Boolean = false, box: Dp = 20.dp) {
    val target = done.coerceIn(0, of)
    var shown by remember { mutableIntStateOf(if (fresh) target - 1 else target) }
    LaunchedEffect(target) {
        if (fresh) delay(FRESH_MS)
        shown = target
    }
    Row(modifier.clearAndSetSemantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(box / 4)) {
        repeat(of) { TickBox(it < shown, accent, box) }
    }
}

/** The run so far, in a practice's header: "n of 3 in a row". */
@Composable
internal fun RunBoxes(run: Int, accent: Color, modifier: Modifier = Modifier) =
    TickBoxes(run, Training.RUN, accent, "${run.coerceIn(0, Training.RUN)} of ${Training.RUN} in a row", modifier, box = 16.dp)

@Composable
private fun TickBox(ticked: Boolean, accent: Color, box: Dp) {
    val shape = RoundedCornerShape(box / 4)
    val still = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    var was by remember { mutableStateOf(ticked) }
    LaunchedEffect(ticked) {
        if (ticked && !was && !still) {
            scale.snapTo(1.4f)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
        was = ticked
    }
    Box(
        Modifier.size(box).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(shape)
            .then(if (ticked) Modifier.background(accent) else Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (ticked) Icon(Icons.Default.Check, null, tint = BoardHues.onFill(accent), modifier = Modifier.size(box * 0.8f))
    }
}

/**
 * One technique's practice: a board whose next hint step is [technique]. The player makes a move; [Course.judge] says
 * whether it is right (adds to the run), wrong (ends it and shows the reasoning) or neither yet (play on: one cross of a
 * step that crosses three). [Training.RUN] in a row calls [onPass] (the first time) and celebrates, as a solved game is
 * celebrated ([Celebration]); its button, labelled [done], is [onBack]: the owner, "send the user back to the list when
 * they achieve the goal". The same for every drill. A board where a hint was opened neither counts nor breaks the run.
 */
@Composable
fun TechniquePractice(
    course: Course,
    technique: String,
    record: Set<String>,
    onPass: (String) -> Unit,
    onBack: () -> Unit,
    done: String = "Back to ${course.puzzle.displayName} training",
) {
    val puzzle = course.puzzle
    val scheme = MaterialTheme.colorScheme
    val accent = Color(puzzle.accent)
    val chime = LocalChime.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val key = "${course.id}|$technique"
    var index by rememberSaveable(key) { mutableIntStateOf(0) }
    var run by rememberSaveable(key) { mutableIntStateOf(if (passPreview) Training.RUN else runPreview) }
    // The celebration showing ([passCheer], as JSON) and whether its confetti has played: saved, so a recreation neither
    // drops it nor plays it again.
    var earned by rememberSaveable(key) { mutableStateOf(if (passPreview) passCheer(course, record, technique, Training.RUN)?.json() else null) }
    var confettiPlayed by rememberSaveable(key) { mutableStateOf(false) }
    val practice by produceState<Course.Practice?>(null, key, index) {
        value = null
        value = withContext(Dispatchers.Default) { course.practice(technique, index) }
    }
    val p = practice
    var board by remember(p) { mutableStateOf(p?.board) }
    var verdict by remember(p) { mutableStateOf<Verdict?>(null) }
    var hinted by remember(p) { mutableStateOf(false) }
    // The step the player's right move made ([Course.made]).
    var made by remember(p) { mutableStateOf<TeachStep?>(null) }
    // A move that works but that the board can no longer judge ([Course.aside]): its note; it neither counts nor breaks the run.
    var aside by remember(p) { mutableStateOf<String?>(null) }
    // A right part of a step not finished yet ([Course.progress]): how far along, in the slot; never a verdict.
    var progress by remember(p) { mutableStateOf<String?>(null) }
    val hints = rememberHintSession("$key|$index")
    val subtitle = if (course.passed(record, technique)) "Passed" else "$run of ${Training.RUN} in a row"

    fun settle(right: Boolean, step: TeachStep) {
        hints.clear()
        made = step
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
                val cheer = passCheer(course, record, technique, run)
                if (cheer != null) {
                    earned = cheer.json()
                    haptics.success()
                    if (!course.passed(record, technique)) onPass(technique)
                }
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
            var note: String? = null
            var along: String? = null
            val (right, step) = withContext(Dispatchers.Default) {
                course.judge(p, next)?.let { it to (if (it) course.made(p, next) else p.step) }
                    ?: run { note = course.aside(p, next); if (note == null) along = course.progress(p, next); null }
            } ?: run {
                if (board === next && verdict == null) {
                    progress = along
                    if (note != null) {
                        hints.clear()
                        aside = note
                        verdict = Verdict.ASIDE
                    }
                }
                return@launch
            }
            if (!right) delay(course.drill.settleMillis)
            if (board === next && verdict == null) settle(right, step)
        }
    }

    // The Hint button's and the hint panel's next step: one path.
    fun onHint() {
        val now = board ?: return
        scope.launch { hints.tap(puzzle, now, onOpened = { hinted = true }, onApply = { judge(it) }) }
    }

    Box(Modifier.fillMaxSize()) {
    TitledScreen(course.titleOf(technique), subtitle, onBack, beside = { RunBoxes(run, accent) }) {
        val b = board
        if (p == null || b == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Finding a board...", color = scheme.onSurfaceVariant)
            }
        } else {
            if (verdict == null) WatchHint(hints, puzzle, b)
            val highlight = when (verdict) {
                null -> hints.highlight
                Verdict.ASIDE -> BoardHighlight.None
                // The step shown: the one to find after a wrong move, the player's own after a right one.
                else -> (made ?: p.step).let { BoardHighlight(strong = it.targets, soft = it.cited - it.targets, warning = false) }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().hintKeys(verdict == null, hints, if (puzzle.offersHints) ::onHint else null),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalBoardHighlight provides highlight) {
                    puzzle.Board(b, { judge(it) }, interactive = verdict == null)
                }
            }
            Box(Modifier.fillMaxWidth().height(slotHeight()).padding(horizontal = 18.dp)) {
                if (verdict == null && hints.active) HintPanel(hints, accent, ::onHint, Modifier.fillMaxSize())
                else {
                    val step = p.step
                    val v = verdict
                    val text = when {
                        v == null -> progress ?: course.explanations.getValue(technique)
                        v == Verdict.ASIDE -> aside.orEmpty()
                        else -> verdictLine(v, step, made ?: step)
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
    cheerOf(earned)?.let { Celebration(it, accent, done, confettiPlayed, { confettiPlayed = true }, onBack) }
    }
}

private val StateJson = Json { ignoreUnknownKeys = true }

private val BoardSaver = Saver<PuzzleState, String>(
    save = { StateJson.encodeToString(PuzzleState.serializer(), it) },
    restore = { runCatching { StateJson.decodeFromString(PuzzleState.serializer(), it) }.getOrNull() },
)

/**
 * The final board: a whole Expert board whose walk needs every technique ([Course.exam]). Solving it without
 * "Show me" calls [onPass] (the first time) and celebrates ([graduateCheer]); its button is [onBack]. Hints are allowed. Kept across a rotation, not across leaving:
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
    // Saved: the solve is answered once (chime, haptics, pass, celebration), not again after a recreation.
    var answered by rememberSaveable(course.id) { mutableStateOf(false) }
    var earned by rememberSaveable(course.id) { mutableStateOf(if (passPreview) graduateCheer(course, record).json() else null) }
    var confettiPlayed by rememberSaveable(course.id) { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    val hints = rememberHintSession("${course.id}|exam")
    WatchHint(hints, puzzle, board)
    fun onHint() {
        val now = board
        scope.launch { hints.tap(puzzle, now, onOpened = {}, onApply = { shown = true; board = it }) }
    }
    LaunchedEffect(board.solved) {
        if (!board.solved || answered) return@LaunchedEffect
        answered = true
        hints.clear()
        chime()
        // Passed (again or for the first time): celebrated, as a technique is; a solve with a Show me stays, to start again.
        if (!shown) {
            earned = graduateCheer(course, record).json()
            haptics.success()
            if (!course.passed(record, Training.EXAM)) onPass(Training.EXAM)
        }
    }
    Box(Modifier.fillMaxSize()) {
    TitledScreen("Final board", if (course.passed(record, Training.EXAM)) "Passed" else "Every technique, in one board", onBack) {
        Box(
            Modifier.weight(1f).fillMaxWidth().hintKeys(!board.solved, hints, if (puzzle.offersHints) ::onHint else null),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalBoardHighlight provides hints.highlight) {
                puzzle.Board(board, { board = it }, interactive = !board.solved)
            }
        }
        Box(Modifier.fillMaxWidth().height(slotHeight()).padding(horizontal = 18.dp)) {
            if (hints.active) HintPanel(hints, accent, ::onHint, Modifier.fillMaxSize())
            else Text(
                when {
                    board.solved && shown -> "Solved, with a Show me along the way. Start again to solve it on your own."
                    board.solved -> "Solved!"
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
                    answered = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = BoardHues.onFill(accent)),
                modifier = Modifier.weight(1f),
            ) { Text("Start again") }
        }
    }
    cheerOf(earned)?.let { Celebration(it, accent, "Back to ${puzzle.displayName} training", confettiPlayed, { confettiPlayed = true }, onBack) }
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
