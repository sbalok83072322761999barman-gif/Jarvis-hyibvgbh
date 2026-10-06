package com.example.jarvis.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jarvis.data.AutomationLogEntity
import com.example.jarvis.data.CommandHistoryEntity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutomationLogsScreen(
    viewModel: JarvisViewModel,
    commandHistory: List<CommandHistoryEntity>,
    automationLogs: List<AutomationLogEntity>,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf("AUTOMATION_LOGS") }
    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RELIABILITY & TELEMETRY LOGS",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisCyan
                            )
                            Text(
                                text = "Every command records Intent, Planned Action, Screen State, Verification, and Retries.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearAllHistoryAndLogs() },
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedTab == "AUTOMATION_LOGS",
                            onClick = { selectedTab = "AUTOMATION_LOGS" },
                            label = { Text("Step-by-Step Logs (${automationLogs.size})") },
                            leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = selectedTab == "COMMAND_HISTORY",
                            onClick = { selectedTab = "COMMAND_HISTORY" },
                            label = { Text("Command History (${commandHistory.size})") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }

        if (selectedTab == "AUTOMATION_LOGS") {
            if (automationLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = JarvisElevatedCard)
                    ) {
                        Text(
                            text = "No automation logs yet. Run any voice or quick command on the HUD tab to inspect Observe → Plan → Act → Verify logs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(automationLogs, key = { it.id }) { log ->
                    val isOk = log.phase.contains("OK") || log.phase.contains("OBSERVE") || log.phase.contains("SELF-CORRECTED")
                    val badgeColor = when {
                        log.phase.contains("FAILED") -> JarvisCrimson
                        log.phase.contains("RETRY") -> JarvisAmber
                        isOk -> JarvisEmerald
                        else -> JarvisCyan
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = JarvisElevatedCard),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = badgeColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = log.phase,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = "${timeFormatter.format(Date(log.timestamp))} • Retries: ${log.retryCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Command: \"${log.commandText}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Intent: ${log.intentDetected}",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan
                            )
                            Text(
                                text = "Planned: ${log.actionPlanned}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextSecondary
                            )
                            Text(
                                text = "Executed: ${log.actionExecuted}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextPrimary
                            )
                            Text(
                                text = "Screen State: ${log.screenStateSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary
                            )
                            Text(
                                text = "Verification: ${log.verificationResult}",
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor
                            )
                            if (log.errorDetails.isNotBlank()) {
                                Text(
                                    text = "Error: ${log.errorDetails}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisCrimson
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(commandHistory, key = { it.id }) { item ->
                val verified = item.verificationStatus == "VERIFIED"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisElevatedCard),
                    border = BorderStroke(1.dp, JarvisBorderCyan)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (verified) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (verified) JarvisEmerald else JarvisAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${item.verificationStatus} • ${item.detectedLanguage} • ${item.riskLevel} RISK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (verified) JarvisEmerald else JarvisAmber
                                )
                            }
                            Text(
                                text = timeFormatter.format(Date(item.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "User: \"${item.userCommand}\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                        Text(
                            text = "JARVIS: \"${item.jarvisResponse}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Actions: ${item.actionsPerformedSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextSecondary
                        )
                        Text(
                            text = "Engine: ${item.engineUsed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextSecondary
                        )
                    }
                }
            }
        }
    }
}
