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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.reportHighlight
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.ui.LocalBoardTones
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.theme.vivid
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/**
 * Hexwords (working name): a block of letters in hexes. Drag through touching hexes to spell a word; the word scores, more
 * than a point more per extra letter, and its letters go. Reach the target. Which word to take first is the game. Spec:
 * `docs/HEXWORDS_SPEC.md`; rules and generator in [HexLogic]; hints in [HexwordsTeacher].
 *
 * Highlight indices are hexes, in reading order.
 */
object Hexwords : PuzzleType {

    override val id = "hexwords"
    override val displayName = "Hexwords"
    override val tagline = "Spend the letters well: every word uses them up"
    override val accent = 0xFF96754A // AccentSpacingTest's printed candidate
    override val rules = listOf(
        "Drag through touching hexes to spell a word of three to seven letters, or tap them in turn and tap the word.",
        "A word's letters leave the board, so a big word can break up the words around it.",
        "Three letters score 3, four 5, five 8, six 12, seven 17. Reach the target to finish.",
        "A word may come again from other letters. Nothing is lost for trying: Undo puts a word back.",
    )

    override val keyboardHelp = listOf(
        "Type a word that traces on the board, Tab tries another way to trace it, Enter takes it, Backspace deletes, Escape clears.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        HexLogic.generateVerified(seed, difficulty) ?: HexLogic.lastResort(difficulty)

    override fun teach(state: PuzzleState): Deduction? = HexwordsTeacher.deduction(state as HexwordsState)

    // ---- walkthrough --------------------------------------------------------------------------

    /**
     * ```
     *   D H O
     *  A C R I
     *   N C E
     * ```
     * DANCE and CHOIR make 16. ANCHOR alone scores 12 and leaves four letters that spell nothing: `HexwordsTeachingTest`
     * proves it against every accepted word.
     */
    internal fun tutorialBoard() = HexwordsState(listOf(3, 4, 3), "dhoacrince", 16)

    internal val ANCHOR = listOf(3, 7, 4, 1, 2, 5)
    internal val DANCE = listOf(0, 3, 7, 8, 9)

    private fun tookOnly(word: String, path: List<Int>): (PuzzleState) -> Boolean = { next ->
        next is HexwordsState && next.taken == listOf(HexTake(word, path))
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val s0 = tutorialBoard()
        val trapped = s0.take("anchor", ANCHOR)
        val left = s0.letters.indices.filter { it !in ANCHOR }.toSet()
        listOf(
            TutorialFrame(
                state = s0,
                caption = "Spell words through touching hexes to reach the target, 16 here. Longer words score far more: 3, 5, 8, 12 and 17 for three to seven letters.",
            ),
            TutorialFrame(
                state = s0,
                caption = "Drag through A, N, C, H, O, R without lifting, then let go.",
                highlight = BoardHighlight(strong = ANCHOR.toSet()),
                accepts = tookOnly("anchor", ANCHOR),
                retry = "Drag along the glowing hexes in order: A, N, C, H, O, R.",
                done = "ANCHOR scores 12, and its letters are gone.",
            ),
            TutorialFrame(
                state = trapped,
                caption = "4 points to go, but D, I, C and E don't touch in a way that spells anything. The biggest word cost the target. Undo puts a word back.",
                highlight = BoardHighlight(strong = left),
            ),
            TutorialFrame(
                state = s0,
                caption = "Look before you take. Start over with DANCE: tap D, A, N, C, E in turn, then tap the word.",
                highlight = BoardHighlight(strong = DANCE.toSet()),
                accepts = tookOnly("dance", DANCE),
                retry = "Tap the glowing hexes in order: D, A, N, C, E. Then tap the word.",
                done = "DANCE scores 8 and leaves a word behind it.",
            ),
            TutorialFrame(
                state = s0.take("dance", DANCE),
                caption = "Your turn: find the last 8 points. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. Every letter earned its place.",
            ),
        )
    }

    // ---- the home motif -----------------------------------------------------------------------

    /** A small block of hexes with a word traced through it and two hexes gone. Fixed, never generated. */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.HUE_PAIRS[0]
        val fill = BoardHues.fill(hueB, dark)
        val mark = BoardHues.mark(hueA)
        val rows = listOf(3, 4, 3)
        Canvas(modifier) {
            val g = HexGeometry(rows, size.width, size.height)
            for (i in g.xs.indices) {
                val c = Offset(g.xs[i], g.ys[i])
                when (i) {
                    MOTIF_GONE[0], MOTIF_GONE[1] -> drawPath(hexPath(c, g.radius * 0.92f), fill.copy(alpha = 0.3f), style = Stroke(g.radius * 0.1f))
                    in MOTIF_WORD -> drawPath(hexPath(c, g.radius * 0.92f), mark)
                    else -> drawPath(hexPath(c, g.radius * 0.92f), fill)
                }
            }
            val ink = BoardHues.onFill(mark)
            MOTIF_WORD.zipWithNext { a, b ->
                drawLine(ink, Offset(g.xs[a], g.ys[a]), Offset(g.xs[b], g.ys[b]), g.radius * 0.22f, StrokeCap.Round)
            }
        }
    }

