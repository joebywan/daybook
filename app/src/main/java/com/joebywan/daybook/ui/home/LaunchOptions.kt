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
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.ui.CappedFontScale
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

    /** The puzzles with a Training course (`docs/TRAINING_SPEC.md`); a tap opens the course. No tier. */
    TRAINING("Training");

    /** Whether the tier row applies: Daily and Random only. */
    val tiered: Boolean get() = this == DAILY || this == RANDOM
}

/**
 * The mode row's three segments. Daily and Random share the first, the one in use drawn large over the other, so the
 * row has room for Training without a fourth, narrower segment.
 */
internal enum class ModeSegment { DAY, LEVELS, TRAINING }

internal val LaunchMode.segment: ModeSegment
    get() = when (this) {
        LaunchMode.DAILY, LaunchMode.RANDOM -> ModeSegment.DAY
        LaunchMode.LEVELS -> ModeSegment.LEVELS
        LaunchMode.TRAINING -> ModeSegment.TRAINING
    }

/**
 * The mode a tap on [segment] picks while in [mode]. The first segment swaps Daily and Random when it is already the one
 * selected; from another mode it returns to [lastDay], whichever of the two was last in use (Daily on launch).
 */
internal fun tappedMode(segment: ModeSegment, mode: LaunchMode, lastDay: LaunchMode): LaunchMode = when (segment) {
    ModeSegment.LEVELS -> LaunchMode.LEVELS
    ModeSegment.TRAINING -> LaunchMode.TRAINING
    ModeSegment.DAY -> when (mode) {
        LaunchMode.DAILY -> LaunchMode.RANDOM
        LaunchMode.RANDOM -> LaunchMode.DAILY
        else -> lastDay
    }
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
 * [LaunchMode] deliberately is not stored: a player who ends a session in Levels, Training or
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
 * whether the tap opens today's puzzle, a freshly seeded random board, a level's board or a Training course.
 *
 * The mode row comes first and the tier row beneath it. Every segment has a small second line, so the three share one
 * height: under Daily/Random the other of the two (a tap on the selected segment swaps them), [levelLine] under Levels,
 * [trainingLine] under Training. [dayMode] is whichever of Daily and Random the first segment shows large.
 *
 * In Levels and Training the tier does not apply, so the tier row is hidden and [levelStrip] or [trainingStrip] is drawn
 * in its place. The row is still laid out underneath (invisible, and it takes no taps), so the strip has exactly its
 * height and nothing below it moves when the mode changes.
 */
@Composable
fun LaunchOptions(
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    mode: LaunchMode,
    dayMode: LaunchMode,
    onMode: (LaunchMode) -> Unit,
    levelLine: String,
    trainingLine: String,
    levelStrip: @Composable () -> Unit = {},
    trainingStrip: @Composable () -> Unit = {},
    sideBySide: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val tiered = mode.tiered
    val still = LocalReducedMotion.current
    // 0 with Daily large, 1 with Random large: the two lines trade size and place.
    val swap by animateFloatAsState(
        if (dayMode == LaunchMode.RANDOM) 1f else 0f, if (still) snap() else tween(SWAP_MS), label = "day-swap",
    )
    val other = if (dayMode == LaunchMode.RANDOM) LaunchMode.DAILY else LaunchMode.RANDOM
    // Side by side on a short wide window (a phone on its side), where a second row costs the grid most of a row of tiles.
    val controls = @Composable { half: Modifier ->
        // A second tone rather than a second green row: two identical-looking controls stacked
        // read as one four-choice thing, and these are different questions.
        SegmentedControl(
            modifier = half,
            options = ModeSegment.entries,
            selected = mode.segment,
            label = { "" },
            tone = scheme.secondary,
            onTone = scheme.onSecondary,
            verticalPadding = 4.dp,
            onSelect = { onMode(tappedMode(it, mode, dayMode)) },
            describe = { segment, selected ->
                when (segment) {
                    ModeSegment.DAY -> {
                        contentDescription = dayMode.label
                        stateDescription = if (selected) "Selected, double-tap for ${other.label}" else "Not selected"
                    }
                    ModeSegment.LEVELS -> contentDescription = "Levels, $levelLine"
                    ModeSegment.TRAINING -> contentDescription = "Training, $trainingLine courses graduated"
                }
            },
            content = { segment, ink, selected ->
                val weight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                when (segment) {
                    ModeSegment.DAY -> DayLabel(swap, ink, weight)
                    ModeSegment.LEVELS -> TwoLines(LaunchMode.LEVELS.label, levelLine, ink, weight)
                    ModeSegment.TRAINING -> TwoLines(LaunchMode.TRAINING.label, trainingLine, ink, weight)
                }
            },
        )
        Box(half) {
            SegmentedControl(
                modifier = if (tiered) Modifier else Modifier.alpha(0f).clearAndSetSemantics { },
                options = Difficulty.entries,
                selected = difficulty,
                label = { it.label },
                tone = scheme.primary,
                onTone = scheme.onPrimary,
                verticalPadding = 9.dp,
                onSelect = { if (tiered) onDifficulty(it) },
            )
            when (mode) {
                LaunchMode.LEVELS -> Box(Modifier.matchParentSize()) { levelStrip() }
                LaunchMode.TRAINING -> Box(Modifier.matchParentSize()) { trainingStrip() }
                else -> Unit
            }
        }
    }
    if (sideBySide) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            controls(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { controls(Modifier) }
    }
}

/** A segment's name over its small second line. */
@Composable
private fun TwoLines(name: String, line: String, ink: Color, weight: FontWeight) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = weight, color = ink, maxLines = 1)
        Text(line, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.8f), maxLines = 1)
    }
}

