package com.example.jarvis.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.jarvis.data.CommandHistoryEntity
import com.example.jarvis.model.AssistantState
import com.example.jarvis.model.DeviceTelemetry
import com.example.jarvis.model.ExecutionPlan
import com.example.jarvis.model.JarvisSettings
import com.example.jarvis.model.SessionContext
import com.example.jarvis.model.StepExecutionResult
import com.example.jarvis.ui.components.DisambiguationCard
import com.example.jarvis.ui.components.JarvisOrbHero
import com.example.jarvis.ui.components.SecurityConfirmationCard
import com.example.jarvis.viewmodel.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBorderCyan
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElevatedCard
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet

private data class SampleCommandGroup(
    val category: String,
    val commands: List<String>
)

private val SAMPLE_COMMAND_GROUPS = listOf(
    SampleCommandGroup(
        category = "Hinglish & Hindi",
        commands = listOf(
            "Hey Jarvis, YouTube kholo",
            "Volume 40% kar do",
            "Brightness 60% kar do",
            "Phone silent kar",
            "Flashlight on karo",
            "Battery kitni hai?",
            "Mere notifications batao"
        )
    ),
    SampleCommandGroup(
        category = "WhatsApp & Context",
        commands = listOf(
            "WhatsApp pe Rahul Sharma ko message bhejo ki main 10 minute mein aa raha hoon",
            "Rahul ko message bhejo ki meeting 5 baje hai",
            "Rahul Sharma ko message bhejna hai",
            "Bol do main 10 minute mein aa raha hoon",
            "Usko wahi message bhejo jo maine abhi bola"
        )
    ),
    SampleCommandGroup(
        category = "Multi-Step & Agent",
        commands = listOf(
            "Chrome kholo aur Vivo Y21 5G ka specification search karo",
            "JARVIS, mujhe YouTube par Android AI assistant ke tutorials dhundhkar 3 useful videos ke naam batao",
            "Chrome kholo, YouTube search karo, latest video open karo aur volume 50% kar do",
            "WhatsApp kholo, Rahul Sharma ko message bhejo ki main ghar pahunch gaya hoon, phir YouTube kholo"
        )
    ),
    SampleCommandGroup(
        category = "Screen, Files & Timers",
        commands = listOf(
            "Screen pe Login button hai, uspe click karo",
            "Search box mein Minecraft likho",
            "Neeche scroll karo",
            "Back jao",
            "Downloads folder kholo",
            "PDF files dhundo",
            "30 minute ka timer lagao",
            "Kal 8 baje mujhe yaad dilana"
        )
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HudCommandScreen(
    viewModel: JarvisViewModel,
    assistantState: AssistantState,
    statusSubtitle: String,
    lastSpokenResponse: String,
    partialTranscript: String,
    waveformBars: List<Float>,
    currentPlan: ExecutionPlan?,
    stepResults: List<StepExecutionResult>,
    pendingConfirmation: ExecutionPlan?,
    pendingDisambiguation: ExecutionPlan?,
    telemetry: DeviceTelemetry,
    settings: JarvisSettings,
    sessionContext: SessionContext,
    commandHistory: List<CommandHistoryEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var commandInputText by rememberSaveable { mutableStateOf("") }
    var selectedCategoryIndex by rememberSaveable { mutableStateOf(0) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.startVoiceListening()
        }
    }

    val triggerMicOrRequestPermission = {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            if (assistantState == AssistantState.LISTENING) {
                viewModel.stopVoiceListening()
            } else {
                viewModel.startVoiceListening()
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top HUD Header Card with Telemetry Art Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                    border = BorderStroke(1.dp, JarvisBorderCyan)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_jarvis_hud_banner_1791299970209),
                            contentDescription = "JARVIS Neural Telemetry Header",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.22f)
                        )
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "J.A.R.V.I.S. CORE OS",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = JarvisCyan
                                    )
                                    Text(
                                        text = "Observe → Plan → Act → Verify → Correct",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextSecondary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = settings.wakeWordEnabled,
                                        onClick = { viewModel.toggleWakeWordStandby(!settings.wakeWordEnabled) },
                                        label = {
                                            Text(
                                                if (settings.wakeWordEnabled) "\"Hey JARVIS\" ON" else "Wake Word",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Hearing,
                                                contentDescription = "Wake Word Toggle",
                                                modifier = Modifier.size(15.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = JarvisCyan,
                                            selectedLeadingIconColor = JarvisCyan
                                        ),
                                        modifier = Modifier.testTag("wake_word_toggle_chip")
                                    )
                                    FilterChip(
                                        selected = settings.agentModeEnabled,
                                        onClick = { viewModel.toggleAgentMode(!settings.agentModeEnabled) },
                                        label = {
                                            Text(
                                                if (settings.agentModeEnabled) "Agent ON" else "Agent Mode",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = "Autonomous Agent Mode",
                                                modifier = Modifier.size(15.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = JarvisViolet.copy(alpha = 0.25f),
                                            selectedLabelColor = Color.White,
                                            selectedLeadingIconColor = JarvisCyan
                                        ),
                                        modifier = Modifier.testTag("agent_mode_toggle_chip")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Live Telemetry Ribbon
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TelemetryMiniBadge(
                                    icon = Icons.Default.BatteryChargingFull,
                                    text = "${telemetry.batteryPercent}% ${if (telemetry.isCharging) "⚡" else ""}",
                                    tint = JarvisEmerald
                                )
                                TelemetryMiniBadge(
                                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                                    text = "Vol ${telemetry.mediaVolumePercent}% (${telemetry.ringerModeLabel})",
                                    tint = JarvisCyan
                                )
                                TelemetryMiniBadge(
                                    icon = Icons.Default.Brightness6,
                                    text = "Bri ${telemetry.brightnessPercent}%",
                                    tint = JarvisAmber
                                )
                                TelemetryMiniBadge(
                                    icon = Icons.Default.Wifi,
                                    text = telemetry.networkTypeLabel,
                                    tint = JarvisCyan
                                )
                                TelemetryMiniBadge(
                                    icon = Icons.Default.Storage,
                                    text = "${telemetry.storageFreeGb}GB Free",
                                    tint = JarvisTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 2. Central JARVIS Orb & Voice Waveform
            item {
                JarvisOrbHero(
                    state = assistantState,
                    statusSubtitle = statusSubtitle,
                    partialTranscript = partialTranscript,
                    waveformBars = waveformBars,
                    onOrbClick = triggerMicOrRequestPermission,
                    onStopClick = { viewModel.cancelActiveExecution() }
                )
            }

            // 3.JARVIS Spoken Response Bubble + Session Context Indicator
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisElevatedCard),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "JARVIS VOICE RESPONSE",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan
                            )
                            Text(
                                text = settings.batteryMode.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"$lastSpokenResponse\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = JarvisTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        if (sessionContext.lastContactName != null || sessionContext.awaitingFollowUpFor != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = JarvisViolet.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Session Memory Active: Contact=${sessionContext.lastContactName ?: "None"} • Last Msg=${sessionContext.lastMessageText ?: "Awaiting input"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisCyan,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Security Confirmation Modal OR Disambiguation Modal (if active)
            if (pendingConfirmation != null) {
                item {
                    SecurityConfirmationCard(
                        plan = pendingConfirmation,
                        onConfirm = { viewModel.confirmPendingPlan() },
                        onCancel = { viewModel.rejectPendingPlan() }
                    )
                }
            }

            if (pendingDisambiguation != null) {
                item {
                    DisambiguationCard(
                        plan = pendingDisambiguation,
                        onSelectOption = { option -> viewModel.selectDisambiguatedOption(option) },
                        onCancel = { viewModel.rejectPendingPlan() }
                    )
                }
            }

            // 5. Active Execution Plan & Reliability Verification Stepper
            if (currentPlan != null) {
                item {
                    ExecutionPlanVerifyCard(
                        plan = currentPlan,
                        stepResults = stepResults
                    )
                }
            }

            // 6. Quick Voice Command Matrix (English, Hindi & Hinglish)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                    border = BorderStroke(1.dp, JarvisBorderCyan)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "VOICE & NATURAL LANGUAGE COMMANDS (TAP TO EXECUTE)",
                            style = MaterialTheme.typography.labelMedium,
                            color = JarvisCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SAMPLE_COMMAND_GROUPS.forEachIndexed { idx, group ->
                                FilterChip(
                                    selected = selectedCategoryIndex == idx,
                                    onClick = { selectedCategoryIndex = idx },
                                    label = { Text(group.category, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = JarvisCyan
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val activeGroup = SAMPLE_COMMAND_GROUPS[selectedCategoryIndex]
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeGroup.commands.forEach { cmd ->
                                AssistChip(
                                    onClick = { viewModel.processUserCommand(cmd) },
                                    label = {
                                        Text(
                                            text = cmd,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = JarvisTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Run command",
                                            tint = JarvisCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = JarvisElevatedCard
                                    ),
                                    border = BorderStroke(1.dp, JarvisBorderCyan)
                                )
                            }
                        }
                    }
                }
            }

            // 7. Direct Hardware & System Controls Card (Verified via SystemController)
            item {
                HardwareControlsQuickCard(
                    telemetry = telemetry,
                    onVolumeCommit = { viewModel.setQuickVolume(it) },
                    onBrightnessCommit = { viewModel.setQuickBrightness(it) },
                    onToggleFlashlight = { viewModel.toggleQuickFlashlight() },
                    onWifiClick = { viewModel.processUserCommand("WiFi on kar do") },
                    onBluetoothClick = { viewModel.processUserCommand("Bluetooth on karo") },
                    onSilentClick = { viewModel.processUserCommand("Phone silent kar") }
                )
            }

            // 8. Recent Verified Command History
            if (commandHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "RECENT COMMAND HISTORY",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisTextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(commandHistory.take(6), key = { it.id }) { item ->
                    CommandHistoryMiniRow(
                        item = item,
                        onReplay = { viewModel.processUserCommand(item.userCommand) }
                    )
                }
            }
        }

        // Bottom Docked Voice + Text Command Input Bar
        Surface(
            color = JarvisGlassSurface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, JarvisBorderCyan)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = triggerMicOrRequestPermission,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (assistantState == AssistantState.LISTENING) JarvisCrimson else JarvisCyan
                        )
                        .testTag("bottom_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input Microphone",
                        tint = JarvisObsidian
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = commandInputText,
                    onValueChange = { commandInputText = it },
                    placeholder = {
                        Text(
                            "Speak or type in Hindi, English, Hinglish...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commandInputText.isNotBlank()) {
                                viewModel.processUserCommand(commandInputText)
                                commandInputText = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorderCyan,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        cursorColor = JarvisCyan
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_text_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (commandInputText.isNotBlank()) {
                            viewModel.processUserCommand(commandInputText)
                            commandInputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(JarvisElevatedCard)
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Execute Command",
                        tint = JarvisCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniBadge(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisElevatedCard.copy(alpha = 0.85f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = JarvisTextPrimary)
    }
}

@Composable
private fun ExecutionPlanVerifyCard(
    plan: ExecutionPlan,
    stepResults: List<StepExecutionResult>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("execution_plan_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STRUCTURED ACTION PLAN (${plan.detectedLanguage.uppercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisCyan
                    )
                    Text(
                        text = plan.intentSummary,
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary
                    )
                }
                Surface(
                    color = Color(plan.riskLevel.badgeColorHex).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(plan.riskLevel.badgeColorHex))
                ) {
                    Text(
                        text = plan.riskLevel.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(plan.riskLevel.badgeColorHex),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            plan.steps.forEach { step ->
                val result = stepResults.firstOrNull { it.stepNumber == step.stepNumber }
                val statusColor = when {
                    result == null -> JarvisTextMuted
                    result.verifiedSuccess -> JarvisEmerald
                    else -> JarvisCrimson
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(JarvisElevatedCard)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            result == null -> Icons.Default.Refresh
                            result.verifiedSuccess -> Icons.Default.Verified
                            else -> Icons.Default.ErrorOutline
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${step.stepNumber}. ${step.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = result?.verificationDetail ?: "Expected: ${step.expectedVerification}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (result != null) statusColor else JarvisTextSecondary
                        )
                    }
                    if (result != null && result.retryCount > 0) {
                        Text(
                            text = "Retry #${result.retryCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareControlsQuickCard(
    telemetry: DeviceTelemetry,
    onVolumeCommit: (Int) -> Unit,
    onBrightnessCommit: (Int) -> Unit,
    onToggleFlashlight: () -> Unit,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onSilentClick: () -> Unit
) {
    var volSlider by remember(telemetry.mediaVolumePercent) {
        mutableStateOf(telemetry.mediaVolumePercent.toFloat())
    }
    var briSlider by remember(telemetry.brightnessPercent) {
        mutableStateOf(telemetry.brightnessPercent.toFloat())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
        border = BorderStroke(1.dp, JarvisBorderCyan)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "REAL-TIME SYSTEM CONTROLS (ACTION → VERIFY)",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisCyan
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Volume Slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Volume: ${volSlider.toInt()}%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                Slider(
                    value = volSlider,
                    onValueChange = { volSlider = it },
                    onValueChangeFinished = { onVolumeCommit(volSlider.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan),
                    modifier = Modifier.weight(1f)
                )
            }

            // Brightness Slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Brightness6, contentDescription = null, tint = JarvisAmber, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bright: ${briSlider.toInt()}%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                Slider(
                    value = briSlider,
                    onValueChange = { briSlider = it },
                    onValueChangeFinished = { onBrightnessCommit(briSlider.toInt()) },
                    valueRange = 5f..100f,
                    colors = SliderDefaults.colors(thumbColor = JarvisAmber, activeTrackColor = JarvisAmber),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Hardware Toggle Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = telemetry.isFlashlightOn,
                    onClick = onToggleFlashlight,
                    label = { Text("Torch") },
                    leadingIcon = { Icon(Icons.Default.FlashlightOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = telemetry.isWifiConnected,
                    onClick = onWifiClick,
                    label = { Text("Wi-Fi") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = telemetry.isBluetoothEnabled,
                    onClick = onBluetoothClick,
                    label = { Text("BT") },
                    leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = telemetry.ringerModeLabel != "Normal",
                    onClick = onSilentClick,
                    label = { Text(telemetry.ringerModeLabel) },
                    leadingIcon = { Icon(Icons.Default.DoNotDisturbOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CommandHistoryMiniRow(
    item: CommandHistoryEntity,
    onReplay: () -> Unit
) {
    val verified = item.verificationStatus == "VERIFIED"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReplay() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisElevatedCard),
        border = BorderStroke(1.dp, JarvisBorderCyan)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (verified) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = item.verificationStatus,
                tint = if (verified) JarvisEmerald else JarvisAmber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "\"${item.userCommand}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "JARVIS: ${item.jarvisResponse}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisCyan
                )
                Text(
                    text = item.actionsPerformedSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
