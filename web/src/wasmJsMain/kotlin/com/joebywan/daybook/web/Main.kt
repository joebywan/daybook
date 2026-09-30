package com.joebywan.daybook.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.SeedHash
import kotlin.time.TimeSource

/**
 * The device's local calendar date as yyyymmdd, read from one `Date` so the three fields cannot
 * straddle midnight. Local, not UTC, because the app's `LocalDate.now()` is the phone's local date.
 */
private fun localDateCode(): Int =
    js("(() => { const d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate(); })()")

private fun locationSearch(): String = js("window.location.search")

private fun removeLoadingNote(): Unit = js("document.getElementById('loading')?.remove()")

/**
 * Asks Compose for one more frame shortly after start-up, and again whenever the page is shown
 * from the back-forward cache. Headless WebKit drops the very first WebGL frame Compose draws and
 * shows an empty page until the first touch; Chromium does not. Whether iOS Safari does is not
 * known, and one spare frame is cheap insurance against a blank board on launch. A resize event is
 * what makes Compose re-measure and redraw without any state of ours changing.
 */
private fun nudgeFirstFrame(): Unit = js("""(() => {
    const nudge = () => requestAnimationFrame(() => requestAnimationFrame(() => window.dispatchEvent(new Event('resize'))));
    nudge();
    setTimeout(nudge, 300);
    window.addEventListener('pageshow', nudge);
})()""")

/** A calendar date the way the app's `LocalDate` would print it, for the parity dump. */
private data class Day(val year: Int, val month: Int, val day: Int) {
    val epochDay: Long get() = SeedHash.epochDay(year, month, day)
    override fun toString(): String =
        "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {
        fun parse(text: String): Day? {
            val parts = text.split('-').map { it.toIntOrNull() ?: return null }
            if (parts.size != 3 || parts[1] !in 1..12 || parts[2] !in 1..31) return null
            return Day(parts[0], parts[1], parts[2])
        }

        fun today(): Day = localDateCode().let { Day(it / 10000, it / 100 % 100, it % 100) }

        /** The inverse of [SeedHash.epochDay] (Hinnant's civil-from-days). */
        fun ofEpochDay(epochDay: Long): Day {
            val z = epochDay + 719468
            val era = z.floorDiv(146097L)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return Day(y, m, d)
        }
    }
}

private fun query(): Map<String, String> =
    locationSearch().removePrefix("?").split('&').filter { it.isNotEmpty() }.associate {
        val eq = it.indexOf('=')
        if (eq < 0) it to "" else it.substring(0, eq) to it.substring(eq + 1)
    }

/** The dates every web parity test in app/ pins. */
private val PARITY_DAYS = listOf(Day(2026, 1, 1), Day(2026, 9, 30), Day(2027, 2, 28))

private fun fingerprint(type: PuzzleType, day: Day, tier: Difficulty): String =
    ParityFingerprint.line(type, day.toString(), tier, SeedHash.daily(day.epochDay, type.id, tier))

/**
 * `?dump`: every puzzle's board, one [ParityFingerprint] line each, on the console — `PARITY` for
 * the pinned dates, `TODAY` for `?date`/`?tier`, and with `&range=N` a `RANGE` line for every tier
 * of N days from 2026-01-01 plus a `TIMING` line per puzzle and tier. `WebParityDumpTest` writes the
 * JVM's side of the range in the same order, so the two diff directly. `&puzzle=<id>` narrows it to
 * one puzzle, which lets a harness split a year across pages. `&times` adds a `TIME <id> <date>
 * <tier> <ms>` line per board, for a distribution rather than only the mean and worst.
 */
private fun dump(params: Map<String, String>, today: Day, tier: Difficulty) {
    val only = params["puzzle"]?.let(PuzzleRegistry::byId)
    val types = if (only != null) listOf(only) else PuzzleRegistry.all
    for (type in types) for (d in PARITY_DAYS) for (t in Difficulty.entries) println("PARITY ${fingerprint(type, d, t)}")
    for (type in types) println("TODAY ${fingerprint(type, today, tier)}")
    val range = params["range"]?.toIntOrNull() ?: 0
    val times = "times" in params
    val start = Day(2026, 1, 1).epochDay
    for (type in types) {
        val worst = LongArray(Difficulty.entries.size)
        val total = LongArray(Difficulty.entries.size)
        for (i in 0 until range) {
            for (t in Difficulty.entries) {
                val began = TimeSource.Monotonic.markNow()
                val day = Day.ofEpochDay(start + i)
                val line = fingerprint(type, day, t)
                val elapsed = began.elapsedNow()
                val ms = elapsed.inWholeMilliseconds
                if (times) println("TIME ${type.id} $day ${t.name} ${elapsed.inWholeMicroseconds / 1000.0}")
                worst[t.ordinal] = maxOf(worst[t.ordinal], ms)
                total[t.ordinal] += ms
                println("RANGE $line")
            }
        }
        if (range > 0) {
            for (t in Difficulty.entries) {
                println("TIMING ${type.id} ${t.name} mean=${total[t.ordinal] / range}ms max=${worst[t.ordinal]}ms")
            }
        }
    }
    println("DUMP DONE")
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val params = query()
    val day = params["date"]?.let(Day::parse) ?: Day.today()
    val tier = params["tier"]?.let { Difficulty.fromKey(it.uppercase()) } ?: Difficulty.STANDARD
    println("daybook: date=$day epochDay=${day.epochDay}")
    if ("dump" in params) dump(params, day, tier)

    ComposeViewport(viewportContainerId = "app") {
        DaybookWebApp(params["date"], params["tier"], params["puzzle"])
    }
    removeLoadingNote()
    nudgeFirstFrame()
}
