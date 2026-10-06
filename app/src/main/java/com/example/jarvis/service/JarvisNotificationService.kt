package com.example.jarvis.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.jarvis.model.CapturedNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isListenerConnected.value = true
        refreshNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) {
            instance = null
            _isListenerConnected.value = false
        }
    }

    fun refreshNotifications() {
        runCatching {
            val sbns = activeNotifications ?: emptyArray()
            val mapped = sbns.mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val pkg = sbn.packageName ?: "android"
                val appLabel = runCatching {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })

                CapturedNotification(
                    key = sbn.key ?: "${pkg}_${sbn.id}",
                    packageName = pkg,
                    appName = appLabel,
                    title = title.ifBlank { appLabel },
                    text = text,
                    postTimeMillis = sbn.postTime,
                    isImportant = sbn.notification.priority >= Notification.PRIORITY_HIGH ||
                        pkg.contains("whatsapp", ignoreCase = true) ||
                        pkg.contains("gm", ignoreCase = true)
                )
            }
            if (mapped.isNotEmpty()) {
                _notifications.value = mapped
            }
        }
    }

    fun dismissNotificationByKey(key: String): Boolean {
        return runCatching {
            cancelNotification(key)
            _notifications.value = _notifications.value.filterNot { it.key == key }
            true
        }.getOrDefault(false)
    }

    fun dismissAll(): Boolean {
        return runCatching {
            cancelAllNotifications()
            _notifications.value = emptyList()
            true
        }.getOrDefault(false)
    }

    companion object {
        @Volatile
        var instance: JarvisNotificationService? = null
            private set

        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

        private val _notifications = MutableStateFlow(
            listOf(
                CapturedNotification(
                    key = "notif_whatsapp_1",
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Rahul Sharma",
                    text = "Bhai kab tak pahunch rahe ho? Meeting 10 min mein start hogi.",
                    postTimeMillis = System.currentTimeMillis() - 4 * 60 * 1000L,
                    isImportant = true
                ),
                CapturedNotification(
                    key = "notif_gmail_2",
                    packageName = "com.google.android.gm",
                    appName = "Gmail",
                    title = "Priya Patel • Sprint Review Deck",
                    text = "Attached the updated Android architecture PDF in Downloads.",
                    postTimeMillis = System.currentTimeMillis() - 18 * 60 * 1000L,
                    isImportant = true
                ),
                CapturedNotification(
                    key = "notif_youtube_3",
                    packageName = "com.google.android.youtube",
                    appName = "YouTube",
                    title = "Android Developers",
                    text = "New video: Building Autonomous AI Agents with Jetpack Compose & Gemini.",
                    postTimeMillis = System.currentTimeMillis() - 45 * 60 * 1000L,
                    isImportant = false
                )
            )
        )
        val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

        fun dismissInMemoryOrSystem(key: String?): Int {
            val svc = instance
            val beforeCount = _notifications.value.size
            if (key == null) {
                svc?.dismissAll()
                _notifications.value = emptyList()
                return beforeCount
            } else {
                svc?.dismissNotificationByKey(key)
                _notifications.value = _notifications.value.filterNot {
                    it.key == key || it.appName.contains(key, ignoreCase = true) || it.title.contains(key, ignoreCase = true)
                }
                return beforeCount - _notifications.value.size
            }
        }

        fun postLocalNotificationEntry(appName: String, title: String, text: String, important: Boolean = true) {
            val item = CapturedNotification(
                key = "local_${System.currentTimeMillis()}",
                packageName = "com.aistudio.jarvisagent.vqxkpm",
                appName = appName,
                title = title,
                text = text,
                postTimeMillis = System.currentTimeMillis(),
                isImportant = important
            )
            _notifications.value = listOf(item) + _notifications.value
        }
    }
}
