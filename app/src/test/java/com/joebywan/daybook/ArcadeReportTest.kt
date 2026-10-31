package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.joebywan.daybook.ColourDistance.de2000
import com.joebywan.daybook.ColourDistance.deut
import com.joebywan.daybook.ColourDistance.prot
import com.joebywan.daybook.ColourDistance.trit
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.ui.home.SOLVED_WASH_FROM
import com.joebywan.daybook.ui.home.tileInk
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.LightScheme
import com.joebywan.daybook.ui.theme.flatVivid
import com.joebywan.daybook.ui.theme.vividFor
import org.junit.Test
import java.io.File

/**
 * The Arcade trial's measured table, written to docs/arcade-trial/REPORT.txt (and stdout). It reports and never fails: the
 * trial wants the numbers, failures included. A "FAIL" line is a real failure of the threshold named on it.
 */
class ArcadeReportTest {
    private val out = StringBuilder()
    private var fails = 0
    private fun line(s: String) { out.appendLine(s) }
    private fun hex(c: Color) = "%06X".format(c.toArgb() and 0xFFFFFF)

    private fun check(need: Float, fg: Color, bg: Color, what: String): Float {
        val c = contrast(fg.compositeOver(bg), bg)
        if (c < need) { fails++; line("  FAIL %-46s %.2f < %.1f (%s on %s)".format(what, c, need, hex(fg), hex(bg))) }
        return c
    }

    private class Worst { var v = 99f; var at = ""; fun see(x: Float, w: String) { if (x < v) { v = x; at = w } } }

