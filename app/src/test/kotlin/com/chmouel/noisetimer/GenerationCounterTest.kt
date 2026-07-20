package com.chmouel.noisetimer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationCounterTest {

    @Test
    fun `starting a new playback invalidates an audio thread from the previous session`() {
        val playbackGenerations = GenerationCounter()
        val firstPlayback = playbackGenerations.advance()

        val secondPlayback = playbackGenerations.advance()

        assertFalse(playbackGenerations.isCurrent(firstPlayback))
        assertTrue(playbackGenerations.isCurrent(secondPlayback))
    }

    @Test
    fun `resetting a timer invalidates expiry work from the previous timer`() {
        val timerGenerations = GenerationCounter()
        val originalTimer = timerGenerations.advance()

        val replacementTimer = timerGenerations.advance()

        assertFalse(timerGenerations.isCurrent(originalTimer))
        assertTrue(timerGenerations.isCurrent(replacementTimer))
    }

    @Test
    fun `stopping playback invalidates pending audio and timer work`() {
        val playbackGenerations = GenerationCounter()
        val timerGenerations = GenerationCounter()
        val activePlayback = playbackGenerations.advance()
        val activeTimer = timerGenerations.advance()

        playbackGenerations.advance()
        timerGenerations.advance()

        assertFalse(playbackGenerations.isCurrent(activePlayback))
        assertFalse(timerGenerations.isCurrent(activeTimer))
    }
}
