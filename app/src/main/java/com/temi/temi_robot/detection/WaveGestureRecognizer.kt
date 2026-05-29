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
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.hypot

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

        // Heuristic parameters — re-tuned 2026-05-18 after open-palm filter rejected
        // every frame: loosen the palm check (3/4 fingers instead of 4/4) and accept
        // a slightly smaller wave amplitude since palm-filter already prunes noise.
        private const val WINDOW_MS = 2000L          // sliding time window for oscillation detection
        private const val MIN_DIRECTION_CHANGES = 3  // at least 3 reversals (real back-and-forth)
        private const val MIN_AMPLITUDE = 0.10f      // wrist must travel >= 10% of frame width
        private const val NOISE_THRESHOLD = 0.010f   // ignore micro-jitter
        private const val MIN_SAMPLES = 4            // need enough open-palm datapoints to decide
        private const val COOLDOWN_MS = 4_000L       // a Hello at most every 4 s (test value — bump back to 10s once detection is validated)

        // MediaPipe Hand landmark indices used for the open-palm check
        private const val WRIST = 0
        // For each finger: (PIP joint, fingertip). Extended <=> dist(wrist, tip) > dist(wrist, pip).
        private val FINGER_PIP_TIP = listOf(6 to 8, 10 to 12, 14 to 16, 18 to 20)
        // Require this many fingers (out of 4) to be extended for "open palm"
        private const val MIN_EXTENDED_FINGERS = 3

        private const val MODEL_ASSET = "hand_landmarker.task"
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Sliding history of (timestamp, normalized X of wrist landmark)
    private val wristXHistory = ArrayDeque<Pair<Long, Float>>()
    private var lastWaveTimeMs = 0L

    // Diagnostic counters
    private var framesProcessed = 0L
    private var handsDetected = 0L
    private var openPalmCount = 0L
    private var firstHandLogged = false

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
            .setMinHandDetectionConfidence(0.3f)
            .setMinHandPresenceConfidence(0.3f)
            .setMinTrackingConfidence(0.3f)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { error -> Log.e(TAG, "MediaPipe error", error) }
            .build()
        handLandmarker = HandLandmarker.createFromOptions(context, options)
        Log.i(TAG, "MediaPipe HandLandmarker initialized")
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
                Log.i(TAG, "Camera frames processed: $framesProcessed (hand-detected: $handsDetected, open-palm: $openPalmCount)")
            }
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
        handsDetected++
        if (!firstHandLogged) {
            firstHandLogged = true
            Log.i(TAG, "First hand detected")
        }
        val landmarks = result.landmarks()[0]
        // Only feed the history when fingers are extended (real "waving hand", not a fist
        // or a hand resting in frame). This kills the "presence detector" false-positives.
        if (!isOpenPalm(landmarks)) return
        openPalmCount++

        val wristX = landmarks[WRIST].x()
        val now = System.currentTimeMillis()

        synchronized(wristXHistory) {
            wristXHistory.addLast(now to wristX)
            while (wristXHistory.isNotEmpty()
                && now - wristXHistory.first().first > WINDOW_MS) {
                wristXHistory.removeFirst()
            }
            if (detectsWaveNow() && now - lastWaveTimeMs > COOLDOWN_MS) {
                lastWaveTimeMs = now
                wristXHistory.clear()   // avoid immediate re-trigger on the next frame
                Log.i(TAG, "Wave detected!")
                onWaveDetected()
            }
        }
    }

    /**
     * Open palm = at least MIN_EXTENDED_FINGERS of the four non-thumb fingers extended.
     * A finger is extended when its tip is farther from the wrist than its PIP joint,
     * measured in 2D normalized coordinates. Thumb intentionally excluded — it's the
     * least reliable for "wave intent" and curling it shouldn't disqualify a wave.
     */
    private fun isOpenPalm(landmarks: List<NormalizedLandmark>): Boolean {
        if (landmarks.size < 21) return false
        val wrist = landmarks[WRIST]
        fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
            return hypot(a.x() - b.x(), a.y() - b.y())
        }
        val extended = FINGER_PIP_TIP.count { (pipIdx, tipIdx) ->
            dist(wrist, landmarks[tipIdx]) > dist(wrist, landmarks[pipIdx])
        }
        return extended >= MIN_EXTENDED_FINGERS
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
