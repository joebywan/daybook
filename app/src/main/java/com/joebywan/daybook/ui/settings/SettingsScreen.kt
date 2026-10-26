package com.joebywan.daybook.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.CardText
import com.joebywan.daybook.ui.TitledScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.platform.SOLVE_SOUND_NOTE
import com.joebywan.daybook.platform.VIBRATION_NOTE
import com.joebywan.daybook.platform.rememberRateLink
import com.joebywan.daybook.ui.home.SegmentedControl
import com.joebywan.daybook.ui.theme.Appearance

/**
 * The player's switches. Reached from the gear on Home; the system back button (or, on the web,
 * the arrow) is the way out, as on Stats. Each setting is a row in [LazyColumn], so the next one
 * is one more `item`.
 */
@Composable
fun SettingsScreen(
    showTimer: Boolean,
    onShowTimer: (Boolean) -> Unit,
    playSound: Boolean,
    onPlaySound: (Boolean) -> Unit,
    vibrate: Boolean,
    onVibrate: (Boolean) -> Unit,
    customOrder: Boolean,
    onResetOrder: () -> Unit,
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
    onBack: () -> Unit,
    /** Opens Training (`docs/TRAINING_SPEC.md`), the Sudoku pilot. */
    onTraining: () -> Unit = {},
) {
    val rateLink = rememberRateLink()

    TitledScreen("Settings", null, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingSwitch(
                    title = "Show the timer while playing",
                    detail = "A running clock above the board. Off or on, your time is counted, " +
                        "shown when you solve, and kept for your statistics.",
                    checked = showTimer,
                    onChecked = onShowTimer,
                )
            }
            item {
                SettingSwitch(
                    title = "Sound",
                    detail = "A short, soft chime when you solve a puzzle. $SOLVE_SOUND_NOTE",
                    checked = playSound,
                    onChecked = onPlaySound,
                )
            }
            item {
                SettingSwitch(
                    title = "Vibration",
                    detail = "A light tap when a move lands, a firmer one when a bump costs a life, and a short buzz " +
                        "when you solve. $VIBRATION_NOTE",
                    checked = vibrate,
                    onChecked = onVibrate,
                )
            }
            item { AppearanceChoice(appearance, onAppearance) }
            item { PuzzleOrderRow(customOrder, onResetOrder) }
            item { TrainingRow(onTraining) }
            item { RateRow(rateLink) }
            item { Credits() }
        }
    }
}

/** Light, dark or the device's, as the home grid's segmented rows are drawn. */
@Composable
private fun AppearanceChoice(appearance: Appearance, onAppearance: (Appearance) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column { CardText("Appearance", "Light or dark, or the same as your device.") }
        SegmentedControl(
            modifier = Modifier,
            options = Appearance.entries,
            selected = appearance,
            label = { it.label },
            tone = scheme.primary,
            onTone = scheme.onPrimary,
            verticalPadding = 7.dp,
            onSelect = onAppearance,
            // The card is already surface-coloured, so the track takes the page's colour to show.
            track = scheme.background,
        )
    }
}

/**
 * The word lists' credit, which SCOWL's licence asks to travel with them, and the typeface's. The full
 * notices are `docs/word-lists/LICENSE-SCOWL.txt` and `docs/fonts/LICENSE-FREDOKA-OFL.txt`; this is the
 * copyright line and the permission each rests on.
 */
@Composable
private fun Credits() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            .padding(14.dp),
    ) {
        CardText(
            "Word lists",
            "Lexicon's words come from SCOWL (Spell Checker Oriented Word Lists), Copyright 2000-2016 " +
                "by Kevin Atkinson, used under its permissive licence. The lists are provided \"as is\" " +
                "without warranty, and Kevin Atkinson makes no representations about their suitability " +
                "for any purpose.",
        )
        Spacer(Modifier.height(12.dp))
        CardText("Typeface", "Fredoka, Copyright 2016 The Fredoka Project Authors, used under the SIL Open Font License 1.1.")
    }
}

/** The home grid's order: says which it is and how to change it (on the grid itself), and resets a player's own. */
@Composable
private fun PuzzleOrderRow(customOrder: Boolean, onReset: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            CardText(
                "Puzzle order",
                if (customOrder) "Your own order. Puzzles added later join at the end."
                else "Most played first, over the last two months. Press and hold a puzzle on the home screen to move it.",
            )
        }
        if (customOrder) {
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
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            // The whole row toggles, and the switch inside is only its picture, so a screen
            // reader meets one control with a name rather than an unlabelled switch.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            CardText(title, detail)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Training's way in for the pilot: the whole row opens it. Its final place is decided after the pilot. */
@Composable
private fun TrainingRow(onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            .clickable(onClick = onOpen)
            .padding(14.dp),
    ) {
        CardText("Training", "Practise Sudoku's techniques one at a time, on real boards.")
    }
}