    private val MOTIF_WORD = listOf(3, 4, 8, 5)
    private val MOTIF_GONE = listOf(0, 9)

    private fun hexPath(c: Offset, r: Float): Path = Path().apply {
        val a = r * sqrt(3f) / 2f
        moveTo(c.x, c.y - r)
        lineTo(c.x + a, c.y - r / 2f)
        lineTo(c.x + a, c.y + r / 2f)
        lineTo(c.x, c.y + r)
        lineTo(c.x - a, c.y + r / 2f)
        lineTo(c.x - a, c.y - r / 2f)
        close()
    }

    // ---- the board ----------------------------------------------------------------------------

    private val LINE = 22.dp
    private val PILL = 40.dp

    private fun headerOf(s: HexwordsState, stuck: Boolean): String = when {
        s.solved -> "${s.score} points. Target reached"
        stuck -> "No words left. Undo one, or start over"
        else -> "${s.left} points to go · ${s.score} of ${s.target}"
    }

    private fun takenLine(s: HexwordsState): String =
        s.taken.asReversed().joinToString("  ") { "${it.word.uppercase()} ${HexLogic.points(it.word.length)}" }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as HexwordsState
        // The gesture outlives every state: it reads the board through this (CLAUDE.md, Snap's lesson).
        val latest by rememberUpdatedState(s)
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = remember(s.letters) { BoardHues.pair(s.letters.hashCode()) }
        val (highlight, glow, pulse) = rememberHintPaint()
        val playable = interactive && !s.solved
        val measurer = rememberTextMeasurer()
        val nb = remember(s.rows) { HexLogic.neighbours(s.rows) }
        val free = s.free
        val stuck = remember(s.letters, free) { HexLogic.roads(s.letters, nb, free, HexLogic.acceptedWords).isEmpty() }
        val feel = rememberMoveFeel(s.letters.indices.map { free shr it and 1L == 1L }, remember { emptyList<IntArray>() }) { false }

