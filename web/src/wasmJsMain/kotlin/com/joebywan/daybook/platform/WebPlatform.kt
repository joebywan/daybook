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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.web.Backup
import com.joebywan.daybook.web.WebStores
import kotlinx.datetime.LocalDate
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

private fun historyBack(): Unit = js("history.back()")

private fun onPopState(handler: () -> Unit): Unit = js("window.addEventListener('popstate', () => handler())")

/**
 * The browser's back button (and Safari's edge swipe) standing in for Android's.
 *
 * While a screen other than Home is showing, one history entry of ours sits on top of the page's
 * own; back pops it and [onBack] runs. Leaving by an in-app control takes that entry back off, so
 * back from Home still leaves the page as it would close the app on a phone. The pop that removal
 * causes is skipped rather than treated as the player's.
 */
private object WebHistory {
    private var pushed = false
    private var skipPops = 0
    var handler: (() -> Unit)? = null

    init {
        onPopState {
            if (skipPops > 0) {
                skipPops--
            } else if (pushed) {
                pushed = false
                handler?.invoke()
            }
        }
    }

    fun enable() {
        if (!pushed) {
            pushHistoryEntry()
            pushed = true
        }
    }

    fun disable() {
        if (pushed) {
            pushed = false
            skipPops++
            historyBack()
        }
    }
}

@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val latest by rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (enabled) {
            WebHistory.handler = { latest() }
            WebHistory.enable()
        }
        onDispose {
            if (enabled) {
                WebHistory.handler = null
                WebHistory.disable()
            }
        }
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
