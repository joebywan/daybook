package com.joebywan.daybook.ui.home

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joebywan.daybook.core.Streak
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.LevelProgress
import com.joebywan.daybook.data.PuzzleOrder
import com.joebywan.daybook.data.Stats
import com.joebywan.daybook.ui.LocalHaptics
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.stats.MedalIcon
import com.joebywan.daybook.platform.GetTheAppCard
import com.joebywan.daybook.platform.formatDate
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** A `DateTimeFormatter` pattern; see `formatDate` in the platform seam. */
private const val HEADER_DATE = "EEEE d MMMM"

/**
 * The grid was first sized so every puzzle fitted one phone screen; with more than twenty it scrolls, so the order does
 * the work instead. By default the most played sit at the top (`PuzzleOrder.mostPlayed`, read once a day so a solve never
 * moves a tile under the player's finger), and a player can set their own order by pressing and holding a tile and dragging
 * it ([PuzzleGrid]). Nothing is hidden:
 * anything else is a scroll away. A phone in portrait has three columns; a wider window (a tablet, a phone on its side)
 * takes a column per [MinTileWidth], up to [MAX_COLUMNS]. A short wide window (a phone on its side) takes more columns,
 * down to [MinShortTileWidth], until two rows fit under the header: see [homeColumns].
 */
private const val COLUMNS = 3
private const val MAX_COLUMNS = 7
private val MinTileWidth = 150.dp
private val MinShortTileWidth = 110.dp
private val TileGap = 8.dp
private val TilePadding = 6.dp

/** A tile's height less its motif: padding, the gap, one line of name, the gap and the tier dots. */
private val TileChrome = TilePadding * 2 + 4.dp + 24.dp + 3.dp + 7.dp

/**
 * Columns for a grid [width] wide with [height] left under the header. A column per [MinTileWidth] (3 to [MAX_COLUMNS]),
 * then, while two rows of tiles would not fit in [height], one more, as long as a tile stays [MinShortTileWidth] wide.
 * Only a short wide window (a phone on its side) ever gets past the first step: a phone in portrait has the height, and
 * is too narrow for a fourth tile of that width anyway.
 */
internal fun homeColumns(width: Dp, height: Dp): Int {
    var columns = (width / MinTileWidth).toInt().coerceIn(COLUMNS, MAX_COLUMNS)
    while (twoRows(width, columns) > height && tileWidth(width, columns + 1) >= MinShortTileWidth) columns++
    return columns
}

private fun tileWidth(width: Dp, columns: Int) = (width - TileGap * (columns - 1)) / columns
private fun previewSize(tileWidth: Dp) = (tileWidth - TilePadding * 2) * PREVIEW_FRACTION
private fun twoRows(width: Dp, columns: Int) = (previewSize(tileWidth(width, columns)) + TileChrome) * 2 + TileGap

/**
 * The motif is deliberately smaller than the tile it sits in. At full tile width four rows run
 * past the bottom of a 360x800 phone, which would give the grid back the scrolling it exists to
 * remove; at this fraction it is still large enough to recognise a puzzle by its shape.
 */
private const val PREVIEW_FRACTION = 0.72f

/**
 * Every puzzle on one screen: pick the difficulty and mode once at the top, then tap a motif.
 *
 * [difficulty] and [mode] are owned by the caller rather than by this screen. Navigating into a
 * puzzle tears the home screen down, so anything remembered here would be reset by every game
 * played — the selector would forget what it was set to between one puzzle and the next.
 */
