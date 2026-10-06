package com.example.jarvis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val batteryModeLabel = intent?.getStringExtra(EXTRA_BATTERY_MODE) ?: "Balanced Mode"
        val notification = buildForegroundNotification(batteryModeLabel)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _isRunning.value = true
        }.onFailure {
            _isRunning.value = false
        }

        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Wake-Word & Standby Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps JARVIS ready for 'Hey JARVIS' voice activation and background automation."
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(batteryModeLabel: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("trigger_listen", true)
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText("Wake phrase: \"Hey JARVIS\" • $batteryModeLabel")
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                "Speak Now",
                pendingOpen
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Standby",
                pendingStop
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_assistant_channel"
        private const val NOTIFICATION_ID = 4041
        const val ACTION_STOP_SERVICE = "com.example.jarvis.STOP_FOREGROUND"
        const val EXTRA_BATTERY_MODE = "extra_battery_mode"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun startStandby(context: Context, batteryModeTitle: String) {
            val intent = Intent(context, JarvisForegroundService::class.java).apply {
                putExtra(EXTRA_BATTERY_MODE, batteryModeTitle)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stopStandby(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            runCatching {
                context.startService(intent)
            }
        }
    }
}
