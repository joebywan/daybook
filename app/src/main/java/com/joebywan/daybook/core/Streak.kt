package com.joebywan.daybook.core

import kotlinx.datetime.LocalDate

/**
 * The streak: a habit, not a perfect record (`docs/REWARDS.md`).
 *
 * [current] is the number of days PLAYED in the run that is still alive, not calendar days. It is 0 when
 * there is no live run. [best] is the largest such count in the whole history. [atRisk] is true when the
 * run is alive but today has no play yet and today ending unplayed would end it (a gentle cue for the UI;
 * nothing here says "you will lose it").
 */
data class Streak(val current: Int, val best: Int, val atRisk: Boolean) {
    val alive: Boolean get() = current > 0

    /** Had a streak once, has none now: the home screen says "welcome back" rather than a bare 0. */
    val lapsed: Boolean get() = current == 0 && best > 0
}

private const val WINDOW = 7
private const val MAX_MISSED = 2

/**
 * Pure and platform-free (the web build compiles it); per-puzzle streaks pass that puzzle's played days.
 * Days after [today] are ignored.
 *
 * Rule. A run starts on a played day. On every day d from there, look at the window of the last seven days
 * ending d, but never reaching back before the run's first day. The run is alive while at most two days of
 * that window were missed, which is "5 of the last 7" once the run is seven days old, and "at most 2 missed
 * since the first play" before that. The third miss ends the run; the next play starts a new one. So the
 * longest gap is two days however long the run, because three missed days in a row always sit inside one
 * seven-day window. Played days only ever add; a run's count is its played days.
 *
 * Today is not a miss until the day is over: with no play today the history is judged as of yesterday, and
 * a play today counts.
 */
fun streakOf(played: Set<LocalDate>, today: LocalDate): Streak = walk(played, today).first

/**
 * The missed days a live run absorbed: not played, inside a run, and not the miss that ended it. These are
 * what "5 of the last 7" forgave, for the calendar to mark quietly. Today is never one (it is not a miss
 * yet), and the miss that ended a run is not forgiven, but the run's earlier ones stay so: they were.
 */
fun forgivenDays(played: Set<LocalDate>, today: LocalDate): Set<LocalDate> =
    walk(played, today).second.map { LocalDate.fromEpochDays(it) }.toSet()

private fun walk(played: Set<LocalDate>, today: LocalDate): Pair<Streak, Set<Long>> {
    val t = today.toEpochDays()
    val days = played.map { it.toEpochDays() }.filter { it <= t }.toSet()
    if (days.isEmpty()) return Streak(0, 0, false) to emptySet()

    fun missed(from: Long, to: Long): Int = (from..to).count { it !in days }

    val end = if (t in days) t else t - 1
    var runStart = -1L
    var count = 0
    var best = 0
    val forgiven = mutableSetOf<Long>()
    for (d in days.min()..end) {
        if (d in days) {
            if (runStart < 0) runStart = d
            count++
        }
        if (runStart >= 0 && missed(maxOf(runStart, d - WINDOW + 1), d) > MAX_MISSED) {
            best = maxOf(best, count)
            runStart = -1
            count = 0
        } else if (runStart >= 0 && d !in days) forgiven.add(d)
    }
    best = maxOf(best, count)
    val atRisk = count > 0 && t !in days && missed(maxOf(runStart, t - WINDOW + 1), t) > MAX_MISSED
    return Streak(count, best, atRisk) to forgiven
}
