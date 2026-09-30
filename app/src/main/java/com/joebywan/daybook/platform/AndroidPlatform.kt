package com.joebywan.daybook.platform

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.joebywan.daybook.data.DataStoreKeyValueStore
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.data.preferencesFile
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.format.DateTimeFormatter

// The Android half of the platform seam. The shared screens, store and navigation call these
// functions by name; the web build has a file of the same functions, in the same package, at
// web/src/wasmJsMain/kotlin/com/joebywan/daybook/platform/WebPlatform.kt. Keep the two in step —
// a signature added here and not there is a web compile error, which is the point.
//
// Every body here is what the shared code did inline before the web build existed, so Android
// behaves exactly as it did.

/** Today on the device's own calendar and time zone. */
fun currentDate(): LocalDate = java.time.LocalDate.now().toKotlinLocalDate()

/** Wall-clock milliseconds, used only to order saved games by how recently they were left. */
fun currentTimeMillis(): Long = System.currentTimeMillis()

/** A fresh nonce for a random board: a second random game must be a new board. */
fun freshNonce(): Long = System.nanoTime()

private val formatters = HashMap<String, DateTimeFormatter>()

/** [date] in a `DateTimeFormatter` [pattern], in the device's locale. */
fun formatDate(date: LocalDate, pattern: String): String =
    date.toJavaLocalDate().format(formatters.getOrPut(pattern) { DateTimeFormatter.ofPattern(pattern) })

/** A clock reading such as 3:07. */
fun formatClock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** The DataStore file named [name], behind the shared [KeyValueStore] interface. */
@Composable
fun rememberKeyValueStore(name: String): KeyValueStore {
    val context = LocalContext.current
    return remember(name) { DataStoreKeyValueStore(context.preferencesFile(name)) }
}

/** The system back button. */
@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

/**
 * An on-screen way back, for platforms without a system back button. Android has one, and the
 * screens deliberately carry no back arrow of their own, so this draws nothing.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun BackButton(onBack: () -> Unit) = Unit

/** Status-bar icon tint to match the theme. Bar colours come from enableEdgeToEdge(). */
@Composable
fun SystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
}

/** Whether the stats screen offers a backup export and import. Android has its own backup. */
const val OFFERS_BACKUP: Boolean = false

/** The backup controls on the stats screen; never shown on Android (see [OFFERS_BACKUP]). */
@Composable
fun BackupControls() = Unit
