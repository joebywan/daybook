package com.joebywan.daybook.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.ACHIEVEMENTS
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.platform.BackButton
import kotlinx.datetime.LocalDate

/** Every achievement: earned ones as colour badges, the rest as silhouettes with their description so there is something to aim for. */
@Composable
fun AchievementsScreen(today: LocalDate, completions: List<Completion>, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val earned = earnedAchievements(completions, today).map { it.id }.toSet()
    Column(Modifier.fillMaxSize().background(scheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Column(Modifier.weight(1f).padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp)) {
                Text("Achievements", style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
                Text(
                    "${earned.size} of ${ACHIEVEMENTS.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        // Definition order, so a badge keeps its place as more are earned.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(ACHIEVEMENTS, key = { it.id }) { a ->
                val got = a.id in earned
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AchievementBadge(a.id, got, Modifier.fillMaxWidth())
                    Text(
                        a.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (got) scheme.primary else scheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        a.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
