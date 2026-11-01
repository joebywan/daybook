package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
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
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.boardStyle
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.theme.vivid
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.delay
import kotlin.math.hypot

/**
 * Dwindle: a grid of letter tiles and a dashed list of goal words. Spell words by swiping through tiles side by side or one
 * above the other. Each tile shows how many goal words still run through it and vanishes when the last is found, so a board starts
 * crowded and clears as it is solved. Other real words are bonus words. No lives and no way to lose.
 *
 * The rules and the generator are in [DwindleLogic], the hints in [DwindleTeacher]; this file is the board. Highlight
 * indices: tiles `row * width + col`, dash [DwindleTeacher.DASH] + goal.
 */
object Dwindle : PuzzleType {

    override val id = "dwindle"
    override val displayName = "Dwindle"
    override val tagline = "Find every word, and watch the letters go"
    override val accent = 0xFFA55C09
    override val rules = listOf(
        "Spell words by swiping through touching tiles, across or up and down (never diagonally), or tap them in turn and tap the word.",
        "A word has three letters or more and uses a tile at most once. Tiles never fall or move.",
        "The dashes show the goal words' lengths. Find them all to finish.",
        "Each tile's number is how many goal words still run through it. At zero it vanishes.",
        "Each goal word has only one way on the board, and no order can leave you stuck. Other real words are bonus words: they use nothing up.",
    )

