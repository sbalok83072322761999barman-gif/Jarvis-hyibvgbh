package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jarvis.ui.screens.AgentInspectorScreen
import com.example.jarvis.ui.screens.AutomationLogsScreen
import com.example.jarvis.ui.screens.HudCommandScreen
import com.example.jarvis.ui.screens.PermissionsAndPrivacyScreen
import com.example.jarvis.ui.screens.TasksAndFilesScreen
import com.example.jarvis.viewmodel.JarvisViewModel
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElevatedCard
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.MyApplicationTheme

enum class JarvisNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HUD("hud", "HUD", Icons.Default.GraphicEq),
    AGENT("agent", "Agent", Icons.Default.AccountTree),
    TASKS_FILES("tasks_files", "Tasks & Files", Icons.Default.FolderSpecial),
    LOGS("logs", "Logs", Icons.Default.Terminal),
    SHIELD("shield", "Shield", Icons.Default.Security)
}

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAssistOrForegroundIntent(intent)
        setContent {
            MyApplicationTheme {
                JarvisAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAssistOrForegroundIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        viewModel.refreshScreenHierarchy()
    }

    private fun handleAssistOrForegroundIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("trigger_listen", false) ||
            intent.action == Intent.ACTION_ASSIST
        ) {
            viewModel.startVoiceListening()
        }
    }
}

@Composable
fun JarvisAppRoot(viewModel: JarvisViewModel) {
    var currentTab by rememberSaveable { mutableStateOf(JarvisNavTab.HUD) }

    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val statusSubtitle by viewModel.statusSubtitle.collectAsStateWithLifecycle()
    val lastSpokenResponse by viewModel.lastSpokenResponse.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.voiceEngine.partialTranscript.collectAsStateWithLifecycle()
    val waveformBars by viewModel.voiceEngine.waveformBars.collectAsStateWithLifecycle()
    val currentPlan by viewModel.currentPlan.collectAsStateWithLifecycle()
    val stepResults by viewModel.stepResults.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmationPlan.collectAsStateWithLifecycle()
    val pendingDisambiguation by viewModel.pendingDisambiguationPlan.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sessionContext by viewModel.sessionContext.collectAsStateWithLifecycle()
    val commandHistory by viewModel.commandHistory.collectAsStateWithLifecycle()
    val automationLogs by viewModel.automationLogs.collectAsStateWithLifecycle()
    val tasksAndReminders by viewModel.tasksAndReminders.collectAsStateWithLifecycle()
    val activeTimers by viewModel.activeTimers.collectAsStateWithLifecycle()
    val workspaceFiles by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    val currentFolderFilter by viewModel.currentFolderFilter.collectAsStateWithLifecycle()
    val filePreview by viewModel.selectedFilePreview.collectAsStateWithLifecycle()
    val notifications by viewModel.activeNotifications.collectAsStateWithLifecycle()
    val screenSnapshot by viewModel.screenSnapshot.collectAsStateWithLifecycle()
    val researchResult by viewModel.latestResearchResult.collectAsStateWithLifecycle()
    val contacts by viewModel.savedContacts.collectAsStateWithLifecycle()
    val permissionsList by viewModel.permissionsList.collectAsStateWithLifecycle()
    val memoryFacts by viewModel.memoryFacts.collectAsStateWithLifecycle()

    if (currentTab != JarvisNavTab.HUD) {
        BackHandler {
            currentTab = JarvisNavTab.HUD
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = JarvisObsidian,
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = JarvisGlassSurface,
                        contentColor = JarvisTextPrimary
                    ) {
                        JarvisNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = JarvisObsidian,
                                    selectedTextColor = JarvisCyan,
                                    indicatorColor = JarvisCyan,
                                    unselectedIconColor = JarvisTextSecondary,
                                    unselectedTextColor = JarvisTextSecondary
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = JarvisGlassSurface,
                        contentColor = JarvisTextPrimary
                    ) {
                        JarvisNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = JarvisObsidian,
                                    selectedTextColor = JarvisCyan,
                                    indicatorColor = JarvisCyan,
                                    unselectedIconColor = JarvisTextSecondary,
                                    unselectedTextColor = JarvisTextSecondary
                                ),
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                when (currentTab) {
                    JarvisNavTab.HUD -> HudCommandScreen(
                        viewModel = viewModel,
                        assistantState = assistantState,
                        statusSubtitle = statusSubtitle,
                        lastSpokenResponse = lastSpokenResponse,
                        partialTranscript = partialTranscript,
                        waveformBars = waveformBars,
                        currentPlan = currentPlan,
                        stepResults = stepResults,
                        pendingConfirmation = pendingConfirmation,
                        pendingDisambiguation = pendingDisambiguation,
                        telemetry = telemetry,
                        settings = settings,
                        sessionContext = sessionContext,
                        commandHistory = commandHistory,
                        modifier = Modifier.weight(1f)
                    )
                    JarvisNavTab.AGENT -> AgentInspectorScreen(
                        viewModel = viewModel,
                        screenSnapshot = screenSnapshot,
                        researchResult = researchResult,
                        contacts = contacts,
                        modifier = Modifier.weight(1f)
                    )
                    JarvisNavTab.TASKS_FILES -> TasksAndFilesScreen(
                        viewModel = viewModel,
                        activeTimers = activeTimers,
                        tasksAndReminders = tasksAndReminders,
                        files = workspaceFiles,
                        currentFolder = currentFolderFilter,
                        filePreview = filePreview,
                        notifications = notifications,
                        modifier = Modifier.weight(1f)
                    )
                    JarvisNavTab.LOGS -> AutomationLogsScreen(
                        viewModel = viewModel,
                        commandHistory = commandHistory,
                        automationLogs = automationLogs,
                        modifier = Modifier.weight(1f)
                    )
                    JarvisNavTab.SHIELD -> PermissionsAndPrivacyScreen(
                        viewModel = viewModel,
                        permissions = permissionsList,
                        settings = settings,
                        memoryFacts = memoryFacts,
                        isCloudKeyReady = viewModel.isCloudKeyReady,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
