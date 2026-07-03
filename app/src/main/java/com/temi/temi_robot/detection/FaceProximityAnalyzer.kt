package com.temi.temi_robot.detection

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Analyse le flux de la caméra frontale et estime la « distance » de
 * l'utilisateur à partir de la TAILLE du visage dans l'image (ML Kit, modèle
 * embarqué, 100 % hors-ligne).
 *
 * On ne cherche pas des mètres : le ratio largeur_visage / largeur_image suffit
 * pour guider « rapprochez-vous / reculez ». Utilisé par le photobooth
 * (PhotoPage) pour ne déclencher la photo que quand la personne est bien cadrée,
 * au lieu de la prendre trop vite.
 *
 * ⚠️ La détection native Temi tient aussi la caméra frontale : cet analyseur
 * doit tourner dans le CameraX de PhotoPage (détection Temi désactivée), pas en
 * parallèle. Les seuils sont empiriques et à ajuster sur le robot.
 *
 * [onResult] est appelé sur le thread principal (listeners ML Kit par défaut).
 */
class FaceProximityAnalyzer(
    private val onResult: (Proximity) -> Unit,
) : ImageAnalysis.Analyzer {

    enum class Proximity { NO_FACE, TOO_FAR, GOOD, TOO_CLOSE }

    companion object {
        // Ratio largeur du visage / largeur de l'image (repère redressé).
        private const val MIN_RATIO = 0.16f   // en dessous : trop loin
        private const val MAX_RATIO = 0.42f   // au dessus : trop près
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.1f)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(media, rotation)
        // Largeur de l'image une fois redressée : les bounding boxes ML Kit sont
        // exprimées dans ce repère, donc on compare au bon axe.
        val uprightWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width

        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() }
                val result = if (face == null || uprightWidth == 0) {
                    Proximity.NO_FACE
                } else {
                    val ratio = face.boundingBox.width().toFloat() / uprightWidth
                    when {
                        ratio < MIN_RATIO -> Proximity.TOO_FAR
                        ratio > MAX_RATIO -> Proximity.TOO_CLOSE
                        else -> Proximity.GOOD
                    }
                }
                onResult(result)
            }
            .addOnFailureListener { onResult(Proximity.NO_FACE) }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** À appeler quand on a fini (fermeture de la page) pour libérer le modèle. */
    fun close() {
        detector.close()
    }
}
