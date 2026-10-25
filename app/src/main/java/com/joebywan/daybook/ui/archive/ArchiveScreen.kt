package com.joebywan.daybook.ui.archive

import com.joebywan.daybook.ui.TitledScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.platform.formatDate
import com.joebywan.daybook.ui.home.TierDots
import com.joebywan.daybook.ui.stats.MonthBar
import com.joebywan.daybook.ui.stats.MonthGrid
import com.joebywan.daybook.ui.stats.firstOfMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/** A `DateTimeFormatter` pattern; see `formatDate` in the platform seam. */
private const val DAY_DATE = "EEE d MMM yyyy"

/** The tier buttons' row: a label and a reserved second line ("in progress"), so picking a day never moves anything. */
private val TIER_ROW = 60.dp
/** The selected day's date line above the tier buttons. */
private val DATE_LINE = 28.dp
/** The month bar (its arrows are 48 dp touch targets) and the weekday letters. */
private val MONTH_BAR = 48.dp
private val WEEKDAYS = 20.dp
private val GAP = 12.dp

/** Where the calendar can go and where it opens. Pure, so the bounds are testable on their own. */
internal object ArchiveCalendar {
    /** Every day from [DailySeed.EPOCH] to [today] has a daily; nothing before the first day, nothing still to come. */
    fun playable(day: LocalDate, today: LocalDate): Boolean = day >= DailySeed.EPOCH && day <= today

    fun firstMonth(): LocalDate = DailySeed.EPOCH.firstOfMonth()

    /** The month of [openAt] (the day just played from here), else of the last day played, else this month. */
    fun opening(openAt: LocalDate?, played: Collection<LocalDate>, today: LocalDate): LocalDate {
        val day = openAt ?: played.filter { playable(it, today) }.maxOrNull() ?: today
        return day.firstOfMonth().coerceIn(firstMonth(), today.firstOfMonth())
    }
}

/**
 * The whole back catalogue, free, a month at a time.
 *
 * Past days are not stored or downloaded — each one is regenerated from its date, so this can run back
 * to [DailySeed.EPOCH] at no cost. Each day shows the home tiles' three tier dots, filled for a tier
 * solved; tapping one shows its three tiers under the grid (beside it on a wide screen), so a play is
 * two taps. The selected day and the month shown are `rememberSaveable`, and coming back from a board
 * opens at that board's day ([openAt], from the route).
 */
