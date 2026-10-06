package com.example.jarvis.ui.screens

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jarvis.data.ExternalContactEntity
import com.example.jarvis.engine.AgentResearchResult
import com.example.jarvis.model.ScreenSnapshot
import com.example.jarvis.service.JarvisAccessibilityService
import com.example.jarvis.viewmodel.JarvisViewModel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBorderCyan
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElevatedCard
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet

@Composable
fun AgentInspectorScreen(
    viewModel: JarvisViewModel,
    screenSnapshot: ScreenSnapshot,
    researchResult: AgentResearchResult?,
    contacts: List<ExternalContactEntity>,
    modifier: Modifier = Modifier
) {
    val isAccConnected by JarvisAccessibilityService.isServiceConnected.collectAsState()
    val sandboxSearchText by JarvisAccessibilityService.sandboxSearchBoxText.collectAsState()
    val sandboxLastNote by JarvisAccessibilityService.sandboxLastActionNote.collectAsState()

    var newContactName by rememberSaveable { mutableStateOf("") }
    var newContactPhone by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Accessibility Service & Full Phone Navigation Controller
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
                            Icon(
                                Icons.Default.AccessibilityNew,
                                contentDescription = null,
                                tint = if (isAccConnected) JarvisEmerald else JarvisAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SCREEN UNDERSTANDING & NAVIGATION",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisCyan
                                )
                                Text(
                                    text = if (isAccConnected) {
                                        "System AccessibilityService Connected"
                                    } else {
                                        "Interactive Screen Sandbox Active (Enable System Service for Cross-App Control)"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isAccConnected) JarvisEmerald else JarvisAmber
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.refreshScreenHierarchy() },
                            modifier = Modifier.testTag("refresh_screen_tree_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Screen Tree", tint = JarvisCyan)
                        }
                    }

                    if (!isAccConnected) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.permissionManager.openPermissionSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            },
                            border = BorderStroke(1.dp, JarvisCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Android Accessibility Settings", color = JarvisCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "PHONE NAVIGATION ACTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.processUserCommand("Back jao") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                        OutlinedButton(
                            onClick = { viewModel.processUserCommand("Home screen par jao") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Home")
                        }
                        OutlinedButton(
                            onClick = { viewModel.processUserCommand("Upar scroll karo") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Up")
                        }
                        OutlinedButton(
                            onClick = { viewModel.processUserCommand("Neeche scroll karo") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Down")
                        }
                    }
                }
            }
        }

        // 2. Interactive Screen Target Sandbox (Verify Click, Type, Checkbox, Scroll)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisElevatedCard),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LIVE SCREEN AUTOMATION TARGET SANDBOX",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisCyan
                    )
                    Text(
                        text = "Say \"Screen pe Login button hai, uspe click karo\" or \"Search box mein Minecraft likho\" to see JARVIS inspect and interact with these UI nodes:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = sandboxSearchText,
                        onValueChange = {
                            JarvisAccessibilityService.updateSandboxTypedText("Search box", it)
                            viewModel.refreshScreenHierarchy()
                        },
                        label = { Text("Search box (Editable Node #1)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sandbox_search_box_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.processUserCommand("Screen pe Login button hai, uspe click karo") },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisObsidian),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Login", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.processUserCommand("Screen pe Send button hai, uspe click karo") },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisViolet, contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Send", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.processUserCommand("Isko download kar") },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisGlassSurface, contentColor = JarvisCyan),
                            border = BorderStroke(1.dp, JarvisCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Download")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = JarvisGlassSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, JarvisBorderCyan)
                    ) {
                        Text(
                            text = "Last Screen Action: $sandboxLastNote",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisEmerald,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // 3. Inspected AccessibilityNodeInfo Hierarchy List
        item {
            Text(
                text = "INSPECTED UI HIERARCHY (${screenSnapshot.nodes.size} NODES)",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisCyan
            )
        }
        items(screenSnapshot.nodes.take(8), key = { it.index }) { node ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                border = BorderStroke(1.dp, JarvisBorderCyan)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = JarvisCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = node.roleBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.displayLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${node.className.substringAfterLast('.')} • Bounds ${node.boundsSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextSecondary
                        )
                    }
                    if (node.isClickable) {
                        IconButton(
                            onClick = {
                                viewModel.processUserCommand("Screen pe ${node.displayLabel} button hai, uspe click karo")
                            }
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = "Click Node", tint = JarvisCyan)
                        }
                    }
                }
            }
        }

        // 4. Browser Agent & Autonomous Agent Mode Research Card
        if (researchResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                    border = BorderStroke(1.dp, JarvisViolet)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = researchResult.sourceLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisCyan
                                )
                                Text(
                                    text = researchResult.query,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JarvisTextPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.systemController.openUrlInBrowser(
                                        researchResult.targetUrl,
                                        researchResult.sourceLabel
                                    )
                                }
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser", tint = JarvisCyan)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        researchResult.bulletPoints.forEach { bullet ->
                            Text(
                                text = "• $bullet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextPrimary,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.processUserCommand("Chrome kholo aur Vivo Y21 5G ka specification search karo")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Vivo Y21 5G Specs", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.processUserCommand("JARVIS, mujhe YouTube par Android AI assistant ke tutorials dhundhkar 3 useful videos ke naam batao")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("3 YouTube Tutorials", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // 5. WhatsApp Contacts & Fail-Safe Disambiguation Directory
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisGlassSurface),
                border = BorderStroke(1.dp, JarvisBorderCyan)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "WHATSAPP CONTACTS & RECIPIENT VERIFICATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisCyan
                    )
                    Text(
                        text = "Notice two contacts named 'Rahul' below: if you say \"Rahul ko message bhejo\", JARVIS refuses to guess and asks which Rahul you mean!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    contacts.forEach { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(JarvisElevatedCard)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${contact.phoneNumber} • ${contact.tag}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.processUserCommand(
                                        "WhatsApp pe ${contact.name} ko message bhejo ki main 10 minute mein aa raha hoon"
                                    )
                                }
                            ) {
                                Text("Message", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { viewModel.deleteContact(contact.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Contact", tint = JarvisTextSecondary)
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
                            value = newContactName,
                            onValueChange = { newContactName = it },
                            placeholder = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newContactPhone,
                            onValueChange = { newContactPhone = it },
                            placeholder = { Text("+91...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (newContactName.isNotBlank() && newContactPhone.isNotBlank()) {
                                    viewModel.addCustomContact(newContactName, newContactPhone, "WhatsApp")
                                    newContactName = ""
                                    newContactPhone = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", tint = JarvisCyan)
                        }
                    }
                }
            }
        }
    }
}
