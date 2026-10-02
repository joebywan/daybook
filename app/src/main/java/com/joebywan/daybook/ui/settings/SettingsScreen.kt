package com.joebywan.daybook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.platform.BackButton
import com.joebywan.daybook.platform.SOLVE_SOUND_NOTE

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
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onBackground,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingSwitch(
                    title = "Show the timer while playing",
                    detail = "Turn off to hide the running clock. Your time is still counted, " +
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
            item { WordListNotice() }
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
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            // The whole row toggles, and the switch inside is only its picture, so a screen
            // reader meets one control with a name rather than an unlabelled switch.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * SCOWL's licence asks that its copyright and permission notice travel with the word lists
 * (full text: `docs/word-lists/LICENSE-SCOWL.txt`). Lexicon's lists are built from it.
 */
@Composable
private fun WordListNotice() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .padding(14.dp),
    ) {
        Text("Word lists", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(
            "Lexicon's words come from SCOWL (Spell Checker Oriented Word Lists). " +
                "Copyright 2000-2016 by Kevin Atkinson. Permission to use, copy, modify, distribute " +
                "and sell these word lists, the associated scripts, the output created from the " +
                "scripts, and its documentation for any purpose is hereby granted without fee, " +
                "provided that the above copyright notice appears in all copies and that both that " +
                "copyright notice and this permission notice appear in supporting documentation. " +
                "Kevin Atkinson makes no representations about the suitability of this array for " +
                "any purpose. It is provided \"as is\" without express or implied warranty. " +
                "The full notice, with the other contributors' credits, is in the project's " +
                "docs/word-lists/LICENSE-SCOWL.txt.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}
