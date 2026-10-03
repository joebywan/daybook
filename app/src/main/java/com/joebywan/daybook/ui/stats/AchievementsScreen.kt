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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.ACHIEVEMENTS
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.platform.BackButton
import kotlinx.datetime.LocalDate

/** Every achievement: earned ones in full, the rest dimmed with their description so there is something to aim for. */
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
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ACHIEVEMENTS, key = { it.id }) { a ->
                val got = a.id in earned
                Column(
                    Modifier.fillMaxWidth().alpha(if (got) 1f else 0.5f)
                        .clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(14.dp),
                ) {
                    Text(
                        a.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (got) scheme.primary else scheme.onSurface,
                    )
                    Text(a.description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
