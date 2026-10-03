package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** Space or Enter picks (or drops) the card under the cursor, as a tap does. */
fun setsPickKey(key: Key): Boolean = key == Key.Spacebar || key == Key.Enter || key == Key.NumPadEnter
