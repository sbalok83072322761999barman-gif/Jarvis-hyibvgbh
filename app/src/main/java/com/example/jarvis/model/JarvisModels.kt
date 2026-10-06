package com.example.jarvis.model

import kotlinx.serialization.Serializable

enum class AssistantState(val statusTitle: String, val defaultSubtitle: String) {
    IDLE("IDLE", "JARVIS is ready."),
    LISTENING("LISTENING", "Listening..."),
    THINKING("THINKING", "Processing..."),
    EXECUTING("EXECUTING", "Executing..."),
    VERIFYING("VERIFYING", "Checking..."),
    SPEAKING("SPEAKING", "Speaking..."),
    AWAITING_CONFIRMATION("SECURITY CHECK", "Awaiting your confirmation..."),
    DISAMBIGUATION("CLARIFICATION NEEDED", "Multiple matches found. Please clarify."),
    COMPLETED("COMPLETED", "Done."),
    ERROR("ERROR", "I couldn't complete that action.")
}

@Serializable
enum class RiskLevel(val displayName: String, val badgeColorHex: Long) {
    LOW("LOW RISK", 0xFF00E676),
    MEDIUM("MEDIUM RISK", 0xFFFFB300),
    HIGH("HIGH RISK", 0xFFFF1744)
}

enum class BatteryMode(
    val title: String,
    val description: String,
    val wakeWordIntervalMs: Long,
    val telemetryPollMs: Long
) {
    PERFORMANCE(
        title = "Performance Mode",
        description = "Fastest response, continuous wake-word & instant screen analysis.",
        wakeWordIntervalMs = 800L,
        telemetryPollMs = 3000L
    ),
    BALANCED(
        title = "Balanced Mode",
        description = "Optimal speed + lower battery usage with adaptive polling.",
        wakeWordIntervalMs = 2000L,
        telemetryPollMs = 8000L
    ),
    BATTERY_SAVER(
        title = "Battery Saver Mode",
        description = "Minimum background work; on-demand voice activation.",
        wakeWordIntervalMs = 5000L,
        telemetryPollMs = 20000L
    )
}

@Serializable
enum class ActionType {
    LAUNCH_APP,
    NAVIGATE_BACK,
    NAVIGATE_HOME,
    NAVIGATE_RECENTS,
    OPEN_NOTIFICATIONS_SHADE,
    OPEN_QUICK_SETTINGS,
    SCROLL_SCREEN,
    CLICK_ELEMENT,
    LONG_PRESS_ELEMENT,
    TYPE_TEXT,
    CLIPBOARD_ACTION,
    SET_VOLUME,
    SET_RINGER_MODE,
    SET_BRIGHTNESS,
    TOGGLE_FLASHLIGHT,
    CONTROL_WIFI,
    CONTROL_BLUETOOTH,
    CONTROL_DND,
    CONTROL_ROTATION,
    MEDIA_CONTROL,
    OPEN_SYSTEM_SETTINGS,
    READ_DEVICE_TELEMETRY,
    READ_NOTIFICATIONS,
    DISMISS_NOTIFICATIONS,
    OPEN_NOTIFICATION,
    WHATSAPP_SEND_MESSAGE,
    BROWSER_SEARCH,
    OPEN_URL,
    FILE_SEARCH,
    FILE_OPEN_FOLDER,
    FILE_CREATE_FOLDER,
    FILE_RENAME,
    FILE_COPY,
    FILE_MOVE,
    FILE_DELETE,
    CAMERA_CAPTURE,
    TAKE_SCREENSHOT,
    SET_TIMER,
    SET_ALARM,
    CREATE_REMINDER,
    CREATE_NOTE_OR_TODO,
    SAVE_MEMORY_FACT,
    WAIT_AND_OBSERVE,
    ANALYZE_SCREEN,
    AGENT_RESEARCH,
    ANSWER_CONVERSATION
}

@Serializable
data class PlannedAction(
    val stepNumber: Int = 1,
    val actionType: ActionType,
    val title: String,
    val target: String = "",
    val value: String = "",
    val secondaryValue: String = "",
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val requiresConfirmation: Boolean = false,
    val expectedVerification: String = ""
)

