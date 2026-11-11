package com.joebywan.daybook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Colours and type; [DaybookTheme] picks the scheme by look and light or dark ([lookScheme]), and both builds compile this file as it stands.

// Arcade: indigo page, one vivid accent per scheme. Each value measured in ArcadeReportTest.
internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF120D33),
    secondary = Color(0xFFFFB300),
    onSecondary = Color(0xFF120D33),
    error = Color(0xFFFF8A9E), // FF5C7A measured 3.53 on the Sudoku box tints
    onError = Color(0xFF120D33),
    background = Color(0xFF120D33),
    onBackground = Color(0xFFF4EFFF),
    surface = Color(0xFF211A5C),
    onSurface = Color(0xFFF4EFFF),
    surfaceVariant = Color(0xFF33287F),
    onSurfaceVariant = Color(0xFFD9D4FF),
    outline = Color(0xFF5F52B8),
    // Material's defaults for these are off-palette; every AlertDialog uses surfaceContainerHigh.
    surfaceContainerLowest = Color(0xFF120D33),
    surfaceContainerLow = Color(0xFF211A5C),
    surfaceContainer = Color(0xFF211A5C),
    surfaceContainerHigh = Color(0xFF211A5C),
    surfaceContainerHighest = Color(0xFF33287F),
)

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3DF5),
    onPrimary = Color.White,
    secondary = Color(0xFFE05A00),
    onSecondary = Color.Black,
    error = Color(0xFFC4122F),
    onError = Color.White,
    background = Color(0xFFF4EFFF),
    onBackground = Color(0xFF21145F),
    surface = Color.White,
    onSurface = Color(0xFF21145F),
    surfaceVariant = Color(0xFFE7DFFF),
    onSurfaceVariant = Color(0xFF4A3F86),
    outline = Color(0xFFB3A6E6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE7DFFF),
)

// Classic (issue #418): the schemes from before Arcade (`3369cc46^`), parchment and ink with moss and amber, exactly as they
// shipped. [lookScheme] picks between the two pairs.
private val Ink = Color(0xFF11201C)
private val InkSoft = Color(0xFF1B2F29)
private val Parchment = Color(0xFFF6F1E7)
private val ParchmentDim = Color(0xFFE6DECD)
private val Moss = Color(0xFF3F9E7F)
private val Amber = Color(0xFFD08A33)
// Clay (`C0563F`) stepped in lightness so error text reads at 4.5:1 on the surface and the background:
// Clay itself was 3.13:1 on the dark surface and 4.02:1 on the light background (ContrastTest).
private val ClayLight = Color(0xFFAD4D39)
private val ClayDark = Color(0xFFD08271)

internal val ClassicDarkScheme = darkColorScheme(
    primary = Moss,
    onPrimary = Ink,
    secondary = Amber,
    onSecondary = Ink,
    error = ClayDark,
    onError = Ink,
    background = Ink,
    onBackground = Parchment,
    surface = InkSoft,
    onSurface = Parchment,
    surfaceVariant = Color(0xFF24403A),
    onSurfaceVariant = ParchmentDim,
    outline = Color(0xFF3C5B53),
    // Material's defaults for these are lavender; every AlertDialog (rules, restart, share) uses
    // surfaceContainerHigh, so unset they put a purple box on a parchment app.
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkSoft,
    surfaceContainer = InkSoft,
    surfaceContainerHigh = InkSoft,
    surfaceContainerHighest = Color(0xFF24403A),
)

internal val ClassicLightScheme = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color.White,
    secondary = Color(0xFFB06C1E),
    onSecondary = Color.White,
    error = ClayLight,
    onError = Color.White,
    background = Parchment,
    onBackground = Ink,
    surface = Color(0xFFFFFBF2),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E0D0),
    onSurfaceVariant = Color(0xFF41504B),
    outline = Color(0xFFB9AE99),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF2),
    surfaceContainer = Color(0xFFFFFBF2),
    surfaceContainerHigh = Color(0xFFFFFBF2),
    surfaceContainerHighest = Color(0xFFE8E0D0),
)

internal val DaybookTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.6.sp,
    ),
)

/**
 * [this] with every style set in [family]. Fredoka comes from each build's seam (a resource on
 * Android, a bundled file on the web), so the styles above name no family of their own.
 */
internal fun Typography.withFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
