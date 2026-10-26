package com.joebywan.daybook.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.joebywan.daybook.ui.TitledScreen
import kotlinx.datetime.LocalDate

/** Every achievement: earned ones as colour badges, the rest as silhouettes with their description so there is something to aim for. */
@Composable
fun AchievementsScreen(today: LocalDate, completions: List<Completion>, onBack: () -> Unit, training: Set<String> = emptySet()) {
    val scheme = MaterialTheme.colorScheme
    val earned = earnedAchievements(completions, today, training = training).map { it.id }.toSet()
    TitledScreen("Achievements", "${earned.size} of ${ACHIEVEMENTS.size}", onBack) {
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
