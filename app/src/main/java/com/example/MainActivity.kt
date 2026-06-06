package com.example

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.PresetTimer
import com.example.data.TimerDatabase
import com.example.data.TimerHistory
import com.example.data.TimerRepository
import com.example.ui.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    containerColor = CosmicSlateBg
                ) { innerPadding ->
                    TimerAppScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TimerAppScreen(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel(
        factory = TimerViewModel.Factory(
            TimerRepository(TimerDatabase.getDatabase(LocalContext.current).timerDao)
        )
    )
) {
    val context = LocalContext.current
    val isTimerMode by viewModel.isTimerMode.collectAsStateWithLifecycle()
    val timerStatus by viewModel.timerStatus.collectAsStateWithLifecycle()
    val remainingMs by viewModel.remainingMs.collectAsStateWithLifecycle()
    val totalMs by viewModel.totalDurationMs.collectAsStateWithLifecycle()
    val activeLabel by viewModel.activeTimerLabel.collectAsStateWithLifecycle()

    val stopwatchMs by viewModel.stopwatchMs.collectAsStateWithLifecycle()
    val stopwatchStatus by viewModel.stopwatchStatus.collectAsStateWithLifecycle()
    val laps by viewModel.laps.collectAsStateWithLifecycle()

    val presetTimers by viewModel.presetTimers.collectAsStateWithLifecycle()
    val timerHistory by viewModel.timerHistory.collectAsStateWithLifecycle()

    // Sound logic on countdown completion
    LaunchedEffect(timerStatus) {
        if (timerStatus == TimerStatus.FINISHED) {
            try {
                // Vibrate the device if supported
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(1000)
                    }
                }

                // Play system Notification alarm
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                if (ringtone != null) {
                    ringtone.play()
                    delay(3500)
                    if (ringtone.isPlaying) {
                        ringtone.stop()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Launcher title and logo
        AppHeaderSection()

        Spacer(modifier = Modifier.height(16.dp))

        // Mode Segmented control
        ModeToggleSection(
            isTimerMode = isTimerMode,
            onModeSelect = { viewModel.setTimerMode(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Large active window
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(CosmicPrimaryHex.copy(alpha = 0.15f), TransparentCard)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isTimerMode) {
                    CountdownTimerView(
                        status = timerStatus,
                        remainingMs = remainingMs,
                        totalMs = totalMs,
                        activeLabel = activeLabel,
                        viewModel = viewModel
                    )
                } else {
                    StopwatchTimerView(
                        status = stopwatchStatus,
                        elapsedMs = stopwatchMs,
                        laps = laps,
                        viewModel = viewModel
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Saved Presets and logs (Below arena)
        if (isTimerMode && timerStatus == TimerStatus.IDLE) {
            PresetsSection(
                presets = presetTimers,
                onPresetClick = { label, seconds ->
                    viewModel.startTimer(seconds, label)
                },
                onDeleteClick = { viewModel.deletePresetTimer(it) },
                viewModel = viewModel
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // History logs
        HistorySection(
            history = timerHistory,
            onClearHistory = { viewModel.clearHistory() }
        )
    }
}

@Composable
fun AppHeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Styled running stopwatch visual element (Hourglass/Timer) with dynamic spinning
        val infiniteTransition = rememberInfiniteTransition(label = "hourglass_spin")
        val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hourglass"
        )

        Box(
            modifier = Modifier
                .size(42.dp)
                .rotate(rotationAngle)
                .background(CosmicSecondaryHex.copy(alpha = 0.2f), shape = CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = "የሰዓት ውበት ምልክት",
                tint = CosmicPrimaryHex,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "ቅጽበት የሰዓት አቆጠራ",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "ቆንጆ እና ፈጣን የሰዓት ቆጣሪ",
                fontSize = 12.sp,
                color = CosmicMutedText,
                letterSpacing = 0.2.sp
            )
        }
    }
}

@Composable
fun ModeToggleSection(isTimerMode: Boolean, onModeSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(CosmicSurface, RoundedCornerShape(26.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle 1: Timer
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .clickable { onModeSelect(true) }
                .then(
                    if (isTimerMode) {
                        Modifier.background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    CosmicPrimaryHex.copy(alpha = 0.25f),
                                    CosmicSecondaryHex.copy(alpha = 0.15f)
                                )
                            )
                        )
                    } else Modifier
                )
                .border(
                    width = if (isTimerMode) 1.5.dp else 0.dp,
                    color = if (isTimerMode) CosmicPrimaryHex.copy(alpha = 0.5f) else Color.Transparent,
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = if (isTimerMode) CosmicPrimaryHex else CosmicMutedText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "የሰዓት ቆጣሪ",
                    fontWeight = if (isTimerMode) FontWeight.Bold else FontWeight.Normal,
                    color = if (isTimerMode) Color.White else CosmicMutedText,
                    fontSize = 14.sp
                )
            }
        }

        // Toggle 2: Stopwatch
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .clickable { onModeSelect(false) }
                .then(
                    if (!isTimerMode) {
                        Modifier.background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    CosmicPrimaryHex.copy(alpha = 0.25f),
                                    CosmicSecondaryHex.copy(alpha = 0.15f)
                                )
                            )
                        )
                    } else Modifier
                )
                .border(
                    width = if (!isTimerMode) 1.5.dp else 0.dp,
                    color = if (!isTimerMode) CosmicPrimaryHex.copy(alpha = 0.5f) else Color.Transparent,
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer10,
                    contentDescription = null,
                    tint = if (!isTimerMode) CosmicPrimaryHex else CosmicMutedText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "የሰዓት መቁጠሪያ",
                    fontWeight = if (!isTimerMode) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isTimerMode) Color.White else CosmicMutedText,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun CountdownTimerView(
    status: TimerStatus,
    remainingMs: Long,
    totalMs: Long,
    activeLabel: String,
    viewModel: TimerViewModel
) {
    if (status == TimerStatus.FINISHED) {
        TimerRingingScreen(viewModel = viewModel)
    } else if (status == TimerStatus.IDLE) {
        TimerSetupScreen(viewModel = viewModel)
    } else {
        // RUNNING or PAUSED State
        val percent = if (totalMs > 0) remainingMs.toFloat() / totalMs.toFloat() else 1f

        // Progress pulses
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_radar")
        val breathScale by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath"
        )

        val strokeColor = Brush.sweepGradient(
            colors = listOf(CosmicPrimaryHex, CosmicSecondaryHex, CosmicTertiaryHex, CosmicPrimaryHex)
        )

        // Title of timer being counted
        Text(
            text = activeLabel.ifBlank { "የሰዓት ቆጣሪ" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .size(240.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulse glow halo in background
            if (status == TimerStatus.RUNNING) {
                Box(
                    modifier = Modifier
                        .size(220.dp * breathScale)
                        .background(CosmicPrimaryHex.copy(alpha = 0.04f), shape = CircleShape)
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val outerRadius = size.minDimension / 2
                // Background Track
                drawCircle(
                    color = CosmicSurfaceVariant.copy(alpha = 0.6f),
                    radius = outerRadius,
                    style = Stroke(width = 12.dp.toPx())
                )
                // Active Sweep Progress
                drawArc(
                    brush = strokeColor,
                    startAngle = -90f,
                    sweepAngle = 360f * percent,
                    useCenter = false,
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Central readable timer numeric displays
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val totalSecondsRemaining = remainingMs / 1000
                val hours = totalSecondsRemaining / 3600
                val minutes = (totalSecondsRemaining % 3600) / 60
                val seconds = totalSecondsRemaining % 60
                val millisecondsFraction = (remainingMs % 1000) / 100

                val primaryTimeString = if (hours > 0) {
                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format("%02d:%02d", minutes, seconds)
                }

                Text(
                    text = primaryTimeString,
                    fontSize = if (hours > 0) 38.sp else 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                // High speed tenths fraction representation
                Text(
                    text = ".$millisecondsFraction",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CosmicPrimaryHex,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Interactivity controls for live timer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset Button
            OutlinedButton(
                onClick = { viewModel.resetTimer() },
                modifier = Modifier
                    .height(50.dp)
                    .testTag("reset_timer_button"),
                border = BorderStroke(1.dp, CosmicTertiaryHex.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicTertiaryHex),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "ሰርዝ",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "እንደገና ጀምር", fontSize = 13.sp)
            }

            // Play/Pause Big FAB
            Button(
                onClick = {
                    if (status == TimerStatus.RUNNING) {
                        viewModel.pauseTimer()
                    } else {
                        viewModel.resumeTimer()
                    }
                },
                modifier = Modifier
                    .width(150.dp)
                    .height(52.dp)
                    .testTag("toggle_play_pause_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == TimerStatus.RUNNING) CosmicSecondaryHex else CosmicPrimaryHex
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (status == TimerStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "ጀምር አቁም",
                        tint = if (status == TimerStatus.RUNNING) Color.White else CosmicTextOnPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (status == TimerStatus.RUNNING) "አቁም" else "ቀጥል",
                        color = if (status == TimerStatus.RUNNING) Color.White else CosmicTextOnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TimerSetupScreen(viewModel: TimerViewModel) {
    val hrs by viewModel.inputHours.collectAsStateWithLifecycle()
    val mins by viewModel.inputMinutes.collectAsStateWithLifecycle()
    val secs by viewModel.inputSeconds.collectAsStateWithLifecycle()
    val customName by viewModel.customTimerNameInput.collectAsStateWithLifecycle()

    var showTimerSaveDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ቆጠራ ሰዓት ያዋቅሩ",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = CosmicPrimaryHex,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Grid of picker increments (Hours, Minutes, Seconds)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PickerIncrementBlock(
                label = "ሰዓት",
                value = hrs,
                rangeMax = 23,
                onValueChange = { viewModel.inputHours.value = it },
                testTagPrefix = "hours"
            )

            PickerIncrementBlock(
                label = "ደቂቃ",
                value = mins,
                rangeMax = 59,
                onValueChange = { viewModel.inputMinutes.value = it },
                testTagPrefix = "minutes"
            )

            PickerIncrementBlock(
                label = "ሰከንድ",
                value = secs,
                rangeMax = 59,
                onValueChange = { viewModel.inputSeconds.value = it },
                testTagPrefix = "seconds"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large Primary Start Button and Saved Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Save Preset Trigger button
            OutlinedButton(
                onClick = {
                    val totalSecs = (hrs * 3600L) + (mins * 60L) + secs
                    if (totalSecs > 0) {
                        showTimerSaveDialog = true
                    }
                },
                modifier = Modifier
                    .weight(0.4f)
                    .height(52.dp)
                    .testTag("save_preset_button"),
                border = BorderStroke(1.dp, CosmicSecondaryHex),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmicSecondaryHex),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "ፕሪሴት አድን",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "ቆጣሪ አስቀምጥ", fontSize = 11.sp, maxLines = 1)
            }

            // Primary Start Now Button
            Button(
                onClick = { viewModel.startTimerFromInputs() },
                enabled = (hrs > 0 || mins > 0 || secs > 0),
                modifier = Modifier
                    .weight(0.6f)
                    .height(52.dp)
                    .testTag("start_countdown_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CosmicPrimaryHex,
                    disabledContainerColor = CosmicSurfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = CosmicTextOnPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ጀምር",
                    color = CosmicTextOnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    // Modal dialog to input Custom preset name
    if (showTimerSaveDialog) {
        AlertDialog(
            onDismissRequest = { showTimerSaveDialog = false },
            containerColor = CosmicSurface,
            title = {
                Text(text = "ለቆጣሪው ስም ይስጡት", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { viewModel.customTimerNameInput.value = it },
                        placeholder = { Text(text = "ምሳሌ፡ ቡና ማፍላት", color = CosmicMutedText) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CosmicSlateBg,
                            unfocusedContainerColor = CosmicSlateBg,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = CosmicPrimaryHex,
                            unfocusedIndicatorColor = CosmicMutedText
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preset_name_input_field")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ቆይታ፡ $hrs ሰዓት, $mins ደቂቃ, $secs ሰከንድ",
                        color = CosmicPrimaryHex,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customName.isNotBlank()) {
                            viewModel.saveCustomTimer(customName, hrs, mins, secs)
                            showTimerSaveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimaryHex)
                ) {
                    Text(text = "አስቀምጥ", color = CosmicTextOnPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimerSaveDialog = false }) {
                    Text(text = "ተው", color = Color.White)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun PickerIncrementBlock(
    label: String,
    value: Int,
    rangeMax: Int,
    onValueChange: (Int) -> Unit,
    testTagPrefix: String
) {
    Card(
        modifier = Modifier
            .width(96.dp)
            .padding(2.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CosmicSurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = CosmicMutedText,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Plus Sign
            IconButton(
                onClick = {
                    val next = if (value < rangeMax) value + 1 else 0
                    onValueChange(next)
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("${testTagPrefix}_plus_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "+",
                    tint = CosmicPrimaryHex,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Numeric Display text
            Text(
                text = String.format("%02d", value),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            // Minus Sign
            IconButton(
                onClick = {
                    val prev = if (value > 0) value - 1 else rangeMax
                    onValueChange(prev)
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("${testTagPrefix}_minus_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "-",
                    tint = CosmicTertiaryHex,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun TimerRingingScreen(viewModel: TimerViewModel) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ringing")
    val ringingScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Blinking Visual Bell
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            CosmicTertiaryHex.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp * ringingScale)
                    .background(CosmicTertiaryHex.copy(alpha = 0.12f), shape = CircleShape)
            )

            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "የሰዓት ማንቂያ",
                tint = CosmicTertiaryHex,
                modifier = Modifier
                    .size(72.dp)
                    .rotate(if (ringingScale > 1.05f) 12f else -12f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "ቆጠራው አብቅቷል!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = CosmicTertiaryHex,
            textAlign = TextAlign.Center
        )

        Text(
            text = "የእርስዎ የተቀናጀ ሰዓት ደርሷል",
            fontSize = 14.sp,
            color = Color.WhiteState,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        // Pulsing dismiss bar
        Button(
            onClick = { viewModel.dismissFinishedAlarm() },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(52.dp)
                .testTag("dismiss_alarm_button"),
            colors = ButtonDefaults.buttonColors(containerColor = CosmicPrimaryHex),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = CosmicTextOnPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "እሺ (ያቁሙ)",
                    color = CosmicTextOnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

val Color.Companion.WhiteState: Color
    get() = Color(0xFFE2E8F0)

@Composable
fun StopwatchTimerView(
    status: StopwatchStatus,
    elapsedMs: Long,
    laps: List<StopwatchLap>,
    viewModel: TimerViewModel
) {
    // Elegant Stopwatch view
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val totalSecs = elapsedMs / 1000
        val mi = totalSecs / 60
        val sc = totalSecs % 60
        val msFrac = (elapsedMs % 1000) / 10

        val digitalTime = String.format("%02d:%02d", mi, sc)
        val msString = String.format(".%02d", msFrac)

        // Circular digital visual panel
        Box(
            modifier = Modifier
                .size(220.dp)
                .padding(8.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            CosmicSecondaryHex.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Constant light animation tracker stroke
            Canvas(modifier = Modifier.fillMaxSize()) {
                val outerRadius = size.minDimension / 2
                drawCircle(
                    color = CosmicSecondaryHex.copy(alpha = 0.15f),
                    radius = outerRadius,
                    style = Stroke(width = 6.dp.toPx())
                )

                // Draw decorative ticking seconds indicator markers
                if (status == StopwatchStatus.RUNNING) {
                    val activeAngle = (elapsedMs % 60000L).toFloat() / 60000f * 360f
                    drawArc(
                        color = CosmicPrimaryHex,
                        startAngle = activeAngle - 90f,
                        sweepAngle = 18f,
                        useCenter = false,
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = digitalTime,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = msString,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicPrimaryHex,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Three button responsive action system
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action: Reset / Lap
            if (status != StopwatchStatus.IDLE) {
                IconButton(
                    onClick = {
                        if (status == StopwatchStatus.RUNNING) {
                            viewModel.recordLap()
                        } else {
                            viewModel.resetStopwatch()
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .background(CosmicSurfaceVariant, CircleShape)
                        .testTag("stopwatch_aux_button")
                ) {
                    Icon(
                        imageVector = if (status == StopwatchStatus.RUNNING) Icons.Default.Flag else Icons.Default.DeleteForever,
                        contentDescription = "ዙር ምልክት ወይም አጽዳ",
                        tint = if (status == StopwatchStatus.RUNNING) CosmicSecondaryHex else CosmicTertiaryHex,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(54.dp))
            }

            // Central: Launch/Pause button
            Button(
                onClick = {
                    if (status == StopwatchStatus.RUNNING) {
                        viewModel.pauseStopwatch()
                    } else {
                        viewModel.startStopwatch()
                    }
                },
                modifier = Modifier
                    .width(140.dp)
                    .height(52.dp)
                    .testTag("stopwatch_toggle_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == StopwatchStatus.RUNNING) CosmicTertiaryHex else CosmicPrimaryHex
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (status == StopwatchStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        tint = if (status == StopwatchStatus.RUNNING) Color.White else CosmicTextOnPrimary,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (status == StopwatchStatus.RUNNING) "አቁም" else if (status == StopwatchStatus.PAUSED) "ቀጥል" else "ጀምር",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (status == StopwatchStatus.RUNNING) Color.White else CosmicTextOnPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.size(54.dp))
        }

        // Laps tables
        if (laps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "የዙር ዝርዝር (Laps)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.WhiteState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Show a grid-like Laps list
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSlateBg),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CosmicSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "ዙር", fontSize = 11.sp, color = CosmicMutedText, modifier = Modifier.weight(0.2f))
                        Text(text = "የዙር ጊዜ", fontSize = 11.sp, color = CosmicMutedText, modifier = Modifier.weight(0.4f), textAlign = TextAlign.End)
                        Text(text = "ጠቅላላ ጊዜ", fontSize = 11.sp, color = CosmicMutedText, modifier = Modifier.weight(0.4f), textAlign = TextAlign.End)
                    }

                    HorizontalDivider(color = CosmicSurfaceVariant)

                    // Limits table list viewport height so we keep single-view constraint in check
                    laps.take(15).forEach { lap ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "#${lap.lapNumber}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicSecondaryHex,
                                modifier = Modifier.weight(0.2f)
                            )
                            Text(
                                text = formatLapMs(lap.lapTimeMs),
                                fontSize = 13.sp,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(0.4f),
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = formatLapMs(lap.cumulativeTimeMs),
                                fontSize = 13.sp,
                                color = CosmicPrimaryHex,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(0.4f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatLapMs(ms: Long): String {
    val totalSecs = ms / 1000
    val mi = totalSecs / 60
    val sc = totalSecs % 60
    val msFrac = (ms % 1000) / 10
    return String.format("%02d:%02d.%02d", mi, sc, msFrac)
}

@Composable
fun PresetsSection(
    presets: List<PresetTimer>,
    onPresetClick: (String, Long) -> Unit,
    onDeleteClick: (PresetTimer) -> Unit,
    viewModel: TimerViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "የተለመዱ ተግባራት (Presets)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Horizontal scrolling default quick buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PresetQuickChip(
                label = "ሻይ ማፍላት",
                durationSeconds = 180L, // 3 min
                icon = Icons.Default.LocalCafe,
                onClick = onPresetClick
            )

            PresetQuickChip(
                label = "እንቁላል",
                durationSeconds = 300L, // 5 min
                icon = Icons.Default.Restaurant,
                onClick = onPresetClick
            )

            PresetQuickChip(
                label = "ትኩረት (ፖሞዶሮ)",
                durationSeconds = 1500L, // 25 min
                icon = Icons.Default.Eco,
                onClick = onPresetClick
            )

            PresetQuickChip(
                label = "የስፖርት",
                durationSeconds = 60L, // 1 min
                icon = Icons.Default.DirectionsRun,
                onClick = onPresetClick
            )

            PresetQuickChip(
                label = "ማረፊያ",
                durationSeconds = 300L, // 5 min
                icon = Icons.Default.SelfImprovement,
                onClick = onPresetClick
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Custom Saved Presets Listing (Room-based)
        Text(
            text = "የእርስዎ የተቀመጡ ቆጣሪዎች",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        if (presets.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CosmicSurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ምንም የራስዎ የተቀመጠ ቆጣሪ የለም። ያዘጋጁትን ሰዓት \"ቆጣሪ አስቀምጥ\" የሚለውን ተጭነው ማያያዝ ይችላሉ!",
                        fontSize = 12.sp,
                        color = CosmicMutedText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPresetClick(preset.name, preset.durationSeconds) },
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CosmicSurfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(0.7f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(CosmicPrimaryHex.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = CosmicPrimaryHex,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = preset.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatSecondsToAmharicReadable(preset.durationSeconds),
                                        fontSize = 11.sp,
                                        color = CosmicMutedText
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.weight(0.3f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Run action trigger
                                IconButton(
                                    onClick = { onPresetClick(preset.name, preset.durationSeconds) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "ጀምር",
                                        tint = CosmicPrimaryHex
                                    )
                                }

                                // Delete preset trigger
                                IconButton(
                                    onClick = { onDeleteClick(preset) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "ስርዝ",
                                        tint = CosmicTertiaryHex.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresetQuickChip(
    label: String,
    durationSeconds: Long,
    icon: ImageVector,
    onClick: (String, Long) -> Unit
) {
    Card(
        modifier = Modifier
            .clickable { onClick(label, durationSeconds) }
            .padding(1.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CosmicSurfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CosmicPrimaryHex,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = formatSecondsToAmharicReadable(durationSeconds),
                    fontSize = 10.sp,
                    color = CosmicMutedText
                )
            }
        }
    }
}

fun formatSecondsToAmharicReadable(totalSecs: Long): String {
    val hrs = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60

    val builder = StringBuilder()
    if (hrs > 0) builder.append("$hrs ሰዓት ")
    if (mins > 0) builder.append("$mins ደቂቃ ")
    if (secs > 0 || (hrs == 0L && mins == 0L)) builder.append("$secs ሰከንድ")
    return builder.toString().trim()
}

@Composable
fun HistorySection(
    history: List<TimerHistory>,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "የቆጠራ ታሪክ (History)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.textButtonColors(contentColor = CosmicTertiaryHex)
                ) {
                    Text(text = "ታሪክ አጽዳ", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (history.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CosmicSurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "የተጠናቀቀ ቆጠራ ታሪክ እዚህ ላይ ይዘረዘራል።",
                        fontSize = 12.sp,
                        color = CosmicMutedText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Display compact list scroll view items
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Show latest 10 elements to maintain optimal sizing boundaries
                history.take(10).forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(0.75f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.type == "Countdown") Icons.Default.CheckCircle else Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (item.type == "Countdown") CosmicPrimaryHex else CosmicSecondaryHex,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = item.label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "ቆይታ፡ ${formatSecondsToAmharicReadable(item.durationSeconds)}",
                                        fontSize = 11.sp,
                                        color = CosmicMutedText
                                    )
                                }
                            }

                            Text(
                                text = formatHistoricTime(item.timestamp),
                                fontSize = 11.sp,
                                color = CosmicMutedText,
                                modifier = Modifier.weight(0.25f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatHistoricTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