    override val keyboardHelp = listOf(
        "Type a word from the tiles, Backspace deletes, Enter tries the word, Escape clears it.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        DwindleLogic.generateVerified(seed, difficulty) ?: DwindleLogic.lastResort(difficulty)

    override fun teach(state: PuzzleState): Deduction? = DwindleTeacher.deduction(state as DwindleState)

    // ---- walkthrough --------------------------------------------------------------------------

    /**
     * The walkthrough's board, by hand:
     *
     * ```
     * C H T
     * A E F
     * T S I
     * ```
     *
     * `DwindleTeachingTest` proves each goal has one way, that EAT is a real word and no goal, and that hints alone finish the
     * free-play frame without the fallback.
     */
    internal fun tutorialBoard(): DwindleState = DwindleState(
        3, 3, "chtaeftsi",
        listOf(
            DwindleGoal("chest", listOf(0, 1, 4, 7, 6)),
            DwindleGoal("chef", listOf(0, 1, 4, 5)),
            DwindleGoal("fist", listOf(5, 8, 7, 6)),
            DwindleGoal("the", listOf(2, 1, 4)),
            DwindleGoal("cat", listOf(0, 3, 6)),
        ),
        diagonal = false,
    )

    /** Accepts a board with exactly these words found and these bonus words, nothing else. */
    private fun only(found: List<String>, bonus: List<String>): (PuzzleState) -> Boolean = { next ->
        next is DwindleState && next.found == found && next.bonus == bonus
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val s0 = tutorialBoard()
        val s1 = s0.enter("chef")
        val s2 = s1.enter("eat")
        val s3 = s2.enter("cat")
        fun tiles(g: Int) = s0.goals[g].cells.toSet()
        listOf(
            TutorialFrame(
                state = s0,
                caption = "The dashes are the words to find. Each tile's number is how many of them still run through it.",
                highlight = BoardHighlight(strong = s0.goals.indices.map { DwindleTeacher.DASH + it }.toSet(), soft = s0.letters.indices.toSet()),
            ),
            TutorialFrame(
                state = s0,
                caption = "Swipe through touching tiles to spell a word, across or up and down. Swipe C, H, E, F, then let go.",
                highlight = BoardHighlight(strong = tiles(1)),
                accepts = only(listOf("chef"), emptyList()),
                retry = "Swipe through the glowing tiles in order: C, H, E, F.",
                done = "CHEF fills its dashes, and each of its tiles' numbers drops by one.",
            ),
            TutorialFrame(
                state = s1,
                caption = "A real word off the list is a bonus word: counted, never needed, and it uses nothing up. Spell EAT.",
                highlight = BoardHighlight(strong = setOf(4, 3, 6)),
                accepts = only(listOf("chef"), listOf("eat")),
                retry = "Swipe through the glowing tiles in order: E, A, T.",
                done = "EAT is a bonus word. The numbers did not change.",
            ),
            TutorialFrame(
                state = s2,
                caption = "A has one use left. This time tap C, A and T in turn, then tap the word.",
                highlight = BoardHighlight(strong = tiles(4)),
                accepts = only(listOf("chef", "cat"), listOf("eat")),
                retry = "Tap the glowing tiles in order: C, A, T. Then tap the word.",
                done = "CAT. A is used up, so it vanishes.",
            ),
            TutorialFrame(
                state = s3,
                caption = "Your turn: find the last words. Stuck? Hint gives you a clue.",
                freePlay = true,
                done = "Solved. Every tile has gone.",
            ),
        )
    }

    // ---- the home motif -----------------------------------------------------------------------

    /** Three rows of tiles, one vanished, a swipe across three, and the dashes above. Fixed, never generated. */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.HUE_PAIRS[1]
        val tile = BoardHues.fill(hueB, dark)
        val stroke = BoardHues.mark(hueA)
        val dash = scheme.onSurfaceVariant
        Canvas(modifier) {
            val u = minOf(size.width / 3.4f, size.height / 4.2f)
            val ox = (size.width - u * 3.4f) / 2f
            val oy = (size.height - u * 4.2f) / 2f
            for ((row, n) in listOf(0 to 4, 1 to 3)) for (k in 0 until n) {
                val x = ox + (k + row * 2.5f + (if (row == 1) -2.2f else 0f)) * u * 0.42f + (if (row == 1) u * 1.9f else 0f)
                drawLine(dash, Offset(x, oy + u * 0.3f), Offset(x + u * 0.3f, oy + u * 0.3f), u * 0.09f, StrokeCap.Round)
            }
            fun centre(r: Int, c: Int) = Offset(ox + (c + 0.7f) * u * 1.02f - u * 0.02f, oy + (r + 1.1f) * u)
            for (r in 0 until 3) for (c in 0 until 3) {
                if (r == 2 && c == 0) continue
                val at = centre(r, c)
                drawRoundRect(tile, Offset(at.x - u * 0.45f, at.y - u * 0.45f), Size(u * 0.9f, u * 0.9f), CornerRadius(u * 0.14f))
            }
            val trail = listOf(centre(0, 0), centre(1, 1), centre(1, 2), centre(2, 1))
            for (k in 1 until trail.size) drawLine(stroke, trail[k - 1], trail[k], u * 0.18f, StrokeCap.Round)
            for (p in trail) drawCircle(stroke, u * 0.2f, p)
        }
    }

    // ---- the board ----------------------------------------------------------------------------

    private const val MAX_TILE = 76f
    private const val LINE = 22f
    private const val PILL = 38f
    private const val BOARD_PAD = 12f

    /** How the board fills its box, in dp: the tile side, the dash unit and the list's rows of goal indices. */
    internal class Fit(val tile: Float, val unit: Float, val rows: List<List<Int>>) {
        val listHeight: Float get() = rows.size * unit * ROW
    }

    private const val ROW = 1.3f
    private const val WORD_GAP = 0.8f

    /** Goals in order, wrapped to [width] at a dash unit of [unit]. */
    internal fun wrap(lengths: List<Int>, width: Float, unit: Float): List<List<Int>> {
        val rows = mutableListOf(mutableListOf<Int>())
        var used = 0f
        lengths.forEachIndexed { g, n ->
            val w = n * unit
            if (rows.last().isNotEmpty() && used + WORD_GAP * unit + w > width) {
                rows += mutableListOf<Int>()
                used = 0f
            }
            if (rows.last().isNotEmpty()) used += WORD_GAP * unit
            rows.last() += g
            used += w
        }
        return rows
    }

