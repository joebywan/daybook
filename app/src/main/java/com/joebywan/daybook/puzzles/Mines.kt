package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.movedCursor
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mines (`docs/MINES_SPEC.md`): numbers say how many mines touch a square; reveal every square without one. Every board
 * is cleared by reasoning from a given opening. Three lives, as Pointers'; no undo. Rules, ladder and generator:
 * [MinesLogic]; hints: [MinesTeacher]. Highlight indices are squares.
 */
object Mines : PuzzleType {

    override val id = "mines"
    override val displayName = "Mines"
    override val tagline = "Every safe square, found without a guess"
    override val accent = 0xFF9D4881
    override val rules = listOf(
        "Each number counts the mines in the squares touching it, corners included.",
        "Reveal every square without a mine. Revealing a mine costs a life; lose all three and the board is lost: try it again from the start.",
        "Flag a square you are sure holds a mine. A flag is your note, never checked, and keeps its square from being revealed by mistake.",
        "A tap uses the tool under the board, Reveal or Flag; a long press uses the other. Every board can be cleared without a guess.",
    )

    override val keyboardHelp = listOf("Arrows move, Space or Enter reveals, F flags or unflags.")

    /**
     * No undo, on purpose (the owner's call). A reveal is never wrong unless it finds a mine, so undo could only hand
     * back a life; flags come off by the same gesture that put them on. A lost board offers Try again.
     */
    override val offersUndo = false

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = MinesLogic.generate(seed, difficulty)

    /** The board when the ladder cleared it from its opening at the tier's depth, null otherwise. */
    internal fun generateVerified(seed: Long, difficulty: Difficulty): MinesState? = MinesLogic.generateVerified(seed, difficulty)

    override fun teach(state: PuzzleState): Deduction? = MinesTeacher.deduction(state as MinesState)

    /** The hardest step the generator's walk needed: a subset or pair on Hard, the count against the numbers or a what-if on Expert; null on Standard. */
    override fun neededTechnique(state: PuzzleState): String? {
        val s = state as MinesState
        val walk = MinesLogic.walk(s.width, s.height, s.mines, s.opening) ?: return null
        if (walk.depth < 2) return null
        return walk.used.filter { levelOf(it) == walk.depth }.maxByOrNull { MinesTeacher.TECHNIQUES.indexOf(it) }
    }

    private fun levelOf(technique: String) = when (technique) {
        MinesTeacher.SUBSET, MinesTeacher.PAIR -> 2
        MinesTeacher.COUNT, MinesTeacher.EDGE, MinesTeacher.WHAT_IF -> 3
        else -> 1
    }

    // ---- walkthrough --------------------------------------------------------------------------------------------

    /**
     * Five by five, four mines, one answer cleared by single numbers:
     * ```
     * 0 0 1 * 1
     * 0 0 1 1 1
     * 0 1 1 2 1
     * 0 1 * 3 *
     * 0 1 2 * 2
     * ```
     * The opening spreads from square 5 (row 2, column 1) and leaves the right of the board hidden.
     */
    internal const val TUTORIAL_MINES = "00010" + "00000" + "00000" + "00101" + "00010"
    internal const val TUTORIAL_OPENING = 5

    /** The 1 whose one hidden square is [BUMP], the mine the player reveals to see what it costs. */
    internal const val FIRST_ONE = 11
    internal const val BUMP = 17

    /** The 1 beside [BUMP] whose other hidden squares ([SAFE]) are then safe. */
    internal const val SECOND_ONE = 12
    internal val SAFE = listOf(8, 13, 18)

    /** The 1 in the top row left with one hidden square, [FLAG_SQUARE], once [SAFE] are open. */
    internal const val THIRD_ONE = 2
    internal const val FLAG_SQUARE = 3

    internal fun tutorialBoard() =
        MinesState(5, 5, TUTORIAL_MINES, TUTORIAL_OPENING, MinesLogic.openingMarks(5, 5, TUTORIAL_MINES, TUTORIAL_OPENING))

