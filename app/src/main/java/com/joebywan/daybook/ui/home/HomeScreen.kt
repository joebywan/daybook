package com.joebywan.daybook.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.Stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HeaderDate = DateTimeFormatter.ofPattern("EEEE d MMMM")

/**
 * Eleven puzzles at three columns is four rows, and four rows of tiles plus a header and the
 * launch options is what fits a phone in one screenful. That is the entire reason this screen is
 * a grid: choosing a puzzle should never involve scrolling to find it.
 */
private const val COLUMNS = 3

private val TileGap = 8.dp
private val TilePadding = 6.dp

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
) {
    val scheme = MaterialTheme.colorScheme
    val doneToday = remember(completions, today) {
        completions.filter { it.day == today }
            .map { it.puzzleId to it.difficulty }
            .toSet()
    }
    val streak = remember(completions, today) { Stats.currentStreak(completions, today) }
    var pickingArchive by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // The grid is sized to fit without scrolling, but a short screen or a large font scale
            // can still overflow it, and a clipped bottom row would hide two whole puzzles.
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
    ) {
        Header(
            today = today,
            streak = streak,
            onArchive = { pickingArchive = true },
            onStats = onStats,
        )
        Spacer(Modifier.height(14.dp))
        LaunchOptions(difficulty, onDifficulty, mode, onMode)
        Spacer(Modifier.height(14.dp))
        PuzzleGrid(
            difficulty = difficulty,
            // Random boards are freshly seeded on every tap, so "solved today" says nothing
            // about the game a tile is about to start. The tiles keep their marks-shaped space
            // either way, so switching mode never shuffles the grid under a finger.
            doneToday = if (mode == LaunchMode.DAILY) doneToday else emptySet(),
            onLaunch = onLaunch,
        )
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

@Composable
private fun Header(
    today: LocalDate,
    streak: Int,
    onArchive: () -> Unit,
    onStats: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Daybook",
            style = MaterialTheme.typography.displaySmall,
            color = scheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        HeaderButton(Icons.Default.History, "Archive", onArchive)
        Spacer(Modifier.width(8.dp))
        HeaderButton(Icons.Default.InsertChart, "Statistics", onStats)
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            today.format(HeaderDate),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        StreakBadge(streak)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(scheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$streak",
            style = MaterialTheme.typography.titleMedium,
            color = if (streak > 0) scheme.secondary else scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text("day streak", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}

/**
 * Laid out by hand rather than with a lazy grid. Eleven tiles never justify recycling, and a lazy
 * grid demands a height of its own — it cannot sit in a scrolling column, which is what lets this
 * grid take exactly the room it needs and scroll only when it does not fit.
 */
@Composable
private fun PuzzleGrid(
    difficulty: Difficulty,
    doneToday: Set<Pair<String, Difficulty>>,
    onLaunch: (String) -> Unit,
) {
    BoxWithConstraints {
        val tileWidth = (maxWidth - TileGap * (COLUMNS - 1)) / COLUMNS
        val previewSize = (tileWidth - TilePadding * 2) * PREVIEW_FRACTION
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            PuzzleRegistry.all.chunked(COLUMNS).forEach { rowPuzzles ->
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
                            previewSize = previewSize,
                            onLaunch = onLaunch,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    // Keeps the two tiles of the last row the same width as the three above them.
                    repeat(COLUMNS - rowPuzzles.size) { Spacer(Modifier.weight(1f)) }
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
    previewSize: Dp,
    onLaunch: (String) -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = Color(puzzle.accent)
    val solved = (puzzle.id to difficulty) in doneToday

    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (solved) accent.copy(alpha = 0.20f) else scheme.surface)
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
