package com.joebywan.daybook.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.joebywan.daybook.core.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * How a tap on a puzzle tile is meant to start a game.
 *
 * This used to be two separate controls on every card — a row of difficulty pills plus a shuffle
 * icon — which is what made eleven cards too tall to see at once. It is one question asked once,
 * above the grid, because the answer is nearly always the same for a whole session.
 */
enum class LaunchMode(val label: String) {
    DAILY("Daily"),
    PRACTICE("Practice"),
}

/**
 * The difficulty the grid is set to, remembered between launches.
 *
 * Separate from `ProgressStore` and from its DataStore file on purpose: this is a view setting,
 * not progress. Losing it costs one tap, so it must never be able to interfere with the file that
 * holds solved days and half-finished boards.
 *
 * Only difficulty is stored. [LaunchMode] deliberately is not: a player who ends a session in
 * Practice would otherwise open the app the next morning to a grid that quietly hides the day's
 * puzzles, and the daily is the whole point of the app.
 */
class LaunchPreferences(private val context: Context) {

    val difficulty: Flow<Difficulty> =
        context.launchStore.data.map { prefs -> Difficulty.fromKey(prefs[KEY_DIFFICULTY].orEmpty()) }

    suspend fun setDifficulty(difficulty: Difficulty) {
        context.launchStore.edit { prefs -> prefs[KEY_DIFFICULTY] = difficulty.name }
    }

    private companion object {
        val KEY_DIFFICULTY = stringPreferencesKey("difficulty")
    }
}

private val Context.launchStore: DataStore<Preferences> by preferencesDataStore(name = "daybook_launch")

/**
 * The two choices that used to live on every card, hoisted above the grid: what difficulty, and
 * whether the tap opens today's puzzle or a fresh practice board.
 */
@Composable
fun LaunchOptions(
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    mode: LaunchMode,
    onMode: (LaunchMode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedControl(
            options = Difficulty.entries,
            selected = difficulty,
            label = { it.label },
            tone = scheme.primary,
            onTone = scheme.onPrimary,
            verticalPadding = 9.dp,
            onSelect = onDifficulty,
        )
        // A second tone rather than a second green row: two identical-looking controls stacked
        // read as one four-choice thing, and these are different questions.
        SegmentedControl(
            options = LaunchMode.entries,
            selected = mode,
            label = { it.label },
            tone = scheme.secondary,
            onTone = scheme.onSecondary,
            verticalPadding = 7.dp,
            onSelect = onMode,
        )
    }
}

@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    tone: Color,
    onTone: Color,
    verticalPadding: Dp,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) tone else Color.Transparent)
                    // selectable rather than clickable so a screen reader announces the row as a
                    // set of choices with one taken, not three unrelated buttons.
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = verticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) onTone else scheme.onSurfaceVariant,
                )
            }
        }
    }
}
