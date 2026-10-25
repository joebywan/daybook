package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.isConfirmKey

/** Space or Enter picks (or drops) the card under the cursor, as a tap does. */
fun setsPickKey(key: Key): Boolean = isConfirmKey(key)