        // The word being spelled and the finger are transient: a refused word costs nothing, one word is one state.
        var picked by remember(s.letters) { mutableStateOf(listOf<Int>()) }
        LetterBeeps(picked.size)
        val tones = LocalBoardTones.current
        var typed by remember(s.letters) { mutableStateOf("") }
        var trace by remember(s.letters) { mutableIntStateOf(0) }
        var finger by remember { mutableStateOf<Offset?>(null) }
        var notice by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(1600)
                notice = null
            }
        }

        fun submit(path: List<Int>) {
            val now = latest
            picked = emptyList()
            typed = ""
            trace = 0
            if (path.isEmpty()) return
            val word = path.map { now.letters[it] }.joinToString("")
            when {
                word.length < HexLogic.MIN_WORD -> notice = "Words have three letters or more"
                !HexLogic.isWord(word) -> notice = "${word.uppercase()} is not in the word list"
                else -> {
                    notice = null
                    // No units here, so the found word's da-ding is played by hand; the word that solves the board plays the win.
                    val next = now.take(word, path)
                    onState(next)
                    if (!next.solved) tones(Tones.word())
                }
            }
        }

        fun retrace() {
            val ways = if (typed.isEmpty()) emptyList() else HexLogic.roads(latest.letters, nb, latest.free, listOf(typed))
            picked = if (ways.isEmpty()) emptyList() else ways[trace.mod(ways.size)].path.toList()
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .boardKeys(playable) { key, _, repeat ->
                    val action = hexKeyAction(key) ?: return@boardKeys false
                    if (!repeat) when (action) {
                        is HexKeyAction.Letter -> {
                            val more = typed + action.letter
                            if (more.length <= HexLogic.MAX_WORD && HexLogic.roads(latest.letters, nb, latest.free, listOf(more)).isNotEmpty()) {
                                typed = more
                                trace = 0
                                retrace()
                            }
                        }
                        HexKeyAction.Backspace -> {
                            typed = typed.dropLast(1)
                            trace = 0
                            retrace()
                        }
                        HexKeyAction.Clear -> {
                            typed = ""
                            picked = emptyList()
                        }
                        HexKeyAction.NextTrace -> {
                            trace++
                            retrace()
                        }
                        HexKeyAction.Enter -> submit(picked)
                    }
                    true
                }
                .padding(horizontal = 8.dp),
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    notice ?: headerOf(s, stuck),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (notice != null || (stuck && !s.solved)) scheme.error else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().height(LINE),
                )
                // The word being spelled, with what it would score; tapping tries it. Its room is always kept.
                Box(Modifier.fillMaxWidth().height(PILL).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    if (picked.isNotEmpty()) {
                        val pts = HexLogic.points(picked.size)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(BoardHues.mark(hueA))
                                .clickable(enabled = playable) { submit(picked) }
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                picked.map { s.letters[it].uppercaseChar() }.joinToString("") + if (pts > 0) "  +$pts" else "",
                                color = BoardHues.onFill(BoardHues.mark(hueA)),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Canvas(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .reportHighlight { size, h ->
                            val g = HexGeometry(s.rows, size.width.toFloat(), size.height.toFloat())
                            val hexes = (h.strong + h.soft).filter { it in g.xs.indices }
                            if (hexes.isEmpty()) {
                                null
                            } else {
                                Rect(
                                    hexes.minOf { g.xs[it] } - g.apothem,
                                    hexes.minOf { g.ys[it] } - g.radius,
                                    hexes.maxOf { g.xs[it] } + g.apothem,
                                    hexes.maxOf { g.ys[it] } + g.radius,
                                )
                            }
                        }
                        .pointerInput(playable, s.letters, s.rows) {
                            if (!playable) return@pointerInput
                            val g = HexGeometry(s.rows, size.width.toFloat(), size.height.toFloat())
                            fun open(i: Int?) = i?.takeIf { latest.free shr it and 1L == 1L }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val start = open(g.hit(down.position.x, down.position.y)) ?: return@awaitEachGesture
                                down.consume()
                                val before = picked
                                var path = listOf(start)
                                var swiped = false
                                typed = ""
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
                                    val at = open(g.hitCore(change.position.x, change.position.y)) ?: continue
                                    val next = swipeHex(path, at, nb)
                                    if (next != path) {
                                        path = next
                                        if (path.size >= 2) swiped = true
                                        if (swiped) picked = path
                                    }
                                }
                                finger = null
                                if (swiped) submit(path) else picked = tapHex(before, start, nb)
                            }
                        },
                ) {
                    drawBlock(s, free, picked, finger, highlight, glow, pulse.value, hueA, hueB, dark, measurer, feel, vivid(accent))
                }
                Text(
                    takenLine(s),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().height(LINE).padding(top = 4.dp),
                )
            }
        }
    }

    private fun DrawScope.drawBlock(
        s: HexwordsState,
        free: Long,
        picked: List<Int>,
        finger: Offset?,
        highlight: BoardHighlight,
        glow: Color,
        pulse: Float,
        hueA: Float,
        hueB: Float,
        dark: Boolean,
        measurer: TextMeasurer,
        feel: MoveFeel,
        accent: Color,
    ) {
        val g = HexGeometry(s.rows, size.width, size.height)
        val mark = BoardHues.mark(hueA)
        val fill = BoardHues.fill(hueB, dark)
        val body = g.radius * 0.94f
        for (i in g.xs.indices) {
            val c = Offset(g.xs[i], g.ys[i])
            val look = highlight.look(i)
            val alpha = if (look.dim) 0.3f else 1f
            if (free shr i and 1L == 1L) {
                val chosen = i in picked
                val tone = if (chosen) mark else fill
                val box = Size(g.apothem * 2f, g.radius * 2f)
                feelSquare(feel, i, Offset(c.x - g.apothem, c.y - g.radius), box, accent) {
                    drawPath(hexPath(c, body), tone.copy(alpha = alpha))
                }
            } else {
                drawPath(hexPath(c, body), fill.copy(alpha = 0.25f * alpha), style = Stroke(g.radius * 0.05f))
            }
            if (look.strong || look.soft) {
                drawPath(
                    hexPath(c, body),
                    if (look.strong) glow.copy(alpha = pulse) else glow.copy(alpha = 0.5f),
                    style = Stroke(g.radius * if (look.strong) 0.12f else 0.08f),
                )
            }
        }
        if (picked.isNotEmpty()) {
            val line = mark.copy(alpha = 0.55f)
            val w = g.radius * 0.2f
            for (k in 1 until picked.size) {
                drawLine(line, Offset(g.xs[picked[k - 1]], g.ys[picked[k - 1]]), Offset(g.xs[picked[k]], g.ys[picked[k]]), w, StrokeCap.Round)
            }
            if (finger != null) drawLine(line, Offset(g.xs[picked.last()], g.ys[picked.last()]), finger, w, StrokeCap.Round)
        }
        // Letters last, so the trail never strikes one through.
        for (i in g.xs.indices) {
            if (free shr i and 1L == 0L) continue
            val c = Offset(g.xs[i], g.ys[i])
            val alpha = if (highlight.look(i).dim) 0.3f else 1f
            val tone = if (i in picked) mark else fill
            feelSquare(feel, i, Offset(c.x - g.apothem, c.y - g.radius), Size(g.apothem * 2f, g.radius * 2f), accent) {
                val layout = measurer.measure(
                    s.letters[i].uppercase(),
                    TextStyle(color = BoardHues.onFill(tone).copy(alpha = alpha), fontSize = (g.radius * 0.95f).toSp(), lineHeight = (g.radius * 1.14f).toSp(), fontWeight = FontWeight.Bold),
                )
                drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
            }
        }
    }
}
