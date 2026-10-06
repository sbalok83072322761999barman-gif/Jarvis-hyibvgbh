package com.example.jarvis.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jarvis.data.MemoryFactEntity
import com.example.jarvis.engine.CapabilityPermissionStatus
import com.example.jarvis.model.BatteryMode
import com.example.jarvis.model.JarvisSettings
import com.example.jarvis.viewmodel.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBorderCyan
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElevatedCard
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun PermissionsAndPrivacyScreen(
    viewModel: JarvisViewModel,
    permissions: List<CapabilityPermissionStatus>,
    settings: JarvisSettings,
    memoryFacts: List<MemoryFactEntity>,
    isCloudKeyReady: Boolean,
    modifier: Modifier = Modifier
) {
    var memKey by rememberSaveable { mutableStateOf("") }
    var memVal by rememberSaveable { mutableStateOf("") }

    val runtimePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Permission Manager Header & Explanation List
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                border = BorderStroke(1.dp, JarvisBorderCyan)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PERMISSION & CAPABILITY MANAGER",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisCyan
                            )
                            Text(
                                text = "Every capability is transparent and user-controlled. JARVIS never requests unnecessary permissions.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextSecondary
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.refreshPermissions() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Permissions", tint = JarvisCyan)
                    }
                }
            }
        }

        items(permissions, key = { it.id }) { perm ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisElevatedCard),
                border = BorderStroke(
                    1.dp,
                    if (perm.isGranted) JarvisEmerald.copy(alpha = 0.5f) else JarvisAmber.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (perm.isGranted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (perm.isGranted) JarvisEmerald else JarvisAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = perm.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                        }
                        if (perm.isGranted) {
                            Surface(
                                color = JarvisEmerald.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "GRANTED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisEmerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (perm.isRuntimePermission && perm.runtimePermissionName != null) {
                                        runtimePermLauncher.launch(perm.runtimePermissionName)
                                    } else if (perm.settingsAction != null) {
                                        viewModel.permissionManager.openPermissionSettings(perm.settingsAction)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JarvisCyan,
                                    contentColor = JarvisObsidian
                                )
                            ) {
                                Text("Grant / Open", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = perm.simpleExplanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Example commands: ${perm.usedForExamples}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisCyan
                    )
                }
            }
        }

        // 2. Background Operation & Battery-Saving Modes
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                border = BorderStroke(1.dp, JarvisBorderCyan)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatterySaver, contentDescription = null, tint = JarvisEmerald)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BACKGROUND OPERATION & BATTERY MODES",
                            style = MaterialTheme.typography.labelMedium,
                            color = JarvisCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    BatteryMode.entries.forEach { mode ->
                        val selected = settings.batteryMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) JarvisCyan.copy(alpha = 0.14f) else JarvisElevatedCard)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) JarvisCyan else JarvisTextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextSecondary
                                )
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setBatteryMode(mode) },
                                label = { Text(if (selected) "Active" else "Select") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Foreground \"Hey JARVIS\" Service",
                                style = MaterialTheme.typography.bodyLarge,
                                color = JarvisTextPrimary
                            )
                            Text(
                                text = "Runs a visible Android Foreground Service notification for hands-free activation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextSecondary
                            )
                        }
                        Switch(
                            checked = settings.wakeWordEnabled,
                            onCheckedChange = { viewModel.toggleWakeWordStandby(it) }
                        )
                    }
                }
            }
        }

        // 3. Voice Response (TTS) & Security Gate Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                border = BorderStroke(1.dp, JarvisBorderCyan)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VOICE TTS & ACTION SECURITY SETTINGS",
                            style = MaterialTheme.typography.labelMedium,
                            color = JarvisCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Spoken TTS Voice Responses", color = JarvisTextPrimary)
                        Switch(
                            checked = settings.voiceResponseEnabled,
                            onCheckedChange = { viewModel.toggleVoiceResponses(it) }
                        )
                    }

                    Text(
                        text = "Voice Speed: ${String.format("%.2fx", settings.speechRate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Slider(
                        value = settings.speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = 0.7f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                    )

                    Text(
                        text = "Voice Pitch: ${String.format("%.2f", settings.speechPitch)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Slider(
                        value = settings.speechPitch,
                        onValueChange = { viewModel.setSpeechPitch(it) },
                        valueRange = 0.7f..1.4f,
                        colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require Confirmation for Medium-Risk Actions", color = JarvisTextPrimary)
                            Text(
                                "Ask before sending WhatsApp messages or deleting files (High-Risk always asks).",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary
                            )
                        }
                        Switch(
                            checked = settings.confirmMediumRisk,
                            onCheckedChange = { viewModel.toggleConfirmMediumRisk(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Cloud AI Brain (Gemini 3.5 Flash)", color = JarvisTextPrimary)
                            Text(
                                if (isCloudKeyReady) {
                                    "GEMINI_API_KEY detected via BuildConfig. Cloud reasoning + Offline NLU active."
                                } else {
                                    "Running in 100% Local Offline NLU Mode. Set GEMINI_API_KEY in AI Studio Secrets panel for Cloud AI."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCloudKeyReady) JarvisEmerald else JarvisAmber
                            )
                        }
                        Switch(
                            checked = settings.cloudAiEnabled,
                            onCheckedChange = { viewModel.toggleCloudAi(it) }
                        )
                    }
                }
            }
        }

        // 4. Privacy & User-Controlled Long-Term Memory
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                border = BorderStroke(1.dp, JarvisBorderCyan)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "USER-CONTROLLED MEMORY & PRIVACY",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisCyan
                            )
                        }
                        Switch(
                            checked = settings.longTermMemoryEnabled,
                            onCheckedChange = { viewModel.toggleLongTermMemory(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    memoryFacts.forEach { fact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(JarvisElevatedCard)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fact.memoryKey, style = MaterialTheme.typography.bodyMedium, color = JarvisCyan, fontWeight = FontWeight.SemiBold)
                                Text(fact.memoryValue, style = MaterialTheme.typography.bodyMedium, color = JarvisTextPrimary)
                            }
                            IconButton(onClick = { viewModel.deleteMemoryFact(fact.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = JarvisTextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = memKey,
                            onValueChange = { memKey = it },
                            placeholder = { Text("Memory Key") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = memVal,
                            onValueChange = { memVal = it },
                            placeholder = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (memKey.isNotBlank() && memVal.isNotBlank()) {
                                    viewModel.addCustomMemoryFact(memKey, memVal)
                                    memKey = ""
                                    memVal = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = JarvisCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearAllHistoryAndLogs() },
                            border = BorderStroke(1.dp, JarvisCrimson),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = JarvisCrimson, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wipe History & Logs", color = JarvisCrimson, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).run {
                                    // Handled via repository
                                }
                                memoryFacts.forEach { viewModel.deleteMemoryFact(it.id) }
                            },
                            border = BorderStroke(1.dp, JarvisAmber),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = JarvisAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Memory", color = JarvisAmber, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
