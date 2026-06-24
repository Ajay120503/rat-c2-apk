package com.rat.client.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.util.UUID

/**
 * Collects device identification and system information.
 */
object DeviceInfoUtils {
    private const val TAG = "DeviceInfoUtils"

    /**
     * Generate or retrieve persistent device ID.
     * Uses Android ID + some device identifiers combined.
     */
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val deviceId = "$androidId-${Build.MANUFACTURER}-${Build.MODEL}"
            .replace(" ", "_")
            .lowercase()

        // Hash to consistent length
        return UUID.nameUUIDFromBytes(deviceId.toByteArray()).toString()
    }

    fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun getManufacturer(): String = Build.MANUFACTURER

    fun getModel(): String = Build.MODEL

    fun getAndroidVersion(): String = Build.VERSION.RELEASE

    fun getSdkVersion(): Int = Build.VERSION.SDK_INT

    fun getTotalStorage(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val bytes = stat.totalBytes
            formatBytes(bytes)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getUsedStorage(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            formatBytes(total - free)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getSimInfo(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                val operator = tm.simOperatorName
                if (!operator.isNullOrBlank()) operator else "No SIM"
            } else {
                "No SIM"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getNetworkType(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                when (tm.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                    TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                    else -> "Cellular"
                }
            } else {
                // Check for WiFi via ConnectivityManager
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    val activeNetwork = cm?.activeNetworkInfo
                    if (activeNetwork?.type == android.net.ConnectivityManager.TYPE_WIFI) {
                        "WiFi"
                    } else {
                        "Unknown"
                    }
                } catch (_: Exception) {
                    "Unknown"
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getBatteryLevel(context: Context): Int {
        return try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            if (intent != null) {
                val level = intent.getIntExtra("level", -1)
                val scale = intent.getIntExtra("scale", -1)
                if (level >= 0 && scale > 0) {
                    (level * 100 / scale)
                } else 0
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    fun isCharging(context: Context): Boolean {
        return try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val status = intent?.getIntExtra("status", -1) ?: -1
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
        }
    }
}

