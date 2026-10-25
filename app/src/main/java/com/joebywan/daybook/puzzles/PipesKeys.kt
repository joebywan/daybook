package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.isConfirmKey

/** Space, Enter or R turns the tile under the cursor, as a tap does. */
fun pipesRotateKey(key: Key): Boolean = isConfirmKey(key) || key == Key.R