@Composable
fun HomeScreen(
    today: LocalDate,
    completions: List<Completion>,
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    mode: LaunchMode,
    onMode: (LaunchMode) -> Unit,
    onLaunch: (String) -> Unit,
    levels: LevelProgress,
    onLevels: () -> Unit,
    onArchive: (String) -> Unit,
    onStats: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit,
    puzzles: List<PuzzleType>,
    inProgress: () -> Set<String>,
    customOrder: Boolean,
    onOrder: (List<String>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val doneToday = remember(completions, today) {
        completions.filter { it.day == today }
            .map { it.puzzleId to it.difficulty }
            .toSet()
    }
    val streak = remember(completions, today) { Stats.streak(completions, today) }
    var pickingArchive by rememberSaveable { mutableStateOf(false) }

    // What the header and the pinned rows take, so the grid knows the height left for its tiles (homeColumns).
    var headerHeight by remember { mutableStateOf(0) }
    var optionsHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val list = rememberLazyListState()
    // Where the grid shows, in root pixels: from the pinned rows' foot to the list's. Plain fields, read by a drag only.
    val window = remember { GridWindow() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // A phone on its side. A tablet in landscape is taller than this and keeps the stacked rows.
        val short = maxHeight < 480.dp && maxWidth > maxHeight
        val gridHeight = maxHeight - TopPadding - with(density) { (headerHeight + optionsHeight).toDp() }
        // The tier and Daily/Random rows stay pinned while the title and the grid scroll under them, so a player
        // halfway down the grid can change either without scrolling back. The grid is sized to fit without
        // scrolling, but a short screen or a large font scale can still overflow it.
        LazyColumn(
            Modifier.fillMaxSize().onGloballyPositioned { window.bottom = it.boundsInRoot().bottom },
            state = list,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = TopPadding, bottom = 18.dp),
        ) {
            item {
                Header(
                    modifier = Modifier.onSizeChanged { headerHeight = it.height },
                    compact = short,
                    today = today,
                    streak = streak,
                    onArchive = { pickingArchive = true },
                    onStats = onStats,
                    onAchievements = onAchievements,
                    onSettings = onSettings,
                )
            }
            stickyHeader {
                // Opaque, so the tiles scrolling under it do not show through.
                Column(
                    Modifier.fillMaxWidth().background(scheme.background).padding(vertical = if (short) 8.dp else 14.dp)
                        .onSizeChanged { optionsHeight = it.height }
                        .onGloballyPositioned { window.top = it.boundsInRoot().bottom },
                ) {
                    LaunchOptions(difficulty, onDifficulty, mode, onMode, levelStrip = { LevelStrip(levels, onLevels) }, sideBySide = short)
                }
            }
            if (mode == LaunchMode.LEVELS) item {
                // The level's draw order, not the arranged one: a level is a set the player is given, in the order dealt.
                LevelGrid(levels, levels.current, onPlay = onLaunch, onAllLevels = onLevels, height = gridHeight, inProgress = inProgress)
            } else item {
                PuzzleGrid(
                    puzzles = puzzles,
                    inProgress = inProgress,
                    difficulty = difficulty,
                    // Random boards are freshly seeded on every tap, so "solved today" says nothing
                    // about the game a tile is about to start. The tiles keep their marks-shaped space
                    // either way, so switching mode never shuffles the grid under a finger.
                    doneToday = if (mode == LaunchMode.DAILY) doneToday else emptySet(),
                    pick = PuzzleRegistry.featured(today.toEpochDays()).id,
                    height = gridHeight,
                    onLaunch = onLaunch,
                    onOrder = onOrder,
                    scroll = { list.scrollBy(it) },
                    window = window,
                )
            }
            // Under the grid, so it moves nothing above it; gone once the player has an order of their own. Only where
            // the order applies: a level's tiles keep their draw order.
            if (mode != LaunchMode.LEVELS && !customOrder) item {
                Text(
                    "Press and hold a puzzle to move it.",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                )
            }
            item { GetTheAppCard() }
        }
    }

    if (pickingArchive) {
        ArchivePicker(
            onPick = { puzzleId ->
                pickingArchive = false
                onArchive(puzzleId)
            },
            onDismiss = { pickingArchive = false },
        )
    }
}

private val TopPadding = 14.dp

