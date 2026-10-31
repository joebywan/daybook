package com.joebywan.daybook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Colours and type; [DaybookTheme] picks the scheme, and both builds compile this file as it stands.

// Arcade trial (docs/arcade-trial/): indigo page, one vivid accent per scheme. Each value measured in ArcadeReportTest.
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