    /** Sized from both axes: the list a share of the height at the largest unit it allows, the tiles the rest. */
    internal fun fit(width: Float, height: Float, cols: Int, rows: Int, lengths: List<Int>): Fit {
        val inner = (width - 2 * BOARD_PAD).coerceAtLeast(0f)
        val units = generateSequence(20f) { it - 1f }.takeWhile { it >= 11f }
        val unit = units.firstOrNull { wrap(lengths, inner, it).size * it * ROW <= height * 0.24f } ?: 11f
        val wrapped = wrap(lengths, inner, unit)
        val left = height - LINE - PILL - LINE - wrapped.size * unit * ROW
        val tile = minOf(inner / cols, left / rows, MAX_TILE).coerceAtLeast(0f)
        return Fit(tile, unit, wrapped)
    }

    /** The tile under ([x], [y]) in a grid of side [tile]: within [reach] of its middle (a share of a tile), else null. */
    internal fun tileAt(x: Float, y: Float, tile: Float, cols: Int, rows: Int, reach: Float): Int? {
        if (tile <= 0f || x < 0f || y < 0f) return null
        val c = (x / tile).toInt()
        val r = (y / tile).toInt()
        if (c >= cols || r >= rows) return null
        return if (hypot(x - (c + 0.5f) * tile, y - (r + 0.5f) * tile) <= reach * tile) r * cols + c else null
    }

    /** While swiping, tiles answer only near their middles, so a diagonal passes between its two side tiles. */
    private const val SWIPE_REACH = 0.42f

    /** A first touch anywhere on a tile counts (corners included). */
    private const val TOUCH_REACH = 0.71f

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as DwindleState
        // The gesture outlives every state: it reads the board through this, never a captured `s` (Snap's lesson).
        val latest by rememberUpdatedState(s)
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = remember(s.letters) { BoardHues.pair(s.letters.hashCode()) }
        val (highlight, glow, pulse) = rememberHintPaint()
        val playable = interactive && !s.solved
        val measurer = rememberTextMeasurer()
        val counts = s.counts()
        val family = MaterialTheme.typography.bodyLarge.fontFamily

        // A found word's tiles settle, and the glow runs along it.
        val units = remember(s.letters) { s.goals.map { it.cells.toIntArray() } }
        val feel = rememberMoveFeel(counts.toList(), units, { Tones.word() }) { s.goals[it].word in s.found }

