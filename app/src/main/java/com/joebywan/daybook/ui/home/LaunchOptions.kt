package com.joebywan.daybook.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.MotionChoice
import com.joebywan.daybook.ui.theme.Appearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * How a tap on a puzzle tile is meant to start a game.
 *
 * This used to be two separate controls on every card — a row of difficulty pills plus a shuffle
 * icon — which is what made eleven cards too tall to see at once. It is one question asked once,
 * above the grid, because the answer is nearly always the same for a whole session.
 */
enum class LaunchMode(val label: String) {
    DAILY("Daily"),

    /** Numbered levels of five boards (`docs/LEVELS_SPEC.md`); the level sets each board's tier. */
    LEVELS("Levels"),
    RANDOM("Random"),
}

/**
 * The grid's tier, the settings and the grid's order, remembered between launches.
 *
 * [store] is the [KeyValueStore.LAUNCH] file.
 *
 * Separate from `ProgressStore` and from its DataStore file on purpose: this is a view setting,
 * not progress. Losing it costs one tap, so it must never be able to interfere with the file that
 * holds solved days and half-finished boards.
 *
 * [LaunchMode] deliberately is not stored: a player who ends a session in
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

    suspend fun setShowTimer(show: Boolean) = setFlag(KEY_SHOW_TIMER, show)

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
    val playSound: Flow<Boolean> = flag(KEY_SOUND)

    suspend fun setPlaySound(play: Boolean) = setFlag(KEY_SOUND, play)

    /** Whether a move, a bump and a solve buzz. On unless the player turned it off. */
    val vibrate: Flow<Boolean> = flag(KEY_VIBRATE)

    suspend fun setVibrate(on: Boolean) = setFlag(KEY_VIBRATE, on)

    /** Reduced motion: [MotionChoice.DEVICE] until the player picks, so nothing changes for anyone who doesn't. */
    val reducedMotion: Flow<MotionChoice> = store.string(KEY_REDUCED_MOTION).map { stored ->
        MotionChoice.entries.firstOrNull { it.name == stored } ?: MotionChoice.DEVICE
    }

    suspend fun setReducedMotion(choice: MotionChoice) = store.putString(KEY_REDUCED_MOTION, choice.name)

    /** An on/off switch, stored as "on" or "off"; anything but "off" (nothing stored included) is on. */
    private fun flag(key: String): Flow<Boolean> = store.string(key).map { it != "off" }

    private suspend fun setFlag(key: String, on: Boolean) = store.putString(key, if (on) "on" else "off")

    /**
     * The player's own order of the home grid, as puzzle ids; empty (the default) means most played first. Read through
     * `PuzzleOrder.arranged`, so an id from a removed puzzle drops out and a new puzzle joins at the end.
     */
    val puzzleOrder: Flow<List<String>> =
        store.string(KEY_ORDER).map { it.orEmpty().split(',').filter(String::isNotEmpty) }

    suspend fun setPuzzleOrder(ids: List<String>) {
        store.putString(KEY_ORDER, ids.joinToString(","))
    }

    /**
     * Whether to show the welcome (`ui/home/Welcome.kt`): only to a player who has not seen it and has not [hasPlayed].
     * A player who has is marked as welcomed on the spot, so the answer cannot change for them later.
     */
    suspend fun needsWelcome(hasPlayed: suspend () -> Boolean): Boolean {
        if (store.string(KEY_WELCOMED).first() != null) return false
        if (!hasPlayed()) return true
        setWelcomed()
        return false
    }

    suspend fun setWelcomed() {
        store.putString(KEY_WELCOMED, "yes")
    }

    /** The daily reminder (`Reminder.kt`, Android only): off unless the player turned it on. */
    val reminder: Flow<Boolean> = store.string(KEY_REMINDER).map { it == "on" }

    /** Turning it either way also answers Home's one offer, so a player who chose in Settings is not asked. */
    suspend fun setReminder(on: Boolean) {
        setFlag(KEY_REMINDER, on)
        setReminderOffered()
    }

    /** The hour the reminder goes off, local time; [DEFAULT_REMINDER_HOUR] until the player picks. */
    val reminderHour: Flow<Int> =
        store.string(KEY_REMINDER_HOUR).map { it?.toIntOrNull()?.takeIf { h -> h in 0..23 } ?: DEFAULT_REMINDER_HOUR }

    suspend fun setReminderHour(hour: Int) = store.putString(KEY_REMINDER_HOUR, hour.toString())

    /** Whether Home's one offer of the reminder was answered; it is never shown again after. */
    val reminderOffered: Flow<Boolean> = store.string(KEY_REMINDER_OFFERED).map { it != null }

    suspend fun setReminderOffered() = store.putString(KEY_REMINDER_OFFERED, "yes")

    private companion object {
        const val KEY_REMINDER = "reminder"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_OFFERED = "reminder_offered"
        const val KEY_WELCOMED = "welcomed"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_REDUCED_MOTION = "reduced_motion"
        const val KEY_ORDER = "puzzle_order"
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
 * whether the tap opens today's puzzle, a level's board or a freshly seeded random board.
 *
 * The mode row comes first and the tier row beneath it. In Levels mode the level sets the tier, so
 * the tier row is hidden and [levelStrip] is drawn in its place. The row is still laid out underneath
 * (invisible, and it takes no taps), so the strip has exactly its height and nothing below it moves
 * when the mode changes.
 */
@Composable
fun LaunchOptions(
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    mode: LaunchMode,
    onMode: (LaunchMode) -> Unit,
    levelStrip: @Composable () -> Unit = {},
    sideBySide: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val levels = mode == LaunchMode.LEVELS
    // Side by side on a short wide window (a phone on its side), where a second row costs the grid most of a row of tiles.
    val controls = @Composable { half: Modifier ->
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
        Box(half) {
            SegmentedControl(
                modifier = if (levels) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier,
                options = Difficulty.entries,
                selected = difficulty,
                label = { it.label },
                tone = scheme.primary,
                onTone = scheme.onPrimary,
                verticalPadding = 9.dp,
                onSelect = { if (!levels) onDifficulty(it) },
            )
            if (levels) Box(Modifier.matchParentSize()) { levelStrip() }
        }
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
    // The selection is one pill that slides to the option taken (or swiped to on Home), the same way for both.
    val still = LocalReducedMotion.current
    val at by animateFloatAsState(
        options.indexOf(selected).toFloat(), if (still) snap() else tween(SLIDE_MS), label = "segment",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(track)
            .padding(4.dp)
            .drawBehind {
                val gap = 4.dp.toPx()
                val w = (size.width - gap * (options.size - 1)) / options.size
                drawRoundRect(tone, Offset(at * (w + gap), 0f), Size(w, size.height), CornerRadius(10.dp.toPx()))
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
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
                    // The ink follows the pill across, so a label is never light on the bare track.
                    color = lerp(scheme.onSurfaceVariant, onTone, (1f - abs(at - i)).coerceIn(0f, 1f)),
                )
            }
        }
    }
}

private const val SLIDE_MS = 220
