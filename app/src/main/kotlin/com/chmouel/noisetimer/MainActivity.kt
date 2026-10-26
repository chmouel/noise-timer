package com.chmouel.noisetimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chmouel.noisetimer.ui.theme.AccentBrown
import com.chmouel.noisetimer.ui.theme.AccentPink
import com.chmouel.noisetimer.ui.theme.AccentWhite
import com.chmouel.noisetimer.ui.theme.NoiseTimerTheme
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        NoiseEngine.init(applicationContext)
        maybeRequestNotificationPermission()

        setContent {
            NoiseTimerTheme {
                var showAbout by rememberSaveable { mutableStateOf(false) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (showAbout) {
                        AboutScreen(onBack = { showAbout = false })
                    } else {
                        NoiseTimerScreen(onOpenAbout = { showAbout = true })
                    }
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
private const val CUSTOM_TIMER = -1

private fun NoiseType.accent(): Color = when (this) {
    NoiseType.WHITE -> AccentWhite
    NoiseType.PINK -> AccentPink
    NoiseType.BROWN -> AccentBrown
}

@Composable
fun NoiseTimerScreen(onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val state by NoiseEngine.state.collectAsStateWithLifecycle()
    var showTimerSheet by rememberSaveable { mutableStateOf(false) }
    val accent = state.noiseType.accent()
    val volumeLabel = stringResource(R.string.volume)
    val volumePercent = NumberFormat.getPercentInstance().format(state.volume)
    val editTimerLabel = stringResource(R.string.edit_timer)
    val timerMillis = if (state.isPlaying) state.remainingMillis else state.timerMinutes * 60_000L
    val timerDescription = if (state.timerMinutes == 0) {
        stringResource(R.string.timer_status_off)
    } else {
        stringResource(
            if (state.isPlaying) R.string.timer_remaining else R.string.timer_duration,
            spokenDuration(timerMillis),
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        val contentWidth = minOf(maxWidth, 480.dp) - 48.dp
        val heroSize = minOf(contentWidth * 0.56f, maxHeight * 0.26f)
            .coerceIn(164.dp, 220.dp)
            .coerceAtMost(contentWidth)
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                    IconButton(
                        onClick = onOpenAbout,
                        modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.about_open),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup().padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        NoiseType.entries.forEach { type ->
                            NoiseChoice(
                                type = type,
                                selected = state.noiseType == type,
                                onClick = { NoiseEngine.setNoiseType(type) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = {
                        // Only playback actions start the foreground service
                        NoiseService.start(
                            context,
                            if (state.isPlaying) NoiseService.ACTION_PAUSE else NoiseService.ACTION_PLAY,
                        )
                    },
                    modifier = Modifier.size(heroSize),
                    shape = CircleShape,
                    contentPadding = PaddingValues(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size((heroSize * 0.28f).coerceAtLeast(48.dp)),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Surface(
                    onClick = { showTimerSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().semantics {
                        onClick(label = editTimerLabel, action = null)
                    },
                ) {
                    Column(
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.sleep_timer),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (state.timerMinutes == 0) {
                                stringResource(R.string.timer_status_off)
                            } else {
                                formatTimerMillis(timerMillis)
                            },
                            style = when {
                                state.timerMinutes == 0 -> MaterialTheme.typography.headlineMedium
                                timerMillis >= 3_600_000L -> MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp)
                                else -> MaterialTheme.typography.displayLarge
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = timerDescription },
                        )
                    }
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(volumeLabel, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        volumePercent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = state.volume,
                    onValueChange = NoiseEngine::setVolume,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        contentDescription = volumeLabel
                        stateDescription = volumePercent
                    },
                )
            }
        }
    }

    if (showTimerSheet) {
        TimerSheet(
            state = state,
            onDismiss = { showTimerSheet = false },
            onSetTimer = { minutes, fade ->
                NoiseEngine.setFadeOutEnabled(fade)
                // Setting the duration restarts the timer, even if it is unchanged
                if (minutes != NoiseEngine.state.value.timerMinutes) {
                    NoiseEngine.setTimerMinutes(minutes)
                }
                showTimerSheet = false
            },
        )
    }
}

@Composable
private fun NoiseChoice(
    type: NoiseType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = type.accent()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) BorderStroke(1.dp, accent) else null,
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp),
    ) {
        Text(
            type.label(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerSheet(
    state: NoisePlayerState,
    onDismiss: () -> Unit,
    onSetTimer: (Int, Boolean) -> Unit,
) {
    var selectedMinutes by rememberSaveable {
        mutableIntStateOf(if (state.timerMinutes in timerPresetsMinutes) state.timerMinutes else CUSTOM_TIMER)
    }
    var customMinutesText by rememberSaveable {
        mutableStateOf(if (state.timerMinutes > 0) state.timerMinutes.toString() else "")
    }
    var fadeOutEnabled by rememberSaveable { mutableStateOf(state.fadeOutEnabled) }
    val customMinutes = customMinutesText.toIntOrNull()?.takeIf {
        it in 1..999 && customMinutesText.all(Char::isDigit)
    }
    val minutes = if (selectedMinutes == CUSTOM_TIMER) customMinutes else selectedMinutes
    val keyboard = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.sleep_timer),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                timerPresetsMinutes.forEach { preset ->
                    FilterChip(
                        selected = selectedMinutes == preset,
                        onClick = { selectedMinutes = preset },
                        label = {
                            Text(
                                if (preset == 0) stringResource(R.string.timer_off)
                                else stringResource(R.string.timer_minutes_short, preset),
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                FilterChip(
                    selected = selectedMinutes == CUSTOM_TIMER,
                    onClick = { selectedMinutes = CUSTOM_TIMER },
                    label = { Text(stringResource(R.string.timer_custom)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            if (selectedMinutes == CUSTOM_TIMER) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = customMinutesText,
                    onValueChange = { customMinutesText = it },
                    label = { Text(stringResource(R.string.custom_minutes_hint)) },
                    supportingText = { Text(stringResource(R.string.custom_minutes_range)) },
                    isError = customMinutesText.isNotEmpty() && customMinutes == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = fadeOutEnabled,
                        enabled = selectedMinutes != 0,
                        role = Role.Switch,
                        onValueChange = { fadeOutEnabled = it },
                    ),
            ) {
                Text(
                    stringResource(R.string.fade_out_label),
                    modifier = Modifier.weight(1f),
                    color = if (selectedMinutes == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Switch(checked = fadeOutEnabled, onCheckedChange = null, enabled = selectedMinutes != 0)
            }
            if (state.isPlaying && state.timerMinutes > 0 && minutes != null && minutes > 0 && minutes != state.timerMinutes) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.timer_restart_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { minutes?.let { onSetTimer(it, fadeOutEnabled) } },
                enabled = minutes != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.set_timer))
            }
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
private fun spokenDuration(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + 999) / 1000
    val parts = buildList {
        val hours = (seconds / 3600).toInt()
        val minutes = (seconds / 60 % 60).toInt()
        val remainder = (seconds % 60).toInt()
        if (hours > 0) add(pluralStringResource(R.plurals.duration_hours, hours, hours))
        if (minutes > 0) add(pluralStringResource(R.plurals.duration_minutes, minutes, minutes))
        if (remainder > 0 || isEmpty()) add(pluralStringResource(R.plurals.duration_seconds, remainder, remainder))
    }
    return parts.joinToString(stringResource(R.string.duration_separator))
}

internal fun formatTimerMillis(ms: Long, locale: Locale = Locale.getDefault()): String {
    val seconds = (ms.coerceAtLeast(0) + 999) / 1000
    return if (seconds < 3600) {
        String.format(locale, "%d:%02d", seconds / 60, seconds % 60)
    } else {
        String.format(locale, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
}
