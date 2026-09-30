package com.joebywan.daybook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.joebywan.daybook.platform.SystemBarsAppearance
import com.joebywan.daybook.platform.platformTypography

@Composable
fun DaybookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    // Only the status-bar icon tint on Android; see the platform seam.
    SystemBarsAppearance(darkTheme)
    // The same styles everywhere; the web swaps in bundled fonts, having no system ones.
    MaterialTheme(colorScheme = scheme, typography = platformTypography(DaybookTypography), content = content)
}
