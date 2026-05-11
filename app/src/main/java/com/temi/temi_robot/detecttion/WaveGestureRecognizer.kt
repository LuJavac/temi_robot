package com.temi.temi_robot.detection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors

/**
 * Detects a "wave" gesture using the front camera + MediaPipe Hands, and triggers a callback.
 *
 * A "wave" is defined empirically as: a hand visible in frame whose wrist moves laterally
 * back-and-forth (>= 3 direction changes) with enough amplitude (>= 15% of frame width)
 * within a 1.5s window.
 *
 * Usage from a Fragment:
 *   private lateinit var waveRec: WaveGestureRecognizer
 *
 *   override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *       super.onViewCreated(view, savedInstanceState)
 *       waveRec = WaveGestureRecognizer(requireContext(), viewLifecycleOwner) {
 *           requireActivity().runOnUiThread {
 *               RobotController.speak("Hello!")
 *           }
 *       }
 *       waveRec.start()
 *   }
 *
 *   override fun onDestroyView() {
 *       super.onDestroyView()
 *       waveRec.stop()
 *   }
 *
 * Prerequisites:
 *   - CAMERA permission granted at runtime
 *   - Model file `hand_landmarker.task` placed in app/src/main/assets/
 */
class WaveGestureRecognizer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onWaveDetected: () -> Unit,
) {
    companion object {
        private const val TAG = "WaveGestureRecognizer"

        // Heuristic parameters — tune empirically on the Temi
        private const val WINDOW_MS = 1500L          // sliding time window for oscillation detection
        private const val MIN_DIRECTION_CHANGES = 3  // at least 3 direction reversals = a wave
        private const val MIN_AMPLITUDE = 0.15f      // wrist must travel >= 15% of frame width
        private const val NOISE_THRESHOLD = 0.005f   // ignore micro-jitter
        private const val MIN_SAMPLES = 8            // need enough datapoints to decide
        private const val COOLDOWN_MS = 4000L        // don't fire twice within 4s

        private const val MODEL_ASSET = "hand_landmarker.task"
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Sliding history of (timestamp, normalized X of wrist landmark)
    private val wristXHistory = ArrayDeque<Pair<Long, Float>>()
    private var lastWaveTimeMs = 0L

    /** Initializes MediaPipe and binds the camera. Idempotent. */
    fun start() {
        if (handLandmarker != null) return
        initMediaPipe()
        bindCamera()
    }

    /** Releases the camera and the MediaPipe model. */
    fun stop() {
        try {
            cameraProvider?.unbindAll()
            handLandmarker?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WaveGestureRecognizer", e)
        } finally {
            handLandmarker = null
            cameraProvider = null
            wristXHistory.clear()
        }
    }

    private fun initMediaPipe() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { error -> Log.e(TAG, "MediaPipe error", error) }
            .build()
        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()

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
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis,
                )
                Log.i(TAG, "Camera bound, front camera + analysis use case active")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap: Bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarker?.detectAsync(mpImage, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing failed", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun handleResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) return
        // Landmark 0 = wrist; .x() is normalized to [0,1] across frame width
        val wristX = result.landmarks()[0][0].x()
        val now = System.currentTimeMillis()

        synchronized(wristXHistory) {
            wristXHistory.addLast(now to wristX)
            while (wristXHistory.isNotEmpty()
                && now - wristXHistory.first().first > WINDOW_MS) {
                wristXHistory.removeFirst()
            }
            if (detectsWaveNow() && now - lastWaveTimeMs > COOLDOWN_MS) {
                lastWaveTimeMs = now
                Log.i(TAG, "Wave detected!")
                onWaveDetected()
            }
        }
    }

    private fun detectsWaveNow(): Boolean {
        if (wristXHistory.size < MIN_SAMPLES) return false
        val xs = wristXHistory.map { it.second }
        if (xs.max() - xs.min() < MIN_AMPLITUDE) return false

        var directionChanges = 0
        var prevDir = 0
        for (i in 1 until xs.size) {
            val delta = xs[i] - xs[i - 1]
            if (kotlin.math.abs(delta) < NOISE_THRESHOLD) continue
            val dir = if (delta > 0) 1 else -1
            if (prevDir != 0 && dir != prevDir) directionChanges++
            prevDir = dir
        }
        return directionChanges >= MIN_DIRECTION_CHANGES
    }
}
