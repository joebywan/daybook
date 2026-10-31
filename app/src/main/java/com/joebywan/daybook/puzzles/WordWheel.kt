package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.reportHighlight
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.ArcadeFill
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.arcadeGlow
import com.joebywan.daybook.ui.theme.arcadeLift
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.ui.LocalBoardTones
import com.joebywan.daybook.ui.rememberMoveFeel

/**
 * Word Wheel: a wheel of five to seven letters and a small crossword of empty slots. Spell words by swiping across the
 * wheel (or tapping its letters in turn); a word the grid holds fills its slot, any other real word is a bonus word.
 * Fill every slot to finish. There are no lives and no way to lose.
 *
 * Nothing to prove about a board beyond its layout: the grid's words are the answer. The rules and the generator are in
 * [WordWheelLogic], the lists in [WheelWords], the hints in [WordWheelTeacher]; this file is the board.
 *
 * Highlight indices: grid squares `row * width + col`, wheel letter `i` of [WordWheelState.wheel] is
 * [WordWheelTeacher.WHEEL] + i (the letter, not its place on screen, which Shuffle moves).
 */
object WordWheel : PuzzleType {

    override val id = "wordwheel"
    override val displayName = "Word Wheel"
    override val tagline = "Spell every word in the grid from the wheel"
    override val accent = 0xFF2891AC
    override val rules = listOf(
        "Spell words from the wheel by swiping across its letters, or tap them in turn and tap the word to try it.",
        "Each letter on the wheel is used at most once in a word, and no word is shorter than the grid's shortest.",
        "A word the grid holds fills its slot. Fill every slot to finish.",
        "Other real words count as bonus words. They are never needed.",
        "Shuffle moves the letters round the wheel and changes nothing else.",
    )

