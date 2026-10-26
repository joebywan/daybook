package com.joebywan.daybook.platform

import kotlin.random.Random
import kotlin.time.Clock

// The part of the platform seam both builds do the same way; each build's own file has the rest.

/** Wall-clock milliseconds, used only to order saved games by how recently they were left. */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** A fresh nonce for a random board: it only has to be new, never ordered (a second random game is a new board). */
fun freshNonce(): Long = Random.nextLong()

/** A clock reading such as 3:07. */
fun formatClock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * How long a board may take before "Setting out..." appears, so a quick one does not flash it. Where generation blocks the
 * thread that paints ([GENERATION_ANIMATES] false), the message has to be painted before it starts, so there is no wait.
 */
val LOADING_MESSAGE_DELAY_MS: Long = if (GENERATION_ANIMATES) 150L else 0L