/**
 * "Daily" over "Random", or the other way up: the one in use large, the other small beneath. [swap] runs 0 (Daily large)
 * to 1 (Random large); the two trade place and size by scaling, never by measuring again, so the box keeps one height
 * through the swap and nothing under the row moves. That height is [TwoLines]': a large line and a small one.
 */
@Composable
private fun DayLabel(swap: Float, ink: Color, weight: FontWeight) {
    val large = MaterialTheme.typography.labelLarge
    val small = MaterialTheme.typography.labelSmall
    val k = small.fontSize.value / large.fontSize.value
    fun Modifier.shrunk(t: Float) = graphicsLayer {
        val s = 1f - (1f - k) * t
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
    Layout(
        content = {
            Text(LaunchMode.DAILY.label, Modifier.shrunk(swap), style = large, fontWeight = weight, color = ink.copy(alpha = 1f - 0.2f * swap), maxLines = 1)
            Text(LaunchMode.RANDOM.label, Modifier.shrunk(1f - swap), style = large, fontWeight = weight, color = ink.copy(alpha = 0.8f + 0.2f * swap), maxLines = 1)
            // Unseen: the small line's height, for the box.
            Text(" ", style = small, maxLines = 1)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (daily, random, probe) = measurables.map { it.measure(loose) }
        val line = daily.height
        val width = maxOf(daily.width, random.width)
        layout(width, line + probe.height) {
            daily.place((width - daily.width) / 2, (swap * line).roundToInt())
            random.place((width - random.width) / 2, ((1f - swap) * line).roundToInt())
        }
    }
}

private const val SWAP_MS = 150

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
    /** What a screen reader hears for an option instead of its drawn text; null reads the text. */
    describe: (SemanticsPropertyReceiver.(option: T, selected: Boolean) -> Unit)? = null,
    /** An option's face, drawn in `ink`; one line of [label] unless given. */
    content: @Composable (option: T, ink: Color, selected: Boolean) -> Unit = { option, ink, selected ->
        Text(
            label(option),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = ink,
            maxLines = 1,
        )
    },
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
                    .then(if (describe == null) Modifier else Modifier.semantics { describe(option, isSelected) })
                    .padding(vertical = verticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                // Capped: at 2x the pill broke "Standar/d".
                CappedFontScale {
                    Box(if (describe == null) Modifier else Modifier.clearAndSetSemantics { }) {
                        // The ink follows the pill across, so a label is never light on the bare track.
                        content(option, lerp(scheme.onSurfaceVariant, onTone, (1f - abs(at - i)).coerceIn(0f, 1f)), isSelected)
                    }
                }
            }
        }
    }
}

private const val SLIDE_MS = 220