@Composable
private fun Header(
    modifier: Modifier,
    compact: Boolean,
    today: LocalDate,
    streak: Streak,
    onArchive: () -> Unit,
    onStats: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DaybookMark(Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Daybook",
                // 24sp, one line: with four buttons a 360dp-wide phone wrapped the 30sp name.
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp),
                maxLines = 1,
                color = scheme.onBackground,
                modifier = if (compact) Modifier else Modifier.weight(1f),
            )
            // On a short window the date and streak join the title's row, which gives the grid back about 44dp.
            if (compact) {
                Spacer(Modifier.width(14.dp))
                HeaderDate(today, oneLine = true, Modifier.weight(1f))
                StreakBadge(streak)
                Spacer(Modifier.width(10.dp))
            }
            // Each button's touch target is 48dp (Material's minimum) around its 36dp circle; neighbours overlap by
            // the 6dp between circles, and the offset puts the last circle back at the margin, so the title keeps its room at 360dp.
            Row(Modifier.offset(x = HeaderTouchInset), horizontalArrangement = Arrangement.spacedBy(-HeaderTouchInset)) {
                HeaderButton("Archive", onArchive) { Icon(Icons.Default.History, it, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
                HeaderButton("Statistics", onStats) { Icon(Icons.Default.InsertChart, it, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
                HeaderButton("Achievements", onAchievements) { MedalIcon(Modifier.size(22.dp), scheme.onSurfaceVariant, it) }
                HeaderButton("Settings", onSettings) { Icon(Icons.Default.Settings, it, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
            }
        }
        if (!compact) Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderDate(today, oneLine = false, Modifier.weight(1f))
            StreakBadge(streak)
        }
    }
}

@Composable
private fun HeaderDate(today: LocalDate, oneLine: Boolean, modifier: Modifier) {
    Text(
        formatDate(today, HEADER_DATE),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (oneLine) 1 else Int.MAX_VALUE,
        overflow = if (oneLine) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier,
    )
}

/** Four of them share the header with the title, so each is a little under the 40dp the three used to be. */
private val HeaderButtonSize = 36.dp

/** The touch target's margin round the circle: (48 - 36) / 2. */
private val HeaderTouchInset = 6.dp

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit, icon: @Composable (label: String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(HeaderButtonSize + HeaderTouchInset * 2)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(HeaderButtonSize).clip(CircleShape).background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) { icon(label) }
    }
}

