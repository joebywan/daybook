package com.joebywan.daybook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.joebywan.daybook.platform.SystemBarsAppearance
import com.joebywan.daybook.platform.platformTypography

/** The player's light or dark choice in Settings; [SYSTEM] follows the device. */
enum class Appearance(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

@Composable
fun DaybookTheme(
    appearance: Appearance = Appearance.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    val scheme = if (darkTheme) DarkScheme else LightScheme
    // The system-bar icon tint on Android, the page behind the canvas on the web; see the platform seam.
    SystemBarsAppearance(darkTheme)
    // The same styles everywhere; the web swaps in bundled fonts, having no system ones.
    MaterialTheme(colorScheme = scheme, typography = platformTypography(DaybookTypography), content = content)
}
