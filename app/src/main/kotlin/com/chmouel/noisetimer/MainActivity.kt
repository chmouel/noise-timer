package com.chmouel.noisetimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chmouel.noisetimer.ui.theme.NoiseTimerTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NoiseEngine.init(applicationContext)
        maybeRequestNotificationPermission()

        setContent {
            NoiseTimerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoiseTimerScreen()
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private val timerPresetsMinutes = listOf(0, 5, 15, 30, 45, 60, 90, 120)

@Composable
fun NoiseTimerScreen() {
    val context = LocalContext.current
    val state by NoiseEngine.state.collectAsStateWithLifecycle()
    var customMinutesText by remember { mutableStateOf("") }

    // Play/Pause are the only actions that need to go through the service,
    // since starting/stopping the foreground service (and its lifetime) is
    // the service's whole job.
    fun sendPlaybackAction(action: String) {
        NoiseService.start(context, action)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NoiseType.entries.forEach { type ->
                FilterChip(
                    selected = state.noiseType == type,
                    onClick = { NoiseEngine.setNoiseType(type) },
                    label = { Text(type.label()) },
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        FilledIconButton(
            onClick = {
                if (state.isPlaying) {
                    sendPlaybackAction(NoiseService.ACTION_PAUSE)
                } else {
                    sendPlaybackAction(NoiseService.ACTION_PLAY)
                }
            },
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(Modifier.height(40.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.volume), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.volume,
                onValueChange = { v -> NoiseEngine.setVolume(v) },
                modifier = Modifier.semantics {
                    contentDescription = "Volume"
                },
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                timerPresetsMinutes.forEach { minutes ->
                    FilterChip(
                        selected = state.timerMinutes == minutes,
                        onClick = { NoiseEngine.setTimerMinutes(minutes) },
                        label = {
                            Text(if (minutes == 0) stringResource(R.string.timer_off) else "${minutes}m")
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customMinutesText,
                    onValueChange = { customMinutesText = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.custom_minutes_hint)) },
                    singleLine = true,
                    modifier = Modifier.width(160.dp),
                )
                Button(onClick = {
                    val minutes = customMinutesText.toIntOrNull()
                    if (minutes != null && minutes > 0) {
                        NoiseEngine.setTimerMinutes(minutes)
                    }
                }) {
                    Text(stringResource(R.string.set))
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.toggleable(
                    value = state.fadeOutEnabled,
                    role = Role.Checkbox,
                    onValueChange = { checked -> NoiseEngine.setFadeOutEnabled(checked) },
                ),
            ) {
                Checkbox(checked = state.fadeOutEnabled, onCheckedChange = null)
                Text(stringResource(R.string.fade_out_label))
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (state.isPlaying && state.timerMinutes > 0) {
                    formatMillis(state.remainingMillis)
                } else {
                    stringResource(R.string.no_timer)
                },
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun NoiseType.label(): String = when (this) {
    NoiseType.WHITE -> stringResource(R.string.noise_white)
    NoiseType.PINK -> stringResource(R.string.noise_pink)
    NoiseType.BROWN -> stringResource(R.string.noise_brown)
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
