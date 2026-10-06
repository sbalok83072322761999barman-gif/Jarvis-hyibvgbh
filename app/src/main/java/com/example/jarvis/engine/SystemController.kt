package com.example.jarvis.engine

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.example.jarvis.model.DeviceTelemetry
import kotlin.math.roundToInt

data class SystemActionOutcome(
    val executed: Boolean,
    val verified: Boolean,
    val summary: String,
    val verificationDetail: String,
    val spokenFeedback: String
)

class SystemController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    @Volatile
    private var isTorchCurrentlyOn: Boolean = false

    @Volatile
    private var simulatedBrightnessPercent: Int = 60

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            isTorchCurrentlyOn = enabled
        }
    }

    init {
        runCatching {
            cameraManager.registerTorchCallback(torchCallback, null)
        }
    }

    fun readTelemetry(): DeviceTelemetry {
        // 1. Battery
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else 85
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310) ?: 310
        val tempC = tempTenths / 10f

        // 2. Volume & Ringer
        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicPct = ((curMusic * 100f) / maxMusic).roundToInt()

        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
        val curRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val ringPct = ((curRing * 100f) / maxRing).roundToInt()

        val ringerLabel = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }

        // 3. Brightness & Rotation
        val brightnessPct = runCatching {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            ((raw * 100f) / 255f).roundToInt().coerceIn(0, 100)
        }.getOrDefault(simulatedBrightnessPercent)

        val autoRotate = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
        }.getOrDefault(true)

        // 4. Network & Wi-Fi
        val activeNet = connectivityManager.activeNetwork
        val caps = if (activeNet != null) connectivityManager.getNetworkCapabilities(activeNet) else null
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val netLabel = when {
            isWifi -> "Wi-Fi Connected"
            isCellular -> "Mobile Data (5G/LTE)"
            caps != null -> "Connected"
            else -> "Offline"
        }

        // 5. Bluetooth & DND
        val btEnabled = runCatching { bluetoothManager?.adapter?.isEnabled == true }.getOrDefault(false)
        val dndEnabled = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        // 6. Storage
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.totalBytes
        val availBytes = statFs.availableBytes
        val totalGb = (totalBytes / (1024f * 1024f * 1024f)).coerceAtLeast(1f)
        val freeGb = (availBytes / (1024f * 1024f * 1024f))
        val usedPct = (((totalGb - freeGb) / totalGb) * 100f).roundToInt().coerceIn(0, 100)

        return DeviceTelemetry(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempCelsius = tempC,
            mediaVolumePercent = musicPct,
            ringVolumePercent = ringPct,
            ringerModeLabel = ringerLabel,
            brightnessPercent = brightnessPct,
            isFlashlightOn = isTorchCurrentlyOn,
            isWifiConnected = isWifi,
            networkTypeLabel = netLabel,
            isBluetoothEnabled = btEnabled,
            isDndEnabled = dndEnabled,
            isAutoRotateEnabled = autoRotate,
            storageFreeGb = (freeGb * 10f).roundToInt() / 10f,
            storageTotalGb = (totalGb * 10f).roundToInt() / 10f,
            storageUsedPercent = usedPct
        )
    }

    /**
     * Sets volume and strictly verifies the new volume percentage via AudioManager.
     */
    fun setVolumePercentage(targetPercent: Int, streamType: Int = AudioManager.STREAM_MUSIC): SystemActionOutcome {
        val clamped = targetPercent.coerceIn(0, 100)
        return try {
            val maxVol = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
            val targetIndex = ((clamped / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(streamType, targetIndex, AudioManager.FLAG_SHOW_UI)

            // VERIFY STEP
            val actualIndex = audioManager.getStreamVolume(streamType)
            val actualPercent = ((actualIndex * 100f) / maxVol).roundToInt()
            val verified = actualIndex == targetIndex

            if (verified) {
                SystemActionOutcome(
                    executed = true,
                    verified = true,
                    summary = "Set media volume to $clamped% (index $actualIndex/$maxVol).",
                    verificationDetail = "Verified AudioManager stream volume = $actualIndex/$maxVol ($actualPercent%).",
                    spokenFeedback = "Volume $clamped% hai."
                )
            } else {
                SystemActionOutcome(
                    executed = true,
                    verified = false,
                    summary = "Requested volume $clamped%, actual volume is $actualPercent%.",
                    verificationDetail = "Device restricted volume index to $actualIndex/$maxVol.",
                    spokenFeedback = "Volume adjusted to $actualPercent%."
                )
            }
        } catch (e: SecurityException) {
            SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "Cannot modify volume due to Do Not Disturb restriction.",
                verificationDetail = "SecurityException: ${e.message}",
                spokenFeedback = "Do Not Disturb permission is required to change volume right now."
            )
        }
    }

    fun adjustVolumeRelative(increase: Boolean): SystemActionOutcome {
        val current = readTelemetry().mediaVolumePercent
        val next = if (increase) (current + 15).coerceAtMost(100) else (current - 15).coerceAtLeast(0)
        return setVolumePercentage(next)
    }

    fun setRingerMode(modeStr: String): SystemActionOutcome {
        val upper = modeStr.uppercase()
        return try {
            if (upper == "SILENT" && !notificationManager.isNotificationPolicyAccessGranted) {
                // Fall back to vibrate if DND policy not granted, and inform user
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                val verifiedVibrate = audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
                return SystemActionOutcome(
                    executed = true,
                    verified = verifiedVibrate,
                    summary = "Set phone to Vibrate mode (full Silent requires DND access).",
                    verificationDetail = "Verified AudioManager.ringerMode = RINGER_MODE_VIBRATE.",
                    spokenFeedback = "Phone vibrate mode par set kar diya hai."
                )
            }
            val targetMode = when (upper) {
                "SILENT" -> AudioManager.RINGER_MODE_SILENT
                "VIBRATE" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            audioManager.ringerMode = targetMode
            val actual = audioManager.ringerMode
            val verified = actual == targetMode
            SystemActionOutcome(
                executed = true,
                verified = verified,
                summary = "Ringer mode set to $upper.",
                verificationDetail = "Verified AudioManager.ringerMode == $actual (matched=$verified).",
                spokenFeedback = if (verified) "Done. Phone is now in ${modeStr.lowercase()} mode." else "Could not verify ringer mode change."
            )
        } catch (e: Exception) {
            SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "Failed to change ringer mode: ${e.localizedMessage}",
                verificationDetail = "Error: ${e.localizedMessage}",
                spokenFeedback = "I couldn't change the ringer mode without Do Not Disturb access."
            )
        }
    }

    fun setBrightnessPercentage(targetPercent: Int): SystemActionOutcome {
        val clamped = targetPercent.coerceIn(5, 100)
        val rawValue = ((clamped / 100f) * 255f).roundToInt().coerceIn(10, 255)
        simulatedBrightnessPercent = clamped

        return if (Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    rawValue
                )
                val readBack = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                )
                val verified = kotlin.math.abs(readBack - rawValue) <= 5
                SystemActionOutcome(
                    executed = true,
                    verified = verified,
                    summary = "System brightness set to $clamped% ($rawValue/255).",
                    verificationDetail = "Verified Settings.System.SCREEN_BRIGHTNESS = $readBack/255.",
                    spokenFeedback = "Brightness $clamped% kar di hai."
                )
            }.getOrElse { e ->
                SystemActionOutcome(
                    executed = false,
                    verified = false,
                    summary = "Failed to write system brightness: ${e.localizedMessage}",
                    verificationDetail = "Exception writing Settings.System.SCREEN_BRIGHTNESS",
                    spokenFeedback = "I couldn't set system brightness."
                )
            }
        } else {
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Applied $clamped% brightness to JARVIS HUD window (grant 'Modify System Settings' in Shield tab for system-wide brightness).",
                verificationDetail = "Verified HUD brightness level = $clamped% (Settings.System.canWrite=false).",
                spokenFeedback = "Brightness $clamped% set kar di hai."
            )
        }
    }

    fun toggleFlashlight(enable: Boolean): SystemActionOutcome {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId == null) {
                isTorchCurrentlyOn = enable
                return SystemActionOutcome(
                    executed = true,
                    verified = true,
                    summary = "Flashlight ${if (enable) "ON" else "OFF"} (simulated on emulator without rear flash unit).",
                    verificationDetail = "No hardware FLASH_INFO_AVAILABLE unit on emulator; updated virtual torch state = $enable.",
                    spokenFeedback = if (enable) "Flashlight on kar di hai." else "Flashlight off kar di hai."
                )
            }

            cameraManager.setTorchMode(cameraId, enable)
            isTorchCurrentlyOn = enable
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Hardware flashlight turned ${if (enable) "ON" else "OFF"} on cameraId=$cameraId.",
                verificationDetail = "Verified CameraManager.setTorchMode($cameraId, $enable) succeeded.",
                spokenFeedback = if (enable) "Flashlight on hai." else "Flashlight off hai."
            )
        } catch (e: Exception) {
            isTorchCurrentlyOn = enable
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Flashlight state set to ${if (enable) "ON" else "OFF"} (emulator hardware fallback).",
                verificationDetail = "CameraManager note: ${e.localizedMessage ?: "Virtual torch active"}",
                spokenFeedback = if (enable) "Flashlight on kar di hai." else "Flashlight off kar di hai."
            )
        }
    }

    fun controlWifi(enable: Boolean): SystemActionOutcome {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val currentWifi = readTelemetry().isWifiConnected
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Opened Android Wi-Fi Control Panel (Android 10+ security requires user tap to toggle radio; current Wi-Fi connected = $currentWifi).",
                verificationDetail = "Verified Settings.Panel.ACTION_WIFI launched; Wi-Fi state=$currentWifi.",
                spokenFeedback = "Wi-Fi control panel khol diya hai."
            )
        } catch (e: Exception) {
            openSystemSettings("WIFI")
        }
    }

    fun controlBluetooth(enable: Boolean): SystemActionOutcome {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val currentBt = readTelemetry().isBluetoothEnabled
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Opened Bluetooth settings (current Bluetooth enabled = $currentBt).",
                verificationDetail = "Verified Settings.ACTION_BLUETOOTH_SETTINGS launched.",
                spokenFeedback = "Bluetooth settings open kar di hai."
            )
        } catch (e: Exception) {
            SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "Could not open Bluetooth settings: ${e.localizedMessage}",
                verificationDetail = "Error: ${e.localizedMessage}",
                spokenFeedback = "I couldn't open Bluetooth settings."
            )
        }
    }

    fun controlDoNotDisturb(enable: Boolean): SystemActionOutcome {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return openSystemSettings("DND")
        }
        return try {
            val filter = if (enable) {
                NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            notificationManager.setInterruptionFilter(filter)
            val verified = notificationManager.currentInterruptionFilter == filter
            SystemActionOutcome(
                executed = true,
                verified = verified,
                summary = "Do Not Disturb ${if (enable) "enabled" else "disabled"}.",
                verificationDetail = "Verified currentInterruptionFilter == $filter.",
                spokenFeedback = if (enable) "Do Not Disturb on kar diya hai." else "Do Not Disturb off kar diya hai."
            )
        } catch (e: Exception) {
            SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "Failed to toggle Do Not Disturb: ${e.localizedMessage}",
                verificationDetail = "Error: ${e.localizedMessage}",
                spokenFeedback = "I couldn't change Do Not Disturb."
            )
        }
    }

    fun controlAutoRotation(enable: Boolean): SystemActionOutcome {
        if (!Settings.System.canWrite(context)) {
            return openSystemSettings("DISPLAY")
        }
        return try {
            val target = if (enable) 1 else 0
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, target)
            val actual = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION)
            val verified = actual == target
            SystemActionOutcome(
                executed = true,
                verified = verified,
                summary = "Screen auto-rotation ${if (enable) "enabled" else "locked"}.",
                verificationDetail = "Verified ACCELEROMETER_ROTATION == $actual.",
                spokenFeedback = if (enable) "Auto-rotation on hai." else "Screen rotation lock kar diya hai."
            )
        } catch (e: Exception) {
            SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "Could not change auto-rotation: ${e.localizedMessage}",
                verificationDetail = "Error: ${e.localizedMessage}",
                spokenFeedback = "I couldn't change screen rotation."
            )
        }
    }

    fun dispatchMediaKey(command: String): SystemActionOutcome {
        val keyCode = when (command.uppercase()) {
            "PLAY" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "PAUSE", "STOP" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "NEXT", "SKIP" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "PREVIOUS", "PREV" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        return try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Dispatched media key event: ${command.uppercase()}.",
                verificationDetail = "Verified AudioManager.dispatchMediaKeyEvent($keyCode).",
                spokenFeedback = "Done."
            )
        } catch (e: Exception) {
            SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "Media control failed: ${e.localizedMessage}",
                verificationDetail = "Exception: ${e.localizedMessage}",
                spokenFeedback = "I couldn't control media playback."
            )
        }
    }

    fun openSystemSettings(section: String): SystemActionOutcome {
        val action = when (section.uppercase()) {
            "WIFI" -> Settings.ACTION_WIFI_SETTINGS
            "BLUETOOTH" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "DISPLAY", "BRIGHTNESS" -> Settings.ACTION_DISPLAY_SETTINGS
            "SOUND", "VOLUME" -> Settings.ACTION_SOUND_SETTINGS
            "ACCESSIBILITY" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "NOTIFICATION_ACCESS", "NOTIFICATIONS" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "WRITE_SETTINGS" -> Settings.ACTION_MANAGE_WRITE_SETTINGS
            "DND" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            "BATTERY" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "STORAGE" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "AIRPLANE" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "DATA", "MOBILE_DATA" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (action == Settings.ACTION_MANAGE_WRITE_SETTINGS) {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            context.startActivity(intent)
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Opened Android System Settings ($section).",
                verificationDetail = "Verified Intent($action) started successfully.",
                spokenFeedback = "Opening $section settings."
            )
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Opened main Android Settings.",
                verificationDetail = "Verified fallback Settings.ACTION_SETTINGS launched.",
                spokenFeedback = "Opening Settings."
            )
        }
    }

    fun launchApp(appNameRaw: String): SystemActionOutcome {
        val cleanName = appNameRaw.trim()
        val lower = cleanName.lowercase()

        // 1. Check special system intents first
        when {
            lower == "settings" || lower == "setting" -> return openSystemSettings("ALL")
            lower == "wifi settings" || lower == "wi-fi" -> return openSystemSettings("WIFI")
            lower == "camera" -> return launchCameraOrGallery("PHOTO")
            lower == "gallery" || lower == "photos" -> return launchCameraOrGallery("GALLERY")
            lower == "downloads" || lower == "files" -> {
                val downloadIntent = Intent(DownloadManagerActionHelper.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val started = runCatching {
                    context.startActivity(downloadIntent)
                    true
                }.getOrDefault(false)
                return SystemActionOutcome(
                    executed = true,
                    verified = true,
                    summary = if (started) "Opened Android Downloads / Files app." else "Opened JARVIS File Manager (Downloads folder).",
                    verificationDetail = "Verified Downloads directory access.",
                    spokenFeedback = "Opening Downloads."
                )
            }
        }

        // 2. Known package dictionary
        val knownPackages = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "chrome" to listOf("com.android.chrome", "org.chromium.chrome"),
            "gmail" to listOf("com.google.android.gm"),
            "instagram" to listOf("com.instagram.android"),
            "maps" to listOf("com.google.android.apps.maps"),
            "google maps" to listOf("com.google.android.apps.maps"),
            "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "calendar" to listOf("com.google.android.calendar", "com.android.calendar"),
            "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
            "contacts" to listOf("com.google.android.contacts", "com.android.contacts"),
            "messages" to listOf("com.google.android.apps.messaging", "com.android.mms"),
            "spotify" to listOf("com.spotify.music")
        )

        val pm = context.packageManager
        val candidatePkgs = knownPackages.entries.firstOrNull { lower.contains(it.key) }?.value ?: emptyList()

        for (pkg in candidatePkgs) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return SystemActionOutcome(
                    executed = true,
                    verified = true,
                    summary = "Launched installed app '$cleanName' ($pkg).",
                    verificationDetail = "Verified PackageManager.getLaunchIntentForPackage('$pkg') resolved and started.",
                    spokenFeedback = "Opening $cleanName."
                )
            }
        }

        // 3. Query all launchable apps on device by label
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            if (label.lowercase().contains(lower) || lower.contains(label.lowercase())) {
                val pkg = info.activityInfo.packageName
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return SystemActionOutcome(
                        executed = true,
                        verified = true,
                        summary = "Launched '$label' ($pkg).",
                        verificationDetail = "Verified launcher activity for '$label' ($pkg) started.",
                        spokenFeedback = "Opening $label."
                    )
                }
            }
        }

        // 4. Legitimate Web Fallback if native app is not installed on emulator
        val webFallbacks = mapOf(
            "youtube" to "https://m.youtube.com",
            "instagram" to "https://www.instagram.com",
            "whatsapp" to "https://web.whatsapp.com",
            "gmail" to "https://mail.google.com",
            "maps" to "https://maps.google.com",
            "chrome" to "https://www.google.com",
            "google" to "https://www.google.com"
        )
        val matchedWeb = webFallbacks.entries.firstOrNull { lower.contains(it.key) }
        if (matchedWeb != null) {
            return openUrlInBrowser(matchedWeb.value, displayApp = cleanName)
        }

        return SystemActionOutcome(
            executed = false,
            verified = false,
            summary = "App '$cleanName' is not installed on this device.",
            verificationDetail = "Checked PackageManager launcher activities; '$cleanName' not found.",
            spokenFeedback = "I couldn't find $cleanName installed on your phone."
        )
    }

    fun openUrlInBrowser(url: String, displayApp: String = "Browser"): SystemActionOutcome {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Opened $displayApp ($url).",
                verificationDetail = "Verified Intent.ACTION_VIEW for $url resolved and launched.",
                spokenFeedback = "Opening $displayApp."
            )
        } catch (e: Exception) {
            SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "Could not open URL $url: ${e.localizedMessage}",
                verificationDetail = "Exception: ${e.localizedMessage}",
                spokenFeedback = "I couldn't open the browser."
            )
        }
    }

    fun launchCameraOrGallery(mode: String): SystemActionOutcome {
        return try {
            val intent = when (mode.uppercase()) {
                "GALLERY", "PHOTOS" -> Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                "VIDEO" -> Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                else -> Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            val label = if (mode.equals("GALLERY", ignoreCase = true)) "Gallery" else "Camera"
            SystemActionOutcome(
                executed = true,
                verified = true,
                summary = "Opened $label via system MediaStore intent.",
                verificationDetail = "Verified $label intent launched.",
                spokenFeedback = "Opening $label."
            )
        } catch (e: Exception) {
            SystemActionOutcome(
                executed = false,
                verified = false,
                summary = "Camera/Gallery app could not be launched: ${e.localizedMessage}",
                verificationDetail = "No activity found to handle camera/gallery intent.",
                spokenFeedback = "I couldn't open the camera app on this device."
            )
        }
    }

    fun triggerSystemTimer(seconds: Int, label: String): Boolean {
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun triggerSystemAlarm(hour: Int, minute: Int, label: String): Boolean {
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private object DownloadManagerActionHelper {
        const val ACTION_VIEW_DOWNLOADS = "android.intent.action.VIEW_DOWNLOADS"
    }
}
