package com.joebywan.daybook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joebywan.daybook.platform.SystemBarsAppearance
import com.joebywan.daybook.platform.platformTypography

/**
 * The player's look in Settings (issue #418): [CLASSIC] is what the app drew before the Arcade colours (parchment and moss,
 * quiet boards, flat home tiles), [ARCADE] the vivid indigo look. Colour only: no save, seed or board depends on it.
 * Read anywhere through [classicLook], which [DaybookTheme] sets, as it sets [useVividScheme]'s dark flag.
 */
enum class Look(val label: String) {
    CLASSIC("Classic"),
    ARCADE("Arcade"),
}

// A global, as the vivid flag is: `vivid()`, `BoardHues` and the draw lambdas that call them are not composables.
// Snapshot state, so a composition or a draw that read it runs again when the player changes look.
private var lookNow by mutableStateOf(Look.ARCADE)

/** Whether the look showing is [Look.CLASSIC]. */
fun classicLook(): Boolean = lookNow == Look.CLASSIC

/** Sets the look showing; [DaybookTheme] calls it, and so do tests that measure both looks. */
fun useLook(look: Look) { if (lookNow != look) lookNow = look }

/** The Material scheme of the look showing (or of [classic]'s), light or [dark]. */
fun lookScheme(dark: Boolean, classic: Boolean = classicLook()): ColorScheme = when {
    classic -> if (dark) ClassicDarkScheme else ClassicLightScheme
    else -> if (dark) DarkScheme else LightScheme
}

/** The player's light or dark choice in Settings; [SYSTEM] follows the device. */
enum class Appearance(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

@Composable
fun DaybookTheme(
    appearance: Appearance = Appearance.SYSTEM,
    look: Look = Look.ARCADE,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    useLook(look)
    val scheme = lookScheme(darkTheme)
    useVividScheme(darkTheme)
    // The system-bar icon tint on Android, the page behind the canvas on the web; see the platform seam.
    SystemBarsAppearance(darkTheme, look == Look.CLASSIC)
    // The same styles everywhere; the web swaps in bundled fonts, having no system ones.
    MaterialTheme(colorScheme = scheme, typography = platformTypography(DaybookTypography), content = content)
}
