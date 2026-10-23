package com.joebywan.daybook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.platform.BackButton

/**
 * The home grid's order, rearranged by hand (docs/REWARDS.md, "Puzzle order"). Up and down buttons rather than a drag, so
 * every move is a plain button a screen reader can press. The first move turns "most played first" into the player's own
 * order, starting from what the grid showed; [onReset] goes back.
 */
@Composable
fun ArrangeScreen(
    puzzles: List<PuzzleType>,
    custom: Boolean,
    onOrder: (List<String>) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    fun move(from: Int, to: Int) {
        val ids = puzzles.map { it.id }.toMutableList()
        ids.add(to, ids.removeAt(from))
        onOrder(ids)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(
                "Arrange puzzles",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onBackground,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (custom) "Your own order. Puzzles added later join at the end."
                else "Most played first. Move a puzzle to keep your own order.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (custom) {
                Text(
                    "Reset to most played",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onReset)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // No item keys: keyed, the list follows a top row moved up out of view and jumps under the finger.
            itemsIndexed(puzzles) { i, puzzle ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surface)
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    puzzle.Preview(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        puzzle.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    MoveButton(Icons.Default.KeyboardArrowUp, "Move ${puzzle.displayName} up", i > 0) { move(i, i - 1) }
                    MoveButton(Icons.Default.KeyboardArrowDown, "Move ${puzzle.displayName} down", i < puzzles.lastIndex) {
                        move(i, i + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label) }
}
