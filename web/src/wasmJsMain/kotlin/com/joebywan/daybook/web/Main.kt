package com.joebywan.daybook.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.platform.currentDate
import com.joebywan.daybook.platform.dateOverride
import com.joebywan.daybook.core.TodaysThree
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.LevelSolve
import com.joebywan.daybook.platform.formatClock
import com.joebywan.daybook.platform.formatDate
import com.joebywan.daybook.ui.play.levelShareCard
import com.joebywan.daybook.ui.play.threeShareCard
import kotlinx.datetime.LocalDate
import com.joebywan.daybook.ui.play.EarnedAchievement
import com.joebywan.daybook.ui.play.Praise
import com.joebywan.daybook.ui.play.finishPreview
import com.joebywan.daybook.ui.play.shareCard
import com.joebywan.daybook.ui.play.sharePreviewOpen
import com.joebywan.daybook.ui.training.passPreview
import com.joebywan.daybook.ui.training.runPreview
import com.joebywan.daybook.ui.play.techniqueLine
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.WordWheelState
import kotlin.time.TimeSource

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

/**
 * iOS Safari can keep 100svh at its old value after a rotation (the app stayed landscape-short in portrait, width
 * right, height not). `#app` is sized by svh for the floating toolbar, so when svh is plainly not the window's height,
 * this falls back to the inset rule underneath it. Compose follows its container.
 */
private fun followRotation(): Unit = js("""(() => {
    const app = document.getElementById('app');
    const probe = document.createElement('div');
    probe.style.cssText = 'position:fixed;top:0;width:0;height:100svh;visibility:hidden';
    document.body.appendChild(probe);
    const check = () => {
        const stale = probe.offsetHeight > 0 && Math.abs(probe.offsetHeight - window.innerHeight) > window.innerHeight * 0.2;
        app.style.height = stale ? 'auto' : '';
        app.style.bottom = stale ? 'env(safe-area-inset-bottom, 0px)' : '';
    };
    const later = () => { check(); setTimeout(check, 300); };
    window.addEventListener('resize', later);
    window.addEventListener('orientationchange', later);
    window.visualViewport?.addEventListener('resize', later);
})()""")

/**
 * Compose never recovers a lost WebGL context, and everything is drawn through it, so the page stays dead until the app is
 * force quit. iOS Safari can take the context under memory pressure. A reload brings it back and saved games survive
 * one; the stored time stops a device that keeps losing it from reloading in a loop.
 */
private fun reloadOnLostContext(): Unit = js("""(() => {
    const onLost = () => {
        let last = 0;
        try { last = Number(sessionStorage.getItem('daybook_gl_reload')) || 0; sessionStorage.setItem('daybook_gl_reload', String(Date.now())); } catch (e) {}
        if (Date.now() - last > 10000) location.reload();
    };
    const until = Date.now() + 5000;
    const attach = () => {
        const canvas = [...document.querySelectorAll('#app *')].find(e => e.shadowRoot)?.shadowRoot.querySelector('canvas');
        if (canvas) canvas.addEventListener('webglcontextlost', onLost);
        else if (Date.now() < until) requestAnimationFrame(attach);
    };
    attach();
})()""")

private fun query(): Map<String, String> =
    locationSearch().removePrefix("?").split('&').filter { it.isNotEmpty() }.associate {
        val eq = it.indexOf('=')
        if (eq < 0) it to "" else it.substring(0, eq) to it.substring(eq + 1)
    }

/** The dates `BoardPinTest` in app/ pins. */
private val PARITY_DAYS = listOf(LocalDate(2026, 1, 1), LocalDate(2026, 9, 30), LocalDate(2027, 2, 28))

private fun fingerprint(type: PuzzleType, day: LocalDate, tier: Difficulty): String =
    ParityFingerprint.line(type, day.toString(), tier, DailySeed.seedFor(day, type.id, tier))

/**
 * `?dump`: every puzzle's board, one [ParityFingerprint] line each, on the console — `PARITY` for
 * the pinned dates, `TODAY` for `?date`/`?tier`, and with `&range=N` a `RANGE` line for every tier
 * of N days from 2026-01-01 plus a `TIMING` line per puzzle and tier. `WebParityDumpTest` writes the
 * JVM's side of the range in the same order, so the two diff directly. `&puzzle=<id>` narrows it to
 * one puzzle, which lets a harness split a year across pages. `&times` adds a `TIME <id> <date>
 * <tier> <ms>` line per board, for a distribution rather than only the mean and worst. `&puzzle=levels`
 * prints levels 1..N instead (five `RANGE levels ...` lines each), which `check.py` diffs as one more page.
 */
