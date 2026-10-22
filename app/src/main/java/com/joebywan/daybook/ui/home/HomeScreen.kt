package com.joebywan.daybook.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.joebywan.daybook.data.Stats
import com.joebywan.daybook.ui.stats.MedalIcon
import com.joebywan.daybook.platform.GetTheAppCard
import com.joebywan.daybook.platform.formatDate
import kotlinx.datetime.LocalDate

/** A `DateTimeFormatter` pattern; see `formatDate` in the platform seam. */
private const val HEADER_DATE = "EEEE d MMMM"

/**
 * The grid was first sized so every puzzle fitted one phone screen; with more than twenty it scrolls, so the order does
 * the work instead. By default the most played sit at the top (`PuzzleOrder.mostPlayed`, read once a day so a solve never
 * moves a tile under the player's finger), and a player can set their own order (`ArrangeScreen`). Nothing is hidden:
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
    onArchive: (String) -> Unit,
    onStats: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit,
    puzzles: List<PuzzleType>,
    inProgress: () -> Set<String>,
    onArrange: () -> Unit,
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
            Modifier.fillMaxSize(),
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
                        .onSizeChanged { optionsHeight = it.height },
                ) {
                    LaunchOptions(difficulty, onDifficulty, mode, onMode, sideBySide = short)
                }
            }
            item {
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
                )
            }
            item {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Arrange puzzles",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onArrange)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
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
            HeaderButton(Icons.Default.History, "Archive", onArchive)
            Spacer(Modifier.width(6.dp))
            HeaderButton(Icons.Default.InsertChart, "Statistics", onStats)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(HeaderButtonSize).clip(CircleShape).background(scheme.surface).clickable(onClick = onAchievements),
                contentAlignment = Alignment.Center,
            ) { MedalIcon(Modifier.size(22.dp), scheme.onSurfaceVariant, "Achievements") }
            Spacer(Modifier.width(6.dp))
            HeaderButton(Icons.Default.Settings, "Settings", onSettings)
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

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(HeaderButtonSize)
            .clip(CircleShape)
            .background(scheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
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

/**
 * Laid out by hand rather than with a lazy grid. A couple of dozen tiles never justify recycling, and a lazy
 * grid demands a height of its own — it cannot sit in a scrolling column, which is what lets this
 * grid take exactly the room it needs and scroll only when it does not fit.
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
) {
    BoxWithConstraints {
        val columns = homeColumns(maxWidth, height)
        val previewSize = previewSize(tileWidth(maxWidth, columns))
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            puzzles.chunked(columns).forEach { rowPuzzles ->
                Row(
                    // The last row is short. Intrinsic height keeps every tile in a row as tall as
                    // the tallest, which only matters once a large font scale wraps a name.
                    Modifier.height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    rowPuzzles.forEach { puzzle ->
                        PuzzleTile(
                            puzzle = puzzle,
                            difficulty = difficulty,
                            doneToday = doneToday,
                            isPick = puzzle.id == pick,
                            inProgress = inProgress,
                            previewSize = previewSize,
                            onLaunch = onLaunch,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    // Keeps the two tiles of the last row the same width as the three above them.
                    repeat(columns - rowPuzzles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PuzzleTile(
    puzzle: PuzzleType,
    difficulty: Difficulty,
    doneToday: Set<Pair<String, Difficulty>>,
    isPick: Boolean,
    inProgress: () -> Set<String>,
    previewSize: Dp,
    onLaunch: (String) -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = Color(puzzle.accent)
    val solved = (puzzle.id to difficulty) in doneToday
    // A started pick shows "In progress" alone: the invitation has been taken up, and two labels cannot share a thumbnail.
    val started = puzzle.id in inProgress()
    val marked = isPick && !solved && !started

    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (solved) accent.copy(alpha = 0.20f) else scheme.surface)
            // Today's pick (unsolved): gold ring and sparkles, drawn inside the tile, so nothing moves.
            .then(if (marked) Modifier.sparkleFrame() else Modifier)
            .clickable(onClick = { onLaunch(puzzle.id) })
            .padding(TilePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            puzzle.Preview(
                Modifier
                    .size(previewSize)
                    .clip(RoundedCornerShape(10.dp))
            )
            if (marked) TryMeLabel(previewSize, Modifier.align(Alignment.Center))
            if (started) InProgressLabel(previewSize, Modifier.align(Alignment.Center))
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
            puzzle.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = if (solved) accent else scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        TierDots(
            accent = accent,
            selected = difficulty,
            cleared = { tier -> (puzzle.id to tier) in doneToday },
        )
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
