package com.example.data

import kotlinx.coroutines.flow.Flow

class TimerRepository(private val timerDao: TimerDao) {
    val presets: Flow<List<PresetTimer>> = timerDao.getAllPresets()
    val history: Flow<List<TimerHistory>> = timerDao.getHistory()

    suspend fun addPreset(name: String, durationSeconds: Long) {
        timerDao.insertPreset(PresetTimer(name = name, durationSeconds = durationSeconds))
    }

    suspend fun deletePreset(preset: PresetTimer) {
        timerDao.deletePreset(preset)
    }

    suspend fun addHistory(label: String, durationSeconds: Long, type: String) {
        timerDao.insertHistory(TimerHistory(label = label, durationSeconds = durationSeconds, type = type))
    }

    suspend fun clearHistory() {
        timerDao.clearHistory()
    }
}
