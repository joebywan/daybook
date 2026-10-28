package com.joebywan.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.platform.BackButton

/**
 * A screen reached from Home: the page's background inside the safe area, a header of [title] over [subtitle]
 * beside the back control (which draws nothing on Android, where the system back button is the way out), an
 * optional [trailing] action, then [content], centred. [beside] sits after the subtitle on its line (a practice's run),
 * so it takes no width from the title.
 */
@Composable
fun TitledScreen(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    beside: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.background).windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Column(Modifier.weight(1f).padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
                if (subtitle != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(subtitle, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    if (beside != null) {
                        Spacer(Modifier.width(10.dp))
                        beside()
                    }
                }
            }
            trailing()
        }
        content()
    }
}

private val CardShape = RoundedCornerShape(16.dp)

/** The rounded card every row of the secondary screens is drawn on. */
fun Modifier.card(color: Color): Modifier = clip(CardShape).background(color)

/** A card's heading over its explanation. */
@Composable
fun CardText(title: String, detail: String, titleColor: Color = MaterialTheme.colorScheme.onSurface) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
