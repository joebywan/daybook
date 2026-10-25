package com.joebywan.daybook.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.platform.currentDate
import com.joebywan.daybook.platform.dateOverride
import com.joebywan.daybook.ui.DaybookApp
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.home.LaunchMode
import kotlinx.datetime.LocalDate

/**
 * The whole app, as on Android: `DaybookApp` and every screen are app/'s own files, with storage,
 * dates, back navigation and board generation supplied by `platform/WebPlatform.kt`.
 *
 * The three parameters are the page's query: `?date=` is the day the app believes it is, `?tier=`
 * the difficulty the home grid is set to (it persists, as a tap would), and `?puzzle=<id>` opens
 * that puzzle's daily board at that difficulty instead of the home grid.
 *
 * For the render harness: `?mode=levels` (or `random`) opens home in that mode, `?screen=levels` the levels
 * list, `&level=N` with it that level's boards, and `?puzzle=<id>&level=N` that board of level N;
 * `?screen=archive&puzzle=<id>` that puzzle's archive, with `&day=` that day selected; `?screen=stats` the statistics
 * (`&share` with it opens the day's share preview, once a daily is solved); `?screen=techniques` the techniques page.
 */
@Composable
fun DaybookWebApp(dateParam: String?, tierParam: String?, puzzleParam: String?, params: Map<String, String> = emptyMap()) {
    val start = remember {
        WebStores.init()
        dateOverride = dateParam?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val launch = WebStores.get(KeyValueStore.LAUNCH)
        tierParam?.let { key -> launch.write("difficulty", Difficulty.fromKey(key.uppercase()).name) }
        val tier = Difficulty.fromKey(launch.read("difficulty").orEmpty())
        val level = params["level"]?.toIntOrNull()?.takeIf { it >= 1 }
        when {
            params["screen"] == "levels" -> Route.Levels(level)
            params["screen"] == "stats" -> Route.Stats
            params["screen"] == "techniques" -> Route.Techniques
            params["screen"] == "archive" && puzzleParam != null ->
                Route.Archive(puzzleParam, params["day"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            else -> puzzleParam?.let(PuzzleRegistry::byId)?.let {
                if (level != null) Route.Play(it.id, tier, null, level = level) else Route.Play(it.id, tier, currentDate())
            } ?: Route.Home
        }
    }
    val mode = LaunchMode.entries.firstOrNull { it.name.equals(params["mode"], ignoreCase = true) } ?: LaunchMode.DAILY
    DaybookApp(startAt = start, startMode = mode)
}
