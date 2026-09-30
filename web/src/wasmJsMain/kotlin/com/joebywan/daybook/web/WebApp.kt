package com.joebywan.daybook.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.platform.dateOverride
import com.joebywan.daybook.ui.DaybookApp
import com.joebywan.daybook.ui.theme.DaybookTheme
import kotlinx.datetime.LocalDate

/**
 * The whole app, as on Android: `DaybookApp` and every screen are app/'s own files, with storage,
 * dates and back navigation supplied by `platform/WebPlatform.kt`.
 *
 * [dateParam] and [tierParam] are the `?date=` and `?tier=` test hooks: the day the app believes
 * it is, and the difficulty the home grid starts on.
 */
@Composable
fun DaybookWebApp(dateParam: String?, tierParam: String?) {
    remember {
        WebStores.init()
        dateOverride = dateParam?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        tierParam?.let { key ->
            WebStores.get(KeyValueStore.LAUNCH).write("difficulty", Difficulty.fromKey(key.uppercase()).name)
        }
    }
    DaybookTheme {
        DaybookApp()
    }
}
