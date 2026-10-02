package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** Space, Enter or X shades the square under the cursor, or clears it, as a tap does. */
fun litsToggleKey(key: Key): Boolean = key == Key.Spacebar || key == Key.Enter || key == Key.NumPadEnter || key == Key.X

/**
 * Shift+arrow: the square [from] has just been left, [to] is where the cursor landed. Lays [from]'s
 * state (shaded or not) on [to], as a drag from [from] would, or null if [to] already matches.
 */
fun LitsState.lay(from: Int, to: Int): LitsState? = paint(listOf(to), shaded[from]).takeIf { it !== this }