    override val tutorial: List<TutorialFrame> by lazy {
        val s0 = tutorialBoard()
        val s1 = s0.reveal(BUMP)!!
        val s2 = SAFE.fold(s1) { acc, i -> acc.reveal(i)!! }
        val s3 = s2.flag(FLAG_SQUARE)!!
        fun around(i: Int) = MinesLogic.neighbours(5, 5, i).toSet()
        listOf(
            TutorialFrame(
                state = s0,
                caption = "Each number counts the mines in the squares touching it, corners included. Reveal every square without one.",
                highlight = BoardHighlight(strong = setOf(SECOND_ONE), soft = around(SECOND_ONE)),
            ),
            TutorialFrame(
                state = s0,
                caption = "This 1 has only one hidden square beside it, so that square is a mine. Tap it anyway, to see.",
                highlight = BoardHighlight(strong = setOf(BUMP), soft = setOf(FIRST_ONE)),
                accepts = { it is MinesState && it.bumps == listOf(BUMP) && it.marks == s0.marks },
                retry = "Tap the glowing square.",
                done = "A mine. That cost a life: lose all three and the board is lost.",
            ),
            TutorialFrame(
                state = s1,
                caption = "This 1 touches that mine, so its other hidden squares are safe. Tap each glowing square to reveal it.",
                glow = { now ->
                    BoardHighlight(strong = SAFE.filter { !(now as MinesState).isOpen(it) }.toSet(), soft = setOf(SECOND_ONE, BUMP))
                },
                steps = { next ->
                    val m = next as? MinesState
                    when {
                        m == null || m.bumps != s1.bumps || (0 until 25).any { m.marks[it] != s1.marks[it] && it !in SAFE } ||
                            SAFE.any { m.marks[it] == MinesLogic.FLAG } -> TutorialStep.REJECT
                        SAFE.all { m.isOpen(it) } -> TutorialStep.DONE
                        else -> TutorialStep.PROGRESS
                    }
                },
                retry = "Tap the glowing square.",
                done = "Safe. A number with all its mines found frees the rest.",
            ),
            TutorialFrame(
                state = s2,
                caption = "This 1 has one hidden square left, so it is a mine. Flag it: long-press it, or choose Flag below and tap it.",
                highlight = BoardHighlight(strong = setOf(FLAG_SQUARE), soft = setOf(THIRD_ONE)),
                accepts = { it is MinesState && it.marks == s3.marks && it.bumps == s3.bumps },
                retry = "Long-press the glowing square.",
                done = "Flagged. A flag is your note: that square can't be revealed by mistake.",
            ),
            TutorialFrame(
                state = s3,
                caption = "Your turn: reveal every safe square. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Cleared. That's all there is to it.",
            ),
        )
    }

    // ---- colour -------------------------------------------------------------------------------------------------

    /** A hue per number, 1 to 6 (Blue, Teal, Violet, Amber, Rose, Gold); 7 and 8 are `onSurface`. The digit carries it too. */
    private val NUMBER_HUES = listOf(215f, 175f, 268f, 34f, 330f, 46f)

    private fun numberColour(k: Int, dark: Boolean, scheme: ColorScheme): Color =
        if (k in 1..NUMBER_HUES.size) BoardHues.ink(NUMBER_HUES[k - 1], dark) else scheme.onSurface

    // ---- drawing ------------------------------------------------------------------------------------------------

    private class Overlay(
        val highlight: BoardHighlight = BoardHighlight.None,
        val glow: Color = Color.Unspecified,
        val pulse: Float = 1f,
        /** The square a mine was just revealed on, and how far its flash has run (0 to 1); -1 for none. */
        val bumpAt: Int = -1,
        val bump: Float = 0f,
        val shake: Boolean = true,
    )

    private fun digitLayouts(measurer: TextMeasurer, fontPx: Float, density: Density): List<TextLayoutResult> {
        val style = TextStyle(fontSize = with(density) { fontPx.toSp() }, fontWeight = FontWeight.SemiBold)
        return (0..8).map { measurer.measure(it.toString(), style) }
    }