private fun dump(params: Map<String, String>, today: LocalDate, tier: Difficulty) {
    val levelsOnly = params["puzzle"] == "levels"
    val only = params["puzzle"]?.let(PuzzleRegistry::byId)
    val types = if (levelsOnly) emptyList() else if (only != null) listOf(only) else PuzzleRegistry.all
    for (type in types) for (d in PARITY_DAYS) for (t in Difficulty.entries) println("PARITY ${fingerprint(type, d, t)}")
    for (type in types) println("TODAY ${fingerprint(type, today, tier)}")
    val range = params["range"]?.toIntOrNull()?.coerceIn(0, 3650) ?: 0 // ten years; a crafted huge range hung the tab
    val times = "times" in params
    val start = LocalDate(2026, 1, 1).toEpochDays()
    for (type in types) {
        val worst = LongArray(Difficulty.entries.size)
        val total = LongArray(Difficulty.entries.size)
        for (i in 0 until range) {
            for (t in Difficulty.entries) {
                val began = TimeSource.Monotonic.markNow()
                val day = LocalDate.fromEpochDays(start + i)
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
    val state = if (words) LexiconState(5, 6, "crane", listOf("slate", "pride", "crane"))
        else p.generate(1L, tier).let { if (it is WordWheelState) it.copy(bonus = listOf("one", "two", "three")) else it }
    val log = if (words) "mmhm" else "mmmmmmumm" + "m".repeat(9) + "hmmmmm"
    shareCard(p, tier, "22 Oct", "12:04", 6, state, log, hints = 1)
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val params = query()
    dateOverride = DailySeed.fromLink(params["date"])
    val day = currentDate()
    val tier = params["tier"]?.let { Difficulty.fromKey(it.uppercase()) } ?: Difficulty.STANDARD
    println("daybook: date=$day epochDay=${day.toEpochDays()}")
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
            // `&alltiers` previews the top show (`allTiers`), its confetti and its border.
            allTiers = "alltiers" in params,
            title = if ("alltiers" in params) "All three tiers!" else if (n > 0) "7 day streak" else if ("leveldone" in params) "Level ${params["level"]} done" else "All your own work.",
            big = n > 0 || "alltiers" in params, streak = if (n > 0) 7 else 0, lines = listOf(sampleLine(params, tier)),
            achievements = all.take(n), toGo = if ("togo" in params) "Only Hard on Kings left for all done today!" else null,
            share = sampleShare(params["puzzle"], tier),
            // `&leveldone` also shows the level's card, `&threedone` today's three's (sample solves, never stored).
            setShare = when {
                "leveldone" in params -> (params["level"]?.toIntOrNull() ?: 1).let { n ->
                    val ids = listOf("sudoku", "kings", "pipes", "words", "sand")
                    levelShareCard(n, ids, ids.mapIndexed { i, id -> LevelSolve(n, id, 200 + 60 * i, if (i == 1) 1 else 0) }, ::formatClock)
                }
                "threedone" in params -> day.let { today ->
                    val three = TodaysThree.of(today)
                    threeShareCard(three, three.map { Completion(it, tier, today, 300, 0) }, today, tier, formatDate(today, "d MMM"), 4, ::formatClock)
                }
                else -> null
            },
            setShareLabel = if ("leveldone" in params) "Share level" else "Share three",
        )
        // `&share` opens the share preview over the frame.
        sharePreviewOpen = "share" in params
    }
    // `?screen=training&technique=<id>&run=N` opens the practice on a run of N; `&pass` with its celebration showing.
    if (params["screen"] == "training") {
        runPreview = params["run"]?.toIntOrNull() ?: 0
        passPreview = "pass" in params
    }
    // `?screen=stats&share` opens the day's share preview.
    if (params["screen"] == "stats") sharePreviewOpen = "share" in params

    ComposeViewport(viewportContainerId = "app") {
        DaybookWebApp(params)
    }
    removeLoadingNote()
    nudgeFirstFrame()
    followRotation()
    reloadOnLostContext()
    focusCanvas()
}
