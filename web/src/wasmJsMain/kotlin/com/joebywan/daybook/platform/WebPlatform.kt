package com.joebywan.daybook.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.SolveTone
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.theme.withFamily
import com.joebywan.daybook.web.Backup
import com.joebywan.daybook.web.WebStores
import com.joebywan.daybook.web.resources.Res
import com.joebywan.daybook.web.resources.fredoka_bold
import com.joebywan.daybook.web.resources.fredoka_medium
import com.joebywan.daybook.web.resources.fredoka_regular
import com.joebywan.daybook.web.resources.fredoka_semibold
import com.joebywan.daybook.web.resources.noto_sans_symbols_arrows
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.Font
import kotlin.coroutines.resume
import kotlin.random.Random

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

private fun nowMillis(): Double = js("Date.now()")

fun currentDate(): LocalDate =
    dateOverride ?: localDateCode().let { LocalDate(it / 10000, it / 100 % 100, it % 100) }

fun currentTimeMillis(): Long = nowMillis().toLong()

/** Only has to be fresh, never ordered: it seeds a random board. */
fun freshNonce(): Long = Random.nextLong()

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

fun formatClock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

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
        Modifier.padding(start = 6.dp).size(44.dp).clip(CircleShape).clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = scheme.onSurfaceVariant)
    }
}

/** The page's theme-color meta tags already follow the colour scheme; nothing to do per frame. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SystemBarsAppearance(darkTheme: Boolean) = Unit

/**
 * A browser can drop a site's storage, and there is no cloud backup behind it the way Android has,
 * so the stats screen offers a file the player can keep.
 */
const val OFFERS_BACKUP: Boolean = true

@Composable
fun BackupControls() {
    val scheme = MaterialTheme.colorScheme
    var message by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .padding(14.dp),
    ) {
        Text("Backup", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(
            "Progress lives in this browser only. Keep a copy, or move it to another device.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackupButton("Export", Modifier.weight(1f)) {
                message = Backup.export()
            }
            BackupButton("Import", Modifier.weight(1f)) {
                Backup.import { result -> message = result }
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

@Composable
private fun BackupButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
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

/**
 * Zero, deliberately. A delayed message would never be seen: generation holds the only thread, so
 * the message has to be painted before it starts (see [awaitPaint]). Boards that are cheap are
 * mostly in the cache and skip the loading screen entirely.
 */
const val LOADING_MESSAGE_DELAY_MS: Long = 0L

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
// A browser lends wasm none of its fonts, so the only one available is the one Compose ships. Five
// are bundled (web/src/wasmJsMain/composeResources/font): Fredoka at 400/500/600/700, the same
// family Android bundles, and the arrows from Noto Sans Symbols as a fallback for glyphs Fredoka
// lacks. All are cut down to the characters in use; see docs/WEB_BUILD.md.
// Fredoka and Noto are under the SIL Open Font License, whose notice each file carries in its
// name table.

/** [base] in the bundled Fredoka; the arrows are what Skia falls back to for a glyph it lacks. */
@Composable
fun platformTypography(base: Typography): Typography {
    val fredoka = FontFamily(
        Font(Res.font.fredoka_regular, FontWeight.Normal),
        Font(Res.font.fredoka_medium, FontWeight.Medium),
        Font(Res.font.fredoka_semibold, FontWeight.SemiBold),
        Font(Res.font.fredoka_bold, FontWeight.Bold),
    )
    val arrows = FontFamily(Font(Res.font.noto_sans_symbols_arrows))
    val resolver = LocalFontFamilyResolver.current
    // Preloaded fonts are what Skia falls back to for a glyph the requested font lacks.
    LaunchedEffect(resolver, arrows) { resolver.preload(arrows) }
    return remember(base, fredoka) { base.withFamily(fredoka) }
}

// --- The solve sound (Web Audio) ---------------------------------------------------------------
// The samples are the shared SolveTone; JS only holds them and a lazily made AudioContext. Browsers
// start audio only from a gesture, and Safari wants the context woken inside the handler itself, so
// the first taps wake it (a silent one-sample buffer) and the solve, which always follows taps,
// finds it running. Everything is guarded: no Web Audio, or a refusal, is silence and nothing else.

private fun newFloatArray(n: Int): JsAny = js("new Float32Array(n)")

private fun setFloat(array: JsAny, i: Int, v: Float): Unit = js("array[i] = v")

private fun keepTone(tone: JsAny, rate: Int): Unit = js(
    """(() => {
        const S = globalThis.__daybookAudio || (globalThis.__daybookAudio = { tone: null, rate: 0, ctx: null, buf: null, wired: false });
        S.tone = tone; S.rate = rate;
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

private fun startTone(): Unit = js(
    """(() => {
        const S = globalThis.__daybookAudio;
        if (!S || !S.tone) return;
        try {
            const AC = globalThis.AudioContext || globalThis.webkitAudioContext;
            if (!AC) return;
            if (!S.ctx) S.ctx = new AC();
            const ctx = S.ctx;
            if (ctx.state !== 'running') ctx.resume();
            if (!S.buf) {
                S.buf = ctx.createBuffer(1, S.tone.length, S.rate);
                S.buf.getChannelData(0).set(S.tone);
            }
            const src = ctx.createBufferSource();
            src.buffer = S.buf;
            src.connect(ctx.destination);
            src.start();
        } catch (e) { }
    })()"""
)

/**
 * Plays the solve sound when called, if [enabled]. While enabled it hands the shared samples to the
 * page once and arms the first-tap wake-up; off, it does nothing at all.
 */
@Composable
fun rememberSolveSoundPlayer(enabled: Boolean): () -> Unit {
    LaunchedEffect(enabled) {
        if (enabled) {
            try {
                val tone = SolveTone.samples()
                val array = newFloatArray(tone.size)
                for (i in tone.indices) setFloat(array, i, tone[i])
                keepTone(array, SolveTone.SAMPLE_RATE)
            } catch (_: Throwable) {
            }
        }
    }
    return remember(enabled) {
        if (!enabled) ({}) else ({ try { startTone() } catch (_: Throwable) { } })
    }
}
