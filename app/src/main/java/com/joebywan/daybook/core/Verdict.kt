package com.joebywan.daybook.core

/**
 * What an exhaustive count found. Only [ExactlyOne] carries an answer, so neither "two or more" nor a search that
 * gave up can pass for one. A count with a budget needs its own type with a truncated case (Lits, Sand).
 */
sealed interface Verdict<out T> {
    data object None : Verdict<Nothing>
    data class ExactlyOne<T>(val value: T) : Verdict<T>
    data object Several : Verdict<Nothing>

    companion object {
        /** The verdict of a count that reached [found]; [answer] is read only when that is exactly one. */
        inline fun <T> of(found: Int, answer: () -> T): Verdict<T> = when (found) {
            0 -> None
            1 -> ExactlyOne(answer())
            else -> Several
        }
    }
}
