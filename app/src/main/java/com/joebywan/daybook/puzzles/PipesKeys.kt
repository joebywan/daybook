package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** Space, Enter or R turns the tile under the cursor, as a tap does. */
fun pipesRotateKey(key: Key): Boolean = key == Key.Spacebar || key == Key.Enter || key == Key.NumPadEnter || key == Key.R
