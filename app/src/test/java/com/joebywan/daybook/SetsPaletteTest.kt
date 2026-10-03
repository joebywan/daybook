package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.puzzles.Card
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsTeacher
import com.joebywan.daybook.ui.theme.BoardHues
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sets' three card colours, told apart by colour as well as by the card's other three traits: every pair is at least a
 * set CIEDE2000 apart for normal vision and for simulated deuteranopia and protanopia (Machado 2009, severity 1), and
 * each is clear of the card surface in both schemes. Written from the CIE formulas, independent of the palette's own
 * construction.
 */
class SetsPaletteTest {
    private fun lin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun gam(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

    private fun rgb(c: Color) = doubleArrayOf(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble())

    private fun lab(r: DoubleArray): DoubleArray {
        val l = r.map(::lin)
        val x = (0.4124 * l[0] + 0.3576 * l[1] + 0.1805 * l[2]) / 0.95047
        val y = 0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2]
        val z = (0.0193 * l[0] + 0.1192 * l[1] + 0.9505 * l[2]) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    private val deut = arrayOf(
        doubleArrayOf(0.367322, 0.860646, -0.227968),
        doubleArrayOf(0.280085, 0.672501, 0.047413),
        doubleArrayOf(-0.011820, 0.042940, 0.968881),
    )
    private val prot = arrayOf(
        doubleArrayOf(0.152286, 1.052583, -0.204868),
        doubleArrayOf(0.114503, 0.786281, 0.099216),
        doubleArrayOf(-0.003882, -0.048116, 1.051998),
    )

    private fun sim(r: DoubleArray, m: Array<DoubleArray>): DoubleArray {
        val l = r.map(::lin)
        return DoubleArray(3) { i -> gam((m[i][0] * l[0] + m[i][1] * l[1] + m[i][2] * l[2]).coerceIn(0.0, 1.0)) }
    }

    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI

    private fun de2000(p: DoubleArray, q: DoubleArray): Double {
        val (l1, a1, b1) = p.toList()
        val (l2, a2, b2) = q.toList()
        val cm = (hypot(a1, b1) + hypot(a2, b2)) / 2
        val g = 0.5 * (1 - sqrt(cm.pow(7) / (cm.pow(7) + 25.0.pow(7))))
        val a1p = (1 + g) * a1
        val a2p = (1 + g) * a2
        val c1 = hypot(a1p, b1)
        val c2 = hypot(a2p, b2)
        val h1 = (deg(atan2(b1, a1p)) + 360) % 360
        val h2 = (deg(atan2(b2, a2p)) + 360) % 360
        var dh = h2 - h1
        if (dh > 180) dh -= 360 else if (dh < -180) dh += 360
        val dL = l2 - l1
        val dC = c2 - c1
        val dH = 2 * sqrt(c1 * c2) * sin(rad(dh) / 2)
        val lm = (l1 + l2) / 2
        val cp = (c1 + c2) / 2
        val hm = when {
            abs(h1 - h2) <= 180 -> (h1 + h2) / 2
            h1 + h2 < 360 -> (h1 + h2 + 360) / 2
            else -> (h1 + h2 - 360) / 2
        }
        val t = 1 - 0.17 * cos(rad(hm - 30)) + 0.24 * cos(rad(2 * hm)) + 0.32 * cos(rad(3 * hm + 6)) -
            0.20 * cos(rad(4 * hm - 63))
        val sl = 1 + 0.015 * (lm - 50).pow(2) / sqrt(20 + (lm - 50).pow(2))
        val sc = 1 + 0.045 * cp
        val sh = 1 + 0.015 * cp * t
        val rt = -2 * sqrt(cp.pow(7) / (cp.pow(7) + 25.0.pow(7))) * sin(rad(60 * exp(-((hm - 275) / 25).pow(2))))
        return sqrt((dL / sl).pow(2) + (dC / sc).pow(2) + (dH / sh).pow(2) + rt * (dC / sc) * (dH / sh))
    }

    private fun apart(a: Color, b: Color) = minOf(
        de2000(lab(rgb(a)), lab(rgb(b))),
        de2000(lab(sim(rgb(a), deut)), lab(sim(rgb(b), deut))),
        de2000(lab(sim(rgb(a), prot)), lab(sim(rgb(b), prot))),
        de2000(lab(sim(rgb(a), trit)), lab(sim(rgb(b), trit))),
    )

    private val trit = arrayOf(
        doubleArrayOf(1.255528, -0.076749, -0.178779),
        doubleArrayOf(-0.078411, 0.930809, 0.147602),
        doubleArrayOf(0.004733, 0.691367, 0.303900),
    )

    // WCAG 2 contrast ratio from relative luminance.
    private fun luminance(c: Color): Double = rgb(c).map(::lin).let { 0.2126 * it[0] + 0.7152 * it[1] + 0.0722 * it[2] }
    private fun contrast(a: Color, b: Color) = (maxOf(luminance(a), luminance(b)) + 0.05) / (minOf(luminance(a), luminance(b)) + 0.05)

    // The card surface of ui/theme/Palette.kt: light, then dark.
    private val surfaces = listOf(Color(0xFFFFFBF2) to false, Color(0xFF1B2F29) to true)

    @Test
    fun `the three card colours are far apart in both schemes, all three kinds of colour-blindness included`() {
        // Measured min: light 24.2, dark 20.3 (colour-blind distances are the binding ones).
        for ((_, dark) in surfaces) {
            val p = Sets.palette(dark)
            for (i in 0 until 3) for (j in i + 1 until 3) {
                val d = apart(p[i], p[j])
                assertTrue("dark=$dark colours $i and $j: $d", d >= 20)
            }
        }
    }

    @Test
    fun `each card colour is at least 3 to 1 against the card surface in both schemes`() {
        // Measured min: light 3.47 (Teal), dark 3.91 (Violet).
        for ((surface, dark) in surfaces) for (c in Sets.palette(dark)) {
            val r = contrast(c, surface)
            assertTrue("dark=$dark: only $r:1", r >= 3.0)
        }
    }

    @Test
    fun `the colours differ in lightness as well as hue, so no two collapse to one grey`() {
        // Amber and Teal are the close pair (1.36 light, 1.24 dark); Violet is clear of both.
        for ((_, dark) in surfaces) {
            val p = Sets.palette(dark)
            for (i in 0 until 3) for (j in i + 1 until 3) {
                val dl = abs(lab(rgb(p[i]))[0] - lab(rgb(p[j]))[0])
                assertTrue("dark=$dark colours $i and $j: dL* $dl", dl >= 6)
            }
        }
    }

    @Test
    fun `no card colour is a Coral or Green step, which are the right and wrong colours`() {
        val reserved = listOf(8f, 145f).flatMap { h ->
            listOf(false, true).flatMap { d -> listOf(BoardHues.fill(h, d), BoardHues.ink(h, d), BoardHues.mark(h)) }
        }
        for ((_, dark) in surfaces) for (p in Sets.palette(dark)) assertTrue("a card colour is a reserved hue", p !in reserved)
    }

    @Test
    fun `three distinct colours, named in the teacher index for index`() {
        for ((_, dark) in surfaces) assertEquals(3, Sets.palette(dark).toSet().size)
        val names = listOf("amber", "teal", "violet")
        for (i in 0 until 3) {
            val words = SetsTeacher.describe(Card(count = 0, shape = 0, shading = 0, colour = i)).split(" ")
            assertEquals(names[i], words[2])
        }
    }
}

