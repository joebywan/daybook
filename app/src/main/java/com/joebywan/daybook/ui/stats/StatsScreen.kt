package com.joebywan.daybook.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.Stats
import java.time.LocalDate

@Composable
fun StatsScreen(
    today: LocalDate,
    completions: List<Completion>,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = scheme.onSurfaceVariant)
            }
            Text(
                "Statistics",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onBackground,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Solved", Stats.totalSolved(completions).toString(), Modifier.weight(1f))
                    Tile(
                        "Streak",
                        Stats.currentStreak(completions, today).toString(),
                        Modifier.weight(1f),
                    )
                    Tile("Best", Stats.bestStreak(completions).toString(), Modifier.weight(1f))
                }
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
                                if (best == null) "—" else "%d:%02d".format(best / 60, best % 60),
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
