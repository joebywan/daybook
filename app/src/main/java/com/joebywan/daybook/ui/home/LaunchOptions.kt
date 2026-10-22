package com.joebywan.daybook.ui.home

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
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.ui.theme.Appearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    RANDOM("Random"),
}

/**
 * The difficulty the grid is set to, remembered between launches.
 *
 * [store] is the [KeyValueStore.LAUNCH] file.
 *
 * Separate from `ProgressStore` and from its DataStore file on purpose: this is a view setting,
 * not progress. Losing it costs one tap, so it must never be able to interfere with the file that
 * holds solved days and half-finished boards.
 *
 * Difficulty is the only grid selection stored (the timer switch is a setting, not a selection). [LaunchMode] deliberately is not: a player who ends a session in
 * Random would otherwise open the app the next morning to a grid that quietly hides the day's
 * puzzles, and the daily is the whole point of the app.
 */
class LaunchPreferences(private val store: KeyValueStore) {

    val difficulty: Flow<Difficulty> =
        store.string(KEY_DIFFICULTY).map { Difficulty.fromKey(it.orEmpty()) }

    suspend fun setDifficulty(difficulty: Difficulty) {
        store.putString(KEY_DIFFICULTY, difficulty.name)
    }

    /**
     * Whether the running clock is drawn on the play screen. Only the clock: the time is still
     * counted, shown on the solved card and kept for Stats. Off for a new player, since a running
     * clock is pressure the app does not mean to add; see [timerShown] and [settleShowTimer].
     */
    val showTimer: Flow<Boolean> = store.string(KEY_SHOW_TIMER).map { timerShown(it, hasPlayed = false) }

    suspend fun setShowTimer(show: Boolean) {
        store.putString(KEY_SHOW_TIMER, if (show) "on" else "off")
    }

    /**
     * Writes the switch down the first time a build with the off default runs: on for a player who
     * already has progress (earlier builds showed everyone the clock, and it must not vanish on an
     * update), off for a new one. A stored value is the player's own and is never touched.
     */
    suspend fun settleShowTimer(hasPlayed: suspend () -> Boolean) {
        if (store.string(KEY_SHOW_TIMER).first() == null) setShowTimer(timerShown(null, hasPlayed()))
    }

    /** Light, dark or the device's; [Appearance.SYSTEM] until the player picks. */
    val appearance: Flow<Appearance> = store.string(KEY_APPEARANCE).map { stored ->
        Appearance.entries.firstOrNull { it.name == stored } ?: Appearance.SYSTEM
    }

    suspend fun setAppearance(appearance: Appearance) {
        store.putString(KEY_APPEARANCE, appearance.name)
    }

    /**
     * Whether a solved puzzle plays its short chime. On unless the player turned it off. The device
     * being on silent is a separate, platform check; this is the player's own switch.
     */
    val playSound: Flow<Boolean> = store.string(KEY_SOUND).map { it != "off" }

    suspend fun setPlaySound(play: Boolean) {
        store.putString(KEY_SOUND, if (play) "on" else "off")
    }

    /** Whether a move, a bump and a solve buzz. On unless the player turned it off. */
    val vibrate: Flow<Boolean> = store.string(KEY_VIBRATE).map { it != "off" }

    suspend fun setVibrate(on: Boolean) {
        store.putString(KEY_VIBRATE, if (on) "on" else "off")
    }

    private companion object {
        const val KEY_VIBRATE = "vibrate"
        const val KEY_SOUND = "play_sound"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_SHOW_TIMER = "show_timer"
        // The web page reads it before the app loads, as `daybook_launch.appearance` (index.html).
        const val KEY_APPEARANCE = "appearance"
    }
}

/**
 * The timer switch from what is [stored]: "off" hides the clock and any other value shows it. Nothing
 * stored means a player who never touched the switch, and they see the clock only if they [hasPlayed].
 */
internal fun timerShown(stored: String?, hasPlayed: Boolean): Boolean = stored?.let { it != "off" } ?: hasPlayed

/**
 * The two choices that used to live on every card, hoisted above the grid: what difficulty, and
 * whether the tap opens today's puzzle or a freshly seeded random board.
 */
@Composable
fun LaunchOptions(
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    mode: LaunchMode,
    onMode: (LaunchMode) -> Unit,
    sideBySide: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // Side by side on a short wide window (a phone on its side), where a second row costs the grid most of a row of tiles.
    val controls = @Composable { half: Modifier ->
        SegmentedControl(
            modifier = half,
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
            modifier = half,
            options = LaunchMode.entries,
            selected = mode,
            label = { it.label },
            tone = scheme.secondary,
            onTone = scheme.onSecondary,
            verticalPadding = 7.dp,
            onSelect = onMode,
        )
    }
    if (sideBySide) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            controls(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { controls(Modifier) }
    }
}

@Composable
internal fun <T> SegmentedControl(
    modifier: Modifier,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    tone: Color,
    onTone: Color,
    verticalPadding: Dp,
    onSelect: (T) -> Unit,
    track: Color = MaterialTheme.colorScheme.surface,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(track)
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
