package com.example.jarvis.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userCommand: String,
    val detectedLanguage: String,
    val intentSummary: String,
    val jarvisResponse: String,
    val actionsPerformedSummary: String,
    val verificationStatus: String, // VERIFIED, PARTIAL, FAILED, AWAITING_CONFIRMATION, CANCELLED
    val riskLevel: String,
    val engineUsed: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_logs")
data class AutomationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandId: Long,
    val commandText: String,
    val phase: String, // OBSERVE, PLAN, ACT, VERIFY, CORRECT, ERROR
    val intentDetected: String,
    val actionPlanned: String,
    val actionExecuted: String,
    val screenStateSummary: String,
    val verificationResult: String,
    val errorDetails: String = "",
    val retryCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks_reminders")
data class TaskReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String,
    val category: String, // REMINDER, TIMER, ALARM, NOTE, TODO, SCHEDULED_CMD
    val triggerTimeLabel: String,
    val triggerTimeMillis: Long = 0L,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryKey: String,
    val memoryValue: String,
    val category: String, // CONTACT, PREFERENCE, ALIAS, CONTEXT
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface JarvisDao {
    // Command History
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 100")
    fun getCommandHistory(): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommandHistory(entry: CommandHistoryEntity): Long

    @Query("DELETE FROM command_history")
    suspend fun clearCommandHistory()

    // Automation Logs
    @Query("SELECT * FROM automation_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAutomationLogs(): Flow<List<AutomationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomationLog(log: AutomationLogEntity): Long

    @Query("DELETE FROM automation_logs")
    suspend fun clearAutomationLogs()

    // Tasks, Reminders & Notes
    @Query("SELECT * FROM tasks_reminders ORDER BY isCompleted ASC, createdAt DESC")
    fun getTasksAndReminders(): Flow<List<TaskReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskReminder(task: TaskReminderEntity): Long

    @Query("UPDATE tasks_reminders SET isCompleted = :completed WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM tasks_reminders WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    // Long-Term User-Controlled Memory
    @Query("SELECT * FROM memory_facts ORDER BY updatedAt DESC")
    fun getMemoryFacts(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts ORDER BY updatedAt DESC")
    suspend fun getMemoryFactsSnapshot(): List<MemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryFact(fact: MemoryFactEntity): Long

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteMemoryFactById(id: Long)

    @Query("DELETE FROM memory_facts")
    suspend fun clearAllMemoryFacts()
}

@Database(
    entities = [
        CommandHistoryEntity::class,
        AutomationLogEntity::class,
        ExternalContactEntity::class,
        TaskReminderEntity::class,
        MemoryFactEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_agent.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "saved_contacts")
data class ExternalContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val tag: String = "WhatsApp"
)

@Dao
interface ContactDao {
    @Query("SELECT * FROM saved_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ExternalContactEntity>>

    @Query("SELECT * FROM saved_contacts ORDER BY name ASC")
    suspend fun getAllContactsSnapshot(): List<ExternalContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ExternalContactEntity): Long

    @Query("DELETE FROM saved_contacts WHERE id = :id")
    suspend fun deleteContact(id: Long)
}
