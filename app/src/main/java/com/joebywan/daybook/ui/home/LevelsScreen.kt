package com.joebywan.daybook.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.LevelProgress
import com.joebywan.daybook.platform.BackButton
import com.joebywan.daybook.platform.formatClock

/** How many boards of [level] are solved; a puzzle no longer in the registry counts, as it shows finished. */
fun solvedCount(progress: LevelProgress, level: Int): Int {
    val solved = progress.solved(level)
    return progress.plan(level).count { it.puzzleId in solved || PuzzleRegistry.byId(it.puzzleId) == null }
}

/**
 * The line under the home grid's level strip: the tiers of [level] ("Mostly Hard, one Expert"), led by "Level N
 * done" while the level before it is finished and this one is still untouched. Derived, so nothing pops up and
 * nothing has to be dismissed: the first solve in the new level is what retires it.
 */
fun levelNote(progress: LevelProgress, level: Int): String {
    val summary = Levels.summary(progress.plan(level))
    val previous = level - 1
    return if (previous in progress.done && progress.solved(level).isEmpty()) "Level $previous done  ·  $summary" else summary
}

/**
 * What sits in the tier row's place in Levels mode: the level, how many of its boards are done, and a bar.
 * Tapping it opens the levels list, as the "All levels" tile does.
 */
@Composable
internal fun LevelStrip(progress: LevelProgress, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val level = progress.current
    val done = solvedCount(progress, level)
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(scheme.surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Level $level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1,
            )
            Text("$done of ${Levels.SIZE}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(scheme.outline.copy(alpha = 0.25f))) {
            if (done > 0) Box(Modifier.fillMaxWidth(done / Levels.SIZE.toFloat()).fillMaxHeight().background(scheme.primary))
        }
    }
}

/**
 * The five boards of [level] as home tiles, in plan order, ticked when solved in the level and saying their tier when
 * it is not Standard; [note] above them (one line, always laid out). [onAllLevels] adds a sixth tile for the list.
 */
@Composable
internal fun LevelGrid(
    progress: LevelProgress,
    level: Int,
    onPlay: (puzzleId: String) -> Unit,
    onAllLevels: (() -> Unit)?,
    note: String = levelNote(progress, level),
) {
    val scheme = MaterialTheme.colorScheme
    val solved = progress.solved(level)
    // A puzzle gone from the registry has nothing to draw; it counts as finished (solvedCount) and is left out.
    val boards = progress.plan(level).mapNotNull { b -> PuzzleRegistry.byId(b.puzzleId)?.let { it to b.difficulty } }
    Column {
        Text(
            note, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        TileGrid(boards.size + if (onAllLevels != null) 1 else 0) { i, previewSize, modifier ->
            val board = boards.getOrNull(i)
            if (board == null) {
                val tone = scheme.secondary
                HomeTile(
                    name = "All levels", accent = tone, solved = false, onClick = { onAllLevels?.invoke() }, modifier = modifier,
                    motif = {
                        Box(
                            Modifier.size(previewSize).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.FormatListNumbered, null, tint = tone, modifier = Modifier.size(previewSize * 0.42f))
                        }
                    },
                    footer = { TileFooter(if (progress.done.isEmpty()) "" else "${progress.done.size} done", scheme.onSurfaceVariant) },
                )
            } else {
                val (puzzle, tier) = board
                PuzzleTile(puzzle, puzzle.id in solved, isPick = false, previewSize, { onPlay(puzzle.id) }, modifier) { accent ->
                    TileFooter(if (tier == Difficulty.STANDARD) "" else tier.label, accent)
                }
            }
        }
    }
}

/** One line under a tile's name, always laid out (an empty one keeps the tiles of a row level). */
@Composable
private fun TileFooter(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, minLines = 1)
}

/**
 * Every level: the next one (locked), the current one with Play, then the finished ones, newest first.
 * [selected] shows one finished (or the current) level's five boards instead, to replay any of them.
 */
@Composable
fun LevelsScreen(
    progress: LevelProgress,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onPlay: (level: Int, puzzleId: String?) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val current = progress.current
    val level = selected?.takeIf(progress::isUnlocked)
    Column(
        Modifier.fillMaxSize().background(scheme.background).windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Nothing on Android, where the system back button is the way out.
            BackButton(onBack)
            Column(Modifier.weight(1f).padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp)) {
                Text(
                    if (level == null) "Levels" else "Level $level",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onBackground,
                )
                Text(
                    if (level == null) "Five boards each. Finish one to open the next." else levelLine(progress, level),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (level != null) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                LevelGrid(progress, level, onPlay = { onPlay(level, it) }, onAllLevels = null, note = Levels.summary(progress.plan(level)))
            }
            return@Column
        }
        val finished = progress.done.sortedDescending()
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { LockedRow(current + 1, current) }
            item {
                LevelRow(progress, current, onClick = { onSelect(current) }) {
                    Text(
                        "Play",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(scheme.primary)
                            .clickable { onPlay(current, null) }.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            items(finished, key = { it }) { n ->
                LevelRow(progress, n, onClick = { onSelect(n) }) {
                    Text(
                        formatClock(progress.solves[n].orEmpty().sumOf { it.seconds }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "3 of 5 · Mostly Standard, one Hard" for the current level; the tiers alone for a finished one. */
private fun levelLine(progress: LevelProgress, level: Int): String {
    val summary = Levels.summary(progress.plan(level))
    return if (level in progress.done) summary else "${solvedCount(progress, level)} of ${Levels.SIZE}  ·  $summary"
}

@Composable
private fun LevelRow(progress: LevelProgress, level: Int, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val solved = progress.solved(level)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Level $level", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Spacer(Modifier.height(6.dp))
            // A dot per board in its puzzle's colour: filled once solved.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                progress.plan(level).forEach { b ->
                    val accent = PuzzleRegistry.byId(b.puzzleId)?.let { Color(it.accent) } ?: scheme.outline
                    val done = b.puzzleId in solved || PuzzleRegistry.byId(b.puzzleId) == null
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (done) accent else scheme.outline.copy(alpha = 0.3f)))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun LockedRow(level: Int, current: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Level $level", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            Text("Finish $current to open", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Icon(Icons.Default.Lock, "Locked", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
