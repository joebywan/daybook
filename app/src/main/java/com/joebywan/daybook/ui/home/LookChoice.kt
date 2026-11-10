package com.joebywan.daybook.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.CardText
import com.joebywan.daybook.ui.style
import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.Look
import com.joebywan.daybook.ui.theme.lookScheme

// The Look choice (issue #418): Classic, the colours from before Arcade, or Arcade. Asked once, in the welcome for a new
// player and in a card on Home for one who played before it existed; Settings holds it afterwards. Each choice is shown
// as a small swatch drawn in that look's own colours (a corner of a Sudoku grid and a home tile), whichever look is
// showing, so a player compares the two side by side rather than by switching.

/** The two swatches side by side; [chosen] is outlined, null outlines neither. A tap picks one at once. */
@Composable
internal fun LookPicker(chosen: Look?, onPick: (Look) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (look in Look.entries) {
            LookSwatch(look, chosen == look, { onPick(look) }, Modifier.weight(1f))
        }
    }
}

/**
 * [look] drawn small in its own colours, in the light or dark now showing: a 3x3 corner of a Sudoku grid (a given, an
 * entered digit, the selected square and the next box's shade) beside a home tile, the look's name under them.
 */
@Composable
internal fun LookSwatch(look: Look, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = MaterialTheme.colorScheme
    val dark = BoardHues.isDark(now)
    val classic = look == Look.CLASSIC
    val s = lookScheme(dark, classic)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .semantics { selected = chosen }
            .clip(shape)
            .background(s.background)
            .border(if (chosen) 3.dp else 1.dp, if (chosen) now.primary else now.outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SwatchGrid(classic, dark, 17.dp)
            SwatchTile(classic, dark, Modifier.width(46.dp).height(51.dp))
        }
        Text(
            look.label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold),
            color = s.onBackground,
        )
    }
}

/** The grid's corner: what each look's Sudoku board draws for the same squares. */
@Composable
private fun SwatchGrid(classic: Boolean, dark: Boolean, cell: Dp) {
    val s = lookScheme(dark, classic)
    val (hueA, hueB) = BoardHues.HUE_PAIRS[0]
    val a = if (dark) ArcadeBoard.Dark else ArcadeBoard.Light
    // Reading order: a given 5, an entered 7, the selected square, the next box's column on the right.
    val digits = listOf(5, 0, 7, 0, 0, 0, 0, 2, 0)
    val givens = setOf(0, 7)
    val selected = 4
    Box(
        Modifier.size(cell * 3)
            .then(if (classic) Modifier else Modifier.background(a.frame, RoundedCornerShape(5.dp))),
    ) {
        for (i in 0 until 9) {
            val r = i / 3
            val c = i % 3
            val nextBox = c == 2
            val entered = digits[i] != 0 && i !in givens
            val fill: Brush
            val ink: Color
            if (classic) {
                // Sudoku's Classic colours: the selection in hue A over the surface, every other box tinted in A, digits B.
                fill = SolidColor(
                    when {
                        i == selected -> BoardHues.pinnedMark(hueA).copy(alpha = if (dark) 0.55f else 0.40f).compositeOver(s.surface)
                        nextBox -> BoardHues.pinnedFill(hueA, dark).copy(alpha = if (dark) 0.55f else 0.30f).compositeOver(s.surface)
                        else -> s.surface
                    }
                )
                ink = if (entered) BoardHues.ink(hueB, dark, classic = true) else s.onSurface
            } else {
                val shade = androidx.compose.ui.graphics.lerp(a.square, a.given, 0.35f)
                val f = when {
                    i == selected -> a.selected
                    entered -> a.control
                    i in givens -> com.joebywan.daybook.ui.theme.ArcadeFill(a.given, a.given, a.givenInk)
                    nextBox -> com.joebywan.daybook.ui.theme.ArcadeFill(shade, shade, a.squareInk)
                    else -> com.joebywan.daybook.ui.theme.ArcadeFill(a.square, a.square, a.squareInk)
                }
                fill = f.brush
                ink = f.ink
            }
            Box(
                Modifier
                    .padding(start = cell * c, top = cell * r)
                    .size(cell)
                    .padding(if (classic) 1.dp else 0.75.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(fill)
                    .then(if (!classic && i == selected) Modifier.border(1.5.dp, a.selectedEdge, RoundedCornerShape(3.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (digits[i] != 0) {
                    Text(digits[i].toString(), color = ink, style = (if (i in givens) BoardGlyph.GIVEN else BoardGlyph.ENTERED).style(cell))
                }
            }
        }
    }
}

/** A home tile in each look: Classic's flat surface, Arcade's gradient in Sudoku's accent; its name and tier dots. */
@Composable
private fun SwatchTile(classic: Boolean, dark: Boolean, modifier: Modifier) {
    val s = lookScheme(dark, classic)
    val tile = if (classic) ArcadeBoard.classicTile(dark) else ArcadeBoard.arcadeTile(Color(Sudoku.accent), dark)
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape).background(tile.brush).padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(if (classic) s.surfaceVariant else s.surface))
        Text("Sudoku", color = tile.ink, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { k ->
                val dot = Modifier.size(4.dp).clip(CircleShape)
                val ring = if (classic) s.onSurfaceVariant else tile.ink
                Box(if (k == 0) dot.background(if (classic) BoardHues.textOn(Color(Sudoku.accent), s.surface, 3f) else tile.ink) else dot.border(0.75.dp, ring, CircleShape))
            }
        }
    }
}

/** Settings' Look card: the two swatches, beside Appearance. */
@Composable
internal fun LookSetting(look: Look, onLook: (Look) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().card(scheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CardText("Look", "Classic is Daybook's original, calmer colours; Arcade is brighter and bolder. Light or dark applies to both.")
        LookPicker(look, onLook)
    }
}

/**
 * The one-time offer on Home for a player from before the choice existed (issue #418): a card under the grid, as the
 * reminder's offer is, shown until they pick either look, never again. A pick in Settings answers it too. Until then the
 * app stays on Arcade, the look they already have.
 */
@Composable
internal fun LookOffer(onLook: (Look) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).card(scheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CardText(
            "Which look do you prefer?",
            "Classic brings back Daybook's original, calmer colours; Arcade is the brighter look you have now. " +
                "Tap one to choose. You can change it any time in Settings.",
        )
        LookPicker(null, onLook)
    }
}