    private fun DrawScope.drawBoard(
        s: MinesState,
        u: Float,
        digits: List<TextLayoutResult>,
        scheme: ColorScheme,
        hues: Pair<Float, Float>,
        overlay: Overlay,
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
    ) {
        val w = s.width
        val h = s.height
        val dark = BoardHues.isDark(scheme)
        val tile = BoardHues.fill(hues.first, dark)
        val flagInk = BoardHues.ink(hues.second, dark)
        val hl = overlay.highlight
        val lit = !hl.isEmpty
        fun dim(i: Int) = lit && i !in hl.strong && i !in hl.soft
        val gap = u * 0.06f

        drawRoundRect(scheme.surface, Offset.Zero, Size(w * u, h * u), CornerRadius(u * 0.15f))
        val thin = maxOf(1f, u * 0.02f)
        for (c in 1 until w) drawLine(scheme.outline.copy(alpha = 0.35f), Offset(c * u, 0f), Offset(c * u, h * u), thin)
        for (r in 1 until h) drawLine(scheme.outline.copy(alpha = 0.35f), Offset(0f, r * u), Offset(w * u, r * u), thin)

        for (i in 0 until w * h) {
            var x = (i % w) * u
            val y = (i / w) * u
            val alpha = if (dim(i)) 0.35f else 1f
            if (i == overlay.bumpAt && overlay.shake) {
                val t = overlay.bump
                x += 0.12f * u * sin(6 * PI.toFloat() * t) * (1 - t)
            }
            feelSquare(feel, i, Offset(x, y), Size(u, u), accent) {
                when {
                    i in s.bumps -> {
                        drawRoundRect(scheme.error.copy(alpha = alpha), Offset(x + gap, y + gap), Size(u - 2 * gap, u - 2 * gap), CornerRadius(u * 0.16f))
                        drawMine(x, y, u, scheme.onError.copy(alpha = alpha))
                    }
                    s.isOpen(i) -> {
                        val k = s.number(i)
                        if (k > 0) {
                            val layout = digits[k]
                            drawText(
                                layout, numberColour(k, dark, scheme).copy(alpha = alpha),
                                Offset(x + (u - layout.size.width) / 2f, y + (u - layout.size.height) / 2f),
                            )
                        }
                    }
                    else -> {
                        drawRoundRect(tile.copy(alpha = alpha), Offset(x + gap, y + gap), Size(u - 2 * gap, u - 2 * gap), CornerRadius(u * 0.16f))
                        if (s.isFlag(i)) drawFlag(x, y, u, flagInk.copy(alpha = alpha), scheme.onSurface.copy(alpha = 0.7f * alpha))
                    }
                }
            }
        }

        // A mine just found: a ring that flashes out from its square.
        if (overlay.bumpAt >= 0) {
            val t = overlay.bump
            val cx = (overlay.bumpAt % w + 0.5f) * u
            val cy = (overlay.bumpAt / w + 0.5f) * u
            drawCircle(scheme.error.copy(alpha = (1f - t) * 0.8f), u * (0.5f + 0.6f * t), Offset(cx, cy), style = Stroke(u * 0.08f))
        }

        if (lit) {
            fun rim(indices: Set<Int>, width: Float, color: Color) {
                for (i in indices) {
                    if (i !in 0 until w * h) continue
                    val x = (i % w) * u
                    val y = (i / w) * u
                    drawRoundRect(color, Offset(x + width / 2, y + width / 2), Size(u - width, u - width), CornerRadius(u * 0.15f), style = Stroke(width))
                }
            }
            rim(hl.soft - hl.strong, u * 0.07f, overlay.glow.copy(alpha = 0.5f))
            rim(hl.strong, u * 0.2f, scheme.background.copy(alpha = 0.9f * overlay.pulse))
            rim(hl.strong, u * 0.11f, overlay.glow.copy(alpha = overlay.pulse))
        }
    }

