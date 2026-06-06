package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Entity for Saved Custom Presets
@Entity(tableName = "preset_timers")
data class PresetTimer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val durationSeconds: Long,
    val createdAt: Long = System.currentTimeMillis()
)

// Entity for Historified completed timers
@Entity(tableName = "timer_history")
data class TimerHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val durationSeconds: Long,
    val type: String, // "Countdown" or "Stopwatch"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TimerDao {
    // Preset Queries
    @Query("SELECT * FROM preset_timers ORDER BY createdAt DESC")
    fun getAllPresets(): Flow<List<PresetTimer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetTimer)

    @Delete
    suspend fun deletePreset(preset: PresetTimer)

    // History Queries
    @Query("SELECT * FROM timer_history ORDER BY timestamp DESC LIMIT 50")
    fun getHistory(): Flow<List<TimerHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TimerHistory)

    @Query("DELETE FROM timer_history")
    suspend fun clearHistory()
}

@Database(entities = [PresetTimer::class, TimerHistory::class], version = 1, exportSchema = false)
abstract class TimerDatabase : RoomDatabase() {
    abstract val timerDao: TimerDao

    companion object {
        @Volatile
        private var INSTANCE: TimerDatabase? = null

        fun getDatabase(context: Context): TimerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TimerDatabase::class.java,
                    "kitsibet_timer_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
