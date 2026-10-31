package com.joebywan.daybook.platform

import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.CardText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.SolveTone
import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.Haptics
import com.joebywan.daybook.ui.settings.githubRateLink
import com.joebywan.daybook.ui.settings.RateLink
import com.joebywan.daybook.ui.theme.withFamily
import com.joebywan.daybook.web.Backup
import com.joebywan.daybook.web.WebStores
import com.joebywan.daybook.web.resources.Res
import com.joebywan.daybook.web.resources.fredoka_bold
import com.joebywan.daybook.web.resources.fredoka_medium
import com.joebywan.daybook.web.resources.fredoka_regular
import com.joebywan.daybook.web.resources.fredoka_semibold
import com.joebywan.daybook.web.resources.noto_sans_math
import com.joebywan.daybook.web.resources.noto_sans_symbols2
import com.joebywan.daybook.web.resources.noto_sans_symbols_arrows
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.preloadFont
import kotlin.coroutines.resume

// The web half of the platform seam: the same functions, in the same package, as
// app/src/main/java/com/joebywan/daybook/platform/AndroidPlatform.kt. The shared screens call them
// by name, and each build compiles exactly one of the two files.

/** Set by `?date=` so a harness can open the app on any day; null means the device's own date. */
internal var dateOverride: LocalDate? = null

/**
 * The device's local calendar date as yyyymmdd, read from one `Date` so the three fields cannot
 * straddle midnight. Local, not UTC, because Android's `LocalDate.now()` is the phone's local date.
 */
private fun localDateCode(): Int =
    js("(() => { const d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate(); })()")

fun currentDate(): LocalDate =
    dateOverride ?: localDateCode().let { LocalDate(it / 10000, it / 100 % 100, it % 100) }

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * The few `DateTimeFormatter` patterns the screens use, in English. A browser has no JVM locale
 * data, and the fields are simple enough that spelling them out beats shipping a formatter.
 */
fun formatDate(date: LocalDate, pattern: String): String =
    pattern.split(' ').joinToString(" ") { field ->
        when (field) {
            "EEEE" -> DAY_NAMES[date.dayOfWeek.ordinal]
            "EEE" -> DAY_NAMES[date.dayOfWeek.ordinal].take(3)
            "d" -> date.day.toString()
            "MMMM" -> MONTH_NAMES[date.month.ordinal]
            "MMM" -> MONTH_NAMES[date.month.ordinal].take(3)
            "yyyy" -> date.year.toString()
            else -> error("formatDate: unsupported field '$field' in '$pattern'")
        }
    }

@Composable
fun rememberKeyValueStore(name: String): KeyValueStore = remember(name) { WebStores.get(name) }

private fun pushHistoryEntry(): Unit = js("history.pushState({ daybook: true }, '')")

private fun historyGo(delta: Int): Unit = js("history.go(delta)")

private fun onPopState(handler: () -> Unit): Unit = js("window.addEventListener('popstate', () => handler())")

private fun later(action: () -> Unit): Unit = js("setTimeout(() => action(), 0)")

/**
 * The browser's back button (and Safari's edge swipe) standing in for Android's.
 *
 * Every enabled [PlatformBackHandler] owns one history entry of ours on top of the page's own, so
 * back runs the innermost one — the walkthrough before the screen under it, as on Android. A handler
 * that goes away by other means (an in-app control) takes its entry back off, so back from Home still
 * leaves the page as it would close the app on a phone; the pop that causes is skipped rather than
 * treated as the player's. Removals in one frame are batched into a single `history.go`, because
 * several `history.back()` calls in a row are not reliably all honoured.
 */
private object WebHistory {
    class Entry(val onBack: () -> Unit)

    private val stack = mutableListOf<Entry>()
    private var skipPops = 0
    private var pendingRemovals = 0

    init {
        onPopState {
            if (skipPops > 0) skipPops-- else stack.removeLastOrNull()?.onBack?.invoke()
        }
    }

    fun push(onBack: () -> Unit): Entry {
        pushHistoryEntry()
        return Entry(onBack).also(stack::add)
    }

    fun remove(entry: Entry) {
        // Already gone if the player's own back press is what removed it.
        if (!stack.remove(entry)) return
        if (pendingRemovals++ == 0) {
            later {
                skipPops++
                historyGo(-pendingRemovals)
                pendingRemovals = 0
            }
        }
    }
}

