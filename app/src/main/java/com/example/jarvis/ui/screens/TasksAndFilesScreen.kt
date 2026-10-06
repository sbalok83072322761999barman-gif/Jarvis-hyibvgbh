package com.example.jarvis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.jarvis.data.TaskReminderEntity
import com.example.jarvis.model.ActiveTimerItem
import com.example.jarvis.model.CapturedNotification
import com.example.jarvis.model.JarvisFileItem
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
fun TasksAndFilesScreen(
    viewModel: JarvisViewModel,
    activeTimers: List<ActiveTimerItem>,
    tasksAndReminders: List<TaskReminderEntity>,
    files: List<JarvisFileItem>,
    currentFolder: String,
    filePreview: Pair<String, String>?,
    notifications: List<CapturedNotification>,
    modifier: Modifier = Modifier
) {
    var newTaskTitle by rememberSaveable { mutableStateOf("") }
    var newTaskTime by rememberSaveable { mutableStateOf("Tomorrow 8:00 AM") }
    var newFolderName by rememberSaveable { mutableStateOf("") }

    if (filePreview != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeFilePreview() },
            title = { Text(filePreview.first, color = JarvisCyan) },
            text = {
                Text(
                    text = filePreview.second,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.closeFilePreview() }) {
                    Text("Close", color = JarvisCyan)
                }
            },
            containerColor = JarvisElevatedCard
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Active Countdown Timers & Quick Timer Triggers
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
                            Icon(Icons.Default.Timer, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TIMERS, ALARMS & REMINDERS",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisCyan
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.processUserCommand("30 minute ka timer lagao") }
                        ) {
                            Text("+ 30m Timer", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (activeTimers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        activeTimers.forEach { timer ->
                            val mins = timer.remainingSeconds / 60
                            val secs = timer.remainingSeconds % 60
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(JarvisElevatedCard)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(timer.label, style = MaterialTheme.typography.bodyMedium, color = JarvisTextPrimary)
                                    Text(
                                        text = String.format("%02d:%02d remaining", mins, secs),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = JarvisEmerald
                                    )
                                }
                                IconButton(onClick = { viewModel.cancelCountdownTimer(timer.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Cancel Timer", tint = JarvisCrimson)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    tasksAndReminders.take(6).forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(JarvisElevatedCard)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { checked ->
                                    viewModel.toggleTaskCompleted(task.id, checked)
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${task.category} • ${task.triggerTimeLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisCyan
                                )
                            }
                            IconButton(onClick = { viewModel.deleteTaskById(task.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Task", tint = JarvisTextSecondary)
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
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            placeholder = { Text("Add reminder or note...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    viewModel.addCustomTask(newTaskTitle, "Manual reminder", "REMINDER", newTaskTime)
                                    newTaskTitle = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisObsidian)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task")
                        }
                    }
                }
            }
        }

        // 2. Notification Control Center
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
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = JarvisAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NOTIFICATION CONTROL (${notifications.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisCyan
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.processUserCommand("Mere notifications batao") }
                            ) {
                                Text("Read Aloud", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { viewModel.processUserCommand("Notifications clear karo") }
                            ) {
                                Text("Clear All", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    if (notifications.isEmpty()) {
                        Text(
                            text = "No active notifications. You're all caught up!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary
                        )
                    } else {
                        notifications.take(5).forEach { notif ->
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
                                    Text(
                                        text = "${notif.appName} • ${notif.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (notif.isImportant) JarvisCyan else JarvisTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = notif.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = JarvisTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. JARVIS File Manager (Downloads, Documents, PDF Search, Rename, Delete)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                border = BorderStroke(1.dp, JarvisBorderCyan)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FILE MANAGER & STORAGE OPERATIONS",
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
                        listOf("All", "Downloads", "Documents", "Notes").forEach { folder ->
                            FilterChip(
                                selected = currentFolder.equals(folder, ignoreCase = true),
                                onClick = { viewModel.refreshFiles(folder) },
                                label = { Text(folder) }
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.processUserCommand("PDF files dhundo") }
                        ) {
                            Text("Find PDFs", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    files.forEach { fileItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(JarvisElevatedCard)
                                .clickable { viewModel.previewFile(fileItem) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    fileItem.isDirectory -> Icons.Default.Folder
                                    fileItem.extension == "pdf" -> Icons.Default.PictureAsPdf
                                    else -> Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = if (fileItem.isDirectory) JarvisAmber else JarvisCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileItem.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (fileItem.isDirectory) {
                                        "Folder • ${fileItem.sizeBytes} items"
                                    } else {
                                        "${fileItem.categoryLabel} • ${fileItem.sizeBytes} bytes (Tap to open)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextSecondary
                                )
                            }
                            if (!fileItem.isDirectory) {
                                IconButton(
                                    onClick = {
                                        viewModel.processUserCommand("Is file ka naam change karo")
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.DriveFileRenameOutline,
                                        contentDescription = "Rename File",
                                        tint = JarvisCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.processUserCommand("Delete file ${fileItem.name}")
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete File",
                                        tint = JarvisCrimson,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("New folder name...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    viewModel.createNewFolder(newFolderName)
                                    newFolderName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisObsidian)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Folder")
                        }
                    }
                }
            }
        }
    }
}
