package com.temi.temi_robot.detection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Keeps the front camera bound and exposes the latest frame as JPEG for the
 * face-recognition-at-wake flow. No gesture detection: it only captures frames.
 *
 * (Anciennement WaveGestureRecognizer. La détection du geste "wave" a été retirée
 * le 2026-07-23 — trop de faux déclenchements. On conserve uniquement le flux
 * caméra qui alimente la reconnaissance faciale au réveil ; ouvrir une 2e caméra
 * pour la reco entrerait en conflit avec celle-ci. L'implémentation du wave detector
 * est conservée en commentaire dans WaveGestureRecognizer.kt comme preuve du travail.)
 *
 * Prerequisites:
 *   - CAMERA permission granted at runtime
 */
class CameraFrameProvider(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    companion object {
        private const val TAG = "CameraFrameProvider"
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var started = false

    // Diagnostic counter
    private var framesProcessed = 0L

    // Dernière frame caméra (pour la reconnaissance faciale au réveil). On réutilise
    // ce flux au lieu d'ouvrir une 2e caméra, qui entrerait en conflit avec celle-ci
    // (bind silencieux qui échoue).
    @Volatile private var latestFrame: Bitmap? = null
    @Volatile private var latestRotation: Int = 0

    /** Binds the camera. Idempotent. */
    fun start() {
        if (started) return
        started = true
        bindCamera()
    }

    /** Releases the camera. */
    fun stop() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping CameraFrameProvider", e)
        } finally {
            cameraProvider = null
            latestFrame = null
            started = false
        }
    }

    /**
     * Dernière frame caméra en JPEG (remise droite selon la rotation), pour la
     * reconnaissance faciale. Renvoie null si aucune frame encore reçue.
     */
    fun latestJpeg(): ByteArray? {
        val frame = latestFrame ?: return null
        val rotation = latestRotation
        val upright = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
        } else {
            frame
        }
        return ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()

                // Diagnostic: list every camera CameraX can see on this device.
                val cams = cameraProvider?.availableCameraInfos ?: emptyList()
                Log.i(TAG, "Available cameras: ${cams.size}")
                cams.forEachIndexed { i, info ->
                    val lensName = when (info.lensFacing) {
                        CameraSelector.LENS_FACING_FRONT -> "FRONT"
                        CameraSelector.LENS_FACING_BACK -> "BACK"
                        CameraSelector.LENS_FACING_EXTERNAL -> "EXTERNAL"
                        else -> "UNKNOWN(${info.lensFacing})"
                    }
                    Log.i(TAG, "  cam[$i] lensFacing=$lensName")
                }

                // Prefer FRONT (selfie cam); fall back to BACK if FRONT not exposed.
                val selector: CameraSelector = when {
                    cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true -> {
                        Log.i(TAG, "Using FRONT camera")
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true -> {
                        Log.w(TAG, "FRONT camera not available, falling back to BACK camera")
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    else -> {
                        Log.e(TAG, "No camera available at all — aborting bind")
                        return@addListener
                    }
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    analysis,
                )
                Log.i(TAG, "Camera bound + analysis use case active")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            framesProcessed++
            if (framesProcessed == 1L || framesProcessed % 60L == 0L) {
                Log.i(TAG, "Camera frames processed: $framesProcessed")
            }
            // Mémorise la dernière frame + sa rotation pour la reconnaissance faciale.
            latestFrame = imageProxy.toBitmap()
            latestRotation = imageProxy.imageInfo.rotationDegrees
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing failed", e)
        } finally {
            imageProxy.close()
        }
    }
}
