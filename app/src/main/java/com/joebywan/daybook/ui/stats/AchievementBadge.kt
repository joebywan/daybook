package com.joebywan.daybook.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip

/** PLACEHOLDER: the real badge art is being finished elsewhere; keep this signature, theirs wins on merge. The caller sizes it. */
@Composable
fun AchievementBadge(id: String, earned: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).alpha(if (earned) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Text(id.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary)
    }
}
