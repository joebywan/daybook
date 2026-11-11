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

/** Daybook's source on GitHub. */
const val GITHUB_URL = "https://github.com/joebywan/daybook"

/** The issues board, where a player reports a problem or suggests something (`.github/ISSUE_TEMPLATE/`). */
const val ISSUES_URL = "$GITHUB_URL/issues"

/** Where no store takes ratings (F-Droid, a downloaded APK, the web): a star on GitHub, opened through [openUri]. */
fun githubRateLink(openUri: (String) -> Unit) = RateLink(
    title = "Star Daybook on GitHub",
    detail = "Daybook is open source. If it has earned it, a star on GitHub helps other puzzlers find it.",
    open = { openUri(GITHUB_URL) },
)

/** The same rounded row as a setting, but the whole row is one button that opens [link]. */
@Composable
fun RateRow(link: RateLink) = LinkRow(link.title, link.detail, link.open)

/** A setting-shaped row that is one button: the guide, the issues board, a rating. */
@Composable
fun LinkRow(title: String, detail: String, open: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .card(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = open)
            .padding(14.dp),
    ) {
        CardText(title, detail)
    }
}
