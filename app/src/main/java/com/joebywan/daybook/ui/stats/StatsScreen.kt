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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.joebywan.daybook.data.KeptCard
import com.joebywan.daybook.data.Stats
import com.joebywan.daybook.platform.BackButton
import com.joebywan.daybook.platform.BackupControls
import com.joebywan.daybook.platform.OFFERS_BACKUP
import com.joebywan.daybook.platform.formatClock
import com.joebywan.daybook.ui.play.SharePreview
import kotlinx.datetime.LocalDate

/**
 * Lifetime numbers. There is no back control on the screen: the system back button is the way out,
 * which is why the title sits on the same margin as the cards below it rather than behind an arrow.
 */
@Composable
fun StatsScreen(
    today: LocalDate,
    completions: List<Completion>,
    /** Share cards kept after their finish frames ([KeptCard]): a tier's best, and its last random board. */
    keptCards: List<KeptCard>,
    onAchievements: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Which kept card is open, as "puzzleId|tier|best", so a rotation keeps it open.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    fun keyOf(c: KeptCard) = "${c.puzzleId}|${c.difficulty.name}|${c.best}"
    keptCards.firstOrNull { keyOf(it) == open }?.let { SharePreview(it.card) { open = null } }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
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

        // A phone's width at most: the calendar's square days would otherwise run a tablet's width and off its foot.
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val streak = Stats.streak(completions, today)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Solved", Stats.totalSolved(completions).toString(), Modifier.weight(1f))
                    Tile(
                        if (streak.lapsed) "Welcome back" else "Streak",
                        if (streak.alive) streak.current.toString() else "-",
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
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            // Always laid out, greyed when there is nothing to share, so a card arriving moves nothing.
                            fun kept(best: Boolean) = keptCards.firstOrNull { it.puzzleId == puzzle.id && it.difficulty == difficulty && it.best == best }
                            ShareLink("Best", kept(true), Color(puzzle.accent)) { open = keyOf(it) }
                            ShareLink("Last", kept(false), Color(puzzle.accent)) { open = keyOf(it) }
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

/** A share link for one kept card: [label] in [accent], or greyed and inert when there is no [kept] card. */
@Composable
private fun ShareLink(label: String, kept: KeptCard?, accent: Color, onOpen: (KeptCard) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (kept != null) accent else scheme.outlineVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = kept != null) { kept?.let(onOpen) }
            .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    )
}
