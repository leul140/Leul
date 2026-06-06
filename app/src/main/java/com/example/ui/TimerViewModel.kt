package com.example.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PresetTimer
import com.example.data.TimerHistory
import com.example.data.TimerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TimerStatus {
    IDLE, RUNNING, PAUSED, FINISHED
}

enum class StopwatchStatus {
    IDLE, RUNNING, PAUSED
}

data class StopwatchLap(
    val lapNumber: Int,
    val lapTimeMs: Long,
    val cumulativeTimeMs: Long
)

class TimerViewModel(private val repository: TimerRepository) : ViewModel() {

    // Database Flows
    val presetTimers: StateFlow<List<PresetTimer>> = repository.presets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val timerHistory: StateFlow<List<TimerHistory>> = repository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Mode Selector (Timer / Stopwatch)
    private val _isTimerMode = MutableStateFlow(true)
    val isTimerMode: StateFlow<Boolean> = _isTimerMode.asStateFlow()

    fun setTimerMode(isTimer: Boolean) {
        _isTimerMode.value = isTimer
    }

    // --- COUNTDOWN TIMER STATE ---
    private val _timerStatus = MutableStateFlow(TimerStatus.IDLE)
    val timerStatus: StateFlow<TimerStatus> = _timerStatus.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    // Temp picked inputs (Hours, Minutes, Seconds)
    val inputHours = MutableStateFlow(0)
    val inputMinutes = MutableStateFlow(0)
    val inputSeconds = MutableStateFlow(0)

    private var countdownJob: Job? = null
    private var timerTargetEndTime = 0L

    // Name for preset saving custom text field
    val customTimerNameInput = MutableStateFlow("")

    // Active Timer Description for run logging (e.g. "5 minutes" or Custom Label)
    private val _activeTimerLabel = MutableStateFlow("")
    val activeTimerLabel: StateFlow<String> = _activeTimerLabel.asStateFlow()

    fun startTimer(durationSeconds: Long, label: String = "የባለሙያ ቆጣሪ") {
        countdownJob?.cancel()
        val durationMs = durationSeconds * 1000L
        _totalDurationMs.value = durationMs
        _remainingMs.value = durationMs
        _activeTimerLabel.value = label
        _timerStatus.value = TimerStatus.RUNNING

        timerTargetEndTime = SystemClock.elapsedRealtime() + durationMs
        runCountdownLoop()
    }

    fun startTimerFromInputs(label: String = "") {
        val hrs = inputHours.value
        val mins = inputMinutes.value
        val secs = inputSeconds.value
        val totalSecs = (hrs * 3600L) + (mins * 60L) + secs
        if (totalSecs > 0) {
            val resolvedLabel = if (label.isNotBlank()) label else {
                "${if (hrs > 0) "$hrs ሰዓት " else ""}${if (mins > 0) "$mins ደቂቃ " else ""}${if (secs > 0) "$secs ሰከንድ" else ""}"
            }
            startTimer(totalSecs, resolvedLabel)
        }
    }

    fun pauseTimer() {
        if (_timerStatus.value == TimerStatus.RUNNING) {
            countdownJob?.cancel()
            _timerStatus.value = TimerStatus.PAUSED
        }
    }

    fun resumeTimer() {
        if (_timerStatus.value == TimerStatus.PAUSED) {
            _timerStatus.value = TimerStatus.RUNNING
            timerTargetEndTime = SystemClock.elapsedRealtime() + _remainingMs.value
            runCountdownLoop()
        }
    }

    fun resetTimer() {
        countdownJob?.cancel()
        _timerStatus.value = TimerStatus.IDLE
        _remainingMs.value = 0L
        _totalDurationMs.value = 0L
        _activeTimerLabel.value = ""
    }

    fun dismissFinishedAlarm() {
        if (_timerStatus.value == TimerStatus.FINISHED) {
            _timerStatus.value = TimerStatus.IDLE
            _remainingMs.value = 0L
            _totalDurationMs.value = 0L
        }
    }

    private fun runCountdownLoop() {
        countdownJob = viewModelScope.launch {
            while (_timerStatus.value == TimerStatus.RUNNING) {
                val now = SystemClock.elapsedRealtime()
                val diff = timerTargetEndTime - now
                if (diff <= 0) {
                    _remainingMs.value = 0L
                    _timerStatus.value = TimerStatus.FINISHED
                    // Add to room history
                    val activeLabel = _activeTimerLabel.value.ifBlank { "የሰዓት ቆጣሪ" }
                    val minutesDone = _totalDurationMs.value / 1000L
                    repository.addHistory(activeLabel, minutesDone, "Countdown")
                    break
                } else {
                    _remainingMs.value = diff
                }
                delay(40) // Smooth progress display rate
            }
        }
    }


