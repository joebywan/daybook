package com.joebywan.daybook.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.platform.formatClock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * "This week" on the statistics screen, derived from the completion history; nothing is stored.
 *
 * Only dated completions count: a completion carries the day of the board it solved and no time of play, so practice and
 * level solves (`day == null`) cannot be placed in a week at all. An archive board counts on its own day, which is the day it
 * was played whenever the player plays the daily on the day.
 */
object ThisWeek {

    /** Weeks of solve times a trend looks back over, this week included. */
    const val WEEKS = 8

    /** Weeks with a solve a trend needs before it is drawn. */
    const val MIN_POINTS = 3

    /** One day of the strip. */
    data class Day(val day: LocalDate, val solved: Int)

    /** Today and the six days before it, oldest first, with the boards solved on each. */
    fun days(all: List<Completion>, today: LocalDate): List<Day> {
        val counts = all.mapNotNull { it.day }.groupingBy { it }.eachCount()
        return (6 downTo 0).map { back -> today.minus(DatePeriod(days = back)).let { Day(it, counts[it] ?: 0) } }
    }

    /** The line under the strip. Never a count of what is missing. */
    fun summary(days: List<Day>): String {
        val played = days.count { it.solved > 0 }
        val boards = days.sumOf { it.solved }
        if (played == 0) return "A fresh week: today's boards are ready."
        fun n(k: Int, one: String) = if (k == 1) "1 $one" else "$k ${one}s"
        return "${n(played, "day")} played, ${n(boards, "daily board")} solved."
    }

    /**
     * A tier's solve times over the last [WEEKS] weeks: the median of each week, oldest first, null for a week without one.
     * Week 0 is today and the six days before it, so a week never splits at a month or a year.
     */
    fun weeklyMedians(all: List<Completion>, puzzleId: String, difficulty: Difficulty, today: LocalDate): List<Int?> {
        val now = today.toEpochDays()
        val byWeek = all.filter { it.puzzleId == puzzleId && it.difficulty == difficulty && it.day != null }
            .groupBy { (now - it.day!!.toEpochDays()).let { back -> if (back < 0) -1 else (back / 7).toInt() } }
        return (WEEKS - 1 downTo 0).map { w -> byWeek[w]?.map { it.seconds }?.sorted()?.let { it[it.size / 2] } }
    }

    /** A puzzle's trend: its most solved tier in the window (the easier on a tie) and that tier's weekly medians. */
    data class Trend(val difficulty: Difficulty, val medians: List<Int?>) {
        /** The median of the latest week with a solve. */
        val recent: Int get() = medians.last { it != null }!!
    }

    /** Null until the tier has a solve in [MIN_POINTS] different weeks: two points are not a trend. */
    fun trend(all: List<Completion>, puzzleId: String, today: LocalDate): Trend? =
        Difficulty.entries
            .map { Trend(it, weeklyMedians(all, puzzleId, it, today)) }
            .maxByOrNull { t -> t.medians.count { it != null } } // First of the most, so the easier tier on a tie.
            ?.takeIf { t -> t.medians.count { it != null } >= MIN_POINTS }
}

/** The seven-day strip: a circle a day, filled and numbered on a day with a solve, today outlined. Static, so reduced motion has nothing to still. */
@Composable
internal fun WeekStrip(all: List<Completion>, today: LocalDate, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val days = ThisWeek.days(all, today)
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(14.dp)) {
        Text("This week", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "MTWTFSS"[d.day.dayOfWeek.ordinal].toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    var m = Modifier.size(34.dp).clip(CircleShape)
                    m = if (d.solved > 0) m.background(scheme.primary) else m.border(BorderStroke(1.5.dp, scheme.outlineVariant), CircleShape)
                    if (d.day == today) m = m.border(BorderStroke(2.dp, scheme.onSurface), CircleShape)
                    Box(m, contentAlignment = Alignment.Center) {
                        if (d.solved > 0) Text(d.solved.toString(), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(ThisWeek.summary(days), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

/** A puzzle's trend under its tiers: a line through the weekly medians, gaps bridged, and the recent median. Nothing judges it. */
@Composable
internal fun TrendLine(trend: ThisWeek.Trend, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    Spacer(Modifier.height(10.dp))
    // Two lines: half a phone's width has no room for the tier, the words and the time together.
    Text("Your recent median", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
    Row(Modifier.fillMaxWidth()) {
        Text(trend.difficulty.label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(formatClock(trend.recent), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
    }
    Spacer(Modifier.height(4.dp))
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val points = trend.medians.withIndex().filter { it.value != null }
        val lo = points.minOf { it.value!! }
        val hi = points.maxOf { it.value!! }
        val r = 2.5.dp.toPx()
        fun at(i: Int, v: Int) = Offset(
            r + (size.width - 2 * r) * i / (ThisWeek.WEEKS - 1),
            if (hi == lo) size.height / 2 else size.height - r - (size.height - 2 * r) * (v - lo) / (hi - lo),
        )
        val offsets = points.map { at(it.index, it.value!!) }
        offsets.zipWithNext { a, b -> drawLine(accent, a, b, 2.dp.toPx(), StrokeCap.Round) }
        offsets.forEach { drawCircle(accent, r, it) }
    }
}
