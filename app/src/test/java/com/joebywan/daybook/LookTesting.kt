package com.joebywan.daybook

import androidx.compose.material3.ColorScheme
import com.joebywan.daybook.ui.theme.Look
import com.joebywan.daybook.ui.theme.lookScheme
import com.joebywan.daybook.ui.theme.useLook

/**
 * Runs [block] once in each look (issue #418: Classic and Arcade), with the look set as the app sets it, so `vivid()`,
 * `BoardHues` and `ArcadeBoard.of` answer for that look; Arcade again afterwards, whatever happens.
 */
internal fun forEachLook(block: (Look) -> Unit) {
    try {
        for (look in Look.entries) {
            useLook(look)
            block(look)
        }
    } finally {
        useLook(Look.ARCADE)
    }
}

/** The look showing's two schemes, named "<look> light" and "<look> dark". */
internal fun lookSchemes(look: Look): List<Pair<String, ColorScheme>> =
    listOf("${look.label} light" to lookScheme(false), "${look.label} dark" to lookScheme(true))
