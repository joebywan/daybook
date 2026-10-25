package com.joebywan.daybook.core

/** Calls [action] with the index of every set bit of this mask, lowest first. */
internal inline fun Long.forEachBit(action: (Int) -> Unit) {
    var rest = this
    while (rest != 0L) {
        action(rest.countTrailingZeroBits())
        rest = rest and (rest - 1)
    }
}