@Composable
fun ArchiveScreen(
    puzzleId: String,
    today: LocalDate,
    completions: List<Completion>,
    onPlay: (LocalDate, Difficulty) -> Unit,
    onBack: () -> Unit,
    /** The tiers of a day's daily with a game left part-way. */
    inProgress: (LocalDate) -> Flow<Set<Difficulty>>,
    /** The day to select: the one just played, so coming back keeps the player's place. */
    openAt: LocalDate? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val puzzle = PuzzleRegistry.byId(puzzleId) ?: return
    val accent = Color(puzzle.accent)
    val done = completions.filter { it.puzzleId == puzzleId }
        .map { it.day to it.difficulty }
        .toSet()
    val lastMonth = today.firstOfMonth()
    // Epoch days go through the saver; a LocalDate does not.
    var shown by rememberSaveable {
        mutableStateOf(ArchiveCalendar.opening(openAt, done.mapNotNull { it.first }, today).toEpochDays())
    }
    var picked by rememberSaveable { mutableStateOf(openAt?.takeIf { ArchiveCalendar.playable(it, today) }?.toEpochDays()) }
    val month = LocalDate.fromEpochDays(shown)
    val selected = picked?.let(LocalDate::fromEpochDays)
    val started by remember(selected) { selected?.let(inProgress) ?: flowOf(emptySet()) }.collectAsState(emptySet())

    TitledScreen(
        "${puzzle.displayName} archive",
        "${today.toEpochDays() - DailySeed.EPOCH.toEpochDays() + 1} days, all unlocked",
        onBack,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Sized from both axes: six weeks of squares and the tier panel have to fit the box either way.
            // Beside the grid whenever the box is wider than tall (a phone on its side, a tablet across).
            val wide = maxWidth > maxHeight
            val panelW = if (wide) minOf(360.dp, maxWidth * 0.45f) else 0.dp
            val panelH = if (wide) 0.dp else DATE_LINE + TIER_ROW + GAP
            val cell = minOf(
                (maxWidth - panelW - (if (wide) GAP else 0.dp)) / 7,
                (maxHeight - MONTH_BAR - WEEKDAYS - panelH) / 6,
                64.dp,
            )
            val grid = @Composable {
                Column(Modifier.width(cell * 7)) {
                    MonthBar(
                        month,
                        back = month > ArchiveCalendar.firstMonth(),
                        forward = month < lastMonth,
                        onMonth = { shown = it.toEpochDays() },
                    )
                    MonthGrid(month, cell, weeks = 6) { day, m ->
                        if (day < DailySeed.EPOCH) {
                            Spacer(m.aspectRatio(1f))
                        } else {
                            DayCell(
                                day, today, accent, day == selected,
                                cleared = { (day to it) in done },
                                onClick = { picked = day.toEpochDays() },
                                modifier = m,
                            )
                        }
                    }
                }
            }
            val panel = @Composable { width: Dp ->
                TierPanel(
                    selected, today, accent,
                    solved = { (selected to it) in done },
                    started = started,
                    onPlay = { d -> selected?.let { onPlay(it, d) } },
                    modifier = Modifier.width(width),
                )
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    grid()
                    Column {
                        Spacer(Modifier.height(MONTH_BAR + WEEKDAYS))
                        panel(panelW)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    grid()
                    panel(cell * 7)
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    today: LocalDate,
    accent: Color,
    isSelected: Boolean,
    cleared: (Difficulty) -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val open = ArchiveCalendar.playable(day, today)
    val all = Difficulty.entries.all(cleared)
    val shape = RoundedCornerShape(10.dp)
    var m = modifier.aspectRatio(1f).padding(2.dp).clip(shape)
    if (open) m = m.background(if (all) accent.copy(alpha = 0.22f) else scheme.surface).clickable(onClick = onClick)
    when {
        isSelected -> m = m.border(BorderStroke(2.5.dp, accent), shape)
        day == today -> m = m.border(BorderStroke(1.5.dp, scheme.onSurface), shape)
    }
    Column(
        m.alpha(if (open) 1f else 0.35f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.day.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (all) accent else scheme.onSurface,
        )
        if (open) {
            Spacer(Modifier.height(2.dp))
            TierDots(accent, selected = null, cleared = cleared)
        }
    }
}

/** The selected day and its three tiers, each 60 dp tall; before a day is picked, a prompt in the same room. */
@Composable
private fun TierPanel(
    day: LocalDate?,
    today: LocalDate,
    accent: Color,
    solved: (Difficulty) -> Boolean,
    started: Set<Difficulty>,
    onPlay: (Difficulty) -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            if (day == null) "Pick a day to play it." else formatDate(day, DAY_DATE) + if (day == today) "  ·  today" else "",
            style = MaterialTheme.typography.titleMedium,
            color = if (day == null) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
            modifier = Modifier.height(DATE_LINE),
        )
        Row(Modifier.fillMaxWidth().height(TIER_ROW), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (day != null) Difficulty.entries.forEach { difficulty ->
                val isSolved = solved(difficulty)
                val ink = if (isSolved) accent else scheme.onSurfaceVariant
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSolved) accent.copy(alpha = 0.22f) else scheme.surfaceVariant)
                        .clickable { onPlay(difficulty) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSolved) {
                            Icon(Icons.Default.Check, "Solved", tint = accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(difficulty.label, style = MaterialTheme.typography.labelLarge, color = ink)
                    }
                    // Under the centred label, in room kept for it, so a save appearing never moves the label.
                    if (!isSolved && difficulty in started) {
                        Text(
                            "in progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = ink,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
