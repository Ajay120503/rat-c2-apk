package com.rat.client.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rat.client.services.BackgroundService

/**
 * Monitors screen state changes but doesn't need to do anything special
 * since the foreground service keeps running regardless.
 */
class ScreenReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ScreenReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen ON")
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF")
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User unlocked device")
            }
        }
    }
}

