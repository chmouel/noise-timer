package com.chmouel.noisetimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps the process alive in the foreground while noise is playing so
 * playback survives the app being backgrounded, and surfaces play/pause/stop
 * controls in a notification. All the actual audio work happens in
 * [NoiseEngine]; this service only reacts to its state.
 */
class NoiseService : LifecycleService() {

    companion object {
        const val ACTION_PLAY = "com.chmouel.noisetimer.action.PLAY"
        const val ACTION_PAUSE = "com.chmouel.noisetimer.action.PAUSE"
        const val ACTION_STOP = "com.chmouel.noisetimer.action.STOP"
        const val ACTION_SET_NOISE = "com.chmouel.noisetimer.action.SET_NOISE"
        const val ACTION_SET_TIMER = "com.chmouel.noisetimer.action.SET_TIMER"
        const val ACTION_SET_VOLUME = "com.chmouel.noisetimer.action.SET_VOLUME"
        const val ACTION_SET_FADE = "com.chmouel.noisetimer.action.SET_FADE"

        const val EXTRA_NOISE_TYPE = "extra_noise_type"
        const val EXTRA_TIMER_MINUTES = "extra_timer_minutes"
        const val EXTRA_VOLUME = "extra_volume"
        const val EXTRA_FADE = "extra_fade"

        private const val CHANNEL_ID = "noise_playback"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context, action: String, build: Intent.() -> Unit = {}) {
            val intent = Intent(context, NoiseService::class.java).apply {
                this.action = action
                build()
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NoiseEngine.init(applicationContext)
        createNotificationChannel()
        // StateFlow immediately replays its current value to new collectors.
        // That initial value is almost always isPlaying=false (we haven't
        // been told to play yet -- onCreate() always runs before
        // onStartCommand()), so without dropping it the `else` branch below
        // would call stopSelf() right away and tear the service down before
        // onStartCommand(ACTION_PLAY) gets a chance to call
        // startForeground(), which crashes with
        // ForegroundServiceDidNotStartInTimeException. Dropping the replayed
        // value means we only react to genuine isPlaying transitions
        // (play/pause/stop actions, or the timer finishing).
        // The notification content only depends on isPlaying/noiseType, but
        // NoiseEngine.state also ticks remainingMillis every 200s while a
        // timer is running. Without distinctUntilChangedBy we'd re-post an
        // identical notification 5x/second for no visible benefit, wasting
        // battery and spamming the system.
        NoiseEngine.state.drop(1).distinctUntilChangedBy { it.isPlaying to it.noiseType }.onEach { state ->
            if (state.isPlaying) {
                postNotification(buildNotification(state))
            } else {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.launchIn(lifecycleScope)
    }

    /**
     * Wraps [NotificationManagerCompat.notify] with an explicit runtime
     * permission check. On API 33+, POST_NOTIFICATIONS is revocable by the
     * user (they can dismiss the permission dialog MainActivity shows on
     * launch), so this call must not assume it's granted -- if it isn't,
     * we just skip updating the notification; playback itself is
     * unaffected either way.
     */
    private fun postNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> {
                // Must call startForeground synchronously in response to
                // startForegroundService(), before doing anything async.
                val startingState = NoiseEngine.state.value.copy(isPlaying = true)
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(startingState),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
                NoiseEngine.play()
            }
            ACTION_PAUSE -> NoiseEngine.pause()
            ACTION_STOP -> NoiseEngine.stop()
            ACTION_SET_NOISE -> {
                val ordinal = intent.getIntExtra(EXTRA_NOISE_TYPE, 0)
                NoiseEngine.setNoiseType(NoiseType.entries.getOrElse(ordinal) { NoiseType.WHITE })
            }
            ACTION_SET_TIMER -> {
                NoiseEngine.setTimerMinutes(intent.getIntExtra(EXTRA_TIMER_MINUTES, 0))
            }
            ACTION_SET_VOLUME -> {
                NoiseEngine.setVolume(intent.getFloatExtra(EXTRA_VOLUME, 0.6f))
            }
            ACTION_SET_FADE -> {
                NoiseEngine.setFadeOutEnabled(intent.getBooleanExtra(EXTRA_FADE, true))
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, NoiseService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildNotification(state: NoisePlayerState): Notification {
        val label = when (state.noiseType) {
            NoiseType.WHITE -> getString(R.string.noise_white)
            NoiseType.PINK -> getString(R.string.noise_pink)
            NoiseType.BROWN -> getString(R.string.noise_brown)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_playing, label))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.pause), actionIntent(ACTION_PAUSE))
            .addAction(0, getString(R.string.stop), actionIntent(ACTION_STOP))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
