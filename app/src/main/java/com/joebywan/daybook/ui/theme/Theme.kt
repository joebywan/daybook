package com.joebywan.daybook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.joebywan.daybook.platform.SystemBarsAppearance

@Composable
fun DaybookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    // Only the status-bar icon tint on Android; see the platform seam.
    SystemBarsAppearance(darkTheme)
    MaterialTheme(colorScheme = scheme, typography = DaybookTypography, content = content)
}
