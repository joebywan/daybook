package com.joebywan.daybook.ui.stats

import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.TitledScreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joebywan.daybook.platform.formatDate
import com.joebywan.daybook.ui.play.SharePreview
import com.joebywan.daybook.ui.play.dayShareCard
import com.joebywan.daybook.ui.play.sharePreviewOpen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.ACHIEVEMENTS
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.KeptCard
import com.joebywan.daybook.data.PuzzleTechniques
import com.joebywan.daybook.data.Stats
import com.joebywan.daybook.platform.BackupControls
import com.joebywan.daybook.platform.OFFERS_BACKUP
import com.joebywan.daybook.platform.formatClock
import kotlinx.datetime.LocalDate

/** Lifetime numbers, with a share of today's dailies once one is solved. */
@Composable
fun StatsScreen(
    today: LocalDate,
    completions: List<Completion>,
    /** Share cards kept after their finish frames ([KeptCard]): a tier's best, and its last random board. */
    keptCards: List<KeptCard>,
    /** The home grid's order, so a puzzle is found where the player keeps it. */
    puzzles: List<PuzzleType>,
    onAchievements: () -> Unit,
    /** Per puzzle with a teacher, what its hints have explained (`TechniquesMet`). */
    techniques: List<PuzzleTechniques>,
    onTechniques: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Which kept card is open, as "puzzleId|tier|best", so a rotation keeps it open.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    fun keyOf(c: KeptCard) = "${c.puzzleId}|${c.difficulty.name}|${c.best}"
    keptCards.firstOrNull { keyOf(it) == open }?.let { SharePreview(it.card) { open = null } }
    val streak = Stats.streak(completions, today)

    TitledScreen("Statistics", "All time, on this device.", onBack, trailing = {
        // Only once a daily is solved today. The title's two lines are taller than the button, so it moves nothing.
        val card = remember(completions, today) {
            dayShareCard(PuzzleRegistry.all.map { it.id }, completions, today, formatDate(today, "d MMM"), streak.current)
        }
        // Saveable, so turning the device keeps the preview open.
        var sharing by rememberSaveable { mutableStateOf(sharePreviewOpen) }
        if (card != null) {
            TextButton(onClick = { sharing = true }, Modifier.padding(end = 8.dp)) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share today")
            }
            if (sharing) SharePreview(card) { sharing = false }
        }
    }) {
        // A phone's width at most: the calendar's square days would otherwise run a tablet's width and off its foot.
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { WeekStrip(completions, today, Modifier.fillMaxWidth()) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Solved", completions.size.toString(), Modifier.weight(1f))
                    Tile("Streak", if (streak.alive) streak.current.toString() else DASH, Modifier.weight(1f))
                    Tile("Best streak", streak.best.toString(), Modifier.weight(1f))
                }
            }

            item {
                val got = earnedAchievements(completions, today).size
                Row(
                    Modifier.fillMaxWidth().card(scheme.surface).clickable(onClick = onAchievements).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Achievements", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                    Text("$got of ${ACHIEVEMENTS.size}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }

            item {
                // A count of what was met, never "of" a total: the page is a record of hints, not a list to finish.
                val met = techniques.sumOf { p -> p.techniques.count { it.met } }
                Row(
                    Modifier.fillMaxWidth().card(scheme.surface)
                        .clickable(onClick = onTechniques).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Techniques", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                    if (met > 0) Text("$met shown", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }

            item {
                StreakCalendar(completions.mapNotNullTo(HashSet()) { it.streakDay }, today, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            // Two a row, in the home grid's order (the player's own, or most played first).
            items(puzzles.chunked(2), key = { it.first().id }) { pair ->
                // Equal heights, so a trend under one card leaves its neighbour matching rather than short.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { PuzzleStats(it, completions, keptCards, today, Modifier.weight(1f).fillMaxHeight()) { c -> open = keyOf(c) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // A web page has no system backup to fall back on; Android does, so this is web-only.
            if (OFFERS_BACKUP) {
                item { BackupControls() }
            }
        }
    }
}

/** The one dash the screen uses for "nothing yet". */
private const val DASH = "–"

/** One puzzle's card: per tier, its best time over its solve count and share links; one line when never played. */
@Composable
private fun PuzzleStats(
    puzzle: PuzzleType,
    completions: List<Completion>,
    keptCards: List<KeptCard>,
    today: LocalDate,
    modifier: Modifier,
    onOpen: (KeptCard) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = Color(puzzle.accent)
    Column(modifier.card(scheme.surface).padding(14.dp)) {
        Text(puzzle.displayName, style = MaterialTheme.typography.titleMedium, color = BoardHues.textOn(accent, scheme.surface), maxLines = 1)
        // Never played: one line, not three tiers of dashes and zeroes.
        if (completions.none { it.puzzleId == puzzle.id }) {
            Spacer(Modifier.height(8.dp))
            Text("Not played yet", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        } else Difficulty.entries.forEach { difficulty ->
            val best = Stats.bestTime(completions, puzzle.id, difficulty)
            val count = completions.count { it.puzzleId == puzzle.id && it.difficulty == difficulty }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(difficulty.label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(if (best == null) DASH else formatClock(best), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$count solved", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                // Always laid out, greyed when there is nothing to share, so a card arriving moves nothing.
                fun kept(best: Boolean) = keptCards.firstOrNull { it.puzzleId == puzzle.id && it.difficulty == difficulty && it.best == best }
                ShareLink("Best", kept(true), accent, onOpen)
                ShareLink("Last", kept(false), accent, onOpen)
            }
        }
        remember(completions, today, puzzle.id) { ThisWeek.trend(completions, puzzle.id, today) }?.let { TrendLine(it, accent) }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.card(scheme.surface).padding(vertical = 18.dp),
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
        color = if (kept != null) BoardHues.textOn(accent, scheme.surface) else scheme.outlineVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = kept != null) { kept?.let(onOpen) }
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    )
}
