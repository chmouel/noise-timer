package com.chmouel.noisetimer

import kotlin.math.max

/**
 * Pure, JVM-testable noise synthesis math, factored out of [NoiseEngine] so
 * it can be unit tested without touching Android framework classes
 * (AudioTrack, SystemClock, etc).
 */

/**
 * Paul Kellet's refined pink noise filter. Holds its own running filter
 * state (b0-b6) across calls, one instance per playback session.
 */
class PinkNoiseFilter {
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0

    /** Feed one white-noise sample in [-1, 1], get the next pink-noise sample out. */
    fun next(white: Double): Double {
        b0 = 0.99886 * b0 + white * 0.0555179
        b1 = 0.99332 * b1 + white * 0.0750759
        b2 = 0.96900 * b2 + white * 0.1538520
        b3 = 0.86650 * b3 + white * 0.3104856
        b4 = 0.55000 * b4 + white * 0.5329522
        b5 = -0.7616 * b5 - white * 0.0168980
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
        b6 = white * 0.115926
        return pink * 0.11
    }
}

/**
 * Brown (red) noise leaky integrator. Returns the new *raw* integrator
 * state (not gain-scaled) — callers keep this as their running state and
 * apply the output gain (see [NoiseEngine]'s x3.5 scale-up) themselves,
 * since scaling before feeding back into the integrator would make it
 * diverge.
 */
fun nextBrownSample(previousBrown: Double, white: Double): Double {
    return (previousBrown + 0.02 * white) / 1.02
}

/**
 * Fade-out gain for the last [fadeSeconds] of a running sleep timer.
 * Returns 1f (no attenuation) when fade-out is disabled, there's no timer
 * running (remainingMillis <= 0 meaning "no timer"), or there's more than
 * [fadeSeconds] left; ramps linearly down to 0f as remainingMillis
 * approaches zero.
 */
fun fadeGainFor(remainingMillis: Long, fadeSeconds: Int, fadeEnabled: Boolean): Float {
    if (!fadeEnabled) return 1f
    val fadeWindowMillis = fadeSeconds * 1000f
    if (remainingMillis <= 0 || remainingMillis > fadeWindowMillis) return 1f
    return max(0f, remainingMillis / fadeWindowMillis)
}
