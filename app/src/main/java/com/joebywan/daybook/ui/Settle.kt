package com.joebywan.daybook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How long a break must last before it shows, so a player passing through an illegal step is not shouted at. */
const val SETTLE_MILLIS = 1000L

/**
 * [now] as it was a second after [change] last changed (the next change cancels the wait and starts
 * it over); [now] itself when [reset] changes, so a board that loads broken shows it at once. A board
 * shows a break only where both agree, so a new break waits a second and a repair shows at once.
 */
@Composable
fun <T> rememberSettled(now: T, reset: Any?, change: Any?): T {
    var settled by remember(reset) { mutableStateOf(now) }
    LaunchedEffect(change) {
        delay(SETTLE_MILLIS)
        settled = now
    }
    return settled
}

/** The flags of [now] that have also held for a second ([rememberSettled]). */
@Composable
fun rememberSettledFlags(now: BooleanArray, reset: Any?, change: Any?): BooleanArray {
    val settled = rememberSettled(now, reset, change)
    return BooleanArray(now.size) { now[it] && settled.getOrElse(it) { false } }
}
