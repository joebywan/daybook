package com.joebywan.daybook

import com.joebywan.daybook.puzzles.ChessPosition
import kotlin.math.abs

/** The first four FEN fields of [this], the inverse of [ChessPosition.fromFen]. Only the tests need it. */
fun ChessPosition.toFen(): String {
    val sb = StringBuilder()
    for (r in 7 downTo 0) {
        var empty = 0
        for (f in 0..7) {
            val v = pieceAt(r * 8 + f)
            if (v == 0) { empty++; continue }
            if (empty > 0) { sb.append(empty); empty = 0 }
            val c = ChessPosition.LETTERS[abs(v)]
            sb.append(if (v > 0) c else c.lowercaseChar())
        }
        if (empty > 0) sb.append(empty)
        if (r > 0) sb.append('/')
    }
    sb.append(if (whiteToMove) " w " else " b ")
    if (castling == 0) sb.append('-') else {
        if (castling and 1 != 0) sb.append('K')
        if (castling and 2 != 0) sb.append('Q')
        if (castling and 4 != 0) sb.append('k')
        if (castling and 8 != 0) sb.append('q')
    }
    sb.append(' ')
    if (ep < 0) sb.append('-') else sb.append(('a' + ep % 8)).append(('1' + ep / 8))
    return sb.toString()
}