    // --- STOPWATCH STATE ---
    private val _stopwatchStatus = MutableStateFlow(StopwatchStatus.IDLE)
    val stopwatchStatus: StateFlow<StopwatchStatus> = _stopwatchStatus.asStateFlow()

    private val _stopwatchMs = MutableStateFlow(0L)
    val stopwatchMs: StateFlow<Long> = _stopwatchMs.asStateFlow()

    private val _laps = MutableStateFlow<List<StopwatchLap>>(emptyList())
    val laps: StateFlow<List<StopwatchLap>> = _laps.asStateFlow()

    private var stopwatchJob: Job? = null
    private var stopwatchStartTime = 0L

    fun startStopwatch() {
        if (_stopwatchStatus.value == StopwatchStatus.IDLE) {
            _stopwatchStatus.value = StopwatchStatus.RUNNING
            stopwatchStartTime = SystemClock.elapsedRealtime()
            _laps.value = emptyList()
            runStopwatchLoop()
        } else if (_stopwatchStatus.value == StopwatchStatus.PAUSED) {
            _stopwatchStatus.value = StopwatchStatus.RUNNING
            stopwatchStartTime = SystemClock.elapsedRealtime() - _stopwatchMs.value
            runStopwatchLoop()
        }
    }

    fun pauseStopwatch() {
        if (_stopwatchStatus.value == StopwatchStatus.RUNNING) {
            stopwatchJob?.cancel()
            _stopwatchStatus.value = StopwatchStatus.PAUSED
        }
    }

    fun resetStopwatch() {
        stopwatchJob?.cancel()
        // Save current benchmark to history if stopwatch ran for more than 1 second
        val finalTimeMs = _stopwatchMs.value
        if (finalTimeMs >= 1000L) {
            viewModelScope.launch {
                repository.addHistory("የማቆሚያ ሰዓት: ${formatLapTime(finalTimeMs)}", finalTimeMs / 1000, "Stopwatch")
            }
        }
        _stopwatchStatus.value = StopwatchStatus.IDLE
        _stopwatchMs.value = 0L
        _laps.value = emptyList()
    }

    fun recordLap() {
        if (_stopwatchStatus.value == StopwatchStatus.RUNNING) {
            val currentCumulative = _stopwatchMs.value
            val lapNumber = _laps.value.size + 1
            val lastCumulative = if (_laps.value.isEmpty()) 0L else _laps.value.first().cumulativeTimeMs
            val lapTime = currentCumulative - lastCumulative
            
            val newLap = StopwatchLap(
                lapNumber = lapNumber,
                lapTimeMs = lapTime,
                cumulativeTimeMs = currentCumulative
            )
            // Laps shown in descending order (newest first)
            _laps.value = listOf(newLap) + _laps.value
        }
    }

    private fun runStopwatchLoop() {
        stopwatchJob = viewModelScope.launch {
            while (_stopwatchStatus.value == StopwatchStatus.RUNNING) {
                val now = SystemClock.elapsedRealtime()
                _stopwatchMs.value = now - stopwatchStartTime
                delay(22) // High frequency refresh for accurate fractional display
            }
        }
    }

    private fun formatLapTime(ms: Long): String {
        val totalSecs = ms / 1000
        val mi = totalSecs / 60
        val sc = totalSecs % 60
        val msFrac = (ms % 1000) / 10
        return String.format("%02d:%02d.%02d", mi, sc, msFrac)
    }


    // --- DATABASE ACTIONS ---
    fun saveCustomTimer(name: String, hrs: Int, mins: Int, secs: Int) {
        val totalSecs = (hrs * 3600L) + (mins * 60L) + secs
        if (name.isNotBlank() && totalSecs > 0) {
            viewModelScope.launch {
                repository.addPreset(name, totalSecs)
                customTimerNameInput.value = ""
            }
        }
    }

    fun deletePresetTimer(preset: PresetTimer) {
        viewModelScope.launch {
            repository.deletePreset(preset)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        stopwatchJob?.cancel()
    }

    // ViewModel Factory
    class Factory(private val repo: TimerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
                return TimerViewModel(repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
