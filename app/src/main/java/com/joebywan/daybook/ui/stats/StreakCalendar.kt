package com.joebywan.daybook.ui.stats

import com.joebywan.daybook.ui.card
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.forgivenDays
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * A month of days played, so the "5 of the last 7" rule can be seen working (`docs/REWARDS.md`). Filled
 * is a day played; a ring is a missed day the streak forgave; today has an outline; days to come are dim.
 * Everything is derived from [played] (the daily solve days) and nothing is stored. Weeks start on Monday.
 * Browsing stops at the first month with a play and at the current one. A day with a daily ([DailySeed.EPOCH] to
 * today) is a button: [onDay] opens it in the archive.
 */
@Composable
fun StreakCalendar(played: Set<LocalDate>, today: LocalDate, modifier: Modifier, onDay: (LocalDate) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val forgiven = forgivenDays(played, today)
    val thisMonth = today.firstOfMonth()
    val firstMonth = (played.minOrNull() ?: today).firstOfMonth()
    // Epoch days go through the saver; a LocalDate does not.
    var shown by rememberSaveable { mutableStateOf(thisMonth.toEpochDays()) }
    val month = LocalDate.fromEpochDays(shown).firstOfMonth()

    Column(
        modifier.card(scheme.surface).padding(14.dp),
    ) {
        MonthBar(
            month,
            back = month > firstMonth,
            forward = month < thisMonth,
            onMonth = { shown = it.toEpochDays() },
        )
        Spacer(Modifier.height(6.dp))
        MonthGrid(month) { day, m ->
            DayCell(day, played, forgiven, today, m, onClick = { onDay(day) }.takeIf { day >= DailySeed.EPOCH && day <= today })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary))
            Text(" played    ", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Box(Modifier.size(10.dp).border(BorderStroke(1.5.dp, scheme.primary.copy(alpha = 0.6f)), CircleShape))
            Text(" missed, streak kept", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Play on 5 of any 7 days to keep your streak going.",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    played: Set<LocalDate>,
    forgiven: Set<LocalDate>,
    today: LocalDate,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val on = day in played
    Box(
        modifier.aspectRatio(1f).padding(3.dp).alpha(if (day > today) 0.35f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        var m = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
        if (onClick != null) m = m.clickable(onClick = onClick)
        if (on) m = m.background(scheme.primary)
        if (day in forgiven) m = m.border(BorderStroke(1.5.dp, scheme.primary.copy(alpha = 0.6f)), CircleShape)
        if (day == today) m = m.border(BorderStroke(2.dp, scheme.onSurface), CircleShape)
        Box(m, contentAlignment = Alignment.Center) {
            Text(
                day.day.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (on) scheme.onPrimary else scheme.onSurface,
            )
        }
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        glyph,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.25f)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(top = 4.dp),
    )
}

/** The month's name and year between a back and a forward arrow; an arrow past the bounds is dim and does nothing. */
@Composable
internal fun MonthBar(month: LocalDate, back: Boolean, forward: Boolean, onMonth: (LocalDate) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow("‹", back) { onMonth(month.minus(DatePeriod(months = 1))) }
        Text(
            month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + month.year,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Arrow("›", forward) { onMonth(month.plus(DatePeriod(months = 1))) }
    }
}

/**
 * A Monday-first month: the weekday letters, then a row per week with [day] drawn for each date of [month],
 * handed the modifier that sizes its square. Squares share the width when [cell] is null (the stats calendar);
 * a caller sizing from both axes passes [cell]. [weeks] reserves at least that many rows, so what sits under
 * the grid does not move from month to month.
 */
@Composable
internal fun MonthGrid(
    month: LocalDate,
    cell: Dp? = null,
    weeks: Int = 0,
    day: @Composable (LocalDate, Modifier) -> Unit,
) {
    val lead = month.dayOfWeek.ordinal // Monday = 0
    val length = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    Column(if (cell == null) Modifier.fillMaxWidth() else Modifier) {
        for (w in -1 until maxOf((lead + length + 6) / 7, weeks)) {
            Row {
                for (c in 0 until 7) {
                    val square = if (cell == null) Modifier.weight(1f) else Modifier.width(cell)
                    val n = w * 7 + c - lead + 1
                    when {
                        w < 0 -> Text(
                            "MTWTFSS"[c].toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = square,
                        )
                        n < 1 || n > length -> Spacer(square.aspectRatio(1f))
                        else -> day(LocalDate(month.year, month.month, n), square)
                    }
                }
            }
        }
    }
}

internal fun LocalDate.firstOfMonth() = LocalDate(year, month, 1)
