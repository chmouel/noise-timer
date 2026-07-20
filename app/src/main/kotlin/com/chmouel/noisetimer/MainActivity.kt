package com.chmouel.noisetimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chmouel.noisetimer.ui.theme.AccentBrown
import com.chmouel.noisetimer.ui.theme.AccentPink
import com.chmouel.noisetimer.ui.theme.AccentWhite
import com.chmouel.noisetimer.ui.theme.NoiseTimerTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

private fun NoiseType.accent(): Color = when (this) {
    NoiseType.WHITE -> AccentWhite
    NoiseType.PINK -> AccentPink
    NoiseType.BROWN -> AccentBrown
}

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

    val accent by animateColorAsState(state.noiseType.accent(), tween(600), label = "accent")
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = if (state.isPlaying) 0.20f else 0.10f),
                    0.45f to background,
                    1f to background,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NoiseType.entries.forEach { type ->
                    NoiseCard(
                        type = type,
                        selected = state.noiseType == type,
                        onClick = { NoiseEngine.setNoiseType(type) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(44.dp))

            PlayButton(
                isPlaying = state.isPlaying,
                accent = accent,
                onClick = {
                    if (state.isPlaying) {
                        sendPlaybackAction(NoiseService.ACTION_PAUSE)
                    } else {
                        sendPlaybackAction(NoiseService.ACTION_PLAY)
                    }
                },
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = if (state.isPlaying) {
                    stringResource(R.string.now_playing)
                } else {
                    stringResource(R.string.paused_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.volume,
                    onValueChange = { v -> NoiseEngine.setVolume(v) },
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Volume" },
                )
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(28.dp))

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.sleep_timer).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                    Spacer(Modifier.height(14.dp))

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
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.22f),
                                    selectedLabelColor = accent,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = customMinutesText,
                            onValueChange = { customMinutesText = it.filter(Char::isDigit).take(3) },
                            label = { Text(stringResource(R.string.custom_minutes_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                val minutes = customMinutesText.toIntOrNull()
                                if (minutes != null && minutes > 0) {
                                    NoiseEngine.setTimerMinutes(minutes)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                        ) {
                            Text(stringResource(R.string.set))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = state.fadeOutEnabled,
                                role = Role.Switch,
                                onValueChange = { checked -> NoiseEngine.setFadeOutEnabled(checked) },
                            ),
                    ) {
                        Text(stringResource(R.string.fade_out_label))
                        Switch(
                            checked = state.fadeOutEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedTrackColor = accent),
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            if (state.isPlaying && state.timerMinutes > 0) {
                Text(
                    text = formatMillis(state.remainingMillis),
                    style = MaterialTheme.typography.displayLarge,
                    color = accent,
                )
            } else {
                Text(
                    text = stringResource(R.string.no_timer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NoiseCard(
    type: NoiseType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = type.accent()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.7f)) else null,
        modifier = modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.height(10.dp))
            Text(type.label(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                type.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlayButton(
    isPlaying: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        if (isPlaying) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
                label = "pulseProgress",
            )
            // Two expanding, fading rings behind the button.
            listOf(0f, 0.5f).forEach { offset ->
                val p = (progress + offset) % 1f
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            val scale = 1f + p * 0.55f
                            scaleX = scale
                            scaleY = scale
                            alpha = (1f - p) * 0.35f
                        }
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(104.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accent,
                contentColor = Color(0xFF10121A),
            ),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                modifier = Modifier.size(52.dp),
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

@Composable
private fun NoiseType.description(): String = when (this) {
    NoiseType.WHITE -> stringResource(R.string.noise_white_desc)
    NoiseType.PINK -> stringResource(R.string.noise_pink_desc)
    NoiseType.BROWN -> stringResource(R.string.noise_brown_desc)
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