@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val latest by rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        val entry = if (enabled) WebHistory.push { latest() } else null
        onDispose { entry?.let(WebHistory::remove) }
    }
}

/**
 * A home-screen web app on an iPhone has no back button and no back gesture, so every screen but
 * Home carries this one.
 */
@Composable
fun BackButton(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.padding(start = 6.dp).size(48.dp).clip(CircleShape).clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = scheme.onSurfaceVariant)
    }
}

/**
 * The page behind the canvas (the safe-area margins, the loading note's colours) and the browser's
 * theme-color, set to the theme the app chose rather than the system's, so a Dark choice on a light
 * device has no light edges. index.html makes the same choice from storage before the app loads.
 */
private fun setPageTheme(dark: Boolean): Unit = js("""(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.querySelectorAll('meta[name=theme-color]').forEach(m => m.content = dark ? '#11201C' : '#F6F1E7');
})()""")

@Composable
fun SystemBarsAppearance(darkTheme: Boolean) {
    SideEffect { setPageTheme(darkTheme) }
}

/**
 * A browser can drop a site's storage, and there is no cloud backup behind it the way Android has,
 * so the stats screen offers a file the player can keep.
 */
const val OFFERS_BACKUP: Boolean = true

@Composable
fun BackupControls() {
    val scheme = MaterialTheme.colorScheme
    var message by remember { mutableStateOf<String?>(null) }
    // A backup picked and read, waiting for the player to say it may replace what is here.
    var pending by remember { mutableStateOf<Backup.Pending?>(null) }
    pending?.let { p ->
        fun solves(n: Int) = if (n == 1) "1 solve" else "$n solves"
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Replace your progress?") },
            text = {
                Text(
                    "This browser has ${solves(p.solvesHere)}; the backup has ${solves(p.solvesInBackup)}. " +
                        "Importing replaces everything here with the backup.",
                )
            },
            confirmButton = { TextButton(onClick = { pending = null; message = Backup.restore(p) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { pending = null; message = "Nothing imported." }) { Text("Cancel") } },
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .card(scheme.surface)
            .padding(14.dp),
    ) {
        CardText("Backup", "Progress lives in this browser only. Keep a copy, or move it to another device.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackupButton("Export", Modifier.weight(1f)) {
                message = Backup.export()
            }
            BackupButton("Import", Modifier.weight(1f)) {
                Backup.import(onError = { message = it }, onReady = { pending = it })
            }
        }
        // Reserved, so a message appearing does not move the buttons.
        Text(
            message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 2,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun isIos(): Boolean =
    js("/iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)")

private fun isAndroid(): Boolean = js("/Android/.test(navigator.userAgent)")

/** Opened from a home-screen icon rather than a browser tab. */
private fun isStandalone(): Boolean =
    js("navigator.standalone === true || window.matchMedia('(display-mode: standalone)').matches")

/**
 * Under the home grid, by device: on Android, the app and where to get it; on iPhone and iPad, how to add this
 * page to the home screen (already there: nothing). Desktops get nothing.
 * The Android claims are the manifest's: no INTERNET permission, nothing a player is asked to grant.
 */
@Composable
fun GetTheAppCard() {
    val device = remember { if (isAndroid()) 'a' else if (isIos() && !isStandalone()) 'i' else '-' }
    if (device == '-') return
    val scheme = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .card(scheme.surface)
            .padding(14.dp),
    ) {
        if (device == 'a') {
            CardText(
                "Get the Android app",
                "The same daily puzzles, installed. It asks for no permissions at all, not even the internet, " +
                    "so there is no tracking and nothing to sign up for. No ads, free, works offline.",
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupButton("Google Play", Modifier.weight(1f)) { uri.openUri(PLAY_URL) }
                BackupButton("F-Droid", Modifier.weight(1f)) { uri.openUri(FDROID_URL) }
            }
        } else {
            CardText(
                "Add Daybook to your Home Screen",
                "Tap Share, then Add to Home Screen. It opens full screen like an app and works offline, and " +
                    "your progress is safer: Safari can clear a website's data after a week unused, " +
                    "but not a Home Screen app's.",
            )
        }
    }
}

private const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.joebywan.daybook"

/** The repo's own page: its QR code, the add-repo link and the key fingerprint. */
private const val FDROID_URL = "https://knowhowit.com.au/daybook/fdroid/repo/index.html"

@Composable
private fun BackupButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}

// ---- Board generation -------------------------------------------------------------------------
//
// A browser gives wasm one thread, so a board is generated on the thread that draws the page: LITS
// can take seconds in Safari, Snap and Mosaic half a second. Two things keep that from reading as a
// frozen page. A board is only generated once "Setting out" has actually been painted, and while
// the home grid is up, today's boards are made in advance, one per turn of the event loop, so a
// tap usually finds its board already waiting. The generators themselves are untouched.

private data class BoardKey(val puzzleId: String, val seed: Long, val difficulty: Difficulty)

/** Boards already generated, oldest first; a board is immutable, so one can be handed out twice. */
private val boards = LinkedHashMap<BoardKey, PuzzleState>()

/** Today's eleven at one tier, plus room for the archive and random games around them. */
private const val KEEP_BOARDS = 40

private fun keep(key: BoardKey, board: PuzzleState) {
    boards.remove(key)
    boards[key] = board
    while (boards.size > KEEP_BOARDS) boards.remove(boards.keys.first())
}

/**
 * Calls [callback] once the next frame has been painted: `requestAnimationFrame` runs just before a
 * paint, and a task queued from it runs after. Also a yield — input that arrived meanwhile is
 * handled before [callback] runs.
 */
private fun afterNextPaint(callback: () -> Unit): Unit =
    js("requestAnimationFrame(() => setTimeout(() => callback(), 0))")

private suspend fun awaitPaint() = suspendCancellableCoroutine { done ->
    afterNextPaint { if (done.isActive) done.resume(Unit) }
}

/** A board, from the ones made in advance if it is there, otherwise once the screen says so. */
suspend fun generateBoard(puzzle: PuzzleType, seed: Long, difficulty: Difficulty): PuzzleState {
    val key = BoardKey(puzzle.id, seed, difficulty)
    boards[key]?.let { return it }
    awaitPaint()
    boards[key]?.let { return it }
    return puzzle.generate(seed, difficulty).also { keep(key, it) }
}

/** A board made in advance, if there is one: the loading screen is then skipped altogether. */
fun readyBoard(puzzle: PuzzleType, seed: Long, difficulty: Difficulty): PuzzleState? =
    boards[BoardKey(puzzle.id, seed, difficulty)]

/** The thread that would turn a spinner is the one generating, so a spinner would sit frozen. */
const val GENERATION_ANIMATES: Boolean = false

/** Settings text under the Sound switch: what else keeps the chime quiet on this platform. */
const val SOLVE_SOUND_NOTE: String =
    "A browser cannot see your phone's silent switch, so turn this off if you want it quiet."

/** How long the home grid is up before the first board is made, so an early tap is not kept waiting. */
private const val PREPARE_AFTER_MS = 400L

/**
 * Makes [day]'s boards at [difficulty] while the home grid is showing, in grid order. One board per
 * turn of the event loop, and leaving Home cancels this between boards. A board already underway
 * still finishes first, which is the one wait this cannot remove.
 */
suspend fun prepareBoards(day: LocalDate, difficulty: Difficulty) {
    delay(PREPARE_AFTER_MS)
    for (puzzle in PuzzleRegistry.all) {
        val key = BoardKey(puzzle.id, DailySeed.seedFor(day, puzzle.id, difficulty), difficulty)
        if (key in boards) continue
        awaitPaint()
        keep(key, puzzle.generate(key.seed, difficulty))
    }
}

// ---- Fonts ------------------------------------------------------------------------------------
//
// A browser lends wasm none of its fonts, so the only one available is the one Compose ships. Seven
// are bundled (web/src/wasmJsMain/composeResources/font): Fredoka at 400/500/600/700, the same
// family Android bundles, and as fallbacks for glyphs Fredoka lacks the arrows from Noto Sans
// Symbols, ✓ ✕ from Noto Sans Symbols 2 and ↻ from Noto Sans Math. All are cut down to the
// characters in use; see docs/WEB_BUILD.md.
// Fredoka and Noto are under the SIL Open Font License, whose notice each file carries in its
// name table.

/** [base] in the bundled Fredoka; [WithSymbolFonts] supplies what Skia falls back to for a glyph it lacks. */
@Composable
fun platformTypography(base: Typography): Typography {
    val fredoka = FontFamily(
        Font(Res.font.fredoka_regular, FontWeight.Normal),
        Font(Res.font.fredoka_medium, FontWeight.Medium),
        Font(Res.font.fredoka_semibold, FontWeight.SemiBold),
        Font(Res.font.fredoka_bold, FontWeight.Bold),
    )
    return remember(base, fredoka) { base.withFamily(fredoka) }
}

/**
 * [content] once the symbol fonts are preloaded, which is what makes Skia fall back to them. Text laid out
 * before then finds a glyph missing and Compose fetches a Noto font from fonts.gstatic.com for it, which the
 * CSP blocks, so it stays a box: ↻ ✕ on a Word Wheel opened from a link did, until this held the app back.
 * It holds it back at most [SYMBOL_FONTS_WAIT_MS]: a font that fails to load costs its glyphs, never the app.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun WithSymbolFonts(content: @Composable () -> Unit) {
    val fonts = listOf(
        preloadFont(Res.font.noto_sans_symbols_arrows),
        preloadFont(Res.font.noto_sans_symbols2),
        preloadFont(Res.font.noto_sans_math),
    ).map { it.value }
    val resolver = LocalFontFamilyResolver.current
    var ready by remember { mutableStateOf(false) }
    // Restarts as each font arrives, preloading whichever have (again: the resolver caches them).
    LaunchedEffect(resolver, fonts) {
        fonts.filterNotNull().forEach { resolver.preload(FontFamily(it)) }
        if (fonts.all { it != null }) ready = true
    }
    LaunchedEffect(Unit) {
        delay(SYMBOL_FONTS_WAIT_MS)
        ready = true
    }
    if (ready) content()
}

/** How long [WithSymbolFonts] waits for a font before showing the app without it. */
private const val SYMBOL_FONTS_WAIT_MS = 2500L

// --- The game's sounds (Web Audio) -------------------------------------------------------------
// The samples are the shared Tones; JS only holds them and a lazily made AudioContext. Browsers
// start audio only from a gesture, and Safari wants the context woken inside the handler itself, so
// the first taps wake it (a silent one-sample buffer) and every sound, which follows a tap, finds it
// running. A buffer source is cheap and made per play, as Web Audio intends. Everything is guarded:
// no Web Audio, or a refusal, is silence and nothing else.

private fun newFloatArray(n: Int): JsAny = js("new Float32Array(n)")

private fun setFloat(array: JsAny, i: Int, v: Float): Unit = js("array[i] = v")

private fun keepTone(i: Int, tone: JsAny, rate: Int): Unit = js(
    """(() => {
        const S = globalThis.__daybookAudio || (globalThis.__daybookAudio = { tones: [], bufs: [], rate: 0, ctx: null, wired: false });
        S.tones[i] = tone; S.rate = rate;
        if (S.wired) return;
        S.wired = true;
        const events = ['pointerup', 'touchend', 'click', 'keydown'];
        const wake = () => {
            try {
                if (S.ctx && S.ctx.state === 'running') {
                    events.forEach((e) => window.removeEventListener(e, wake, true));
                    return;
                }
                const AC = globalThis.AudioContext || globalThis.webkitAudioContext;
                if (!AC) return;
                if (!S.ctx) S.ctx = new AC();
                S.ctx.resume();
                const silent = S.ctx.createBufferSource();
                silent.buffer = S.ctx.createBuffer(1, 1, 22050);
                silent.connect(S.ctx.destination);
                silent.start(0);
            } catch (e) { }
        };
        events.forEach((e) => window.addEventListener(e, wake, true));
    })()"""
)

private fun startTone(i: Int): Unit = js(
    """(() => {
        const S = globalThis.__daybookAudio;
        if (!S || !S.tones[i]) return;
        try {
            const AC = globalThis.AudioContext || globalThis.webkitAudioContext;
            if (!AC) return;
            if (!S.ctx) S.ctx = new AC();
            const ctx = S.ctx;
            if (ctx.state !== 'running') ctx.resume();
            if (!S.bufs[i]) {
                S.bufs[i] = ctx.createBuffer(1, S.tones[i].length, S.rate);
                S.bufs[i].getChannelData(0).set(S.tones[i]);
            }
            const src = ctx.createBufferSource();
            src.buffer = S.bufs[i];
            src.connect(ctx.destination);
            src.start();
        } catch (e) { }
    })()"""
)

/**
 * Plays a tone of the bank ([Tones]) when called, if [enabled]. While enabled it renders the bank once, a tone a turn so
 * a frame never waits on all of it, hands it to the page and arms the first-tap wake-up; off, it does nothing at all.
 */
@Composable
fun rememberTonePlayer(enabled: Boolean): (Int) -> Unit {
    LaunchedEffect(enabled) {
        if (enabled) {
            try {
                for (t in 0 until Tones.COUNT) {
                    val tone = Tones.samples(t)
                    val array = newFloatArray(tone.size)
                    for (i in tone.indices) setFloat(array, i, tone[i])
                    keepTone(t, array, SolveTone.SAMPLE_RATE)
                    delay(1)
                }
            } catch (_: Throwable) {
            }
        }
    }
    return remember(enabled) {
        if (!enabled) ({}) else ({ t -> try { startTone(t) } catch (_: Throwable) { } })
    }
}

private fun canShare(): Boolean = js("typeof navigator.share === 'function'")

// A cancelled sheet rejects the promise; that is the player's choice, not an error.
private fun shareNatively(text: String): Unit = js("navigator.share({ text: text }).catch(() => {})")

private fun copyToClipboard(text: String): Unit =
    js("navigator.clipboard ? navigator.clipboard.writeText(text).catch(() => {}) : undefined")

/**
 * Hands a result's text to the browser's share sheet where it has one (phones), or copies it to the clipboard
 * (most desktops), and says so on the button.
 */
@Composable
fun rememberSharer(): (String) -> String? = remember {
    { text ->
        if (canShare()) { shareNatively(text); null } else { copyToClipboard(text); "Copied" }
    }
}

private fun prefersReducedMotion(): Boolean = js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")

private fun onReducedMotionChange(handler: (Boolean) -> Unit): Unit =
    js("window.matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change', (e) => handler(e.matches))")

/**
 * Whether the browser asks for less motion (`prefers-reduced-motion`, which follows the system setting), watched
 * so a change takes effect at once. Called once, at the root, so the one listener is never removed.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    var reduced by remember { mutableStateOf(prefersReducedMotion()) }
    LaunchedEffect(Unit) { onReducedMotionChange { reduced = it } }
    return reduced
}

/** Settings text under the Vibration switch: what else keeps it still on this platform. */
const val VIBRATION_NOTE: String = "Only where the browser can vibrate: Chrome on Android can, iPhone and iPad cannot."

// `navigator.vibrate` is missing on iOS and may refuse before the first tap; either way nothing happens.
private fun vibrate(ms: Int): Unit = js("navigator.vibrate && navigator.vibrate(ms)")

private fun vibratePattern(): Unit = js("navigator.vibrate && navigator.vibrate([20, 60, 40])")

/** The game's buzzes, if [enabled], through `navigator.vibrate`: short, since a phone's motor is loud. */
@Composable
fun rememberHaptics(enabled: Boolean): Haptics = remember(enabled) {
    if (!enabled) Haptics.None else Haptics(tick = { vibrate(8) }, bump = { vibrate(50) }, success = { vibratePattern() })
}

/** The web has no store to rate in, so the link is a star on GitHub. */
@Composable
fun rememberRateLink(): RateLink {
    val uri = LocalUriHandler.current
    return remember(uri) { githubRateLink(uri::openUri) }
}

/** A page cannot notify reliably once it is closed, so the daily reminder is not offered on the web. */
const val OFFERS_REMINDER: Boolean = false

/** Never asked on the web (see [OFFERS_REMINDER]); answers no. */
@Composable
fun rememberAskToNotify(): ((Boolean) -> Unit) -> Unit = remember { { done -> done(false) } }

/** Never called on the web (see [OFFERS_REMINDER]). */
@Composable
fun rememberReminderSchedule(): (Boolean, Int) -> Unit = remember { { _, _ -> } }
