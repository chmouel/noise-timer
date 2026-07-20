package com.chmouel.noisetimer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random

enum class NoiseType { WHITE, PINK, BROWN }

data class NoisePlayerState(
    val isPlaying: Boolean = false,
    val noiseType: NoiseType = NoiseType.WHITE,
    val volume: Float = 0.6f,
    val timerMinutes: Int = 0,
    val fadeOutEnabled: Boolean = true,
    val remainingMillis: Long = 0L,
)

/**
 * Singleton real-time noise generator.
 *
 * Samples are synthesized on a dedicated audio thread and streamed straight
 * into an [AudioTrack] in [AudioTrack.MODE_STREAM] - there is no pre-baked
 * loop, so playback is endless and click-free by construction. The engine
 * is independent of any Activity/Service lifecycle: [MainActivity] observes
 * [state] directly, and [NoiseService] only exists to keep the process
 * alive (with a notification) while audio is playing.
 */
object NoiseEngine {

    private const val PREFS_NAME = "noise_timer_prefs"
    private const val KEY_NOISE_TYPE = "noise_type"
    private const val KEY_VOLUME = "volume"
    private const val KEY_TIMER_MINUTES = "timer_minutes"
    private const val KEY_FADE_OUT = "fade_out"

    private const val FADE_SECONDS = 20

    private val _state = MutableStateFlow(NoisePlayerState())
    val state: StateFlow<NoisePlayerState> = _state

    private var audioThread: Thread? = null
    @Volatile private var running = false

    @Volatile private var masterVolume = 0.6f
    @Volatile private var fadeGain = 1f
    @Volatile private var noiseType = NoiseType.WHITE

    private var timerJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Default)

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = prefs()
        val savedType = NoiseType.entries.getOrElse(prefs.getInt(KEY_NOISE_TYPE, 0)) { NoiseType.WHITE }
        val savedVolume = prefs.getFloat(KEY_VOLUME, 0.6f)
        val savedTimer = prefs.getInt(KEY_TIMER_MINUTES, 0)
        val savedFade = prefs.getBoolean(KEY_FADE_OUT, true)
        noiseType = savedType
        masterVolume = savedVolume
        _state.update {
            it.copy(
                noiseType = savedType,
                volume = savedVolume,
                timerMinutes = savedTimer,
                fadeOutEnabled = savedFade,
            )
        }
    }

    private fun prefs() = appContext!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setNoiseType(type: NoiseType) {
        noiseType = type
        _state.update { it.copy(noiseType = type) }
        prefs().edit { putInt(KEY_NOISE_TYPE, type.ordinal) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        masterVolume = clamped
        _state.update { it.copy(volume = clamped) }
        prefs().edit { putFloat(KEY_VOLUME, clamped) }
    }

    fun setFadeOutEnabled(enabled: Boolean) {
        _state.update { it.copy(fadeOutEnabled = enabled) }
        prefs().edit { putBoolean(KEY_FADE_OUT, enabled) }
    }

    fun setTimerMinutes(minutes: Int) {
        _state.update { it.copy(timerMinutes = minutes) }
        prefs().edit { putInt(KEY_TIMER_MINUTES, minutes) }
        if (_state.value.isPlaying) {
            restartTimer(minutes)
        }
    }

    fun play() {
        if (running) return
        running = true
        fadeGain = 1f
        startAudioThread()
        _state.update { it.copy(isPlaying = true) }
        restartTimer(_state.value.timerMinutes)
    }

    fun pause() {
        stopAudioThread()
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(isPlaying = false, remainingMillis = 0L) }
    }

    fun stop() = pause()

    private fun restartTimer(minutes: Int) {
        timerJob?.cancel()
        fadeGain = 1f
        if (minutes <= 0) {
            _state.update { it.copy(remainingMillis = 0L) }
            return
        }
        val endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        timerJob = engineScope.launch {
            while (isActive) {
                val remaining = endAt - SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    _state.update { it.copy(remainingMillis = 0L) }
                    pause()
                    break
                }
                _state.update { it.copy(remainingMillis = remaining) }
                fadeGain = if (_state.value.fadeOutEnabled && remaining <= FADE_SECONDS * 1000L) {
                    max(0f, remaining / (FADE_SECONDS * 1000f))
                } else {
                    1f
                }
                delay(200)
            }
        }
    }

    private fun startAudioThread() {
        // All AudioTrack setup (including the native sample-rate/buffer-size
        // queries and AudioTrack.Builder().build()) must happen off the
        // caller's thread. On some devices/emulators these calls can block
        // for several seconds while the audio HAL/policy service connects,
        // and this function is invoked synchronously from the foreground
        // service's onStartCommand() (main thread) right after
        // startForeground() — blocking there risks ANRs and, in the worst
        // case, can delay things enough to trip Android's
        // "did not call startForeground in time" watchdog on a subsequent
        // call. Doing everything inside the background thread keeps the
        // caller non-blocking.
        audioThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
                .takeIf { it > 0 } ?: 44100
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).let { if (it > 0) it else 4096 }
            val bufferSize = minBuf * 2

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                running = false
                return@Thread
            }

            runGeneratorLoop(track, bufferSize)
        }.apply { start() }
    }

    private fun stopAudioThread() {
        running = false
        audioThread?.join(500)
        audioThread = null
    }

    private fun runGeneratorLoop(track: AudioTrack, bufferSize: Int) {
        val framesPerBuffer = bufferSize / 2
        val shortBuffer = ShortArray(framesPerBuffer)
        // Pink noise filter state (Paul Kellet's refined method).
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var b3 = 0.0
        var b4 = 0.0
        var b5 = 0.0
        var b6 = 0.0
        // Brown (red) noise running integrator state.
        var brown = 0.0

        track.play()
        try {
            while (running) {
                val type = noiseType
                for (i in 0 until framesPerBuffer) {
                    val white = Random.nextDouble(-1.0, 1.0)
                    val sample = when (type) {
                        NoiseType.WHITE -> white
                        NoiseType.PINK -> {
                            b0 = 0.99886 * b0 + white * 0.0555179
                            b1 = 0.99332 * b1 + white * 0.0750759
                            b2 = 0.96900 * b2 + white * 0.1538520
                            b3 = 0.86650 * b3 + white * 0.3104856
                            b4 = 0.55000 * b4 + white * 0.5329522
                            b5 = -0.7616 * b5 - white * 0.0168980
                            val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                            b6 = white * 0.115926
                            pink * 0.11
                        }
                        NoiseType.BROWN -> {
                            brown = (brown + 0.02 * white) / 1.02
                            brown * 3.5
                        }
                    }
                    val gain = masterVolume * fadeGain
                    val clamped = (sample * gain).coerceIn(-1.0, 1.0)
                    shortBuffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
                }
                track.write(shortBuffer, 0, framesPerBuffer)
            }
        } finally {
            track.stop()
            track.release()
        }
    }
}
