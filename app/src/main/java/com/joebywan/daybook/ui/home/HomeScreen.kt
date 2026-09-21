package com.joebywan.daybook.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.Stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HeaderDate = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
fun HomeScreen(
    today: LocalDate,
    completions: List<Completion>,
    onPlay: (String, Difficulty) -> Unit,
    onPractice: (String, Difficulty) -> Unit,
    onArchive: (String) -> Unit,
    onStats: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val doneToday = completions.filter { it.day == today }
        .map { it.puzzleId to it.difficulty }
        .toSet()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 16.dp, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daybook", style = MaterialTheme.typography.displaySmall, color = scheme.onBackground)
                    Text(
                        today.format(HeaderDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                StreakBadge(Stats.currentStreak(completions, today))
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(scheme.surface)
                        .clickable(onClick = onStats),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.InsertChart, "Statistics", tint = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Every puzzle, every past day, no ads, no subscription.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }

        items(PuzzleRegistry.all, key = { it.id }) { puzzle ->
            PuzzleCard(
                puzzle = puzzle,
                doneToday = doneToday,
                onPlay = onPlay,
                onPractice = onPractice,
                onArchive = onArchive,
            )
        }
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.surface)
            .padding(horizontal = 14.dp, vertical = 9.dp),
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

@Composable
private fun PuzzleCard(
    puzzle: PuzzleType,
    doneToday: Set<Pair<String, Difficulty>>,
    onPlay: (String, Difficulty) -> Unit,
    onPractice: (String, Difficulty) -> Unit,
    onArchive: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(Color(puzzle.accent))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                puzzle.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.History,
                "Archive",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onArchive(puzzle.id) }
                    .padding(6.dp),
            )
            Icon(
                Icons.Default.Shuffle,
                "Practice",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onPractice(puzzle.id, Difficulty.STANDARD) }
                    .padding(6.dp),
            )
        }
        Text(
            puzzle.tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { difficulty ->
                DifficultyPill(
                    label = difficulty.label,
                    done = (puzzle.id to difficulty) in doneToday,
                    accent = Color(puzzle.accent),
                    modifier = Modifier.weight(1f),
                    onClick = { onPlay(puzzle.id, difficulty) },
                )
            }
        }
    }
}

@Composable
private fun DifficultyPill(
    label: String,
    done: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (done) accent.copy(alpha = 0.22f) else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
            color = if (done) accent else scheme.onSurfaceVariant,
        )
    }
}