    @Test fun report() {
        for ((name, s, dark) in listOf(Triple("light", LightScheme, false), Triple("dark", DarkScheme, true))) {
            line("== $name scheme ==")
            val grounds = listOf("surface" to s.surface, "background" to s.background)
            val marks = Worst(); val text = Worst(); val tint = Worst(); val tile = Worst(); val button = Worst()
            line("puzzle       vivid   on-surface on-bg  textOn-surf  onTint-.22 tileInk-solved onFill")
            for (p in PuzzleRegistry.all) {
                val a = vividFor(p.accent, dark)
                val cs = check(3f, a, s.surface, "${p.id} mark on surface")
                val cb = check(3f, a, s.background, "${p.id} mark on background")
                marks.see(minOf(cs, cb), p.id)
                var t = 99f
                for ((g, bg) in grounds) t = minOf(t, check(4.5f, BoardHues.textOn(a, bg), bg, "${p.id} text on $g"))
                text.see(t, p.id)
                var ti = 99f
                for ((g, bg) in grounds) {
                    val ground = a.copy(alpha = .22f).compositeOver(bg)
                    ti = minOf(ti, check(4.5f, BoardHues.onTint(a, bg), ground, "${p.id} onTint over $g"))
                }
                tint.see(ti, p.id)
                val heavy = a.copy(alpha = SOLVED_WASH_FROM).compositeOver(s.surface)
                val tk = check(4.5f, tileInk(a, s.surface), heavy, "${p.id} tileInk on heaviest solved wash")
                check(4.5f, tileInk(a, s.surface), s.surface, "${p.id} tileInk on plain surface")
                tile.see(tk, p.id)
                val bt = check(4.5f, BoardHues.onFill(a), a, "${p.id} label on accent button")
                button.see(bt, p.id)
                line("%-12s %s  %5.2f %6.2f %8.2f %11.2f %10.2f %9.2f".format(p.id, hex(a), cs, cb, t, ti, tk, bt))
            }
            line("worst: marks %.2f (%s), text %.2f (%s), onTint %.2f (%s), tile ink %.2f (%s), button %.2f (%s)".format(
                marks.v, marks.at, text.v, text.at, tint.v, tint.at, tile.v, tile.at, button.v, button.at))
            line("-- structure")
            for ((g, bg) in grounds) {
                line("error on %-10s %.2f".format(g, check(4.5f, s.error, bg, "error on $g")))
                line("onSurfaceVariant on %-10s %.2f".format(g, check(4.5f, s.onSurfaceVariant, bg, "onSurfaceVariant on $g")))
                line("primary on %-10s %.2f".format(g, check(3f, s.primary, bg, "primary on $g")))
                line("secondary on %-10s %.2f".format(g, check(3f, s.secondary, bg, "secondary on $g")))
            }
            line("onError on error %.2f".format(check(4.5f, s.onError, s.error, "onError on error")))
            line("onPrimary on primary %.2f".format(check(4.5f, s.onPrimary, s.primary, "onPrimary on primary")))
            line("onSecondary on secondary %.2f".format(check(4.5f, s.onSecondary, s.secondary, "onSecondary on secondary")))
            line("outline on surface %.2f (decorative: no threshold)".format(contrast(s.outline, s.surface)))
            line("-- content hues")
            for (h in BoardHues.all) {
                val f = BoardHues.fill(h, dark)
                val k = BoardHues.ink(h, dark)
                val ink = minOf(check(4.5f, k, s.surface, "ink $h on surface"), check(4.5f, k, s.background, "ink $h on background"))
                val onF = check(4.5f, BoardHues.onFill(f), f, "digit on fill $h")
                val us = check(4.5f, s.onSurface, f, "onSurface on fill $h")
                // Not counted as failures: a light mark or fill under 3:1 on the surface is as shipped (docs/COLOUR.md: thin graphics take ink).
                val mk = contrast(BoardHues.mark(h), s.surface)
                val mb = contrast(f, s.surface)
                val shipped = contrast(BoardHues.pinnedMark(h), s.surface)
                line("hue %5.0f fill %s ink %s mark %s: ink %.2f, onFill %.2f, onSurface-on-fill %.2f, mark %.2f (old recipe %.2f), fill/surface %.2f".format(
                    h, hex(f), hex(k), hex(BoardHues.mark(h)), ink, onF, us, mk, shipped, mb))
            }
            val sel = BoardHues.selected(dark)
            line("selected %s: digit %.2f, error digit %.2f, ring on surface %.2f".format(hex(sel),
                check(4.5f, BoardHues.onFill(sel), sel, "digit on selected"),
                check(4.5f, BoardHues.textOn(s.error, sel), sel, "error digit on selected"),
                check(3f, BoardHues.selectedRing(dark), s.surface, "selected ring against surface")))
            line("-- spacing, CIEDE2000, closest pair (shipped: normal 5.79, colour-blind 2.90)")
            val acc = PuzzleRegistry.all.map { it.id to vividFor(it.accent, dark) }
            for ((label, set) in listOf("as built" to acc, "the brief's flat .85" to PuzzleRegistry.all.map { it.id to flatVivid(it.accent, dark) }))
            for ((mode, m) in listOf("normal" to null, "deuteranopia" to deut, "protanopia" to prot, "tritanopia" to trit)) {
                val pairs = set.indices.flatMap { i -> (i + 1 until set.size).map { j ->
                    Triple(de2000(set[i].second, set[j].second, m), set[i].first, set[j].first) } }.sortedBy { it.first }
                line("%-20s %-13s %s  under-floor pairs %d".format(label, mode,
                    pairs.take(3).joinToString { "%.2f %s/%s".format(it.first, it.second, it.third) },
                    pairs.count { it.first < if (m == null) 5.7 else 2.85 }))
            }
            val err = acc.minBy { de2000(it.second, s.error, null) }
            line("accent nearest the error red: %s %.2f".format(err.first, de2000(err.second, s.error, null)))
            val green = Color.hsl(145f, .8f, .56f)
            val g = acc.minBy { de2000(it.second, green, null) }
            line("accent nearest the Green mark: %s %.2f".format(g.first, de2000(g.second, green, null)))
            line("")
        }
        line("threshold failures: $fails")
        print(out)
        File(System.getProperty("user.dir")).let { d ->
            val root = if (File(d, "docs").exists()) d else d.parentFile
            File(root, "docs/arcade-trial").mkdirs()
            File(root, "docs/arcade-trial/REPORT.txt").writeText(out.toString())
        }
    }
}
