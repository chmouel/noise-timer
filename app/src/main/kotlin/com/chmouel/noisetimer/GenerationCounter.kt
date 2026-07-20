package com.chmouel.noisetimer

/**
 * Issues monotonically increasing tokens for work that can be superseded.
 * A worker must check [isCurrent] before it commits effects.
 */
class GenerationCounter {
    @Volatile
    private var value = 0

    @Synchronized
    fun advance(): Int {
        value += 1
        return value
    }

    fun isCurrent(generation: Int): Boolean = value == generation
}
