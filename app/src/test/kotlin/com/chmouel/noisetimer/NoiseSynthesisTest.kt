package com.chmouel.noisetimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseSynthesisTest {

    // --- PinkNoiseFilter --------------------------------------------------

    @Test
    fun `pink filter stays silent when fed silence`() {
        val filter = PinkNoiseFilter()
        repeat(100) {
            assertEquals(0.0, filter.next(0.0), 0.0)
        }
    }

    @Test
    fun `pink filter output stays bounded for full-range white noise input`() {
        val filter = PinkNoiseFilter()
        val random = java.util.Random(42)
        for (i in 0 until 100_000) {
            val white = random.nextDouble() * 2.0 - 1.0
            val pink = filter.next(white)
            assertTrue("pink sample $pink out of expected bounds at iteration $i", pink in -1.5..1.5)
            assertTrue("pink sample was NaN at iteration $i", !pink.isNaN())
        }
    }

    @Test
    fun `pink filter is deterministic for a given input sequence`() {
        val inputs = doubleArrayOf(1.0, -1.0, 0.5, -0.5, 0.25, 0.0, -0.25)
        val filterA = PinkNoiseFilter()
        val filterB = PinkNoiseFilter()
        val outputsA = inputs.map { filterA.next(it) }
        val outputsB = inputs.map { filterB.next(it) }
        assertEquals(outputsA, outputsB)
    }

    // --- nextBrownSample ----------------------------------------------------

    @Test
    fun `brown noise integrator state decays toward zero when fed silence`() {
        var brown = 1.0
        repeat(500) {
            brown = nextBrownSample(brown, 0.0)
        }
        assertTrue("expected brown to decay close to zero, was $brown", kotlin.math.abs(brown) < 0.01)
    }

    @Test
    fun `brown noise integrator state stays bounded for full-range white noise input`() {
        var brown = 0.0
        val random = java.util.Random(7)
        for (i in 0 until 100_000) {
            val white = random.nextDouble() * 2.0 - 1.0
            brown = nextBrownSample(brown, white)
            assertTrue("brown state $brown out of expected bounds at iteration $i", brown in -2.0..2.0)
            assertTrue("brown state was NaN at iteration $i", !brown.isNaN())
        }
    }

    @Test
    fun `brown noise state does not diverge when fed back in across many iterations`() {
        // Regression test: NoiseEngine.runGeneratorLoop keeps `brown` as the
        // raw, unscaled integrator state across iterations and applies the
        // x3.5 output gain separately when writing the sample - it must
        // never assign the scaled value back into the state, or the
        // integrator diverges exponentially.
        var brown = 0.0
        val random = java.util.Random(3)
        for (i in 0 until 10_000) {
            val white = random.nextDouble() * 2.0 - 1.0
            brown = nextBrownSample(brown, white)
        }
        assertTrue("brown state diverged to $brown", kotlin.math.abs(brown) < 2.0)
    }

    // --- fadeGainFor --------------------------------------------------------

    @Test
    fun `fade gain is always 1 when fade-out is disabled`() {
        assertEquals(1f, fadeGainFor(remainingMillis = 500, fadeSeconds = 20, fadeEnabled = false), 0f)
        assertEquals(1f, fadeGainFor(remainingMillis = 0, fadeSeconds = 20, fadeEnabled = false), 0f)
        assertEquals(1f, fadeGainFor(remainingMillis = 999_999, fadeSeconds = 20, fadeEnabled = false), 0f)
    }

    @Test
    fun `fade gain is 1 when more time remains than the fade window`() {
        val gain = fadeGainFor(remainingMillis = 25_000, fadeSeconds = 20, fadeEnabled = true)
        assertEquals(1f, gain, 0f)
    }

    @Test
    fun `fade gain is 1 when remaining time equals the fade window`() {
        val gain = fadeGainFor(remainingMillis = 20_000, fadeSeconds = 20, fadeEnabled = true)
        assertEquals(1f, gain, 0.0001f)
    }

    @Test
    fun `fade gain ramps down linearly inside the fade window`() {
        val gain = fadeGainFor(remainingMillis = 10_000, fadeSeconds = 20, fadeEnabled = true)
        assertEquals(0.5f, gain, 0.0001f)
    }

    @Test
    fun `fade gain approaches zero near the end of the timer`() {
        val gain = fadeGainFor(remainingMillis = 1, fadeSeconds = 20, fadeEnabled = true)
        assertTrue(gain in 0f..0.001f)
    }

    @Test
    fun `fade gain treats zero or negative remaining time as no timer`() {
        // Matches production usage: the timer loop breaks out and calls
        // pause() before this would ever be hit with remaining <= 0, so the
        // pure function's contract is to no-op (full volume) rather than
        // guess at "just finished" behavior.
        assertEquals(1f, fadeGainFor(remainingMillis = 0, fadeSeconds = 20, fadeEnabled = true), 0f)
        assertEquals(1f, fadeGainFor(remainingMillis = -100, fadeSeconds = 20, fadeEnabled = true), 0f)
    }
}
