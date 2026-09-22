package com.joebywan.daybook.ui.archive

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.Completion
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val RowDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

/**
 * The whole back catalogue, free.
 *
 * Past days are not stored or downloaded — each one is regenerated from its date, so this list can
 * run back to [DailySeed.EPOCH] at no cost.
 *
 * There is no back control on the screen; the system back button is the way out. With the arrow
 * gone the heading starts at the same margin as the day rows below it, so the screen reads as one
 * column rather than as a title indented behind something that is no longer there.
 */
@Composable
fun ArchiveScreen(
    puzzleId: String,
    today: LocalDate,
    completions: List<Completion>,
    onPlay: (LocalDate, Difficulty) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val puzzle = PuzzleRegistry.byId(puzzleId) ?: return
    val days = remember(today) {
        generateSequence(today) { it.minusDays(1) }
            .takeWhile { !it.isBefore(DailySeed.EPOCH) }
            .toList()
    }
    val done = completions.filter { it.puzzleId == puzzleId }
        .map { it.day to it.difficulty }
        .toSet()

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp)
        ) {
            Text(
                "${puzzle.displayName} archive",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onBackground,
            )
            Text(
                "${days.size} days, all unlocked",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(days, key = { it.toEpochDay() }) { day ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surface)
                        .padding(14.dp),
                ) {
                    Text(
                        day.format(RowDate) + if (day == today) "  ·  today" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Difficulty.entries.forEach { difficulty ->
                            val solved = (day to difficulty) in done
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (solved) Color(puzzle.accent).copy(alpha = 0.22f)
                                        else scheme.surfaceVariant
                                    )
                                    .clickable { onPlay(day, difficulty) }
                                    .padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (solved) {
                                    Icon(
                                        Icons.Default.Check, null,
                                        tint = Color(puzzle.accent),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    difficulty.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (solved) Color(puzzle.accent) else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