@Composable
private fun StreakBadge(streak: Streak) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Never a bare 0 (docs/REWARDS.md). An unseen, zero-width number holds the badge, and so the header, at the
        // height it has with one, so the first solve moves nothing.
        if (!streak.alive) {
            Text("0", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(0.dp).alpha(0f).clearAndSetSemantics { })
            Text(
                if (streak.lapsed) "Welcome back" else "Day one awaits",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        } else {
            Text(
                "${streak.current}",
                style = MaterialTheme.typography.titleMedium,
                color = if (streak.alive) scheme.secondary else scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text("day streak", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
    }
}

/** The part of the list the grid shows through, in root pixels: under the pinned rows, above the list's foot. */
private class GridWindow(var top: Float = 0f, var bottom: Float = Float.MAX_VALUE)

/** A tile being dragged: the puzzle, where on it the finger took hold, and the finger, in the grid's pixels. */
private class Drag(val id: String, val grab: Offset, finger: Offset) {
    var finger by mutableStateOf(finger)
}

/** A tile's layout id: which puzzle it is, and its place in cells (column, row), animated as the others make room. */
private class Cell(val id: String, val at: () -> Offset)

/** The band at each edge where a drag scrolls the list, and the most it scrolls in a frame. */
private val ScrollEdge = 64.dp
private val ScrollStep = 14.dp

/** How long a tile takes to make room for a dragged one, and to lift. */
private const val MOVE_MS = 150

/**
 * Laid out by hand rather than with a lazy grid. A couple of dozen tiles never justify recycling, and a lazy
 * grid demands a height of its own — it cannot sit in a scrolling column, which is what lets this
 * grid take exactly the room it needs and scroll only when it does not fit.
 *
 * Press and hold a tile to lift it, then drag: the others slide to show where it will land, the list scrolls while the
 * finger is near the pinned rows or the foot, and the drop is one [onOrder] (the first makes the order the player's own).
 * Only a long press starts a drag, so a tap still opens a puzzle and a swipe still scrolls. One layout with a keyed child
 * per puzzle, so a tile keeps its animation as it changes rows. The drag is `remember` state; nothing is stored until it drops.
 */
@Composable
private fun PuzzleGrid(
    puzzles: List<PuzzleType>,
    inProgress: () -> Set<String>,
    difficulty: Difficulty,
    doneToday: Set<Pair<String, Difficulty>>,
    pick: String,
    height: Dp,
    onLaunch: (String) -> Unit,
    onOrder: (List<String>) -> Unit,
    scroll: suspend (Float) -> Float,
    window: GridWindow,
) {
    val haptics = LocalHaptics.current
    val still = LocalReducedMotion.current
    val moveSpec: AnimationSpec<Offset> = if (still) snap() else tween(MOVE_MS)
    val liftSpec: AnimationSpec<Float> = if (still) snap() else tween(MOVE_MS)
    val ids = puzzles.map { it.id }
    val byId = puzzles.associateBy { it.id }
    var drag by remember { mutableStateOf<Drag?>(null) }
    var tileHeight by remember { mutableIntStateOf(0) }
    val grid = remember { arrayOfNulls<LayoutCoordinates>(1) }
    BoxWithConstraints {
        val density = LocalDensity.current
        val columns = homeColumns(maxWidth, height)
        val previewSize = previewSize(tileWidth(maxWidth, columns))
        val tileW = with(density) { tileWidth(maxWidth, columns).roundToPx() }
        val gap = with(density) { TileGap.roundToPx() }
        val gridW = with(density) { maxWidth.roundToPx() }
        val rows = (ids.size + columns - 1) / columns

        // The lifted tile's corner: under the finger, kept inside the grid and out from under the pinned rows.
        fun corner(d: Drag): Offset {
            val top = grid[0]?.localToRoot(Offset.Zero)?.y ?: 0f
            val p = d.finger - d.grab
            val lowest = (rows * (tileHeight + gap) - gap - tileHeight).toFloat()
            val y = p.y.coerceIn(window.top - top, maxOf(window.top - top, window.bottom - top - tileHeight))
            return Offset(p.x.coerceIn(0f, (gridW - tileW).toFloat()), y.coerceIn(0f, maxOf(0f, lowest)))
        }
        // Where the lifted tile would land: the cell under its centre.
        val landing by remember(columns, tileW, tileHeight, ids) {
            derivedStateOf {
                drag?.let { d ->
                    val c = corner(d) + Offset(tileW / 2f, tileHeight / 2f)
                    val col = (c.x / (tileW + gap)).toInt().coerceIn(0, columns - 1)
                    val row = (c.y / (tileHeight + gap)).toInt().coerceIn(0, rows - 1)
                    (row * columns + col).coerceAtMost(ids.lastIndex)
                }
            }
        }
        val d = drag
        val shown = if (d == null) ids else PuzzleOrder.moved(ids, ids.indexOf(d.id), landing ?: ids.indexOf(d.id))

        LaunchedEffect(d != null) {
            // Scrolls while the finger is in an edge band, faster the deeper it goes. The finger keeps its place on the
            // screen, so in the grid it moves by what scrolled.
            val edge = with(density) { ScrollEdge.toPx() }
            val step = with(density) { ScrollStep.toPx() }
            while (true) {
                withFrameNanos { }
                val held = drag ?: break
                val y = grid[0]?.localToRoot(held.finger)?.y ?: continue
                val depth = when {
                    y < window.top + edge -> (y - window.top - edge) / edge
                    y > window.bottom - edge -> (y - window.bottom + edge) / edge
                    else -> 0f
                }.coerceIn(-1f, 1f)
                if (depth != 0f) held.finger += Offset(0f, scroll(depth * step))
            }
        }

        Layout(
            content = {
                shown.forEachIndexed { slot, id ->
                    key(id) {
                        val at by animateOffsetAsState(
                            Offset((slot % columns).toFloat(), (slot / columns).toFloat()), moveSpec, label = "tile",
                        )
                        val lift by animateFloatAsState(if (d?.id == id) 1f else 0f, liftSpec, label = "lift")
                        val index = ids.indexOf(id)
                        PuzzleTile(
                            byId.getValue(id), (id to difficulty) in doneToday, isPick = id == pick, inProgress, previewSize,
                            // A long press held still and let go is not a tap: the click lands before the drag ends.
                            onClick = { if (drag == null) onLaunch(id) },
                            modifier = Modifier
                                .layoutId(Cell(id) { at })
                                .graphicsLayer {
                                    scaleX = 1f + 0.06f * lift
                                    scaleY = 1f + 0.06f * lift
                                    shadowElevation = 10.dp.toPx() * lift
                                    shape = RoundedCornerShape(18.dp)
                                }
                                // The drag's twin for a screen reader, which cannot press and drag.
                                .semantics {
                                    customActions = listOfNotNull(
                                        if (index > 0) CustomAccessibilityAction("Move earlier") {
                                            onOrder(PuzzleOrder.moved(ids, index, index - 1)); true
                                        } else null,
                                        if (index < ids.lastIndex) CustomAccessibilityAction("Move later") {
                                            onOrder(PuzzleOrder.moved(ids, index, index + 1)); true
                                        } else null,
                                    )
                                },
                        ) { accent ->
                            TierDots(
                                accent = accent,
                                selected = difficulty,
                                cleared = { tier -> (id to tier) in doneToday },
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .onGloballyPositioned { grid[0] = it }
                .pointerInput(ids, columns, tileW, tileHeight) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { p ->
                            val col = (p.x / (tileW + gap)).toInt()
                            val row = (p.y / (tileHeight + gap)).toInt()
                            val slot = row * columns + col
                            val origin = Offset((col * (tileW + gap)).toFloat(), (row * (tileHeight + gap)).toFloat())
                            if (col < columns && slot in ids.indices && p.x - origin.x < tileW && p.y - origin.y < tileHeight) {
                                drag = Drag(ids[slot], p - origin, p)
                                haptics.tick()
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            drag?.finger = change.position
                        },
                        onDragEnd = {
                            val held = drag
                            val to = landing
                            drag = null
                            if (held != null) {
                                val from = ids.indexOf(held.id)
                                if (to != null && to != from) onOrder(PuzzleOrder.moved(ids, from, to))
                                haptics.tick()
                            }
                        },
                        onDragCancel = { drag = null },
                    )
                },
        ) { measurables, constraints ->
            // Every tile as tall as the tallest, which only matters once a large font scale wraps a name.
            val h = measurables.maxOfOrNull { it.maxIntrinsicHeight(tileW) } ?: 0
            if (h != tileHeight) tileHeight = h
            val placeables = measurables.map { it.measure(Constraints.fixed(tileW, h)) }
            layout(constraints.maxWidth, maxOf(0, rows * (h + gap) - gap)) {
                placeables.forEachIndexed { i, p ->
                    val cell = measurables[i].layoutId as Cell
                    val held = drag
                    if (held?.id == cell.id) {
                        val c = corner(held)
                        p.place(c.x.roundToInt(), c.y.roundToInt(), zIndex = 1f)
                    } else {
                        val at = cell.at()
                        p.place((at.x * (tileW + gap)).roundToInt(), (at.y * (h + gap)).roundToInt())
                    }
                }
            }
        }
    }
}

/** [count] tiles in rows of [homeColumns] for a grid with [height] below the header; [tile] draws tile `i` at the motif size given. */
@Composable
internal fun TileGrid(count: Int, height: Dp, tile: @Composable (index: Int, previewSize: Dp, modifier: Modifier) -> Unit) {
    BoxWithConstraints {
        val columns = homeColumns(maxWidth, height)
        val previewSize = previewSize(tileWidth(maxWidth, columns))
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            (0 until count).chunked(columns).forEach { row ->
                Row(
                    // The last row is short. Intrinsic height keeps every tile in a row as tall as
                    // the tallest, which only matters once a large font scale wraps a name.
                    Modifier.height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    row.forEach { i -> tile(i, previewSize, Modifier.weight(1f).fillMaxHeight()) }
                    // Keeps the two tiles of the last row the same width as the three above them.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * A puzzle's motif and name, ticked when [solved]; [footer] is the line under the name (in the puzzle's accent).
 * [inProgress] is read here, in the tile, so a save that lands during the first frames is never shown stale.
 */
@Composable
internal fun PuzzleTile(
    puzzle: PuzzleType,
    solved: Boolean,
    isPick: Boolean,
    inProgress: () -> Set<String>,
    previewSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
    footer: @Composable (accent: Color) -> Unit,
) {
    val accent = Color(puzzle.accent)
    // A started pick shows "In progress" alone: the invitation has been taken up, and two labels cannot share a thumbnail.
    val started = puzzle.id in inProgress()
    val marked = isPick && !solved && !started
    HomeTile(
        name = puzzle.displayName,
        accent = accent,
        solved = solved,
        onClick = onClick,
        // Today's pick (unsolved): gold ring and sparkles, drawn inside the tile, so nothing moves.
        modifier = modifier,
        frame = if (marked) Modifier.sparkleFrame() else Modifier,
        motif = {
            puzzle.Preview(Modifier.size(previewSize).clip(RoundedCornerShape(10.dp)))
            if (marked) TryMeLabel(previewSize, Modifier.align(Alignment.Center))
            if (started) InProgressLabel(previewSize, Modifier.align(Alignment.Center))
        },
        footer = { footer(accent) },
    )
}

/** The shape every home tile shares: a motif with the solved tick in its corner, a name, a footer line. */
@Composable
internal fun HomeTile(
    name: String,
    accent: Color,
    solved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    frame: Modifier = Modifier,
    motif: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
    footer: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (solved) accent.copy(alpha = 0.20f) else scheme.surface)
            .then(frame)
            .clickable(onClick = onClick)
            .padding(TilePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            motif()
            if (solved) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(scheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, "Solved", tint = accent, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = if (solved) accent else scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        footer()
    }
}

/**
 * Three dots for the three tiers, so a tile can say "you did the Standard one" while the grid is
 * set to Expert. Kept to a few pixels: the tile's job is to be recognised and tapped, and a row of
 * labelled pills is exactly the weight that made the old cards unscannable.
 */
@Composable
private fun TierDots(accent: Color, selected: Difficulty, cleared: (Difficulty) -> Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Difficulty.entries.forEach { tier ->
            val isSelected = tier == selected
            Box(
                Modifier
                    .size(if (isSelected) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            cleared(tier) -> accent
                            isSelected -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
                            else -> scheme.outline.copy(alpha = 0.35f)
                        }
                    )
            )
        }
    }
}

/**
 * One archive door for all eleven puzzles, instead of the icon that used to sit on every card.
 * The archive is a place you go occasionally and deliberately; it does not need to be one tap from
 * a screen whose job is starting today's game.
 */
@Composable
private fun ArchivePicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // The platform default dialog width leaves a puzzle name and its tagline fighting over about
    // 280dp; this is a list to read, so it gets the width of the screen less a margin.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surface)
                .padding(vertical = 18.dp),
        ) {
            Text(
                "Open an archive",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "Every past day of any puzzle, free.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    // fill = false so the dialog stays as short as the list on a tall screen and
                    // only the list gives way on a short one.
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                PuzzleRegistry.all.forEach { puzzle ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(puzzle.id) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        puzzle.Preview(
                            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                puzzle.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface,
                            )
                            // The taglines lost their home when the cards went; this is the one
                            // place left that explains what a motif in the grid actually is.
                            Text(
                                puzzle.tagline,
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}
