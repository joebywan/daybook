package com.joebywan.daybook.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.ui.stats.MedalIcon

/**
 * Shown once, in place of the home grid, to a new player (`LaunchPreferences.needsWelcome`): one line on what Daybook is
 * and a few pointers to what the home screen holds, then Start. No question, no tour of pages, nothing over the grid.
 */
@Composable
internal fun Welcome(onStart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val icon = Modifier.size(22.dp)
    val small = Modifier.size(18.dp)
    Box(
        Modifier.fillMaxSize().background(scheme.background).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            // Centred together on a tall screen; on a short one only the words scroll, so Start is never hidden.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DaybookMark(Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Welcome to Daybook", style = MaterialTheme.typography.headlineSmall, color = scheme.onBackground)
                }
                Text(
                    "New boards every day from ${PuzzleRegistry.all.size} kinds of logic puzzle. Free, with no ads and " +
                        "no account, and it all works offline.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onBackground,
                )
                Pointer(
                    { Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFE8A317), modifier = icon) },
                    "Today's three, over the grid, are a quick one, a longer one and a word or number one: gentle ones " +
                        "while you find your feet, then the same three as everyone else. Tap the ? on any puzzle for its " +
                        "rules and a one-minute walkthrough.",
                )
                Pointer(
                    { Icon(Icons.Default.CalendarToday, null, tint = scheme.secondary, modifier = icon) },
                    "At the top: Daily for today's board (tap it again for Random, a fresh one any time), Levels for " +
                        "sets of five, and Training to practise each puzzle's techniques. Daily and Random come in " +
                        "Standard, Hard and Expert.",
                )
                Pointer(
                    {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.History, null, tint = scheme.onSurfaceVariant, modifier = small)
                            MedalIcon(small, scheme.onSurfaceVariant, "")
                            Icon(Icons.Default.InsertChart, null, tint = scheme.onSurfaceVariant, modifier = small)
                        }
                    },
                    "The buttons by the title open the archive of every past day and your achievements. The chart badge " +
                        "with your streak opens your stats.",
                )
                Pointer(
                    { Icon(Icons.Default.OpenWith, null, tint = scheme.secondary, modifier = icon) },
                    "Press and hold a puzzle to move it. Until you do, the ones you play most come first, and the easiest " +
                        "to pick up lead among the rest.",
                )
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(52.dp)) {
                Text("Start", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Pointer(icon: @Composable () -> Unit, text: String) {
    Row {
        Box(Modifier.width(36.dp).padding(top = 1.dp), contentAlignment = Alignment.TopStart) { icon() }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
