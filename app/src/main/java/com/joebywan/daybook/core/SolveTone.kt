package com.joebywan.daybook.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The sound of a solved puzzle: a two-note marimba pluck, G5 then C6, synthesised rather than
 * shipped as audio. The owner chose it from five candidates; the numbers below were fitted to that
 * candidate's rendering (residual under 0.5% rms), so this is the sound they heard.
 *
 * Pure and platform-free, so Android (`AudioTrack`) and the web (Web Audio) play identical samples.
 * Each note is a fundamental plus a partial at four times its frequency (the bar's second mode),
 * both decaying exponentially, the upper one about three times faster; a 2 ms attack ramp keeps the
 * onset from clicking.
 */
object SolveTone {
    const val SAMPLE_RATE = 44_100

    /** 0.81 s: the last note stops at 0.72 s and the rest is silence, so the track ends clean. */
    const val LENGTH = 35_720

    private const val G5 = 783.99
    private const val C6 = 1046.50

    // Decay rates in 1/s of the fundamental and of the 4x partial; the partial's level relative to it.
    private const val DECAY = 7.05
    private const val UPPER_DECAY = 22.0
    private const val UPPER_LEVEL = 0.348
    private const val ATTACK = 0.002
    private const val RELEASE = 0.002

    private class Note(val freq: Double, val start: Double, val length: Double, val gain: Double)

    // The peak lands near 0.32 of full scale: quiet enough not to startle, audible on a phone speaker.
    private val notes = listOf(
        Note(G5, start = 0.0, length = 0.50, gain = 0.218),
        Note(C6, start = 0.11, length = 0.61, gain = 0.184),
    )

    /** Mono samples in -1..1 at [SAMPLE_RATE], [LENGTH] of them. */
    fun samples(): FloatArray {
        val out = FloatArray(LENGTH)
        for (n in notes) {
            val first = (n.start * SAMPLE_RATE).toInt()
            val last = min(LENGTH, ((n.start + n.length) * SAMPLE_RATE).toInt())
            for (i in first until last) {
                val u = i.toDouble() / SAMPLE_RATE - n.start
                val body = exp(-DECAY * u) * sin(2 * PI * n.freq * u) +
                    UPPER_LEVEL * exp(-UPPER_DECAY * u) * sin(2 * PI * n.freq * 4 * u)
                val fadeIn = min(u / ATTACK, 1.0)
                val fadeOut = min((n.length - u) / RELEASE, 1.0)
                out[i] += (n.gain * fadeIn * fadeOut * body).toFloat()
            }
        }
        return out
    }

    /** The same samples as signed 16-bit PCM, for `AudioTrack`. */
    fun pcm16(): ShortArray {
        val s = samples()
        return ShortArray(s.size) { (s[it] * 32767f).toInt().toShort() }
    }
}
