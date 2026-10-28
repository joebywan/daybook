package com.joebywan.daybook.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import com.joebywan.daybook.ui.CappedFontScale
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.TrainingTally

/** What sits in the tier row's place in Training mode: one line of progress across every course. */
@Composable
internal fun TrainingStrip(tally: TrainingTally) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Capped, and shrinking to fit rather than cut: at 2x it ended "0 tech...".
        CappedFontScale {
            val style = MaterialTheme.typography.labelLarge
            Text(
                "${tally.graduated} of ${tally.courses} graduated  ·  ${tally.passed} techniques passed",
                style = style, color = scheme.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
            )
        }
    }
}

/**
 * Training mode's grid: the puzzles that have a course, in the home grid's order ([puzzles]), each saying how many of its
 * techniques are passed and ticked once graduated (`Course.graduated`). A tap opens the course ([onOpen]).
 */
@Composable
internal fun TrainingGrid(puzzles: List<PuzzleType>, record: Set<String>, height: Dp, onOpen: (String) -> Unit) {
    val courses = puzzles.mapNotNull { Training.of(it.id) }
    TileGrid(courses.size, height) { i, previewSize, modifier ->
        val c = courses[i]
        PuzzleTile(c.puzzle, c.graduated(record), { emptySet() }, previewSize, { onOpen(c.id) }, modifier) { accent ->
            TileFooter("${c.passedCount(record)} of ${c.techniques.size}", accent)
        }
    }
}
