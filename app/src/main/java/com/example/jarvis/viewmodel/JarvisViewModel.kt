package com.example.jarvis.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jarvis.data.CommandHistoryEntity
import com.example.jarvis.data.ExternalContactEntity
import com.example.jarvis.data.JarvisDatabase
import com.example.jarvis.data.JarvisRepository
import com.example.jarvis.data.MemoryFactEntity
import com.example.jarvis.data.TaskReminderEntity
import com.example.jarvis.engine.AgentResearchResult
import com.example.jarvis.engine.CapabilityPermissionStatus
import com.example.jarvis.engine.ExecutionEngine
import com.example.jarvis.engine.JarvisBrain
import com.example.jarvis.engine.JarvisFileManager
import com.example.jarvis.engine.PermissionManager
import com.example.jarvis.engine.SystemController
import com.example.jarvis.engine.VoiceEngine
import com.example.jarvis.model.ActionType
import com.example.jarvis.model.ActiveTimerItem
import com.example.jarvis.model.AssistantState
import com.example.jarvis.model.BatteryMode
import com.example.jarvis.model.CapturedNotification
import com.example.jarvis.model.DeviceTelemetry
import com.example.jarvis.model.ExecutionPlan
import com.example.jarvis.model.JarvisFileItem
import com.example.jarvis.model.JarvisSettings
import com.example.jarvis.model.ScreenSnapshot
import com.example.jarvis.model.SessionContext
import com.example.jarvis.model.StepExecutionResult
import com.example.jarvis.service.JarvisAccessibilityService
import com.example.jarvis.service.JarvisForegroundService
import com.example.jarvis.service.JarvisNotificationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val db = JarvisDatabase.getInstance(application)
    val repository = JarvisRepository(application, db.jarvisDao(), db.contactDao())
    val systemController = SystemController(application)
    val fileManager = JarvisFileManager(application)
    val permissionManager = PermissionManager(application)
    private val brain = JarvisBrain()

    // Reactive Database Flows
    val settings: StateFlow<JarvisSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisSettings()
    )

    val commandHistory = repository.commandHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val automationLogs = repository.automationLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val tasksAndReminders = repository.tasksAndReminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val memoryFacts = repository.memoryFacts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val savedContacts = repository.savedContacts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeNotifications: StateFlow<List<CapturedNotification>> = JarvisNotificationService.notifications

    // Assistant State & Live Telemetry
    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _statusSubtitle = MutableStateFlow(AssistantState.IDLE.defaultSubtitle)
    val statusSubtitle: StateFlow<String> = _statusSubtitle.asStateFlow()

    private val _lastSpokenResponse = MutableStateFlow("JARVIS online. Kahiye, main aapki kya madad kar sakta hoon?")
    val lastSpokenResponse: StateFlow<String> = _lastSpokenResponse.asStateFlow()

    private val _currentPlan = MutableStateFlow<ExecutionPlan?>(null)
    val currentPlan: StateFlow<ExecutionPlan?> = _currentPlan.asStateFlow()

    private val _stepResults = MutableStateFlow<List<StepExecutionResult>>(emptyList())
    val stepResults: StateFlow<List<StepExecutionResult>> = _stepResults.asStateFlow()

    private val _pendingConfirmationPlan = MutableStateFlow<ExecutionPlan?>(null)
    val pendingConfirmationPlan: StateFlow<ExecutionPlan?> = _pendingConfirmationPlan.asStateFlow()

    private val _pendingDisambiguationPlan = MutableStateFlow<ExecutionPlan?>(null)
    val pendingDisambiguationPlan: StateFlow<ExecutionPlan?> = _pendingDisambiguationPlan.asStateFlow()

    private val _sessionContext = MutableStateFlow(SessionContext())
    val sessionContext: StateFlow<SessionContext> = _sessionContext.asStateFlow()

    private val _telemetry = MutableStateFlow(systemController.readTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _screenSnapshot = MutableStateFlow(JarvisAccessibilityService.getActiveOrSandboxSnapshot())
    val screenSnapshot: StateFlow<ScreenSnapshot> = _screenSnapshot.asStateFlow()

    private val _permissionsList = MutableStateFlow(permissionManager.inspectAllPermissions())
    val permissionsList: StateFlow<List<CapabilityPermissionStatus>> = _permissionsList.asStateFlow()

    private val _workspaceFiles = MutableStateFlow<List<JarvisFileItem>>(emptyList())
    val workspaceFiles: StateFlow<List<JarvisFileItem>> = _workspaceFiles.asStateFlow()

    private val _currentFolderFilter = MutableStateFlow("All")
    val currentFolderFilter: StateFlow<String> = _currentFolderFilter.asStateFlow()

    private val _selectedFilePreview = MutableStateFlow<Pair<String, String>?>(null)
    val selectedFilePreview: StateFlow<Pair<String, String>?> = _selectedFilePreview.asStateFlow()

    private val _activeTimers = MutableStateFlow<List<ActiveTimerItem>>(emptyList())
    val activeTimers: StateFlow<List<ActiveTimerItem>> = _activeTimers.asStateFlow()

    private val _latestResearchResult = MutableStateFlow<AgentResearchResult?>(
        AgentResearchResult(
            query = "Vivo Y21 5G Specifications (Ready)",
            sourceLabel = "JARVIS Browser Agent",
            bulletPoints = listOf(
                "Display: 6.51-inch HD+ Halo FullView Display",
                "Chipset: MediaTek Dimensity 700 5G Octa-Core",
                "Camera: 50MP Primary + 2MP Macro | 8MP Front",
                "Battery: 5000mAh with 18W Fast Charge"
            ),
            targetUrl = "https://www.google.com/search?q=Vivo+Y21+5G+specifications"
        )
    )
    val latestResearchResult: StateFlow<AgentResearchResult?> = _latestResearchResult.asStateFlow()

    val isCloudKeyReady: Boolean = brain.isCloudApiKeyConfigured()

    private var currentExecutionJob: Job? = null

    val voiceEngine = VoiceEngine(
        context = application,
        scope = viewModelScope,
        onCommandRecognized = { text, fromWakeWord ->
            processUserCommand(text, triggeredByWakeWord = fromWakeWord)
        },
        onWakeWordTriggered = {
            _assistantState.value = AssistantState.LISTENING
            _statusSubtitle.value = "Wake word 'Hey JARVIS' detected! Listening..."
        },
        onSpeechError = { err ->
            _assistantState.value = AssistantState.IDLE
            _statusSubtitle.value = err
        }
    )

    private val executionEngine = ExecutionEngine(
        context = application,
        systemController = systemController,
        fileManager = fileManager,
        repository = repository,
        onStartInAppTimer = { seconds, label ->
            startCountdownTimer(seconds, label)
        },
        onUpdateAgentResearch = { result ->
            _latestResearchResult.value = result
        },
        onNavigateInAppBack = {
            true
        }
    )

    init {
        viewModelScope.launch {
            repository.ensureInitialDataSeeded()
            fileManager.ensureSeedFiles()
            refreshFiles("All")
        }

        // Observe settings to sync VoiceEngine & Foreground Service
        viewModelScope.launch {
            settings.collect { s ->
                voiceEngine.updateVoicePreferences(
                    enabled = s.voiceResponseEnabled,
                    rate = s.speechRate,
                    pitch = s.speechPitch,
                    wakeWordActive = s.wakeWordEnabled,
                    wakeIntervalMs = s.batteryMode.wakeWordIntervalMs
                )
            }
        }

        // Observe voice listening / speaking states
        viewModelScope.launch {
            voiceEngine.isListening.collect { listening ->
                if (listening && _assistantState.value == AssistantState.IDLE) {
                    _assistantState.value = AssistantState.LISTENING
                    _statusSubtitle.value = AssistantState.LISTENING.defaultSubtitle
                } else if (!listening && _assistantState.value == AssistantState.LISTENING) {
                    _assistantState.value = AssistantState.IDLE
                    _statusSubtitle.value = AssistantState.IDLE.defaultSubtitle
                }
            }
        }

        // Battery-aware periodic telemetry & screen snapshot refresh
        viewModelScope.launch {
            while (isActive) {
                _telemetry.value = systemController.readTelemetry()
                _screenSnapshot.value = JarvisAccessibilityService.getActiveOrSandboxSnapshot()
                val delayMs = settings.value.batteryMode.telemetryPollMs
                delay(delayMs)
            }
        }

        // 1-second ticker for active in-app timers
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val current = _activeTimers.value
                if (current.isNotEmpty()) {
                    val updated = current.mapNotNull { timer ->
                        if (!timer.isRunning) return@mapNotNull timer
                        val nextRem = timer.remainingSeconds - 1
                        if (nextRem <= 0) {
                            voiceEngine.speak("Timer complete: ${timer.label}")
                            JarvisNotificationService.postLocalNotificationEntry(
                                appName = "JARVIS Timer",
                                title = "Timer Finished",
                                text = "${timer.label} has completed.",
                                important = true
                            )
                            null
                        } else {
                            timer.copy(remainingSeconds = nextRem)
                        }
                    }
                    _activeTimers.value = updated
                }
            }
        }
    }

    fun startVoiceListening() {
        _assistantState.value = AssistantState.LISTENING
        _statusSubtitle.value = "Listening... Speak in Hindi, English, or Hinglish"
        voiceEngine.startListening(isWakeWordStandby = false)
    }

    fun stopVoiceListening() {
        voiceEngine.stopListening()
        if (_assistantState.value == AssistantState.LISTENING) {
            _assistantState.value = AssistantState.IDLE
            _statusSubtitle.value = AssistantState.IDLE.defaultSubtitle
        }
    }

    fun cancelActiveExecution() {
        currentExecutionJob?.cancel()
        voiceEngine.stopSpeaking()
        _pendingConfirmationPlan.value = null
        _pendingDisambiguationPlan.value = null
        _assistantState.value = AssistantState.IDLE
        _statusSubtitle.value = "Execution cancelled. JARVIS is ready."
    }

    fun processUserCommand(rawText: String, triggeredByWakeWord: Boolean = false) {
        val text = rawText.trim()
        if (text.isBlank()) return

        currentExecutionJob?.cancel()
        currentExecutionJob = viewModelScope.launch {
            _assistantState.value = AssistantState.THINKING
            _statusSubtitle.value = "Understanding & planning: \"$text\""
            _stepResults.value = emptyList()

            val currentSettings = settings.value
            val memSummary = if (currentSettings.longTermMemoryEnabled) {
                memoryFacts.value.joinToString("; ") { "${it.memoryKey}=${it.memoryValue}" }
            } else ""

            val plan = brain.planCommand(
                rawCommand = text,
                session = _sessionContext.value,
                cloudAiEnabled = currentSettings.cloudAiEnabled,
                confirmMediumRisk = currentSettings.confirmMediumRisk,
                agentModeEnabled = currentSettings.agentModeEnabled,
                memorySummary = memSummary,
                contactResolver = { query -> repository.findContactsByName(query) }
            )

            _currentPlan.value = plan

            // Check if this plan is asking a follow-up for WhatsApp message text
            val firstStep = plan.steps.firstOrNull()
            if (firstStep?.actionType == ActionType.ANSWER_CONVERSATION &&
                firstStep.value == "AWAITING_WHATSAPP_MESSAGE"
            ) {
                _sessionContext.value = _sessionContext.value.copy(
                    lastContactName = firstStep.target,
                    awaitingFollowUpFor = "WHATSAPP_MESSAGE_TEXT"
                )
                _lastSpokenResponse.value = plan.spokenResponse
                _assistantState.value = AssistantState.COMPLETED
                _statusSubtitle.value = plan.spokenResponse
                voiceEngine.speak(plan.spokenResponse)
                repository.recordCommandHistory(
                    CommandHistoryEntity(
                        userCommand = text,
                        detectedLanguage = plan.detectedLanguage,
                        intentSummary = plan.intentSummary,
                        jarvisResponse = plan.spokenResponse,
                        actionsPerformedSummary = "Set session recipient=${firstStep.target}; awaiting message text",
                        verificationStatus = "VERIFIED",
                        riskLevel = plan.riskLevel.name,
                        engineUsed = plan.sourceEngine
                    )
                )
                return@launch
            }

            // Check Section 33 Fail-Safe Disambiguation (e.g., two contacts named Rahul)
            if (plan.requiresClarification && plan.clarificationOptions.isNotEmpty()) {
                _pendingDisambiguationPlan.value = plan
                _assistantState.value = AssistantState.DISAMBIGUATION
                _statusSubtitle.value = plan.clarificationPrompt
                _lastSpokenResponse.value = plan.spokenResponse
                voiceEngine.speak(plan.spokenResponse)
                return@launch
            }

            // Check Section 19 Action Security Confirmation Gate (Medium/High Risk)
            if (plan.requiresConfirmation) {
                _pendingConfirmationPlan.value = plan
                _assistantState.value = AssistantState.AWAITING_CONFIRMATION
                _statusSubtitle.value = plan.confirmationPrompt
                _lastSpokenResponse.value = plan.spokenResponse
                voiceEngine.speak(plan.spokenResponse)
                return@launch
            }

            // Otherwise execute immediately
            executeApprovedPlan(plan)
        }
    }

    fun selectDisambiguatedOption(chosenOption: String) {
        val pending = _pendingDisambiguationPlan.value ?: return
        _pendingDisambiguationPlan.value = null
        val msgText = pending.steps.firstOrNull()?.value ?: "Hello"
        val updatedCmd = "WhatsApp pe $chosenOption ko message bhejo ki $msgText"
        processUserCommand(updatedCmd)
    }

    fun confirmPendingPlan() {
        val plan = _pendingConfirmationPlan.value ?: return
        _pendingConfirmationPlan.value = null
        currentExecutionJob = viewModelScope.launch {
            executeApprovedPlan(plan.copy(requiresConfirmation = false))
        }
    }

    fun rejectPendingPlan() {
        val plan = _pendingConfirmationPlan.value
        _pendingConfirmationPlan.value = null
        _pendingDisambiguationPlan.value = null
        _assistantState.value = AssistantState.IDLE
        _statusSubtitle.value = "Action cancelled by user."
        _lastSpokenResponse.value = "Action cancel kar diya gaya hai."
        voiceEngine.speak("Action cancel kar diya gaya hai.")
        if (plan != null) {
            viewModelScope.launch {
                repository.recordCommandHistory(
                    CommandHistoryEntity(
                        userCommand = plan.originalCommand,
                        detectedLanguage = plan.detectedLanguage,
                        intentSummary = plan.intentSummary,
                        jarvisResponse = "Cancelled by user at Security Gate.",
                        actionsPerformedSummary = "0 actions executed (User declined confirmation)",
                        verificationStatus = "CANCELLED",
                        riskLevel = plan.riskLevel.name,
                        engineUsed = plan.sourceEngine
                    )
                )
            }
        }
    }

    private suspend fun executeApprovedPlan(plan: ExecutionPlan) {
        val commandRowId = repository.recordCommandHistory(
            CommandHistoryEntity(
                userCommand = plan.originalCommand,
                detectedLanguage = plan.detectedLanguage,
                intentSummary = plan.intentSummary,
                jarvisResponse = "Executing...",
                actionsPerformedSummary = "${plan.steps.size} planned actions",
                verificationStatus = "IN_PROGRESS",
                riskLevel = plan.riskLevel.name,
                engineUsed = plan.sourceEngine
            )
        )

        val results = mutableListOf<StepExecutionResult>()
        var allVerified = true

        for (step in plan.steps) {
            val stepResult = executionEngine.executeStepWithVerificationAndSelfCorrection(
                commandId = commandRowId,
                plan = plan,
                step = step,
                onPhaseUpdate = { phase, detail ->
                    _assistantState.value = if (phase == "VERIFYING") {
                        AssistantState.VERIFYING
                    } else {
                        AssistantState.EXECUTING
                    }
                    _statusSubtitle.value = detail
                }
            )
            results.add(stepResult)
            _stepResults.value = results.toList()

            // Update SessionContext based on completed action
            updateSessionContextFromStep(step)

            if (!stepResult.verifiedSuccess) {
                allVerified = false
                break
            }
        }

        // Refresh telemetry, screen snapshot, and files after execution
        _telemetry.value = systemController.readTelemetry()
        _screenSnapshot.value = JarvisAccessibilityService.getActiveOrSandboxSnapshot()
        refreshFiles(_currentFolderFilter.value)

        val finalSpoken = when {
            results.size == 1 && !results.first().spokenFeedback.isNullOrBlank() ->
                results.first().spokenFeedback!!
            allVerified -> plan.spokenResponse
            else -> results.lastOrNull()?.errorReason
                ?: "I couldn't verify completion of that action."
        }

        _lastSpokenResponse.value = finalSpoken
        _assistantState.value = if (allVerified) AssistantState.COMPLETED else AssistantState.ERROR
        _statusSubtitle.value = finalSpoken

        repository.recordCommandHistory(
            CommandHistoryEntity(
                id = commandRowId,
                userCommand = plan.originalCommand,
                detectedLanguage = plan.detectedLanguage,
                intentSummary = plan.intentSummary,
                jarvisResponse = finalSpoken,
                actionsPerformedSummary = results.joinToString(" → ") {
                    "${it.actionTitle} [${if (it.verifiedSuccess) "VERIFIED" else "FAILED"}]"
                },
                verificationStatus = if (allVerified) "VERIFIED" else "FAILED",
                riskLevel = plan.riskLevel.name,
                engineUsed = plan.sourceEngine
            )
        )

        voiceEngine.speak(finalSpoken)
    }

    private fun updateSessionContextFromStep(step: com.example.jarvis.model.PlannedAction) {
        val curr = _sessionContext.value
        val updated = when (step.actionType) {
            ActionType.WHATSAPP_SEND_MESSAGE -> curr.copy(
                lastContactName = step.target,
                lastMessageText = step.value,
                lastActionType = step.actionType,
                awaitingFollowUpFor = null
            )
            ActionType.LAUNCH_APP -> curr.copy(
                lastAppName = step.target,
                lastActionType = step.actionType
            )
            ActionType.BROWSER_SEARCH, ActionType.AGENT_RESEARCH -> curr.copy(
                lastSearchQuery = step.value.ifBlank { step.target },
                lastActionType = step.actionType
            )
            else -> curr.copy(
                lastActionType = step.actionType,
                awaitingFollowUpFor = null
            )
        }
        _sessionContext.value = updated
    }

    // Direct Hardware / Quick Action Controls (also routed through Verification Engine)
    fun setQuickVolume(percent: Int) {
        processUserCommand("Volume $percent% kar do")
    }

    fun setQuickBrightness(percent: Int) {
        processUserCommand("Brightness $percent% kar do")
    }

    fun toggleQuickFlashlight() {
        val next = !_telemetry.value.isFlashlightOn
        processUserCommand(if (next) "Flashlight on karo" else "Flashlight off karo")
    }

    fun refreshPermissions() {
        _permissionsList.value = permissionManager.inspectAllPermissions()
    }

    fun refreshScreenHierarchy() {
        JarvisAccessibilityService.instance?.refreshScreenSnapshot()
        _screenSnapshot.value = JarvisAccessibilityService.getActiveOrSandboxSnapshot()
    }

    fun refreshFiles(folderFilter: String = _currentFolderFilter.value) {
        _currentFolderFilter.value = folderFilter
        viewModelScope.launch {
            _workspaceFiles.value = fileManager.listFilesInFolder(folderFilter)
        }
    }

    fun previewFile(item: JarvisFileItem) {
        if (item.isDirectory) {
            refreshFiles(item.name)
            return
        }
        viewModelScope.launch {
            val content = fileManager.readFilePreview(item.path)
            _selectedFilePreview.value = item.name to content
        }
    }

    fun closeFilePreview() {
        _selectedFilePreview.value = null
    }

    fun createNewFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            fileManager.createFolder(name)
            refreshFiles(_currentFolderFilter.value)
        }
    }

    fun startCountdownTimer(seconds: Int, label: String) {
        val item = ActiveTimerItem(
            id = System.currentTimeMillis(),
            label = label,
            totalSeconds = seconds,
            remainingSeconds = seconds,
            isRunning = true
        )
        _activeTimers.value = listOf(item) + _activeTimers.value
    }

    fun cancelCountdownTimer(id: Long) {
        _activeTimers.value = _activeTimers.value.filterNot { it.id == id }
    }

    // Settings & Background Wake-Word Toggle
    fun toggleWakeWordStandby(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(wakeWordEnabled = enabled) }
            val app = getApplication<Application>()
            if (enabled) {
                JarvisForegroundService.startStandby(app, settings.value.batteryMode.title)
                voiceEngine.startListening(isWakeWordStandby = true)
            } else {
                JarvisForegroundService.stopStandby(app)
                voiceEngine.stopListening()
            }
        }
    }

    fun toggleCloudAi(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(cloudAiEnabled = enabled) }
        }
    }

    fun toggleAgentMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(agentModeEnabled = enabled) }
        }
    }

    fun toggleConfirmMediumRisk(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(confirmMediumRisk = enabled) }
        }
    }

    fun toggleVoiceResponses(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(voiceResponseEnabled = enabled) }
        }
    }

    fun toggleLongTermMemory(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(longTermMemoryEnabled = enabled) }
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(speechRate = rate) }
        }
    }

    fun setSpeechPitch(pitch: Float) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(speechPitch = pitch) }
        }
    }

    fun setBatteryMode(mode: BatteryMode) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(batteryMode = mode) }
            if (settings.value.wakeWordEnabled) {
                JarvisForegroundService.startStandby(getApplication(), mode.title)
            }
        }
    }

    fun addCustomTask(title: String, details: String, category: String, timeLabel: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addTaskReminder(
                TaskReminderEntity(
                    title = title.trim(),
                    details = details.trim(),
                    category = category,
                    triggerTimeLabel = timeLabel.ifBlank { "Scheduled" }
                )
            )
        }
    }

    fun toggleTaskCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(id, completed)
        }
    }

    fun deleteTaskById(id: Long) {
        viewModelScope.launch {
            repository.deleteTask(id)
        }
    }

    fun addCustomContact(name: String, phone: String, tag: String) {
        if (name.isBlank() || phone.isBlank()) return
        viewModelScope.launch {
            repository.addContact(name.trim(), phone.trim(), tag.ifBlank { "WhatsApp" })
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch {
            repository.deleteContact(id)
        }
    }

    fun addCustomMemoryFact(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            repository.addMemoryFact(key.trim(), value.trim(), "USER_MEMORY")
        }
    }

    fun deleteMemoryFact(id: Long) {
        viewModelScope.launch {
            repository.deleteMemoryFact(id)
        }
    }

    fun clearAllHistoryAndLogs() {
        viewModelScope.launch {
            repository.clearCommandHistory()
            repository.clearAutomationLogs()
            _stepResults.value = emptyList()
            _currentPlan.value = null
            _sessionContext.value = SessionContext()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.shutdown()
    }
}
