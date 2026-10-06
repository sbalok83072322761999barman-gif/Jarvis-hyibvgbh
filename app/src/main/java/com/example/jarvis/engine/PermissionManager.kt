package com.example.jarvis.engine

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.jarvis.service.JarvisAccessibilityService
import com.example.jarvis.service.JarvisNotificationService

data class CapabilityPermissionStatus(
    val id: String,
    val title: String,
    val simpleExplanation: String,
    val isGranted: Boolean,
    val isRuntimePermission: Boolean,
    val runtimePermissionName: String? = null,
    val settingsAction: String? = null,
    val usedForExamples: String
)

class PermissionManager(private val context: Context) {

    fun inspectAllPermissions(): List<CapabilityPermissionStatus> {
        val list = mutableListOf<CapabilityPermissionStatus>()

        // 1. Microphone
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityPermissionStatus(
                id = "mic",
                title = "Microphone & Voice Wake-Word",
                simpleExplanation = "Required so JARVIS can hear 'Hey JARVIS' and understand your spoken commands in Hindi, English, and Hinglish.",
                isGranted = micGranted,
                isRuntimePermission = true,
                runtimePermissionName = Manifest.permission.RECORD_AUDIO,
                usedForExamples = "\"Hey Jarvis, YouTube kholo\", \"Volume 40% karo\""
            )
        )

        // 2. Accessibility Service
        val accConnected = JarvisAccessibilityService.isServiceConnected.value || isAccessibilityEnabledInSettings()
        list.add(
            CapabilityPermissionStatus(
                id = "accessibility",
                title = "Accessibility Service (Screen & Navigation)",
                simpleExplanation = "Allows JARVIS to read screen buttons/fields, press Back/Home/Recents, scroll pages, type text, and verify UI actions.",
                isGranted = accConnected,
                isRuntimePermission = false,
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                usedForExamples = "\"Back jao\", \"Neeche scroll karo\", \"Login button pe click karo\""
            )
        )

        // 3. Notification Access
        val notifListenerEnabled = JarvisNotificationService.isListenerConnected.value ||
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        list.add(
            CapabilityPermissionStatus(
                id = "notification_listener",
                title = "Notification Listener Access",
                simpleExplanation = "Allows JARVIS to read incoming notifications, summarize missed alerts, and dismiss notifications when asked.",
                isGranted = notifListenerEnabled,
                isRuntimePermission = false,
                settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
                usedForExamples = "\"Mere notifications batao\", \"Notifications clear karo\""
            )
        )

        // 4. Write System Settings
        val writeSettingsGranted = Settings.System.canWrite(context)
        list.add(
            CapabilityPermissionStatus(
                id = "write_settings",
                title = "Modify System Settings (Brightness & Rotation)",
                simpleExplanation = "Required by Android to adjust system-wide screen brightness percentage and auto-rotation.",
                isGranted = writeSettingsGranted,
                isRuntimePermission = false,
                settingsAction = Settings.ACTION_MANAGE_WRITE_SETTINGS,
                usedForExamples = "\"Brightness 60% kar do\", \"Auto rotation off karo\""
            )
        )

        // 5. Do Not Disturb Policy Access
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndGranted = nm.isNotificationPolicyAccessGranted
        list.add(
            CapabilityPermissionStatus(
                id = "dnd_policy",
                title = "Do Not Disturb & Silent Mode Access",
                simpleExplanation = "Required to switch the phone into full Silent Mode or toggle Do Not Disturb.",
                isGranted = dndGranted,
                isRuntimePermission = false,
                settingsAction = Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                usedForExamples = "\"Phone silent kar\", \"Do Not Disturb on karo\""
            )
        )

        // 6. Contacts Access
        val contactsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityPermissionStatus(
                id = "contacts",
                title = "Contacts Lookup (Safe Recipient Verification)",
                simpleExplanation = "Used to verify contact names and disambiguate multiple contacts (e.g., two contacts named Rahul) before sending WhatsApp messages.",
                isGranted = contactsGranted,
                isRuntimePermission = true,
                runtimePermissionName = Manifest.permission.READ_CONTACTS,
                usedForExamples = "\"Rahul ko WhatsApp karo\", \"Mom ko message bhejo\""
            )
        )

        // 7. Camera & Flashlight
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityPermissionStatus(
                id = "camera",
                title = "Camera & Torch Hardware",
                simpleExplanation = "Used when you ask JARVIS to open the camera or control camera hardware.",
                isGranted = cameraGranted,
                isRuntimePermission = true,
                runtimePermissionName = Manifest.permission.CAMERA,
                usedForExamples = "\"Camera kholo\", \"Flashlight on karo\""
            )
        )

        // 8. Post Notifications (Android 13+)
        val postNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        list.add(
            CapabilityPermissionStatus(
                id = "post_notifications",
                title = "Foreground Service & Timer Alerts",
                simpleExplanation = "Displays the active 'Hey JARVIS' standby status bar indicator and alerts you when timers or reminders complete.",
                isGranted = postNotifGranted,
                isRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                runtimePermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else null,
                usedForExamples = "\"30 minute ka timer lagao\", Background Wake-Word Service"
            )
        )

        return list
    }

    private fun isAccessibilityEnabledInSettings(): Boolean {
        return runCatching {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(context.packageName, ignoreCase = true)
        }.getOrDefault(false)
    }

    fun openPermissionSettings(action: String) {
        runCatching {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (action == Settings.ACTION_MANAGE_WRITE_SETTINGS) {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            context.startActivity(intent)
        }
    }
}
