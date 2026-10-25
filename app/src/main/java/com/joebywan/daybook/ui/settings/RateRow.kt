package com.joebywan.daybook.ui.settings

import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.CardText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Where a player who likes Daybook can say so: the store it came from, or GitHub when that store takes no
 * ratings (F-Droid, a downloaded APK, the web). The platform picks it ([com.joebywan.daybook.platform.rememberRateLink]);
 * it is only ever a link the player taps, never a prompt, and opening it needs no network permission.
 */
class RateLink(val title: String, val detail: String, val open: () -> Unit)

const val GITHUB_URL = "https://github.com/joebywan/daybook"

/** The same rounded row as a setting, but the whole row is one button that opens [link]. */
@Composable
fun RateRow(link: RateLink) {
    Column(
        Modifier
            .fillMaxWidth()
            .card(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = link.open)
            .padding(14.dp),
    ) {
        CardText(link.title, link.detail)
    }
}
