package com.joebywan.daybook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Colours and type, kept apart from [DaybookTheme] because that reaches for the Android window
// and this does not: the web build compiles this file as it stands and wraps it in its own theme.

private val Ink = Color(0xFF11201C)
private val InkSoft = Color(0xFF1B2F29)
private val Parchment = Color(0xFFF6F1E7)
private val ParchmentDim = Color(0xFFE6DECD)
private val Moss = Color(0xFF3F9E7F)
private val Amber = Color(0xFFD08A33)
private val Clay = Color(0xFFC0563F)

internal val DarkScheme = darkColorScheme(
    primary = Moss,
    onPrimary = Ink,
    secondary = Amber,
    onSecondary = Ink,
    error = Clay,
    background = Ink,
    onBackground = Parchment,
    surface = InkSoft,
    onSurface = Parchment,
    surfaceVariant = Color(0xFF24403A),
    onSurfaceVariant = ParchmentDim,
    outline = Color(0xFF3C5B53),
)

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color.White,
    secondary = Color(0xFFB06C1E),
    onSecondary = Color.White,
    error = Clay,
    background = Parchment,
    onBackground = Ink,
    surface = Color(0xFFFFFBF2),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E0D0),
    onSurfaceVariant = Color(0xFF41504B),
    outline = Color(0xFFB9AE99),
)

internal val DaybookTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.6.sp,
    ),
)
