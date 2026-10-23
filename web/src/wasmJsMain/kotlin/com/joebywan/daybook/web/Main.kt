package com.joebywan.daybook.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.ui.play.EarnedAchievement
import com.joebywan.daybook.ui.play.Praise
import com.joebywan.daybook.ui.play.finishPreview
import com.joebywan.daybook.ui.play.shareCard
import com.joebywan.daybook.ui.play.sharePreviewOpen
import com.joebywan.daybook.ui.play.techniqueLine
import com.joebywan.daybook.puzzles.LexiconState
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

/**
 * Gives Compose's canvas the page's keyboard focus on load, so a board opened by a link or a refresh
 * takes keys without a click first (until then focus is on `<body>` and every key is lost). The canvas
 * sits in a shadow root, out of reach of a plain selector, and may not exist yet on the first frame of a
 * loaded machine (one try missed 4 boards in 21), so it is retried each frame for up to five seconds.
 * Left alone if something else already has focus.
 */
private fun focusCanvas(): Unit = js("""(() => {
    const until = Date.now() + 5000;
    const tryFocus = () => {
        if (document.activeElement && document.activeElement !== document.body) return;
        const canvas = [...document.querySelectorAll('#app *')].find(e => e.shadowRoot)?.shadowRoot.querySelector('canvas');
        if (canvas) canvas.focus();
        if (document.activeElement === document.body && Date.now() < until) requestAnimationFrame(tryFocus);
    };
    requestAnimationFrame(tryFocus);
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
 * <tier> <ms>` line per board, for a distribution rather than only the mean and worst. `&puzzle=levels`
 * prints levels 1..N instead (five `RANGE levels ...` lines each), which `check.py` diffs as one more page.
 */
private fun dump(params: Map<String, String>, today: Day, tier: Difficulty) {
    val levelsOnly = params["puzzle"] == "levels"
    val only = params["puzzle"]?.let(PuzzleRegistry::byId)
    val types = if (levelsOnly) emptyList() else if (only != null) listOf(only) else PuzzleRegistry.all
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
    // Levels 1..N, five boards each, under the pseudo-puzzle "levels": with `&puzzle=levels`, or with no `&puzzle`.
    if (levelsOnly || params["puzzle"] == null) {
        for (level in 1..range) ParityFingerprint.levelLines(level).forEach { println("RANGE $it") }
    }
    println("DUMP DONE")
}

/**
 * The praise line for the render harness: `&taught=ID` a hint solve that opened that technique, `&needed` a no-hint solve
 * of the puzzle's seed-1 board at the tier (what `neededTechnique` says it needed), else "No hints needed.".
 */
private fun sampleLine(params: Map<String, String>, tier: Difficulty): String {
    val p = params["puzzle"]?.let(PuzzleRegistry::byId) ?: return "No hints needed."
    val taught = params["taught"]
    val needed = if ("needed" in params) p.neededTechnique(p.generate(1L, tier)) else null
    return techniqueLine(p.id, if (taught != null) 1 else 0, listOfNotNull(taught), needed) ?: "No hints needed."
}

/** A share card for the render harness: three Lexicon guesses after a hint, or 23 logged moves with undos and a hint. */
private fun sampleShare(id: String?, tier: Difficulty) = (id?.let(PuzzleRegistry::byId) ?: PuzzleRegistry.all.first()).let { p ->
    val words = p.id == "words"
    val state = if (words) LexiconState(5, 6, "crane", listOf("slate", "pride", "crane")) else p.generate(1L, tier)
    val log = if (words) "mmhm" else "mmmmmmumm" + "m".repeat(9) + "hmmmmm"
    shareCard(p, tier, "22 Oct", "12:04", 6, state, log, hints = 1)
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val params = query()
    val day = params["date"]?.let(Day::parse) ?: Day.today()
    val tier = params["tier"]?.let { Difficulty.fromKey(it.uppercase()) } ?: Difficulty.STANDARD
    println("daybook: date=$day epochDay=${day.epochDay}")
    if ("dump" in params) dump(params, day, tier)
    // Render harness: `?finish=N` (0-3 achievements) shows the finished frame over the board; `&togo` adds the to-go line;
    // `&taught=ID` or `&needed` shows the technique line (sampleLine).
    params["finish"]?.toIntOrNull()?.let { n ->
        val all = listOf(
            EarnedAchievement("streak7", "A full week", "Play on 7 days in one streak."),
            EarnedAchievement("firstTop", "First Expert", "Solve a puzzle on Expert."),
            EarnedAchievement("solves100", "Hundred solves", "Solve 100 puzzles."),
        )
        finishPreview = Praise(
            // `&level=N` previews a level board's frame; `&leveldone` the one its fifth board shows.
            title = if (n > 0) "7 day streak" else if ("leveldone" in params) "Level ${params["level"]} done" else "All your own work.",
            big = n > 0, streak = if (n > 0) 7 else 0, lines = listOf(sampleLine(params, tier)),
            achievements = all.take(n), toGo = if ("togo" in params) "Only Hard on Kings left for all done today!" else null,
            share = sampleShare(params["puzzle"], tier),
        )
        // `&share` opens the share preview over the frame.
        sharePreviewOpen = "share" in params
    }

    ComposeViewport(viewportContainerId = "app") {
        DaybookWebApp(params["date"], params["tier"], params["puzzle"], params)
    }
    removeLoadingNote()
    nudgeFirstFrame()
    focusCanvas()
}
