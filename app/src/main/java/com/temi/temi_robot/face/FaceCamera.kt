package com.temi.temi_robot.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.temi.temi_robot.detection.FaceProximityAnalyzer
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Capture une photo JPEG depuis la caméra frontale du robot, pour la
 * reconnaissance et l'enrôlement faciaux.
 *
 * Réutilise le même schéma que [com.temi.temi_robot.detection.WaveGestureRecognizer] :
 * énumération CameraX + fallback FRONT -> BACK (sur certains Temi l'unique caméra
 * tournée vers l'utilisateur est exposée comme LENS_FACING_BACK).
 *
 * ⚠️ Contention caméra : la détection native Temi et le wave detector tiennent
 * aussi la caméra frontale. Avant d'appeler [start], libérer la caméra
 * (RobotController.setDetectionModeOn(false) + arrêt du wave detector), et la
 * rendre via [stop] quand on a fini.
 */
class FaceCamera(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    companion object {
        private const val TAG = "FaceCamera"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var proximityAnalyzer: FaceProximityAnalyzer? = null
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /**
     * Ouvre la caméra. [surfaceProvider] est optionnel : le passer (depuis un
     * PreviewView) affiche l'aperçu live pour l'écran d'enrôlement ; le laisser
     * null fait une capture "headless" (cas de la reconnaissance au réveil).
     * [onProximity] (optionnel) reçoit en continu l'estimation de distance de la
     * personne (taille du visage) pour guider "rapprochez-vous / reculez" avant
     * de capturer — évite les photos prises trop vite / trop loin. Callback sur
     * le thread principal.
     * [onReady] indique si la caméra a pu être ouverte.
     */
    fun start(
        surfaceProvider: Preview.SurfaceProvider? = null,
        onProximity: ((FaceProximityAnalyzer.Proximity) -> Unit)? = null,
        onReady: (Boolean) -> Unit = {},
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val selector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> {
                        Log.i(TAG, "Using FRONT camera")
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> {
                        Log.w(TAG, "FRONT camera not available, falling back to BACK camera")
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    else -> {
                        Log.e(TAG, "No camera available — aborting")
                        onReady(false)
                        return@addListener
                    }
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                val useCases = mutableListOf<UseCase>(capture)
                if (surfaceProvider != null) {
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(surfaceProvider)
                    useCases.add(preview)
                }
                if (onProximity != null) {
                    val analyzer = FaceProximityAnalyzer(onProximity)
                    proximityAnalyzer = analyzer
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor, analyzer) }
                    useCases.add(analysis)
                }

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
                Log.i(TAG, "Camera bound (preview=${surfaceProvider != null})")
                onReady(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                onReady(false)
            }
        }, mainExecutor)
    }

    /**
     * Prend une photo et renvoie le JPEG (déjà remis droit selon la rotation
     * caméra), ou null en cas d'échec. Callback sur le thread principal.
     */
    fun captureJpeg(onJpeg: (ByteArray?) -> Unit) {
        val capture = imageCapture ?: run {
            Log.w(TAG, "captureJpeg appelé sans caméra ouverte")
            onJpeg(null)
            return
        }
        capture.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val jpeg = image.use { extractJpeg(it) }
                onJpeg(if (rotation != 0) rotate(jpeg, rotation) else jpeg)
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Capture échouée", exc)
                onJpeg(null)
            }
        })
    }

    /** Libère la caméra (à appeler quand on a fini, pour la rendre au Temi). */
    fun stop() {
        cameraProvider?.unbindAll()
        imageCapture = null
        proximityAnalyzer?.close()
        proximityAnalyzer = null
        analysisExecutor.shutdown()
    }

    private fun extractJpeg(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun rotate(jpeg: ByteArray, degrees: Int): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return jpeg
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return ByteArrayOutputStream().use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }
}
