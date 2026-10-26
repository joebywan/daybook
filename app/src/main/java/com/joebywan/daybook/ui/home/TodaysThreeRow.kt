package com.joebywan.daybook.ui.home

import com.joebywan.daybook.ui.theme.BoardHues
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.ui.play.ShareCard
import com.joebywan.daybook.ui.play.SharePreview

/**
 * Today's three (`core/TodaysThree.kt`) over the grid: a caption with a Share button once one is solved ([card]), then a chip
 * per puzzle at the grid's [tier], tinted and ticked when [done] has it today; the first not done wears the gold sparkles
 * that were today's pick's. A tap opens that daily ([onPlay]). Always the same height whatever is done, and laid out unseen
 * when not [shown], so the grid below never moves. [compact] (a phone on its side) puts the caption beside the chips and
 * each name beside its motif, one line in all, since height is what that window lacks.
 */
@Composable
internal fun TodaysThreeRow(
    three: List<PuzzleType>,
    tier: Difficulty,
    done: Set<Pair<String, Difficulty>>,
    card: ShareCard?,
    shown: Boolean,
    compact: Boolean,
    onPlay: (String) -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val next = three.firstOrNull { (it.id to tier) !in done }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val chips: @Composable (Modifier) -> Unit = { m ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(TileGap), verticalAlignment = Alignment.CenterVertically) {
            three.forEach { p ->
                ThreeChip(p, (p.id to tier) in done, p == next, shown, compact, { onPlay(p.id) }, Modifier.weight(1f))
            }
        }
    }
    Column(
        modifier.fillMaxWidth().padding(bottom = TileGap)
            .then(if (shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics { }),
    ) {
        // The Share button's room is kept while there is nothing to share, so it appears without moving anything.
        Row(
            Modifier.heightIn(compact).widthIn(max = if (compact) 900.dp else 480.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Today's three", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, maxLines = 1,
                modifier = if (compact) Modifier else Modifier.weight(1f),
            )
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(enabled = shown && card != null) { sharing = true },
                contentAlignment = Alignment.Center,
            ) {
                if (card != null) Icon(Icons.Default.Share, "Share today's three", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            if (compact) chips(Modifier.padding(start = 8.dp).weight(1f))
        }
        if (!compact) chips(Modifier.widthIn(max = 480.dp).fillMaxWidth())
    }
    if (sharing && card != null) SharePreview(card) { sharing = false }
}

/** The caption line is 32dp; compact, it also holds the chips, which set its height. */
private fun Modifier.heightIn(compact: Boolean) = if (compact) this else height(32.dp)

@Composable
private fun ThreeChip(
    puzzle: PuzzleType, solved: Boolean, next: Boolean, enabled: Boolean, compact: Boolean, onClick: () -> Unit, modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = Color(puzzle.accent)
    val chip = modifier
        .clip(RoundedCornerShape(14.dp))
        .background(if (solved) accent.copy(alpha = 0.2f).compositeOver(scheme.surface) else scheme.surface)
        .then(if (next) Modifier.sparkleFrame(14.dp) else Modifier)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(6.dp)
    val motif = @Composable {
        Box(contentAlignment = Alignment.TopEnd) {
            puzzle.Preview(Modifier.size(if (compact) 24.dp else 30.dp).clip(RoundedCornerShape(8.dp)))
            if (solved) Box(Modifier.size(14.dp).clip(CircleShape).background(scheme.surface), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "Solved", tint = BoardHues.textOn(accent, scheme.surface, 3f), modifier = Modifier.size(10.dp))
            }
        }
    }
    val name = @Composable {
        Text(
            puzzle.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = if (solved) BoardHues.onTint(accent, scheme.surface, alpha = 0.2f) else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (compact) Row(chip, verticalAlignment = Alignment.CenterVertically) { motif(); Spacer(Modifier.width(8.dp)); name() }
    else Column(chip, horizontalAlignment = Alignment.CenterHorizontally) { motif(); Spacer(Modifier.height(3.dp)); name() }
}
