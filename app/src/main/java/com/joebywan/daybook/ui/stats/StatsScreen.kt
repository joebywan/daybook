package com.joebywan.daybook.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.ACHIEVEMENTS
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.Stats
import com.joebywan.daybook.platform.BackButton
import com.joebywan.daybook.platform.BackupControls
import com.joebywan.daybook.platform.OFFERS_BACKUP
import com.joebywan.daybook.platform.formatClock
import kotlinx.datetime.LocalDate

/**
 * Lifetime numbers. There is no back control on the screen: the system back button is the way out,
 * which is why the title sits on the same margin as the cards below it rather than behind an arrow.
 */
@Composable
fun StatsScreen(
    today: LocalDate,
    completions: List<Completion>,
    onAchievements: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Nothing on Android, where the system back button is the way out.
            BackButton(onBack)
            Column(
                Modifier.weight(1f).padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp)
            ) {
                Text(
                    "Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onBackground,
                )
                Text(
                    "All time, on this device.",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val streak = Stats.streak(completions, today)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Solved", Stats.totalSolved(completions).toString(), Modifier.weight(1f))
                    Tile(
                        if (streak.lapsed) "Welcome back" else "Streak",
                        if (streak.lapsed) "-" else streak.current.toString(),
                        Modifier.weight(1f),
                    )
                    Tile("Best", streak.best.toString(), Modifier.weight(1f))
                }
            }

            item {
                val got = earnedAchievements(completions, today).size
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface)
                        .clickable(onClick = onAchievements).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Achievements", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                    Text("$got of ${ACHIEVEMENTS.size}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }

            item {
                StreakCalendar(completions.mapNotNullTo(HashSet()) { it.day }, today, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            items(PuzzleRegistry.all, key = { it.id }) { puzzle ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surface)
                        .padding(14.dp),
                ) {
                    Text(
                        puzzle.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(puzzle.accent),
                    )
                    Spacer(Modifier.height(8.dp))
                    Difficulty.entries.forEach { difficulty ->
                        val best = Stats.bestTime(completions, puzzle.id, difficulty)
                        val count = completions.count {
                            it.puzzleId == puzzle.id && it.difficulty == difficulty
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                difficulty.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (best == null) "—" else formatClock(best),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.size(14.dp))
                            Text(
                                "$count solved",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // A web page has no system backup to fall back on; Android does, so this is web-only.
            if (OFFERS_BACKUP) {
                item { BackupControls() }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = scheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}
