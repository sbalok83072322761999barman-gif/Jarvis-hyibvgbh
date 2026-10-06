package com.example.jarvis.data

import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.jarvis.model.BatteryMode
import com.example.jarvis.model.ContactInfo
import com.example.jarvis.model.JarvisSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.jarvisDataStore by preferencesDataStore(name = "jarvis_preferences")

class JarvisRepository(
    private val context: Context,
    private val jarvisDao: JarvisDao,
    private val contactDao: ContactDao
) {
    private object PrefKeys {
        val WAKE_WORD = booleanPreferencesKey("wake_word_enabled")
        val CLOUD_AI = booleanPreferencesKey("cloud_ai_enabled")
        val AGENT_MODE = booleanPreferencesKey("agent_mode_enabled")
        val CONFIRM_MEDIUM = booleanPreferencesKey("confirm_medium_risk")
        val LONG_TERM_MEMORY = booleanPreferencesKey("long_term_memory_enabled")
        val VOICE_RESPONSE = booleanPreferencesKey("voice_response_enabled")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val LANGUAGE = stringPreferencesKey("preferred_language")
        val BATTERY_MODE = stringPreferencesKey("battery_mode")
        val SEEDED = booleanPreferencesKey("initial_seeded")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        val batteryModeName = prefs[PrefKeys.BATTERY_MODE] ?: BatteryMode.BALANCED.name
        val batteryMode = runCatching { BatteryMode.valueOf(batteryModeName) }.getOrDefault(BatteryMode.BALANCED)
        JarvisSettings(
            wakeWordEnabled = prefs[PrefKeys.WAKE_WORD] ?: false,
            cloudAiEnabled = prefs[PrefKeys.CLOUD_AI] ?: true,
            agentModeEnabled = prefs[PrefKeys.AGENT_MODE] ?: false,
            confirmMediumRisk = prefs[PrefKeys.CONFIRM_MEDIUM] ?: true,
            longTermMemoryEnabled = prefs[PrefKeys.LONG_TERM_MEMORY] ?: true,
            voiceResponseEnabled = prefs[PrefKeys.VOICE_RESPONSE] ?: true,
            speechRate = prefs[PrefKeys.SPEECH_RATE] ?: 1.05f,
            speechPitch = prefs[PrefKeys.SPEECH_PITCH] ?: 0.98f,
            preferredLanguage = prefs[PrefKeys.LANGUAGE] ?: "Hinglish (EN + HI)",
            batteryMode = batteryMode
        )
    }

    val commandHistory: Flow<List<CommandHistoryEntity>> = jarvisDao.getCommandHistory()
    val automationLogs: Flow<List<AutomationLogEntity>> = jarvisDao.getAutomationLogs()
    val tasksAndReminders: Flow<List<TaskReminderEntity>> = jarvisDao.getTasksAndReminders()
    val memoryFacts: Flow<List<MemoryFactEntity>> = jarvisDao.getMemoryFacts()
    val savedContacts: Flow<List<ExternalContactEntity>> = contactDao.getAllContacts()

    suspend fun ensureInitialDataSeeded() = withContext(Dispatchers.IO) {
        context.jarvisDataStore.edit { prefs ->
            val alreadySeeded = prefs[PrefKeys.SEEDED] ?: false
            if (!alreadySeeded) {
                prefs[PrefKeys.SEEDED] = true
                // Seed default contacts for WhatsApp & disambiguation verification
                if (contactDao.getAllContactsSnapshot().isEmpty()) {
                    contactDao.insertContact(
                        ExternalContactEntity(name = "Rahul Sharma", phoneNumber = "+919876543210", tag = "Friend • WhatsApp")
                    )
                    contactDao.insertContact(
                        ExternalContactEntity(name = "Rahul Verma", phoneNumber = "+919811223344", tag = "Work • WhatsApp")
                    )
                    contactDao.insertContact(
                        ExternalContactEntity(name = "Mom", phoneNumber = "+919988776655", tag = "Family • WhatsApp")
                    )
                    contactDao.insertContact(
                        ExternalContactEntity(name = "Priya Patel", phoneNumber = "+919765432109", tag = "Team Lead • WhatsApp")
                    )
                }
                if (jarvisDao.getMemoryFactsSnapshot().isEmpty()) {
                    jarvisDao.insertMemoryFact(
                        MemoryFactEntity(
                            memoryKey = "Default Rahul Contact",
                            memoryValue = "Ask disambiguation between Rahul Sharma and Rahul Verma unless specified",
                            category = "CONTACT"
                        )
                    )
                    jarvisDao.insertMemoryFact(
                        MemoryFactEntity(
                            memoryKey = "Preferred Language Style",
                            memoryValue = "Bilingual Hinglish + English concise responses",
                            category = "PREFERENCE"
                        )
                    )
                }
            }
        }
    }

    suspend fun updateSettings(transform: (JarvisSettings) -> JarvisSettings) {
        context.jarvisDataStore.edit { prefs ->
            val currentBattery = runCatching {
                BatteryMode.valueOf(prefs[PrefKeys.BATTERY_MODE] ?: BatteryMode.BALANCED.name)
            }.getOrDefault(BatteryMode.BALANCED)
            val current = JarvisSettings(
                wakeWordEnabled = prefs[PrefKeys.WAKE_WORD] ?: false,
                cloudAiEnabled = prefs[PrefKeys.CLOUD_AI] ?: true,
                agentModeEnabled = prefs[PrefKeys.AGENT_MODE] ?: false,
                confirmMediumRisk = prefs[PrefKeys.CONFIRM_MEDIUM] ?: true,
                longTermMemoryEnabled = prefs[PrefKeys.LONG_TERM_MEMORY] ?: true,
                voiceResponseEnabled = prefs[PrefKeys.VOICE_RESPONSE] ?: true,
                speechRate = prefs[PrefKeys.SPEECH_RATE] ?: 1.05f,
                speechPitch = prefs[PrefKeys.SPEECH_PITCH] ?: 0.98f,
                preferredLanguage = prefs[PrefKeys.LANGUAGE] ?: "Hinglish (EN + HI)",
                batteryMode = currentBattery
            )
            val updated = transform(current)
            prefs[PrefKeys.WAKE_WORD] = updated.wakeWordEnabled
            prefs[PrefKeys.CLOUD_AI] = updated.cloudAiEnabled
            prefs[PrefKeys.AGENT_MODE] = updated.agentModeEnabled
            prefs[PrefKeys.CONFIRM_MEDIUM] = updated.confirmMediumRisk
            prefs[PrefKeys.LONG_TERM_MEMORY] = updated.longTermMemoryEnabled
            prefs[PrefKeys.VOICE_RESPONSE] = updated.voiceResponseEnabled
            prefs[PrefKeys.SPEECH_RATE] = updated.speechRate
            prefs[PrefKeys.SPEECH_PITCH] = updated.speechPitch
            prefs[PrefKeys.LANGUAGE] = updated.preferredLanguage
            prefs[PrefKeys.BATTERY_MODE] = updated.batteryMode.name
        }
    }

    suspend fun recordCommandHistory(entry: CommandHistoryEntity): Long =
        jarvisDao.insertCommandHistory(entry)

    suspend fun recordAutomationLog(log: AutomationLogEntity): Long =
        jarvisDao.insertAutomationLog(log)

    suspend fun clearCommandHistory() = jarvisDao.clearCommandHistory()

    suspend fun clearAutomationLogs() = jarvisDao.clearAutomationLogs()

    suspend fun addTaskReminder(task: TaskReminderEntity): Long =
        jarvisDao.insertTaskReminder(task)

    suspend fun toggleTaskCompleted(id: Long, completed: Boolean) =
        jarvisDao.setTaskCompleted(id, completed)

    suspend fun deleteTask(id: Long) = jarvisDao.deleteTaskById(id)

    suspend fun addMemoryFact(key: String, value: String, category: String = "CONTEXT") =
        jarvisDao.insertMemoryFact(MemoryFactEntity(memoryKey = key, memoryValue = value, category = category))

    suspend fun getMemoryFactsList(): List<MemoryFactEntity> =
        jarvisDao.getMemoryFactsSnapshot()

    suspend fun deleteMemoryFact(id: Long) = jarvisDao.deleteMemoryFactById(id)

    suspend fun clearAllMemory() = jarvisDao.clearAllMemoryFacts()

    suspend fun addContact(name: String, phone: String, tag: String = "WhatsApp") =
        contactDao.insertContact(ExternalContactEntity(name = name, phoneNumber = phone, tag = tag))

    suspend fun deleteContact(id: Long) = contactDao.deleteContact(id)

    /**
     * Resolves contacts matching a query from both Android system ContactsContract (if permission granted)
     * and the local JARVIS contacts table.
     */
    suspend fun findContactsByName(query: String): List<ContactInfo> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val results = mutableListOf<ContactInfo>()

        // 1. Check Android System Contacts if READ_CONTACTS is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$cleanQuery%"),
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext()) {
                        val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                        val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                        if (name.isNotBlank() && results.none { it.name.equals(name, ignoreCase = true) }) {
                            results.add(ContactInfo(id = "sys_$id", name = name, phoneNumber = number, relationshipTag = "Device Contact"))
                        }
                    }
                }
            }
        }

        // 2. Also merge saved contacts from Room
        val saved = contactDao.getAllContactsSnapshot()
        for (c in saved) {
            if (c.name.lowercase().contains(cleanQuery) || cleanQuery.contains(c.name.lowercase())) {
                if (results.none { it.name.equals(c.name, ignoreCase = true) }) {
                    results.add(
                        ContactInfo(
                            id = "db_${c.id}",
                            name = c.name,
                            phoneNumber = c.phoneNumber,
                            relationshipTag = c.tag
                        )
                    )
                }
            }
        }
        results
    }
}
