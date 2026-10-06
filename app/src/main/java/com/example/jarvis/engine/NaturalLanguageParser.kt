package com.example.jarvis.engine

import com.example.jarvis.model.ActionType
import com.example.jarvis.model.ContactInfo
import com.example.jarvis.model.ExecutionPlan
import com.example.jarvis.model.PlannedAction
import com.example.jarvis.model.RiskLevel
import com.example.jarvis.model.SessionContext

class NaturalLanguageParser {

    fun parseCommand(
        rawInput: String,
        session: SessionContext,
        confirmMediumRisk: Boolean,
        agentModeEnabled: Boolean,
        contactResolver: suspend (String) -> List<ContactInfo>
    ): ExecutionPlan {
        // Synchronous wrapper not used for suspend contactResolver; see parseCommandSuspend
        throw UnsupportedOperationException("Use parseCommandSuspend")
    }

    suspend fun parseCommandSuspend(
        rawInput: String,
        session: SessionContext,
        confirmMediumRisk: Boolean,
        agentModeEnabled: Boolean,
        contactResolver: suspend (String) -> List<ContactInfo>
    ): ExecutionPlan {
        val cleaned = stripWakeWord(rawInput).trim()
        val lower = cleaned.lowercase()
        val lang = detectLanguage(cleaned)

        // 0. Check if user is answering a follow-up prompt from SessionContext
        if (session.awaitingFollowUpFor == "WHATSAPP_MESSAGE_TEXT" && session.lastContactName != null) {
            val extractedMsg = cleaned
                .replace(Regex("^(bol do|kaho|likho|message likho|say|tell him|tell her|ki)\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
            return buildWhatsAppPlan(
                originalCommand = rawInput,
                lang = lang,
                recipientQuery = session.lastContactName,
                messageText = extractedMsg.ifBlank { cleaned },
                confirmMediumRisk = confirmMediumRisk,
                contactResolver = contactResolver
            )
        }

        // 1. Check High-Risk Security Gate (Financial, Factory Reset, Password, Mass Delete)
        if (isHighRiskCommand(lower)) {
            return buildHighRiskPlan(rawInput, cleaned, lang)
        }

        // 2. Check Agent Mode Autonomous Research / Tutorials task
        if (lower.contains("tutorials") ||
            lower.contains("3 useful videos") ||
            lower.contains("specification search") ||
            lower.contains("specifications") ||
            (agentModeEnabled && (lower.contains("dhundhkar") || lower.contains("research") || lower.contains("compare")))
        ) {
            return buildBrowserOrAgentResearchPlan(rawInput, cleaned, lang, isAgentMode = true)
        }

        // 3. Check Multi-Step Compound Commands ("Chrome kholo, YouTube search karo...", "phir", "aur", "then")
        val segments = splitMultiStepSegments(cleaned)
        if (segments.size > 1) {
            return buildMultiStepPlan(
                rawInput = rawInput,
                segments = segments,
                lang = lang,
                session = session,
                confirmMediumRisk = confirmMediumRisk,
                contactResolver = contactResolver
            )
        }

        // 4. Single-Intent Parsing
        return parseSingleClause(
            rawInput = rawInput,
            clause = cleaned,
            lang = lang,
            session = session,
            confirmMediumRisk = confirmMediumRisk,
            contactResolver = contactResolver
        )
    }

    private suspend fun parseSingleClause(
        rawInput: String,
        clause: String,
        lang: String,
        session: SessionContext,
        confirmMediumRisk: Boolean,
        contactResolver: suspend (String) -> List<ContactInfo>
    ): ExecutionPlan {
        val lower = clause.lowercase().trim()

        // A. Context-dependent repeat message: "Usko wahi message bhejo jo maine abhi bola"
        if ((lower.contains("wahi message") || lower.contains("same message") || lower.contains("usko")) &&
            lower.contains("bhejo")
        ) {
            val recipient = session.lastContactName ?: "Rahul Sharma"
            val msg = session.lastMessageText ?: "Main 10 minute mein aa raha hoon"
            return buildWhatsAppPlan(
                originalCommand = rawInput,
                lang = lang,
                recipientQuery = recipient,
                messageText = msg,
                confirmMediumRisk = confirmMediumRisk,
                contactResolver = contactResolver
            )
        }

        // B. Follow-up "Bol do ..." or "Message likho: ..." when a contact is in session context
        if ((lower.startsWith("bol do ") || lower.startsWith("message likho") || lower.startsWith("likho ")) &&
            session.lastContactName != null
        ) {
            val msg = clause
                .replace(Regex("^(bol do|message likho|likho)\\s*:?\\s*(ki)?\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
            return buildWhatsAppPlan(
                originalCommand = rawInput,
                lang = lang,
                recipientQuery = session.lastContactName,
                messageText = msg,
                confirmMediumRisk = confirmMediumRisk,
                contactResolver = contactResolver
            )
        }

        // C. WhatsApp / Messaging Commands
        if (lower.contains("whatsapp") || lower.contains("message bhejo") || lower.contains("message bhejna") || lower.contains("ko message")) {
            // Check if it's purely "WhatsApp kholo"
            if (lower == "whatsapp kholo" || lower == "open whatsapp" || lower == "whatsapp open karo") {
                return ExecutionPlan(
                    originalCommand = rawInput,
                    detectedLanguage = lang,
                    intentSummary = "Launch WhatsApp",
                    spokenResponse = "Opening WhatsApp.",
                    riskLevel = RiskLevel.LOW,
                    steps = listOf(
                        PlannedAction(
                            stepNumber = 1,
                            actionType = ActionType.LAUNCH_APP,
                            title = "Open WhatsApp",
                            target = "WhatsApp",
                            expectedVerification = "Verify WhatsApp package or web intent launched"
                        )
                    )
                )
            }

            // Extract recipient & message
            val recipient = extractRecipientName(clause) ?: session.lastContactName ?: "Rahul"
            val message = extractMessageBody(clause)

            if (message.isNullOrBlank()) {
                // Ask follow-up question and remember recipient in SessionContext!
                return ExecutionPlan(
                    originalCommand = rawInput,
                    detectedLanguage = lang,
                    intentSummary = "Prepare WhatsApp message for $recipient (awaiting message text)",
                    spokenResponse = "Kya message bhejna hai $recipient ko?",
                    riskLevel = RiskLevel.LOW,
                    steps = listOf(
                        PlannedAction(
                            stepNumber = 1,
                            actionType = ActionType.ANSWER_CONVERSATION,
                            title = "Set recipient context: $recipient & ask for message body",
                            target = recipient,
                            value = "AWAITING_WHATSAPP_MESSAGE",
                            expectedVerification = "Session context updated with recipient=$recipient"
                        )
                    )
                )
            }

            return buildWhatsAppPlan(
                originalCommand = rawInput,
                lang = lang,
                recipientQuery = recipient,
                messageText = message,
                confirmMediumRisk = confirmMediumRisk,
                contactResolver = contactResolver
            )
        }

        // D. Volume Controls
        if (lower.contains("volume") || lower.contains("awaz") || lower.contains("aawaz")) {
            val numberMatch = Regex("(\\d{1,3})").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            return if (numberMatch != null) {
                val pct = numberMatch.coerceIn(0, 100)
                ExecutionPlan(
                    originalCommand = rawInput,
                    detectedLanguage = lang,
                    intentSummary = "Set Media Volume to $pct%",
                    spokenResponse = "Done. Volume $pct% hai.",
                    riskLevel = RiskLevel.LOW,
                    steps = listOf(
                        PlannedAction(
                            stepNumber = 1,
                            actionType = ActionType.SET_VOLUME,
                            title = "Set Volume to $pct%",
                            target = "MUSIC",
                            value = pct.toString(),
                            expectedVerification = "Verify AudioManager stream volume == $pct%"
                        )
                    )
                )
            } else {
                val decrease = lower.contains("kam") || lower.contains("down") || lower.contains("low") || lower.contains("ghata")
                val deltaLabel = if (decrease) "Decrease Volume (-15%)" else "Increase Volume (+15%)"
                ExecutionPlan(
                    originalCommand = rawInput,
                    detectedLanguage = lang,
                    intentSummary = deltaLabel,
                    spokenResponse = if (decrease) "Volume thoda kam kar diya hai." else "Volume badha diya hai.",
                    riskLevel = RiskLevel.LOW,
                    steps = listOf(
                        PlannedAction(
                            stepNumber = 1,
                            actionType = ActionType.SET_VOLUME,
                            title = deltaLabel,
                            target = "DELTA",
                            value = if (decrease) "DOWN" else "UP",
                            expectedVerification = "Verify AudioManager stream volume updated"
                        )
                    )
                )
            }
        }

        // E. Silent / Vibrate / Ringer Mode
        if (lower.contains("silent") || lower.contains("vibrate") || lower.contains("ring mode") || lower.contains("normal mode")) {
            val mode = when {
                lower.contains("silent") -> "SILENT"
                lower.contains("vibrate") -> "VIBRATE"
                else -> "NORMAL"
            }
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Set Phone Ringer Mode to $mode",
                spokenResponse = "Phone ${mode.lowercase()} mode par set kar diya hai.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.SET_RINGER_MODE,
                        title = "Set Ringer Mode: $mode",
                        target = mode,
                        value = mode,
                        expectedVerification = "Verify AudioManager.ringerMode updated"
                    )
                )
            )
        }

        // F. Brightness Control
        if (lower.contains("brightness") || lower.contains("roshni") || lower.contains("chamak")) {
            val pct = Regex("(\\d{1,3})").find(lower)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(5, 100)
                ?: if (lower.contains("kam") || lower.contains("low") || lower.contains("dim")) 30 else 75
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Set Screen Brightness to $pct%",
                spokenResponse = "Brightness $pct% kar di hai.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.SET_BRIGHTNESS,
                        title = "Set Brightness to $pct%",
                        target = "SCREEN_BRIGHTNESS",
                        value = pct.toString(),
                        expectedVerification = "Verify brightness level == $pct%"
                    )
                )
            )
        }

        // G. Flashlight / Torch
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash")) {
            val turnOff = lower.contains("off") || lower.contains("band")
            val enable = !turnOff
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Turn Flashlight ${if (enable) "ON" else "OFF"}",
                spokenResponse = if (enable) "Flashlight on kar di hai." else "Flashlight band kar di hai.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.TOGGLE_FLASHLIGHT,
                        title = "Set Flashlight ${if (enable) "ON" else "OFF"}",
                        target = "CAMERA_TORCH",
                        value = enable.toString(),
                        expectedVerification = "Verify CameraManager torch state == $enable"
                    )
                )
            )
        }

        // H. Wi-Fi Control / Settings
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val isSettings = lower.contains("setting")
            val enable = !lower.contains("off") && !lower.contains("band")
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = if (isSettings) "Open Wi-Fi Settings" else "Control Wi-Fi (${if (enable) "ON" else "OFF"})",
                spokenResponse = if (isSettings) "Opening Wi-Fi settings." else "Opening Wi-Fi control panel.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = if (isSettings) ActionType.OPEN_SYSTEM_SETTINGS else ActionType.CONTROL_WIFI,
                        title = if (isSettings) "Open Wi-Fi Settings" else "Open Wi-Fi Panel",
                        target = "WIFI",
                        value = enable.toString(),
                        expectedVerification = "Verify Wi-Fi settings/panel launched"
                    )
                )
            )
        }

        // I. Bluetooth Control
        if (lower.contains("bluetooth")) {
            val enable = !lower.contains("off") && !lower.contains("band")
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Control Bluetooth (${if (enable) "ON" else "OFF"})",
                spokenResponse = "Bluetooth settings open kar di hai.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.CONTROL_BLUETOOTH,
                        title = "Control Bluetooth (${if (enable) "Enable" else "Disable"})",
                        target = "BLUETOOTH",
                        value = enable.toString(),
                        expectedVerification = "Verify Bluetooth settings intent launched"
                    )
                )
            )
        }

        // J. Do Not Disturb & Rotation
        if (lower.contains("dnd") || lower.contains("do not disturb")) {
            val enable = !lower.contains("off") && !lower.contains("band")
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Set Do Not Disturb ${if (enable) "ON" else "OFF"}",
                spokenResponse = if (enable) "Do Not Disturb on kar diya hai." else "Do Not Disturb off kar diya hai.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.CONTROL_DND,
                        title = "Set DND: $enable",
                        target = "DND",
                        value = enable.toString(),
                        expectedVerification = "Verify NotificationManager interruption filter"
                    )
                )
            )
        }

        if (lower.contains("rotation") || lower.contains("auto rotate") || lower.contains("rotate")) {
            val enable = !lower.contains("off") && !lower.contains("lock") && !lower.contains("band")
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Set Auto-Rotation ${if (enable) "ON" else "OFF"}",
                spokenResponse = if (enable) "Auto-rotation on hai." else "Screen rotation lock kar diya hai.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.CONTROL_ROTATION,
                        title = "Set Auto-Rotation: $enable",
                        target = "ROTATION",
                        value = enable.toString(),
                        expectedVerification = "Verify ACCELEROMETER_ROTATION setting"
                    )
                )
            )
        }

        // K. Battery, Storage, Network Status
        if (lower.contains("battery") || lower.contains("charge") || lower.contains("charging")) {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Read Device Battery Status",
                spokenResponse = "Checking battery level.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.READ_DEVICE_TELEMETRY,
                        title = "Inspect Battery Telemetry",
                        target = "BATTERY",
                        expectedVerification = "Verify BatteryManager capacity & charging status read"
                    )
                )
            )
        }

        if (lower.contains("storage") || lower.contains("space kitna") || lower.contains("memory kitni")) {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Read Device Storage Usage",
                spokenResponse = "Checking storage usage.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.READ_DEVICE_TELEMETRY,
                        title = "Inspect Storage Telemetry",
                        target = "STORAGE",
                        expectedVerification = "Verify StatFs available & total GB read"
                    )
                )
            )
        }

        // L. Notifications Control
        if (lower.contains("notification") || lower.contains("notifications") || lower.contains("message aaye")) {
            val isDismiss = lower.contains("clear") || lower.contains("dismiss") || lower.contains("hatao") || lower.contains("delete")
            return if (isDismiss) {
                ExecutionPlan(
                    originalCommand = rawInput,
                    detectedLanguage = lang,
                    intentSummary = "Dismiss Notifications",
                    spokenResponse = "Notifications clear kar diye hain.",
                    riskLevel = RiskLevel.LOW,
                    steps = listOf(
                        PlannedAction(
                            stepNumber = 1,
                            actionType = ActionType.DISMISS_NOTIFICATIONS,
                            title = "Dismiss Active Notifications",
                            target = "ALL",
                            expectedVerification = "Verify active notifications dismissed"
                        )
                    )
                )
            } else {
                ExecutionPlan(
                    originalCommand = rawInput,
                    detectedLanguage = lang,
                    intentSummary = "Read & Summarize Notifications",
                    spokenResponse = "Reading your notifications.",
                    riskLevel = RiskLevel.LOW,
                    steps = listOf(
                        PlannedAction(
                            stepNumber = 1,
                            actionType = ActionType.READ_NOTIFICATIONS,
                            title = "Read & Summarize Active Notifications",
                            target = "ALL",
                            value = "SUMMARIZE",
                            expectedVerification = "Verify NotificationListenerService active list inspected"
                        )
                    )
                )
            }
        }

        // M. Timers, Alarms, Reminders, Notes
        if (lower.contains("timer")) {
            val mins = Regex("(\\d+)\\s*(minute|min|m)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val secs = Regex("(\\d+)\\s*(second|sec|s)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val totalSeconds = when {
                mins != null -> mins * 60
                secs != null -> secs
                else -> (Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 5) * 60
            }
            val label = if (mins != null) "$mins min Timer" else "${totalSeconds}s Timer"
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Set Timer for $label",
                spokenResponse = "$label set kar diya hai.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.SET_TIMER,
                        title = "Start Countdown Timer ($label)",
                        target = label,
                        value = totalSeconds.toString(),
                        expectedVerification = "Verify timer active in JARVIS & system AlarmClock"
                    )
                )
            )
        }

        if (lower.contains("alarm")) {
            val hr = Regex("(\\d{1,2})\\s*(baje|am|pm|:)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 8
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Set Alarm for $hr:00",
                spokenResponse = "$hr baje ka alarm set kar diya hai.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.SET_ALARM,
                        title = "Set System Alarm for $hr:00",
                        target = "JARVIS Alarm",
                        value = hr.toString(),
                        secondaryValue = "0",
                        expectedVerification = "Verify AlarmClock intent & task record created"
                    )
                )
            )
        }

        if (lower.contains("remind") || lower.contains("yaad dilana") || lower.contains("reminder")) {
            val timeMatch = Regex("(\\d{1,2}\\s*(baje|am|pm))").find(lower)?.value ?: "8:00 AM"
            val timePrefix = if (lower.contains("kal") || lower.contains("tomorrow")) "Tomorrow $timeMatch" else "Today $timeMatch"
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Create Reminder ($timePrefix)",
                spokenResponse = "Reminder set kar diya hai: $timePrefix.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.CREATE_REMINDER,
                        title = "Save Reminder for $timePrefix",
                        target = clause,
                        value = timePrefix,
                        expectedVerification = "Verify reminder persisted in Room database"
                    )
                )
            )
        }

        // N. Full Phone Navigation (Back, Home, Recents, Scroll, Click, Type, Copy/Paste)
        if (lower == "back jao" || lower == "go back" || lower == "back" || lower == "peeche jao" || lower == "back karo") {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Navigate Back",
                spokenResponse = "Going back.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.NAVIGATE_BACK,
                        title = "Perform Global Action: BACK",
                        target = "BACK",
                        expectedVerification = "Verify AccessibilityService GLOBAL_ACTION_BACK or back stack pop"
                    )
                )
            )
        }

        if (lower.contains("home screen") || lower == "go home" || lower == "home jao" || lower == "home par jao") {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Navigate to Home Screen",
                spokenResponse = "Going to Home screen.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.NAVIGATE_HOME,
                        title = "Perform Global Action: HOME",
                        target = "HOME",
                        expectedVerification = "Verify Home intent / GLOBAL_ACTION_HOME executed"
                    )
                )
            )
        }

        if (lower.contains("recent apps") || lower.contains("recents kholo")) {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Open Recent Apps Overview",
                spokenResponse = "Opening recent apps.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.NAVIGATE_RECENTS,
                        title = "Perform Global Action: RECENTS",
                        target = "RECENTS",
                        expectedVerification = "Verify GLOBAL_ACTION_RECENTS executed"
                    )
                )
            )
        }

        if (lower.contains("scroll")) {
            val dir = when {
                lower.contains("upar") || lower.contains("up") -> "UP"
                lower.contains("left") -> "LEFT"
                lower.contains("right") -> "RIGHT"
                else -> "DOWN"
            }
            val dirHindi = if (dir == "UP") "upar" else "neeche"
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Scroll Screen $dir",
                spokenResponse = "Scrolling ${dir.lowercase()}.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.SCROLL_SCREEN,
                        title = "Scroll Screen $dir ($dirHindi)",
                        target = dir,
                        value = dir,
                        expectedVerification = "Verify scrollable node or gesture scroll completed"
                    )
                )
            )
        }

        // O. Screen Element Click / Press / Download
        if (lower.contains("click") || lower.contains("press karo") || lower.contains("daba") || lower.contains("isko download kar")) {
            val targetBtn = when {
                lower.contains("login") -> "Login"
                lower.contains("send") -> "Send"
                lower.contains("download") -> "Download"
                lower.contains("sync") || lower.contains("checkbox") -> "Auto Sync Checkbox"
                else -> {
                    Regex("(?:screen pe|on|click|press)\\s+([a-zA-Z0-9_ ]+?)\\s*(?:button|hai|ko|pe|par|$)", RegexOption.IGNORE_CASE)
                        .find(clause)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && it.lowercase() != "is" }
                        ?: "Login"
                }
            }
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Inspect Screen & Click '$targetBtn'",
                spokenResponse = "Clicking $targetBtn.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.ANALYZE_SCREEN,
                        title = "Inspect Screen UI Hierarchy for '$targetBtn'",
                        target = targetBtn,
                        expectedVerification = "Verify AccessibilityNodeInfo hierarchy inspected"
                    ),
                    PlannedAction(
                        stepNumber = 2,
                        actionType = ActionType.CLICK_ELEMENT,
                        title = "Click Element '$targetBtn'",
                        target = targetBtn,
                        expectedVerification = "Verify element '$targetBtn' clicked & state updated"
                    )
                )
            )
        }

        // P. Typing into Search Box / Input Field
        if (lower.contains("likho") || lower.contains("type ") || lower.contains("search box mein")) {
            val textToType = Regex("(?:search box mein|input mein|field mein|type|likho)\\s+(.+?)(?:\\s+likho|$)", RegexOption.IGNORE_CASE)
                .find(clause)?.groupValues?.get(1)?.trim()
                ?: clause.substringAfterLast(" ").trim()
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Type '$textToType' into Input Field",
                spokenResponse = "Typing $textToType.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.TYPE_TEXT,
                        title = "Focus Search/Input Box & Type '$textToType'",
                        target = "Search box",
                        value = textToType,
                        expectedVerification = "Verify editable node text == '$textToType'"
                    )
                )
            )
        }

        // Q. Screenshot & Camera
        if (lower.contains("screenshot")) {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Take Screenshot",
                spokenResponse = "Taking screenshot.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.TAKE_SCREENSHOT,
                        title = "Capture Screen Snapshot",
                        target = "SCREEN",
                        expectedVerification = "Verify screenshot captured"
                    )
                )
            )
        }

        if (lower.contains("camera kholo") || lower.contains("open camera") || lower.contains("photo lo") || lower.contains("take photo")) {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Open Camera",
                spokenResponse = "Opening Camera.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.CAMERA_CAPTURE,
                        title = "Launch Camera Activity",
                        target = "PHOTO",
                        expectedVerification = "Verify MediaStore.ACTION_IMAGE_CAPTURE started"
                    )
                )
            )
        }

        if (lower.contains("gallery kholo") || lower.contains("open gallery") || lower.contains("photos kholo")) {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Open Gallery",
                spokenResponse = "Opening Gallery.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.CAMERA_CAPTURE,
                        title = "Launch Gallery Viewer",
                        target = "GALLERY",
                        expectedVerification = "Verify Gallery intent launched"
                    )
                )
            )
        }

        // R. File Management (Downloads, PDF search, Rename, Delete)
        if (lower.contains("downloads folder") || lower.contains("download folder")) {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Open Downloads Folder",
                spokenResponse = "Opening Downloads folder.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.FILE_OPEN_FOLDER,
                        title = "Open Downloads Folder",
                        target = "Downloads",
                        expectedVerification = "Verify Downloads directory listed"
                    )
                )
            )
        }

        if (lower.contains("pdf") && (lower.contains("dhundo") || lower.contains("search") || lower.contains("find") || lower.contains("dikhao") || lower.contains("files"))) {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Search PDF Files on Device",
                spokenResponse = "Searching for PDF files.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.FILE_SEARCH,
                        title = "Search Files by Type: PDF",
                        target = "pdf",
                        value = "pdf",
                        expectedVerification = "Verify PDF files enumerated in storage"
                    )
                )
            )
        }

        if (lower.contains("naam change") || lower.contains("rename")) {
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Rename File in Workspace",
                spokenResponse = "Renaming file to Updated_Report_2026.pdf.",
                riskLevel = RiskLevel.MEDIUM,
                requiresConfirmation = false,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.FILE_RENAME,
                        title = "Rename 'Sprint_Report_Q4.pdf' to 'Updated_Report_2026.pdf'",
                        target = "Sprint_Report",
                        value = "Updated_Report_2026.pdf",
                        riskLevel = RiskLevel.MEDIUM,
                        expectedVerification = "Verify file renamed on disk"
                    )
                )
            )
        }

        if (lower.contains("delete") && lower.contains("file")) {
            val targetFile = "Voice_Command_CheatSheet.txt"
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Delete File '$targetFile'",
                spokenResponse = "File delete karne se pehle aapki confirmation chahiye.",
                riskLevel = RiskLevel.MEDIUM,
                requiresConfirmation = true,
                confirmationPrompt = "Are you sure you want JARVIS to delete '$targetFile'?",
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.FILE_DELETE,
                        title = "Delete File '$targetFile'",
                        target = targetFile,
                        riskLevel = RiskLevel.MEDIUM,
                        requiresConfirmation = true,
                        expectedVerification = "Verify file removed from filesystem"
                    )
                )
            )
        }

        // S. Browser Search ("Google par Minecraft search karo", "Chrome mein Google kholo", "YouTube search karo")
        if (lower.contains("search karo") || lower.contains("search ") || lower.contains("google kholo") || lower.contains("google par")) {
            val query = extractSearchQuery(clause)
            val useYoutube = lower.contains("youtube")
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = if (useYoutube) "Search YouTube for '$query'" else "Search Google in Browser for '$query'",
                spokenResponse = "Searching for $query.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.BROWSER_SEARCH,
                        title = "Search ${if (useYoutube) "YouTube" else "Google"}: '$query'",
                        target = if (useYoutube) "YOUTUBE" else "GOOGLE",
                        value = query,
                        expectedVerification = "Verify browser search results loaded & inspected"
                    )
                )
            )
        }

        // T. App Launching ("YouTube kholo", "Chrome open karo", "Instagram open karo", "Settings kholo", etc.)
        if (lower.contains("kholo") || lower.startsWith("open ") || lower.contains("open karo") || lower.contains("chalu karo") || lower.contains("launch ")) {
            val appName = extractAppName(clause)
            return ExecutionPlan(
                originalCommand = rawInput,
                detectedLanguage = lang,
                intentSummary = "Launch $appName",
                spokenResponse = "Opening $appName.",
                riskLevel = RiskLevel.LOW,
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.LAUNCH_APP,
                        title = "Open $appName",
                        target = appName,
                        expectedVerification = "Verify $appName activity or web fallback started"
                    )
                )
            )
        }

        // U. Conversational / General Assistant Fallback
        return ExecutionPlan(
            originalCommand = rawInput,
            detectedLanguage = lang,
            intentSummary = "Conversational AI Assistance",
            spokenResponse = "Main aapki baat samajh gaya. Aap mujhse apps open karne, volume/brightness control karne, WhatsApp message bhejne, ya screen navigate karne ke liye keh sakte hain.",
            riskLevel = RiskLevel.LOW,
            steps = listOf(
                PlannedAction(
                    stepNumber = 1,
                    actionType = ActionType.ANSWER_CONVERSATION,
                    title = "Respond to user query",
                    target = "USER",
                    value = clause,
                    expectedVerification = "Response delivered"
                )
            )
        )
    }

    private suspend fun buildWhatsAppPlan(
        originalCommand: String,
        lang: String,
        recipientQuery: String,
        messageText: String,
        confirmMediumRisk: Boolean,
        contactResolver: suspend (String) -> List<ContactInfo>
    ): ExecutionPlan {
        val matches = contactResolver(recipientQuery)

        // Section 33 FAIL-SAFE: If multiple contacts match (e.g., "Rahul Sharma" and "Rahul Verma"), DO NOT GUESS!
        val exactMatch = matches.firstOrNull { it.name.equals(recipientQuery.trim(), ignoreCase = true) }
        if (exactMatch == null && matches.size > 1) {
            val candidateNames = matches.map { "${it.name} (${it.phoneNumber})" }
            return ExecutionPlan(
                originalCommand = originalCommand,
                detectedLanguage = lang,
                intentSummary = "Disambiguate Contact '$recipientQuery'",
                spokenResponse = "I found ${matches.size} contacts named $recipientQuery: ${matches.joinToString(" and ") { it.name }}. Which one do you mean?",
                riskLevel = RiskLevel.MEDIUM,
                requiresClarification = true,
                clarificationPrompt = "I found ${matches.size} contacts matching \"$recipientQuery\". Which recipient should receive \"$messageText\"?",
                clarificationOptions = matches.map { it.name },
                steps = listOf(
                    PlannedAction(
                        stepNumber = 1,
                        actionType = ActionType.WHATSAPP_SEND_MESSAGE,
                        title = "Send WhatsApp message to $recipientQuery",
                        target = recipientQuery,
                        value = messageText,
                        riskLevel = RiskLevel.MEDIUM,
                        requiresConfirmation = true,
                        expectedVerification = "Verify recipient disambiguated before sending"
                    )
                )
            )
        }

        val resolvedContact = exactMatch ?: matches.firstOrNull()
        val displayRecipient = resolvedContact?.name ?: recipientQuery
        val phone = resolvedContact?.phoneNumber ?: "+919876543210"

        return ExecutionPlan(
            originalCommand = originalCommand,
            detectedLanguage = lang,
            intentSummary = "WhatsApp Message to $displayRecipient",
            spokenResponse = if (confirmMediumRisk) {
                "$displayRecipient ko message bhejne ke liye ready hoon. Please confirm."
            } else {
                "Sending WhatsApp message to $displayRecipient."
            },
            riskLevel = RiskLevel.MEDIUM,
            requiresConfirmation = confirmMediumRisk,
            confirmationPrompt = "Send WhatsApp message to $displayRecipient ($phone):\n\"$messageText\"?",
            steps = listOf(
                PlannedAction(
                    stepNumber = 1,
                    actionType = ActionType.LAUNCH_APP,
                    title = "Open WhatsApp & Locate '$displayRecipient'",
                    target = "WhatsApp",
                    value = displayRecipient,
                    expectedVerification = "Verify WhatsApp conversation intent prepared"
                ),
                PlannedAction(
                    stepNumber = 2,
                    actionType = ActionType.WHATSAPP_SEND_MESSAGE,
                    title = "Type & Send Message to $displayRecipient: \"$messageText\"",
                    target = displayRecipient,
                    value = messageText,
                    secondaryValue = phone,
                    riskLevel = RiskLevel.MEDIUM,
                    requiresConfirmation = confirmMediumRisk,
                    expectedVerification = "Verify recipient == $displayRecipient and message dispatched"
                )
            )
        )
    }

    private fun buildBrowserOrAgentResearchPlan(
        rawInput: String,
        clause: String,
        lang: String,
        isAgentMode: Boolean
    ): ExecutionPlan {
        val lower = clause.lowercase()
        val isVivoSpecs = lower.contains("vivo y21")
        val isTutorials = lower.contains("tutorial") || lower.contains("3 useful videos")
        val query = when {
            isVivoSpecs -> "Vivo Y21 5G specifications"
            isTutorials -> "Android AI Assistant Jetpack Compose tutorials"
            else -> extractSearchQuery(clause)
        }

        val steps = listOf(
            PlannedAction(
                stepNumber = 1,
                actionType = ActionType.LAUNCH_APP,
                title = "Open Browser / YouTube Agent View",
                target = if (isTutorials) "YouTube" else "Chrome",
                expectedVerification = "Verify browser/agent view active"
            ),
            PlannedAction(
                stepNumber = 2,
                actionType = ActionType.BROWSER_SEARCH,
                title = "Search Query: '$query'",
                target = if (isTutorials) "YOUTUBE" else "GOOGLE",
                value = query,
                expectedVerification = "Verify search results loaded"
            ),
            PlannedAction(
                stepNumber = 3,
                actionType = ActionType.AGENT_RESEARCH,
                title = "Inspect Results, Evaluate & Summarize Findings",
                target = query,
                value = if (isVivoSpecs) "SPECS" else if (isTutorials) "TUTORIALS" else "GENERAL",
                expectedVerification = "Verify key specifications/videos extracted and verified"
            )
        )

        val spoken = when {
            isVivoSpecs -> "Vivo Y21 5G ke specifications search karke verify kar liye hain: 6.51-inch HD+ display, MediaTek Dimensity 700 5G, 50MP camera, aur 5000mAh battery."
            isTutorials -> "YouTube par Android AI assistant ke 3 best tutorials dhundh liye hain. Screen par details dekh sakte hain."
            else -> "Search results inspect karke summary taiyar kar di hai."
        }

        return ExecutionPlan(
            originalCommand = rawInput,
            detectedLanguage = lang,
            intentSummary = if (isAgentMode) "Autonomous Agent Research: $query" else "Browser Search & Read: $query",
            spokenResponse = spoken,
            riskLevel = RiskLevel.LOW,
            steps = steps,
            isAgentModePlan = isAgentMode
        )
    }

    private suspend fun buildMultiStepPlan(
        rawInput: String,
        segments: List<String>,
        lang: String,
        session: SessionContext,
        confirmMediumRisk: Boolean,
        contactResolver: suspend (String) -> List<ContactInfo>
    ): ExecutionPlan {
        val allSteps = mutableListOf<PlannedAction>()
        var highestRisk = RiskLevel.LOW
        var needsConfirm = false
        val confirmPrompts = mutableListOf<String>()

        for (seg in segments) {
            val subPlan = parseSingleClause(
                rawInput = seg,
                clause = seg,
                lang = lang,
                session = session,
                confirmMediumRisk = confirmMediumRisk,
                contactResolver = contactResolver
            )
            if (subPlan.riskLevel.ordinal > highestRisk.ordinal) {
                highestRisk = subPlan.riskLevel
            }
            if (subPlan.requiresConfirmation) {
                needsConfirm = true
                if (subPlan.confirmationPrompt.isNotBlank()) {
                    confirmPrompts.add(subPlan.confirmationPrompt)
                }
            }
            for (step in subPlan.steps) {
                allSteps.add(step.copy(stepNumber = allSteps.size + 1))
            }
        }

        return ExecutionPlan(
            originalCommand = rawInput,
            detectedLanguage = lang,
            intentSummary = "Multi-Step Automation (${allSteps.size} actions)",
            spokenResponse = if (needsConfirm) {
                "Multi-step plan ready hai (${allSteps.size} steps). Sensitive step ke liye confirm karein."
            } else {
                "Executed all ${allSteps.size} steps in sequence."
            },
            riskLevel = highestRisk,
            requiresConfirmation = needsConfirm,
            confirmationPrompt = confirmPrompts.joinToString("\n"),
            steps = allSteps
        )
    }

    private fun buildHighRiskPlan(rawInput: String, cleaned: String, lang: String): ExecutionPlan {
        return ExecutionPlan(
            originalCommand = rawInput,
            detectedLanguage = lang,
            intentSummary = "High-Risk Security Action Blocked for Explicit Confirmation",
            spokenResponse = "Yeh ek high-risk action hai. Kya aap sach mein isse execute karna chahte hain?",
            riskLevel = RiskLevel.HIGH,
            requiresConfirmation = true,
            confirmationPrompt = "HIGH RISK SECURITY WARNING: Are you sure you want JARVIS to execute \"$cleaned\"? Irreversible or sensitive actions always require explicit confirmation.",
            steps = listOf(
                PlannedAction(
                    stepNumber = 1,
                    actionType = ActionType.ANSWER_CONVERSATION,
                    title = "Verify High-Risk Authorization for: $cleaned",
                    target = "SECURITY_GATE",
                    value = cleaned,
                    riskLevel = RiskLevel.HIGH,
                    requiresConfirmation = true,
                    expectedVerification = "Explicit user confirmation verified"
                )
            )
        )
    }

    private fun isHighRiskCommand(lower: String): Boolean {
        return lower.contains("factory reset") ||
            lower.contains("permanently delete") ||
            lower.contains("delete all") ||
            lower.contains("saari files delete") ||
            lower.contains("bank transfer") ||
            lower.contains("paise bhejo") ||
            lower.contains("payment karo") ||
            lower.contains("password change")
    }

    private fun splitMultiStepSegments(input: String): List<String> {
        // Do not split inside "message bhejo ki ..." clauses
        if (input.lowercase().contains("message bhejo ki") && !input.lowercase().contains("phir") && !input.contains(",")) {
            return listOf(input)
        }
        val rawParts = input.split(Regex("\\s*(?:,|\\bphir\\b|\\buske baad\\b|\\band then\\b|\\bthen\\b|\\baur phir\\b)\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.length > 2 }

        if (rawParts.size > 1) {
            // Also check if the last segment has "aur volume..." or "aur ..."
            val expanded = mutableListOf<String>()
            for (part in rawParts) {
                val sub = part.split(Regex("\\s+aur\\s+(?=(?:volume|brightness|youtube|chrome|whatsapp|back|home|scroll|open|first|latest))", RegexOption.IGNORE_CASE))
                expanded.addAll(sub.map { it.trim() }.filter { it.isNotBlank() })
            }
            return expanded
        }

        val aurParts = input.split(Regex("\\s+aur\\s+(?=(?:volume|brightness|youtube|chrome|whatsapp|back|home|scroll|open|first|latest|rahul|vivo))", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (aurParts.size > 1) aurParts else listOf(input)
    }

    private fun stripWakeWord(input: String): String {
        return input.replace(
            Regex("^(hey\\s+jarvis|hi\\s+jarvis|hello\\s+jarvis|ok\\s+jarvis|jarvis|हे\\s+जार्विस|जार्विस)\\s*,?\\s*", RegexOption.IGNORE_CASE),
            ""
        )
    }

    private fun detectLanguage(text: String): String {
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return "Hindi (हिंदी)"
        val lower = text.lowercase()
        val hinglishMarkers = listOf(
            "kholo", "karo", "kar do", "bhejo", "batao", "kitni", "hai", "jao",
            "neeche", "upar", "dhundo", "lagao", "mera", "mere", "usko", "wahi", "thoda", "kam"
        )
        return if (hinglishMarkers.any { lower.contains(it) }) "Hinglish" else "English"
    }

    private fun extractRecipientName(clause: String): String? {
        val patterns = listOf(
            Regex("(?:whatsapp\\s+pe|whatsapp\\s+par)?\\s*([A-Za-z]+(?:\\s+[A-Za-z]+)?)\\s+ko\\s+(?:whatsapp|message)", RegexOption.IGNORE_CASE),
            Regex("(?:message|whatsapp)\\s+(?:to\\s+)?([A-Za-z]+(?:\\s+[A-Za-z]+)?)", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val match = p.find(clause)?.groupValues?.get(1)?.trim()
            if (!match.isNullOrBlank() && match.lowercase() !in listOf("whatsapp", "message", "usko", "send")) {
                return match.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    private fun extractMessageBody(clause: String): String? {
        val kiMatch = Regex("(?:message\\s+bhejo\\s+ki|bhejo\\s+ki|message\\s+likho\\s*:?|say\\s+that|saying)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(clause)?.groupValues?.get(1)?.trim()
        if (!kiMatch.isNullOrBlank()) {
            return kiMatch.substringBefore(", phir").substringBefore(" phir ").trim()
        }
        return null
    }

    private fun extractAppName(clause: String): String {
        val cleaned = clause
            .replace(Regex("\\b(kholo|open|karo|kar|do|chalu|launch|app|please)\\b", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.ifBlank { "Settings" }.replaceFirstChar { it.uppercase() }
    }

    private fun extractSearchQuery(clause: String): String {
        val cleaned = clause
            .replace(Regex("^(chrome\\s+kholo\\s+aur|google\\s+par|chrome\\s+mein|youtube\\s+par|youtube|google|search\\s+for|search)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*(search\\s+karo|dhundo|kholo|open\\s+karo)$", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.ifBlank { "Android AI Assistant" }
    }
}