    override val keyboardHelp = listOf(
        "Type letters from the wheel, Backspace deletes, Enter tries the word, Escape clears it.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        WordWheelLogic.generateVerified(seed, difficulty) ?: WordWheelLogic.lastResort(difficulty)

    override fun teach(state: PuzzleState): Deduction? = WordWheelTeacher.deduction(state as WordWheelState)

    // ---- walkthrough --------------------------------------------------------------------------

    /**
     * The walkthrough's board, laid by hand from the wheel BRAIN:
     *
     * ```
     * . B . R .
     * . A . A .
     * B R A I N
     * . N . N .
     * ```
     *
     * BRAIN is the only five-letter grid word the wheel spells; with its R showing, BARN is the only four-letter one with
     * R third; with its I showing, RAIN the only one with I third. `WordWheelTeachingTest` proves those claims against the
     * lists, and that hints alone finish the free-play frame without the clue.
     */
    internal fun tutorialBoard(): WordWheelState = WordWheelState(
        wheel = "irnba",
        width = 5,
        height = 4,
        slots = listOf(
            WheelSlot("brain", 2, 0, true),
            WheelSlot("barn", 0, 1, false),
            WheelSlot("rain", 0, 3, false),
        ),
    )

    /** Accepts a board with exactly these words found and these bonus words, nothing else. */
    private fun only(found: List<String>, bonus: List<String>): (PuzzleState) -> Boolean = { next ->
        next is WordWheelState && next.found == found && next.bonus == bonus
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val s0 = tutorialBoard()
        val s1 = s0.enter("brain")
        val s2 = s1.enter("barn")
        val s3 = s2.enter("bran")
        fun wheelOf(word: String) = WordWheelTeacher.letters(s0.wheel, word)
        fun slot(k: Int) = s0.slots[k].cells(s0.width).toSet()
        listOf(
            TutorialFrame(
                state = s0,
                caption = "Spell words from the wheel's letters to fill the grid. Each slot holds one word, and the longest " +
                    "uses every letter on the wheel.",
                highlight = BoardHighlight(strong = slot(0), soft = wheelOf("brain")),
            ),
            TutorialFrame(
                state = s0,
                caption = "Swipe across B, R, A, I and N without lifting, then let go.",
                highlight = BoardHighlight(strong = wheelOf("brain"), soft = slot(0)),
                accepts = only(listOf("brain"), emptyList()),
                retry = "Swipe across the glowing letters in order: B, R, A, I, N.",
                done = "BRAIN fills its slot.",
            ),
            TutorialFrame(
                state = s1,
                caption = "Crossing letters help: this four-letter slot has R third, and BARN is the only word the wheel " +
                    "spells like that. This time tap B, A, R and N, then tap the word.",
                highlight = BoardHighlight(strong = slot(1), soft = wheelOf("barn")),
                accepts = only(listOf("brain", "barn"), emptyList()),
                retry = "Tap the glowing letters in order: B, A, R, N. Then tap the word.",
                done = "BARN. Swipe or tap, whichever you like.",
            ),
            TutorialFrame(
                state = s2,
                caption = "A real word the grid does not hold is a bonus word: counted, never needed. Spell BRAN.",
                highlight = BoardHighlight(strong = wheelOf("bran")),
                accepts = only(listOf("brain", "barn"), listOf("bran")),
                retry = "Spell the glowing letters: B, R, A, N.",
                done = "BRAN is a bonus word.",
            ),
            TutorialFrame(
                state = s3,
                caption = "Your turn: fill the last slot. Stuck? Hint gives you its clue.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    // ---- the home motif -----------------------------------------------------------------------

    /** A cross of found squares over a wheel of five letters with a swipe across three of them. Fixed, never generated. */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.HUE_PAIRS[0]
        val square = BoardHues.fill(hueB, dark)
        val disc = scheme.surface
        val stroke = BoardHues.mark(hueA)
        val ring = BoardHues.fill(hueA, dark).copy(alpha = if (dark) 0.6f else 0.45f)
        Canvas(modifier) {
            val u = minOf(size.width / 5f, size.height / 5.6f)
            val ox = (size.width - u * 5f) / 2f
            val oy = (size.height - u * 5.6f) / 2f
            // The grid: four across, crossed by three down.
            fun sq(r: Int, c: Int) = drawRoundRect(square, Offset(ox + (c + 0.5f) * u + 1f, oy + r * u * 0.8f + 1f), Size(u * 0.8f - 2f, u * 0.8f - 2f), CornerRadius(u * 0.12f))
            for (c in 0 until 4) sq(1, c)
            sq(0, 2)
            sq(2, 2)
            // The wheel.
            val centre = Offset(ox + u * 2.5f, oy + u * 3.85f)
            drawCircle(ring, radius = u * 1.7f, center = centre)
            val spots = List(5) { k -> WheelGeometry.spot(centre, u * 1.1f, k, 5) }
            drawLine(stroke, spots[0], spots[2], strokeWidth = u * 0.18f, cap = StrokeCap.Round)
            drawLine(stroke, spots[2], spots[4], strokeWidth = u * 0.18f, cap = StrokeCap.Round)
            spots.forEachIndexed { k, p -> drawCircle(if (k % 2 == 0) stroke else disc, radius = u * 0.42f, center = p) }
        }
    }

    // ---- the board ----------------------------------------------------------------------------

    private val MAX_CELL = 52.dp
    private val MAX_WHEEL = 290.dp
    private val LINE = 22.dp
    private val PILL = 40.dp

    /** One line, always laid out: a refusal or a bonus, else how many words are found. */
    private fun headerOf(s: WordWheelState, notice: String?): String = notice ?: buildString {
        append("${s.found.size} of ${s.slots.size} words")
        if (s.bonus.isNotEmpty()) append(" · ${s.bonus.size} bonus")
    }

    /** The bonus words found, newest first, so the one just made is always the one showing. */
    private fun bonusLine(s: WordWheelState): String =
        if (s.bonus.isEmpty()) "" else "Bonus: " + s.bonus.asReversed().joinToString(", ") { it.uppercase() }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as WordWheelState
        // The gesture below outlives every state (it is keyed on the wheel, not the board): it reads the board through
        // this, never a captured `s` (CLAUDE.md, Snap's lesson).
        val latest by rememberUpdatedState(s)
        val a = ArcadeBoard.current
        // Hint rings sit on the frame and the wheel, both dark, so they are amber rather than the page's ink.
        val (highlight, glow, pulse) = rememberHintPaint(ink = a.selected.from)
        val playable = interactive && !s.solved
        val measurer = rememberTextMeasurer()

        // A found word's squares settle, and the glow runs along it.
        val units = remember(s.wheel) { s.slots.map { it.cells(s.width).toIntArray() } }
        val feel = rememberMoveFeel(s.shown().toList(), units) { s.slots[it].word in s.found }

        // The word being spelled (wheel positions) and the finger, transient: never PuzzleState, so a refused word costs nothing.
        var picked by remember(s.wheel) { mutableStateOf(listOf<Int>()) }
        var finger by remember { mutableStateOf<Offset?>(null) }
        // Each letter added rises a step of the scale, each taken back falls one. Silent in the walkthrough (LocalBoardTones).
        val tones = LocalBoardTones.current
        val sounded = remember { intArrayOf(0) }
        LaunchedEffect(picked.size) {
            wheelToneStep(sounded[0], picked.size)?.let { tones(Tones.wheel(it)) }
            sounded[0] = picked.size
        }
        var notice by remember { mutableStateOf<String?>(null) }
        var noticeIsError by remember { mutableStateOf(false) }
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(1600)
                notice = null
            }
        }
        var showBonus by remember(s.wheel) { mutableStateOf(false) }
        // Shuffle only reorders the display; the count is all it needs to remember.
        var shuffles by rememberSaveable(s.wheel) { mutableIntStateOf(0) }
        val order = remember(s.wheel, shuffles) {
            if (shuffles == 0) s.wheel.indices.toList() else Rng(shuffles * 7919L + s.wheel.length).shuffled(s.wheel.indices.toList())
        }

        fun say(text: String, error: Boolean) {
            notice = text
            noticeIsError = error
        }

        fun submit(path: List<Int>) {
            val now = latest
            val word = path.map { now.wheel[it] }.joinToString("")
            picked = emptyList()
            if (word.isEmpty()) return
            when (WordWheelLogic.judge(now, word)) {
                is WheelEntry.Fills -> {
                    notice = null
                    onState(now.enter(word))
                }
                WheelEntry.Bonus -> {
                    say("Bonus word: ${word.uppercase()}", false)
                    onState(now.enter(word))
                }
                WheelEntry.Found -> say("${word.uppercase()} is already found", true)
                WheelEntry.NotAWord -> say("${word.uppercase()} is not in the word list", true)
                WheelEntry.TooShort -> say("Words here have ${now.shortestWord} letters or more", true)
                WheelEntry.NotOnWheel -> say("The wheel cannot spell ${word.uppercase()}", true)
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .boardKeys(playable) { key, _, repeat ->
                    val action = wordWheelKeyAction(key) ?: return@boardKeys false
                    if (!repeat) when (action) {
                        is WheelKeyAction.Letter -> typeWheelLetter(latest.wheel, picked, action.letter)?.let { picked = it }
                        WheelKeyAction.Backspace -> picked = picked.dropLast(1)
                        WheelKeyAction.Clear -> picked = emptyList()
                        WheelKeyAction.Enter -> submit(picked)
                    }
                    true
                }
                .padding(horizontal = 12.dp),
        ) {
            // Sized from both axes: the wheel takes what the width and a share of the height allow, the grid the rest.
            val fixed = LINE * 2 + PILL + 12.dp
            val tall = if (constraints.hasBoundedHeight) maxHeight else maxWidth * 1.6f
            val wheelSize = minOf(maxWidth * 0.64f, (tall - fixed) * 0.46f, MAX_WHEEL).coerceAtLeast(0.dp)

            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // A notice is a chip, so a refusal differs by shape as well as colour; the line's room never changes.
                Box(Modifier.fillMaxWidth().height(LINE), contentAlignment = Alignment.Center) {
                    val chip = if (notice != null) (if (noticeIsError) ArcadeFill(a.error, a.error, a.errorInk) else a.control) else null
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .then(if (chip != null) Modifier.background(chip.brush).padding(horizontal = 10.dp) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            headerOf(s, notice),
                            style = MaterialTheme.typography.labelLarge,
                            color = chip?.ink ?: a.squareInk,
                            fontWeight = if (chip != null) FontWeight.Bold else null,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val cell = minOf(maxWidth / s.width, maxHeight / s.height, MAX_CELL).coerceAtLeast(0.dp)
                    Canvas(Modifier.size(cell * s.width, cell * s.height).highlightGrid(s.width, s.height)) {
                        drawGrid(s, cell.toPx(), highlight, glow, pulse.value, a, measurer, feel)
                    }
                }

                // The word being spelled. Tapping it tries it; its room is always kept, so nothing moves when it fills.
                Box(Modifier.fillMaxWidth().height(PILL).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    if (picked.isNotEmpty()) {
                        Box(
                            Modifier
                                .arcadeLift(a.letter, 16.dp)
                                .clip(RoundedCornerShape(50))
                                .background(a.letter.brush)
                                .border(2.dp, a.selectedEdge, RoundedCornerShape(50))
                                .clickable(enabled = playable) { submit(picked) }
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                picked.map { s.wheel[it].uppercaseChar() }.joinToString(""),
                                color = a.letter.ink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().height(wheelSize).keepClear(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SideButton("↻", playable) { shuffles++ }
                    Canvas(
                        Modifier
                            .size(wheelSize)
                            .reportHighlight { size, h ->
                                if ((h.strong + h.soft).any { it >= WordWheelTeacher.WHEEL }) Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()) else null
                            }
                            .pointerInput(playable, s.wheel, order) {
                                if (!playable) return@pointerInput
                                val geometry = WheelGeometry(size.width.toFloat(), order.size)
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val start = geometry.hit(down.position)?.let { order[it] }
                                    if (start == null) {
                                        // The middle of the wheel tries a tapped word.
                                        if (geometry.inMiddle(down.position) && picked.isNotEmpty()) {
                                            down.consume()
                                            submit(picked)
                                        }
                                        return@awaitEachGesture
                                    }
                                    down.consume()
                                    val before = picked
                                    var path = listOf(start)
                                    var swiped = false
                                    finger = down.position
                                    if (before.isEmpty()) picked = path
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        change.consume()
                                        finger = change.position
                                        val at = geometry.hit(change.position)?.let { order[it] } ?: continue
                                        val next = swipeWheelLetter(path, at)
                                        if (next != path) {
                                            path = next
                                            if (path.size >= 2) swiped = true
                                            if (swiped) picked = path
                                        }
                                    }
                                    finger = null
                                    if (swiped) submit(path) else picked = tapWheelLetter(before, start)
                                }
                            },
                    ) {
                        drawWheel(s, order, picked, finger, highlight, glow, pulse.value, a, measurer)
                    }
                    SideButton("✕", playable && picked.isNotEmpty()) { picked = emptyList() }
                }

                // A tap opens the whole list; the line's room is always kept, so nothing moves when it fills.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(LINE)
                        .clickable(enabled = s.bonus.isNotEmpty()) { showBonus = true }
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (s.bonus.isNotEmpty()) Row(
                        Modifier.fillMaxHeight().clip(RoundedCornerShape(50)).background(a.control.brush).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            bonusLine(s),
                            style = MaterialTheme.typography.bodySmall,
                            color = a.control.ink,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(" ›", style = MaterialTheme.typography.bodySmall, color = a.control.ink)
                    }
                }
            }
        }

        if (showBonus) {
            AlertDialog(
                onDismissRequest = { showBonus = false },
                title = { Text("Bonus words: ${s.bonus.size}") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        s.bonus.sorted().forEach { Text(it.uppercase(), Modifier.padding(vertical = 2.dp)) }
                    }
                },
                confirmButton = { TextButton(onClick = { showBonus = false }) { Text("Close") } },
            )
        }
    }

    /** A round button beside the wheel, showing a glyph. */
    @Composable
    private fun SideButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
        val a = ArcadeBoard.current
        Box(
            Modifier
                .size(44.dp)
                .then(if (enabled) Modifier.arcadeLift(a.control, 22.dp) else Modifier)
                .clip(CircleShape)
                .then(if (enabled) Modifier.background(a.control.brush) else Modifier.background(a.given))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = if (enabled) a.control.ink else a.givenInk.copy(alpha = 0.45f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }

    /** Where the wheel's letters sit in a square of side [side] px: position `p` of [count], clockwise from the top. */
    internal class WheelGeometry(val side: Float, val count: Int) {
        val centre = Offset(side / 2f, side / 2f)
        val orbit = side * 0.34f
        val disc = side * if (count >= 7) 0.115f else 0.14f

        fun spot(p: Int): Offset = spot(centre, orbit, p, count)

        /** The position under [at], or null. A little generous, since fingers are bigger than discs. */
        fun hit(at: Offset): Int? = (0 until count).firstOrNull { p ->
            val c = spot(p)
            hypot(at.x - c.x, at.y - c.y) <= disc * 1.2f
        }

        fun inMiddle(at: Offset): Boolean = hypot(at.x - centre.x, at.y - centre.y) <= orbit - disc * 1.2f

        companion object {
            /** Place [p] of [count] round a circle of radius [orbit] about [centre], the first at the top. */
            fun spot(centre: Offset, orbit: Float, p: Int, count: Int): Offset {
                val a = -PI / 2 + p * 2 * PI / count
                return Offset(centre.x + (orbit * cos(a)).toFloat(), centre.y + (orbit * sin(a)).toFloat())
            }
        }
    }

    private fun DrawScope.drawGrid(
        s: WordWheelState,
        cell: Float,
        highlight: BoardHighlight,
        glow: Color,
        pulse: Float,
        a: ArcadeBoard,
        measurer: TextMeasurer,
        feel: MoveFeel = MoveFeel.Still,
    ) {
        val used = s.used()
        val shown = s.shown()
        val inset = cell * 0.06f
        val corner = CornerRadius(cell * 0.14f)
        // One path, so the translucent dark frame does not double up or seam where squares meet.
        val frame = Path()
        for (i in used.indices) if (used[i]) frame.addRect(Rect(Offset((i % s.width) * cell, (i / s.width) * cell), Size(cell, cell)))
        drawPath(frame, a.frame)
        for (i in used.indices) {
            if (!used[i]) continue
            val topLeft = Offset((i % s.width) * cell + inset, (i / s.width) * cell + inset)
            val box = Size(cell - 2 * inset, cell - 2 * inset)
            val look = highlight.look(i)
            val alpha = if (look.dim) 0.3f else 1f
            val letter = shown[i]
            feelSquare(feel, i, topLeft, box, Color.White) {
                if (letter != null) {
                    a.letter.glow?.let { arcadeGlow(it.copy(alpha = 0.35f * alpha), topLeft, box, corner.x, (inset / density).dp) }
                    drawRoundRect(a.letter.brush(topLeft, box), topLeft, box, corner, alpha = alpha)
                    val layout = measurer.measure(letter.uppercase(), TextStyle(color = a.letter.ink.copy(alpha = alpha), fontSize = (cell * 0.5f).toSp(), fontWeight = FontWeight.Bold))
                    drawText(layout, topLeft = Offset(topLeft.x + (box.width - layout.size.width) / 2f, topLeft.y + (box.height - layout.size.height) / 2f))
                } else {
                    drawRoundRect(a.square, topLeft, box, corner, alpha = alpha)
                }
            }
            if (look.strong || look.soft) {
                val w = if (look.strong) cell * 0.08f else cell * 0.05f
                drawRoundRect(
                    if (look.strong) glow.copy(alpha = pulse) else glow.copy(alpha = 0.5f),
                    Offset(topLeft.x - w, topLeft.y - w),
                    Size(box.width + 2 * w, box.height + 2 * w),
                    CornerRadius(corner.x + w),
                    style = Stroke(w),
                )
            }
        }
    }

    private fun DrawScope.drawWheel(
        s: WordWheelState,
        order: List<Int>,
        picked: List<Int>,
        finger: Offset?,
        highlight: BoardHighlight,
        glow: Color,
        pulse: Float,
        a: ArcadeBoard,
        measurer: TextMeasurer,
    ) {
        val g = WheelGeometry(size.width, order.size)
        // A gradient rim round a dark well, in both schemes, so the amber path and tiles have something to stand on.
        drawCircle(a.control.brush(Offset.Zero, size), radius = size.width / 2f, center = g.centre)
        drawCircle(wheelWell(a), radius = size.width * 0.488f, center = g.centre)
        // Where each wheel letter is on screen.
        val at = IntArray(order.size)
        order.forEachIndexed { p, i -> at[i] = p }
        if (picked.isNotEmpty()) {
            val stroke = g.disc * 0.32f
            val path = a.selected.brush(Offset.Zero, size)
            for (k in 1 until picked.size) drawLine(path, g.spot(at[picked[k - 1]]), g.spot(at[picked[k]]), stroke, StrokeCap.Round)
            if (finger != null) drawLine(path, g.spot(at[picked.last()]), finger, stroke, StrokeCap.Round, alpha = 0.7f)
        }
        for (p in order.indices) {
            val i = order[p]
            val c = g.spot(p)
            val chosen = i in picked
            // Chosen differs by size and a white ring as well as by colour. Only drawn larger: the hit circle stays.
            val radius = if (chosen) g.disc * 1.08f else g.disc
            val corner = Offset(c.x - radius, c.y - radius)
            val box = Size(radius * 2, radius * 2)
            val fill = if (chosen) a.selected else a.control
            if (chosen) fill.glow?.let { arcadeGlow(it, corner, box, radius, (g.disc * 0.22f / density).dp) }
            drawCircle(fill.brush(corner, box), radius = radius, center = c)
            if (chosen) drawCircle(Color.White, radius = radius, center = c, style = Stroke(g.disc * 0.09f))
            val look = highlight.look(WordWheelTeacher.WHEEL + i)
            if (look.strong || look.soft) {
                val w = g.disc * if (look.strong) 0.16f else 0.1f
                drawCircle(if (look.strong) glow.copy(alpha = pulse) else glow.copy(alpha = 0.5f), radius = radius + w, center = c, style = Stroke(w))
            }
            val layout = measurer.measure(s.wheel[i].uppercase(), TextStyle(color = fill.ink, fontSize = (g.disc * 1.05f).toSp(), fontWeight = FontWeight.Bold))
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
        }
    }

    /** The wheel's dark middle: the frame's indigo in light, the given square's in dark (whose frame is translucent). */
    internal fun wheelWell(a: ArcadeBoard): Color = if (a.frame.alpha == 1f) a.frame else a.given
}
