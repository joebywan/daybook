package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.ui.CappedFontScale
import com.joebywan.daybook.ui.LocalBoardTones
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.boardSp
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.arcadeHardShadow
import com.joebywan.daybook.ui.theme.classicLook
import com.joebywan.daybook.ui.theme.vivid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Test puzzle: sixteen words, four groups of four (`docs/QUARTETS_SPEC.md`). In
 * [com.joebywan.daybook.core.PuzzleRegistry.lab] only. The rules are [QuartetsLogic]; this is the object and its board.
 */
object Quartets : PuzzleType {

    override val id = "test-quartets"
    override val displayName = "test:Quartets"
    override val tagline = "Sixteen words, four groups of four"

    // Lexicon's: the Arcade accent set is full, and a lab puzzle shares one.
    override val accent = 0xFF278E8E

    override val rules = listOf(
        "Sixteen words hide four groups of four. The words of a group share something.",
        "Pick four words and tap Submit. A right group is set aside, with what it shares.",
        "A wrong four costs one of four lives. Lose all four and the board is lost; Try again deals it afresh.",
        "Some words fit more than one group, but there is exactly one way to split all sixteen.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = QuartetsLogic.generate(seed, difficulty)

    // Undo could only hand back a life (Pointers' reasoning). Hints come with the teacher.
    override val offersUndo: Boolean get() = false
    override val offersHints: Boolean get() = false

    // ---- pure helpers ------------------------------------------------------------------------

    /** Band hues in found order: Violet, Amber, Teal, Blue (no Coral, Green or Rose: nothing is right or wrong by colour). */
    internal val BAND_HUES = listOf(268f, 34f, 175f, 215f)

    /**
     * The order the tiles are shown in, as indices into the words: dealt order, or after [shuffles] presses of Shuffle one
     * fixed permutation per count. Display only, never state; filtering it keeps the tiles left in their relative order.
     */
    internal fun displayOrder(words: Int, shuffles: Int): List<Int> =
        if (shuffles == 0) (0 until words).toList() else Rng(shuffles * 7919L + words).shuffled((0 until words).toList())

    /** The indices of [mask]'s words, lowest first. */
    internal fun indicesOf(mask: Int): List<Int> = (0 until 32).filter { mask and (1 shl it) != 0 }

    /** The mask of a pick of word indices. */
    internal fun maskOf(picked: Set<Int>): Int = picked.fold(0) { m, i -> m or (1 shl i) }

    /** The groups a board shows as bands, top down, and whether each is one the player found (else revealed on a loss). */
    internal fun bands(s: QuartetsState): List<Pair<Int, Boolean>> =
        s.found.map { it to true } + (if (s.failed) QuartetsLogic.reveal(s).map { it to false } else emptyList())

    // ---- the motif ---------------------------------------------------------------------------

    @Composable
    override fun Preview(modifier: Modifier) {
        val a = ArcadeBoard.current
        val dark = BoardHues.isDark(MaterialTheme.colorScheme)
        val violet = BoardHues.fill(BAND_HUES[0], dark)
        val amber = BoardHues.fill(BAND_HUES[1], dark)
        val picked = vivid(accent)
        Canvas(modifier) {
            val gap = size.minDimension * 0.05f
            val w = (size.width - gap * 3) / 4
            val h = (size.height - gap * 3) / 4
            val r = CornerRadius(h * 0.22f)
            val bandW = size.width
            // Two found bands, a title stroke over a line of words.
            listOf(violet, amber).forEachIndexed { row, fill ->
                val y = row * (h + gap)
                drawRoundRect(fill, Offset(0f, y), Size(bandW, h), r)
                val ink = BoardHues.onFill(fill)
                drawRoundRect(ink, Offset(bandW * 0.32f, y + h * 0.22f), Size(bandW * 0.36f, h * 0.18f), CornerRadius(h * 0.09f))
                drawRoundRect(ink.copy(alpha = 0.6f), Offset(bandW * 0.14f, y + h * 0.6f), Size(bandW * 0.72f, h * 0.14f), CornerRadius(h * 0.07f))
            }
            // A 2x4 of tiles, two of them picked.
            for (row in 0 until 2) for (col in 0 until 4) {
                val tl = Offset(col * (w + gap), (row + 2) * (h + gap))
                val on = (row == 0 && col == 1) || (row == 1 && col == 2)
                drawRoundRect(if (on) picked else a.square, tl, Size(w, h), r)
                drawRoundRect(if (on) a.selectedEdge else a.frame, tl, Size(w, h), r, style = Stroke(if (on) gap * 0.6f else gap * 0.3f))
                drawRoundRect(
                    (if (on) BoardHues.onFill(picked) else a.squareInk).copy(alpha = 0.7f),
                    Offset(tl.x + w * 0.2f, tl.y + h * 0.42f), Size(w * 0.6f, h * 0.16f), CornerRadius(h * 0.08f),
                )
            }
        }
    }

    // ---- the board ---------------------------------------------------------------------------

    private val NOTE = 28.dp
    private val LIVES = 28.dp
    private val BUTTONS = 48.dp
    private val GAP = 6.dp
    private val MAX_TILE = 132.dp
    private const val ROWS = 4
    private const val COLS = 4

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as QuartetsState
        val live = interactive && !s.solved && !s.failed
        val tones = LocalBoardTones.current
        val still = LocalReducedMotion.current
        val scope = rememberCoroutineScope()
        val (highlight, glow, pulse) = rememberHintPaint()

        // The pick belongs to the board it was made on: a board arriving any other way (Try again, a hint's Show me) starts empty.
        var picked by remember(s.words) { mutableStateOf(emptySet<Int>()) }
        var pickedOn by remember(s.words) { mutableStateOf<QuartetsState?>(null) }
        val pick = if (pickedOn == s) picked else emptySet()

        // Shuffle reorders the display only; the count is all it remembers.
        var shuffles by rememberSaveable(s.words) { mutableIntStateOf(0) }
        val order = remember(s.words.size, shuffles) { displayOrder(s.words.size, shuffles) }

        var notice by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(1600)
                notice = null
            }
        }
        val shake = remember { Animatable(0f) }
        var shaking by remember { mutableIntStateOf(0) }

        fun tap(i: Int) {
            if (!live) return
            val now = pick
            val next = when {
                i in now -> now - i
                now.size < QuartetsLogic.GROUP -> now + i
                else -> return
            }
            picked = next
            pickedOn = s
        }

        fun submit() {
            if (!live || pick.size != QuartetsLogic.GROUP) return
            val mask = maskOf(pick)
            val next = s.guess(mask)
            if (next == null) {
                if (mask in s.misses) notice = "Already tried"
                return
            }
            if (next.found.size > s.found.size) {
                notice = null
                picked = emptySet()
                pickedOn = null
                if (!next.solved) tones(Tones.word())
            } else {
                notice = if (QuartetsLogic.oneAway(s, mask)) "One away" else "Not one of the groups"
                pickedOn = if (next.failed) null else next
                if (!still) {
                    shaking = mask
                    scope.launch {
                        shake.snapTo(0f)
                        shake.animateTo(0f, keyframes {
                            durationMillis = 360
                            -1f at 60
                            1f at 140
                            -0.7f at 220
                            0.4f at 290
                        })
                        shaking = 0
                    }
                }
            }
            onState(next)
        }

        val a = ArcadeBoard.current
        val classic = classicLook()
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val accentColour = vivid(accent)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val bandList = bands(s)
        val rest = order.filter { s.remaining and (1 shl it) != 0 && bandList.none { (g, _) -> g and (1 shl it) != 0 } }

        val header = notice ?: when {
            s.solved -> "All four groups found"
            s.failed -> "Out of lives: the groups were"
            else -> "${s.found.size} of 4 groups found"
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            WordHeader(header, notice != null, notice != null && notice != "One away", NOTE)
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Sized from both axes: four across the width, four down the height, never taller than wide.
                val tileW = minOf((maxWidth - GAP * (COLS - 1)) / COLS, MAX_TILE).coerceAtLeast(0.dp)
                val tileH = minOf((maxHeight - GAP * (ROWS - 1)) / ROWS, tileW * 0.78f).coerceAtLeast(0.dp)
                val gridW = tileW * COLS + GAP * (COLS - 1)
                val corner = tileH * 0.18f
                val wordStyle = MaterialTheme.typography.labelLarge
                // One size for every tile: the longest word in capitals fits its tile on one line.
                val wordSp = remember(s.words, tileW, tileH, density, wordStyle) {
                    fitSize(measurer, s.words.map { it.uppercase() }, wordStyle.copy(fontWeight = FontWeight.ExtraBold), tileW * 0.86f, minOf(tileH * 0.34f, 22.dp), density)
                }
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    bandList.forEachIndexed { k, (group, mine) ->
                        val hue = BAND_HUES[k % BAND_HUES.size]
                        Band(
                            title = QuartetsLogic.titleOf(s, group),
                            words = indicesOf(group).map { s.words[it].uppercase() },
                            hue = hue, found = mine, width = gridW, height = tileH, corner = corner, dark = dark,
                        )
                    }
                    rest.chunked(COLS).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                            for (i in row) {
                                val on = i in pick
                                val strong = i in highlight.strong
                                val dim = !highlight.isEmpty && !strong && i !in highlight.soft
                                val fill = if (on) accentColour else a.square
                                val ink = if (on) BoardHues.onFill(accentColour) else a.squareInk
                                val shape = RoundedCornerShape(corner)
                                Box(
                                    Modifier
                                        .size(tileW, tileH)
                                        .highlightAnchor(i)
                                        .graphicsLayer {
                                            if (shaking and (1 shl i) != 0) translationX = shake.value * 6.dp.toPx()
                                        }
                                        .then(if (dim) Modifier.alpha(0.3f) else Modifier)
                                        .then(if (!classic) Modifier.arcadeHardShadow(corner) else Modifier)
                                        .clip(shape)
                                        .background(fill)
                                        .border(if (on) 3.dp else 1.dp, if (on) a.selectedEdge else a.frame, shape)
                                        .then(if (strong) Modifier.border(3.dp, glow.copy(alpha = pulse.value), shape) else Modifier)
                                        .clickable(enabled = live) { tap(i) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        s.words[i].uppercase(),
                                        style = wordStyle.copy(fontSize = wordSp.boardSp(), lineHeight = (wordSp * 1.2f).boardSp()),
                                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = ink,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Lives(s.livesLeft, s.failed)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().height(BUTTONS).keepClear(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (s.failed) {
                    if (interactive) ActionButton("Try again", enabled = true, primary = true) {
                        picked = emptySet()
                        pickedOn = null
                        notice = null
                        onState(s.retry())
                    }
                } else {
                    ActionButton("Shuffle", enabled = live, primary = false) { shuffles++ }
                    ActionButton("Clear", enabled = live && pick.isNotEmpty(), primary = false) {
                        picked = emptySet()
                        pickedOn = null
                    }
                    ActionButton(
                        "Submit", enabled = live && pick.size == QuartetsLogic.GROUP, primary = true,
                        glowing = QuartetsLogic.SUBMIT in highlight.strong, glow = glow.copy(alpha = pulse.value),
                        modifier = Modifier.highlightAnchor(QuartetsLogic.SUBMIT),
                    ) { submit() }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    /**
     * The largest size (in dp) at most [cap] at which every one of [texts] fits [width] on one line in [style]. Measured,
     * not guessed: Fredoka's W and M are far wider than its I.
     */
    private fun fitSize(
        measurer: androidx.compose.ui.text.TextMeasurer,
        texts: List<String>,
        style: TextStyle,
        width: Dp,
        cap: Dp,
        density: androidx.compose.ui.unit.Density,
    ): Dp {
        if (cap <= 0.dp || width <= 0.dp) return 0.dp
        val ref = 40.dp
        val sized = with(density) { style.copy(fontSize = ref.toSp()) }
        val widest = texts.maxOfOrNull { measurer.measure(it, sized, softWrap = false, maxLines = 1).size.width } ?: 0
        if (widest == 0) return cap
        val fits = with(density) { ref * (width.toPx() / widest) }
        return minOf(cap, fits)
    }

    /** A group set aside: its title over its four words. A found one is filled; one revealed on a loss is only outlined. */
    @Composable
    private fun Band(title: String, words: List<String>, hue: Float, found: Boolean, width: Dp, height: Dp, corner: Dp, dark: Boolean) {
        val a = ArcadeBoard.current
        val scheme = MaterialTheme.colorScheme
        val fill = if (found) BoardHues.fill(hue, dark) else a.square
        val ink = if (found) BoardHues.onFill(fill) else BoardHues.ink(hue, dark)
        val shape = RoundedCornerShape(corner)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val style = MaterialTheme.typography.labelLarge
        val line = words.joinToString(", ")
        val titleSp = minOf(height * 0.3f, 18.dp)
        val wordsSp = remember(line, width, height, density, style) {
            fitSize(measurer, listOf(line), style.copy(fontWeight = FontWeight.Medium), width * 0.92f, minOf(height * 0.26f, 16.dp), density)
        }
        Column(
            Modifier
                .size(width, height)
                .then(if (found && !classicLook()) Modifier.arcadeHardShadow(corner) else Modifier)
                .clip(shape)
                .background(fill)
                .then(if (found) Modifier else Modifier.border(2.dp, ink, shape))
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = style.copy(fontSize = titleSp.boardSp(), lineHeight = (titleSp * 1.2f).boardSp()),
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                line,
                style = style.copy(fontSize = wordsSp.boardSp(), lineHeight = (wordsSp * 1.2f).boardSp()),
                fontWeight = FontWeight.Medium,
                color = if (found) ink else scheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }

    /** Lives as four dots, filled while left, with a short label; its row is always laid out. */
    @Composable
    private fun Lives(left: Int, failed: Boolean) {
        val a = ArcadeBoard.current
        val scheme = MaterialTheme.colorScheme
        val ink = if (classicLook()) scheme.onSurfaceVariant else a.squareInk
        val dot = if (classicLook()) scheme.onSurface else a.squareInk
        Row(
            Modifier.fillMaxWidth().height(LIVES),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CappedFontScale {
                Text(
                    if (failed) "No lives left" else "Lives",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (failed) scheme.error else ink,
                    maxLines = 1,
                )
            }
            Canvas(Modifier.width(14.dp * QuartetsLogic.LIVES + 8.dp * (QuartetsLogic.LIVES - 1)).height(14.dp)) {
                val d = size.height
                val step = d + 8.dp.toPx()
                for (k in 0 until QuartetsLogic.LIVES) {
                    val c = Offset(k * step + d / 2, d / 2)
                    // A life left is a filled dot; one spent an empty ring, so the count never rests on colour.
                    if (k < left) drawCircle(dot, d / 2, c)
                    else drawCircle(dot.copy(alpha = 0.55f), d / 2 - 1.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }

    /** A button under the board: [primary] in the look's control fill (Classic: the accent's tint), the rest plain. */
    @Composable
    private fun ActionButton(
        label: String,
        enabled: Boolean,
        primary: Boolean,
        modifier: Modifier = Modifier,
        glowing: Boolean = false,
        glow: Color = Color.Unspecified,
        onClick: () -> Unit,
    ) {
        val a = ArcadeBoard.current
        val classic = classicLook()
        val scheme = MaterialTheme.colorScheme
        val accentColour = vivid(accent)
        val shape = RoundedCornerShape(14.dp)
        val filled = primary && enabled
        val brush: Brush = when {
            !filled -> SolidColor(a.square)
            classic -> SolidColor(accentColour.copy(alpha = 0.22f).compositeOver(scheme.surface))
            else -> a.control.brush
        }
        val ink = when {
            !filled -> a.squareInk
            classic -> BoardHues.onTint(accentColour, scheme.surface)
            else -> a.control.ink
        }
        // One modifier chain whatever the state (only its values change), and no alpha layer: a disabled button fades its label.
        Box(
            modifier
                .widthIn(min = 92.dp)
                .height(44.dp)
                .arcadeHardShadow(14.dp)
                .background(brush, shape)
                .border(if (glowing) 3.dp else if (filled) 2.dp else 1.dp, if (glowing) glow else if (filled) ink else a.frame, shape)
                .clip(shape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            CappedFontScale {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (enabled) ink else ink.copy(alpha = 0.45f), maxLines = 1)
            }
        }
    }
}