        // The word being spelled, transient: never PuzzleState, so a refused word costs nothing.
        var picked by remember(s.letters) { mutableStateOf(listOf<Int>()) }
        LetterBeeps(picked.size)
        var notice by remember { mutableStateOf<String?>(null) }
        var noticeIsError by remember { mutableStateOf(false) }
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(1600)
                notice = null
            }
        }

        fun say(text: String, error: Boolean) {
            notice = text
            noticeIsError = error
        }

        fun submit(path: List<Int>) {
            val now = latest
            val word = path.map { now.letters[it] }.joinToString("")
            picked = emptyList()
            if (word.isEmpty()) return
            when (DwindleLogic.judge(now, word)) {
                DwindleEntry.Goal -> {
                    notice = null
                    onState(now.enter(word))
                }
                DwindleEntry.Bonus -> {
                    say("Bonus word: ${word.uppercase()}", false)
                    onState(now.enter(word))
                }
                DwindleEntry.Found -> say("${word.uppercase()} is already found", true)
                DwindleEntry.NotAWord -> say("${word.uppercase()} is not in the word list", true)
                DwindleEntry.TooShort -> say("Words have three letters or more", true)
                DwindleEntry.NotOnTiles -> say("The tiles cannot spell ${word.uppercase()}", true)
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .boardKeys(playable) { key, _, repeat ->
                    val action = wordWheelKeyAction(key) ?: return@boardKeys false
                    if (!repeat) when (action) {
                        is WheelKeyAction.Letter -> {
                            val word = picked.map { latest.letters[it] }.joinToString("") + action.letter
                            DwindleLogic.liveWay(latest, word)?.let { picked = it }
                        }
                        WheelKeyAction.Backspace -> picked = picked.dropLast(1)
                        WheelKeyAction.Clear -> picked = emptyList()
                        WheelKeyAction.Enter -> submit(picked)
                    }
                    true
                },
        ) {
            val tall = if (constraints.hasBoundedHeight) maxHeight.value else maxWidth.value * 1.6f
            val fit = fit(maxWidth.value, tall, s.width, s.height, s.goals.map { it.word.length })
            val tile = fit.tile.dp

            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    notice ?: buildString {
                        append("${s.found.size} of ${s.goals.size} words")
                        if (s.bonus.isNotEmpty()) append(" · ${s.bonus.size} bonus")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (notice != null && noticeIsError) scheme.error else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().height(LINE.dp),
                )

                Canvas(Modifier.fillMaxWidth().height(fit.listHeight.dp)) {
                    drawList(s, fit, highlight, glow, pulse.value, hueA, dark, scheme.onSurfaceVariant, measurer, family)
                }

                // The word being spelled. Tapping it tries it; its room is always kept, so nothing moves when it fills.
                Box(Modifier.fillMaxWidth().height(PILL.dp).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                    if (picked.isNotEmpty()) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(BoardHues.mark(hueA))
                                .clickable(enabled = playable) { submit(picked) }
                                .padding(horizontal = 18.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                picked.map { s.letters[it].uppercaseChar() }.joinToString(""),
                                color = BoardHues.onFill(BoardHues.mark(hueA)),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(
                        Modifier
                            .size(tile * s.width, tile * s.height)
                            .highlightGrid(s.width, s.height)
                            .pointerInput(playable, s.letters, fit.tile) {
                                if (!playable) return@pointerInput
                                val side = fit.tile * density
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val start = tileAt(down.position.x, down.position.y, side, s.width, s.height, TOUCH_REACH)
                                        ?.takeIf { latest.counts()[it] > 0 } ?: return@awaitEachGesture
                                    down.consume()
                                    val before = picked
                                    var path = listOf(start)
                                    var swiped = false
                                    var last = down.position
                                    if (before.isEmpty()) picked = path
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        change.consume()
                                        if (!change.pressed) break
                                        for (p in swipePoints(last, change.position, side / 8f)) {
                                            val at = tileAt(p.x, p.y, side, s.width, s.height, SWIPE_REACH) ?: continue
                                            val next = DwindleLogic.swipe(latest, latest.counts(), path, at)
                                            if (next != path) {
                                                path = next
                                                if (path.size >= 2) swiped = true
                                                if (swiped) picked = path
                                            }
                                        }
                                        last = change.position
                                    }
                                    if (swiped) submit(path) else picked = DwindleLogic.tap(latest, latest.counts(), before, start)
                                }
                            },
                    ) {
                        drawTiles(s, counts, picked, fit.tile.dp.toPx(), highlight, glow, pulse.value, hueA, hueB, dark, measurer, feel, vivid(accent), family)
                    }
                }

                BonusLine(s.bonus, LINE.dp)
            }
        }
    }

    private fun DrawScope.drawList(
        s: DwindleState,
        fit: Fit,
        highlight: BoardHighlight,
        glow: Color,
        pulse: Float,
        hueA: Float,
        dark: Boolean,
        dash: Color,
        measurer: TextMeasurer,
        family: FontFamily?,
    ) {
        val u = fit.unit.dp.toPx()
        val ink = BoardHues.ink(hueA, dark)
        fit.rows.forEachIndexed { row, goals ->
            val total = goals.sumOf { s.goals[it].word.length } * u + (goals.size - 1) * WORD_GAP * u
            var x = (size.width - total) / 2f
            val top = row * u * ROW
            for (g in goals) {
                val word = s.goals[g].word
                val found = word in s.found
                val look = highlight.look(DwindleTeacher.DASH + g)
                if (look.strong || look.soft) {
                    drawRoundRect(
                        if (look.strong) glow.copy(alpha = pulse) else glow.copy(alpha = 0.5f),
                        Offset(x - u * 0.1f, top + u * 0.02f),
                        Size(word.length * u + u * 0.2f, u * ROW - u * 0.04f),
                        CornerRadius(u * 0.2f),
                        style = Stroke(u * 0.1f),
                    )
                }
                val alpha = if (look.dim) 0.4f else 1f
                for (i in word.indices) {
                    val cx = x + (i + 0.5f) * u
                    if (found) {
                        val layout = measurer.measure(word[i].uppercase(), TextStyle(color = ink.copy(alpha = alpha), fontSize = (u * 0.85f).toSp(), fontWeight = FontWeight.Bold, fontFamily = family))
                        drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, top + (u * ROW - layout.size.height) / 2f))
                    } else {
                        val y = top + u * (ROW - 0.3f)
                        drawLine(dash.copy(alpha = alpha), Offset(cx - u * 0.33f, y), Offset(cx + u * 0.33f, y), u * 0.12f, StrokeCap.Round)
                    }
                }
                x += word.length * u + WORD_GAP * u
            }
        }
    }

    private fun DrawScope.drawTiles(
        s: DwindleState,
        counts: IntArray,
        picked: List<Int>,
        cell: Float,
        highlight: BoardHighlight,
        glow: Color,
        pulse: Float,
        hueA: Float,
        hueB: Float,
        dark: Boolean,
        measurer: TextMeasurer,
        feel: MoveFeel,
        accent: Color,
        family: FontFamily?,
    ) {
        val fill = BoardHues.fill(hueB, dark)
        val ink = BoardHues.onFill(fill)
        val mark = BoardHues.mark(hueA)
        val inset = cell * 0.05f
        val corner = CornerRadius(cell * 0.14f)
        val box = Size(cell - 2 * inset, cell - 2 * inset)
        fun centre(i: Int) = Offset((i % s.width + 0.5f) * cell, (i / s.width + 0.5f) * cell)
        // Under the tiles, so the trail never crosses a letter.
        for (k in 1 until picked.size) drawLine(mark, centre(picked[k - 1]), centre(picked[k]), cell * 0.14f, StrokeCap.Round)
        for (i in counts.indices) {
            val topLeft = Offset((i % s.width) * cell + inset, (i / s.width) * cell + inset)
            val look = highlight.look(i)
            val alpha = if (look.dim) 0.3f else 1f
            feelSquare(feel, i, topLeft, box, accent) {
                if (counts[i] == 0) {
                    // Vanished: only a faint place stays, so the grid still reads as a grid and nothing seems to have moved.
                    drawRoundRect(ink.copy(alpha = 0.08f * alpha), topLeft, box, corner, style = Stroke(cell * 0.02f))
                } else {
                    val chosen = i in picked
                    val bg = if (chosen) mark else fill
                    val text = if (chosen) BoardHues.onFill(mark) else ink
                    drawRoundRect(bg.copy(alpha = alpha), topLeft, box, corner)
                    val letter = measurer.measure(s.letters[i].uppercase(), TextStyle(color = text.copy(alpha = alpha), fontSize = (cell * 0.52f).toSp(), fontWeight = FontWeight.Bold, fontFamily = family))
                    drawText(letter, topLeft = Offset(topLeft.x + (box.width - letter.size.width) / 2f, topLeft.y + (box.height - letter.size.height) / 2f))
                    val count = measurer.measure(counts[i].toString(), boardStyle(BoardGlyph.CORNER, cell * 0.8f).copy(color = text.copy(alpha = alpha), fontFamily = family))
                    drawText(count, topLeft = Offset(topLeft.x + box.width - count.size.width - cell * 0.07f, topLeft.y + cell * 0.03f))
                }
            }
            if (look.strong || look.soft) {
                val w = if (look.strong) cell * 0.07f else cell * 0.045f
                drawRoundRect(
                    if (look.strong) glow.copy(alpha = pulse) else glow.copy(alpha = 0.5f),
                    Offset(topLeft.x - w / 2f, topLeft.y - w / 2f),
                    Size(box.width + w, box.height + w),
                    CornerRadius(corner.x + w),
                    style = Stroke(w),
                )
            }
        }
    }
}