    /** A round mine with eight spikes and a glint. */
    private fun DrawScope.drawMine(x: Float, y: Float, u: Float, ink: Color) {
        val c = Offset(x + u / 2, y + u / 2)
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4
            val d = Offset(cos(a), sin(a))
            drawLine(ink, c + d * (u * 0.12f), c + d * (u * 0.32f), u * 0.07f, StrokeCap.Round)
        }
        drawCircle(ink, u * 0.2f, c)
    }

    /** A pennant on a pole, on a little base. */
    private fun DrawScope.drawFlag(x: Float, y: Float, u: Float, cloth: Color, pole: Color) {
        val sw = maxOf(1.5f, u * 0.06f)
        drawLine(pole, Offset(x + u * 0.4f, y + u * 0.2f), Offset(x + u * 0.4f, y + u * 0.78f), sw, StrokeCap.Round)
        drawLine(pole, Offset(x + u * 0.28f, y + u * 0.78f), Offset(x + u * 0.6f, y + u * 0.78f), sw, StrokeCap.Round)
        drawPath(Path().apply {
            moveTo(x + u * 0.42f, y + u * 0.18f)
            lineTo(x + u * 0.78f, y + u * 0.34f)
            lineTo(x + u * 0.42f, y + u * 0.5f)
            close()
        }, cloth)
    }

    // ---- the home motif -----------------------------------------------------------------------------------------

    /** A legal 4x4 crop: two flagged mines, three hidden squares and the numbers around them. Never generated. */
    private val motif = MinesState(
        4, 4,
        mines = "0001" + "0000" + "1001" + "1000",
        opening = 0,
        marks = "ooof" + "oooo" + ".oof" + ".oo.",
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val unit = minOf(maxWidth, maxHeight) / 4
            val unitPx = with(density) { unit.toPx() }
            val digits = remember(unitPx) { digitLayouts(measurer, unitPx * 0.6f, density) }
            Canvas(Modifier.size(unit * 4)) { drawBoard(motif, unitPx, digits, scheme, BoardHues.HUE_PAIRS[0], Overlay()) }
        }
    }

    // ---- the board ----------------------------------------------------------------------------------------------

    private const val BUMP_MS = 600

    private class Bump(val cell: Int, val progress: Animatable<Float, *>)

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as MinesState
        val n = s.width * s.height
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val hues = remember(s.mines) { BoardHues.pair(s.mines.hashCode()) }
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())
        // The pen is the screen's, not the board's (Nonogram's): in the state it would be a move.
        var flagPen by rememberSaveable(s.mines) { mutableStateOf(false) }
        var cursor by remember(s.mines) { mutableStateOf<Int?>(null) }
        val scope = rememberCoroutineScope()
        var bump by remember(s.mines) { mutableStateOf<Bump?>(null) }
        // Reduced motion: a mine found flashes without the shake.
        val still = LocalReducedMotion.current
        val live = interactive && !s.solved && !s.failed
        val feel = rememberMoveFeel(List(n) { if (it in s.bumps) 'x' else s.marks[it] })

        fun act(cell: Int, flag: Boolean) {
            val next = (if (flag) s.flag(cell) else s.reveal(cell)) ?: return
            if (next.lives < s.lives) {
                val b = Bump(cell, Animatable(0f))
                bump = b
                scope.launch {
                    b.progress.animateTo(1f, tween(BUMP_MS, easing = LinearEasing))
                    if (bump === b) bump = null
                }
            }
            onState(next)
        }

        Column(
            Modifier
                .fillMaxSize()
                .boardKeys(live) { key, _, repeat ->
                    movedCursor(cursor, key, s.height, s.width)?.let { cursor = it; return@boardKeys true }
                    val at = cursor ?: return@boardKeys false
                    val action = minesKeyAction(key) ?: return@boardKeys false
                    if (!repeat) act(at, action == MinesKeyAction.Flag)
                    true
                }
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Lives and the count, always this height, so a mine found or a loss never moves the board.
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Pointers.Hearts(s.lives, Modifier)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (s.failed) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Out of lives", style = MaterialTheme.typography.bodyMedium, color = scheme.error, maxLines = 1)
                            FilledTonalButton(onClick = { if (interactive) onState(s.retry()) }) { Text("Try again", maxLines = 1) }
                        }
                    } else {
                        val left = s.minesLeft
                        Text(
                            if (s.solved) "All clear" else "$left mine${if (left == 1) "" else "s"} left",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Both axes: whichever runs out first sets the square.
                val step = minOf(maxWidth / s.width, maxHeight / s.height)
                val unitPx = with(density) { step.toPx() }
                val digits = remember(unitPx) { digitLayouts(measurer, unitPx * 0.58f, density) }
                val b = bump
                Canvas(
                    Modifier
                        .size(step * s.width, step * s.height)
                        .highlightGrid(s.width, s.height)
                        .gridCursor(cursor.takeIf { live }, s.width, s.height, scheme.primary)
                        .pointerInput(s, live, flagPen) {
                            if (!live) return@pointerInput
                            fun cellAt(o: Offset): Int {
                                val unit = size.width.toFloat() / s.width
                                val c = (o.x / unit).toInt().coerceIn(0, s.width - 1)
                                val r = (o.y / unit).toInt().coerceIn(0, s.height - 1)
                                return r * s.width + c
                            }
                            detectTapGestures(
                                onTap = { o -> act(cellAt(o), flagPen) },
                                onLongPress = { o -> act(cellAt(o), !flagPen) },
                            )
                        },
                ) {
                    drawBoard(
                        s, size.width / s.width, digits, scheme, hues,
                        Overlay(highlight, glow, pulse.value, b?.cell ?: -1, b?.progress?.value ?: 0f, !still),
                        feel, Color(accent),
                    )
                }
            }

            PenRow(flagPen, enabled = live, onPen = { flagPen = it })
        }
    }

    /** Reveal or Flag. Kept clear of the hint popover: the player reaches for it to make a hinted move. */
    @Composable
    private fun PenRow(flagPen: Boolean, enabled: Boolean, onPen: (Boolean) -> Unit) {
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).keepClear(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            for (option in listOf(false, true)) {
                val chosen = flagPen == option
                Row(
                    Modifier
                        .width(124.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (chosen) Color(accent).copy(alpha = 0.22f) else scheme.surface)
                        .border(if (chosen) 2.dp else 1.dp, if (chosen) Color(accent) else scheme.outline, RoundedCornerShape(14.dp))
                        .clickable(enabled = enabled) { onPen(option) },
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(22.dp)) {
                        val u = size.minDimension
                        if (option) drawFlag(0f, 0f, u, BoardHues.ink(BoardHues.HUE_PAIRS[0].second, dark), scheme.onSurface.copy(alpha = 0.7f))
                        else {
                            // An open square: a tile with its number showing.
                            drawRoundRect(scheme.onSurface.copy(alpha = 0.75f), Offset(u * 0.12f, u * 0.12f), Size(u * 0.76f, u * 0.76f), CornerRadius(u * 0.16f), style = Stroke(u * 0.08f))
                            drawCircle(scheme.onSurface.copy(alpha = 0.75f), u * 0.1f, Offset(u / 2, u / 2))
                        }
                    }
                    Text(if (option) "Flag" else "Reveal", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                }
            }
        }
    }
}