@Serializable
data class ExecutionPlan(
    val originalCommand: String,
    val detectedLanguage: String = "Hinglish",
    val intentSummary: String,
    val spokenResponse: String,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String = "",
    val requiresClarification: Boolean = false,
    val clarificationPrompt: String = "",
    val clarificationOptions: List<String> = emptyList(),
    val steps: List<PlannedAction> = emptyList(),
    val isAgentModePlan: Boolean = false,
    val sourceEngine: String = "Offline NLU + Planner"
)

data class StepExecutionResult(
    val stepNumber: Int,
    val actionTitle: String,
    val executedSuccess: Boolean,
    val verifiedSuccess: Boolean,
    val observationSummary: String,
    val verificationDetail: String,
    val retryCount: Int = 0,
    val errorReason: String? = null,
    val spokenFeedback: String? = null
)

data class ScreenNodeItem(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val className: String,
    val viewIdResourceName: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val boundsSummary: String,
    val centerX: Int,
    val centerY: Int
) {
    val displayLabel: String
        get() = when {
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            viewIdResourceName.isNotBlank() -> viewIdResourceName.substringAfterLast("/")
            else -> className.substringAfterLast(".")
        }

    val roleBadge: String
        get() = when {
            isEditable -> "INPUT"
            isCheckable -> if (isChecked) "CHECKED" else "CHECKBOX"
            isClickable && className.contains("Button", ignoreCase = true) -> "BUTTON"
            isClickable -> "CLICKABLE"
            isScrollable -> "SCROLL"
            else -> "TEXT"
        }
}

data class ScreenSnapshot(
    val packageName: String,
    val appTitle: String,
    val capturedAtMillis: Long,
    val nodes: List<ScreenNodeItem>,
    val isSimulatedFallback: Boolean = false
) {
    val clickableNodes: List<ScreenNodeItem>
        get() = nodes.filter { it.isClickable }

    val editableNodes: List<ScreenNodeItem>
        get() = nodes.filter { it.isEditable }

    val summaryText: String
        get() = "App: $appTitle ($packageName) • ${nodes.size} nodes (${clickableNodes.size} clickable, ${editableNodes.size} inputs)"
}

data class DeviceTelemetry(
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 31.5f,
    val mediaVolumePercent: Int = 50,
    val ringVolumePercent: Int = 70,
    val ringerModeLabel: String = "Normal",
    val brightnessPercent: Int = 60,
    val isFlashlightOn: Boolean = false,
    val isWifiConnected: Boolean = true,
    val networkTypeLabel: String = "Wi-Fi",
    val isBluetoothEnabled: Boolean = false,
    val isDndEnabled: Boolean = false,
    val isAutoRotateEnabled: Boolean = true,
    val storageFreeGb: Float = 24.5f,
    val storageTotalGb: Float = 64.0f,
    val storageUsedPercent: Int = 62
)

data class ActiveTimerItem(
    val id: Long,
    val label: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val isRunning: Boolean = true
)

data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val relationshipTag: String = ""
)

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTimeMillis: Long,
    val isImportant: Boolean = false
)

data class JarvisFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val extension: String,
    val categoryLabel: String
)

data class SessionContext(
    val lastContactName: String? = null,
    val lastContactPhone: String? = null,
    val lastAppName: String? = null,
    val lastMessageText: String? = null,
    val lastSearchQuery: String? = null,
    val lastActionType: ActionType? = null,
    val awaitingFollowUpFor: String? = null, // e.g., "WHATSAPP_MESSAGE_TEXT" or "WHATSAPP_CONTACT"
    val recentTurns: List<Pair<String, String>> = emptyList()
)

data class JarvisSettings(
    val wakeWordEnabled: Boolean = false,
    val cloudAiEnabled: Boolean = true,
    val agentModeEnabled: Boolean = false,
    val confirmMediumRisk: Boolean = true,
    val longTermMemoryEnabled: Boolean = true,
    val voiceResponseEnabled: Boolean = true,
    val speechRate: Float = 1.05f,
    val speechPitch: Float = 0.98f,
    val preferredLanguage: String = "Hinglish (EN + HI)",
    val batteryMode: BatteryMode = BatteryMode.BALANCED
)
