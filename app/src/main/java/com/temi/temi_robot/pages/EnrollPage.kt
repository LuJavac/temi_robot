package com.temi.temi_robot.pages

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import com.temi.temi_robot.R
import com.temi.temi_robot.RobotController
import com.temi.temi_robot.detection.FaceProximityAnalyzer
import com.temi.temi_robot.face.FaceCamera
import com.temi.temi_robot.face.FaceClient
import com.temi.temi_robot.telemetry.TelemetryClient

/**
 * Écran d'enrôlement facial : aperçu caméra live (miroir) + guidage multi-angles.
 * La personne tape son prénom, puis le robot la guide ("regarde-moi", "tourne à
 * gauche/droite…") en prenant une photo nette à chaque étape, et envoie le tout
 * au service face du Pi.
 *
 * Contention caméra : on coupe la détection native Temi à l'entrée pour libérer
 * la caméra frontale ; en quittant, MainPage relance son wave detector.
 */
class EnrollPage : Fragment() {

    private var faceCamera: FaceCamera? = null
    private val handler = Handler(Looper.getMainLooper())
    private val frames = mutableListOf<ByteArray>()

    // Dernière estimation de distance (taille du visage), mise à jour en continu
    // par l'analyseur de la caméra. Sert à cadrer la 1re photo avant de capturer.
    private var currentProximity = FaceProximityAnalyzer.Proximity.NO_FACE
    private var goodSinceMs = 0L
    private var stepStartMs = 0L
    private var lastPrompt: FaceProximityAnalyzer.Proximity? = null
    private var lastPromptMs = 0L

    companion object {
        private const val POLL_MS = 200L          // fréquence de vérif du cadrage
        private const val HOLD_MS = 800L          // rester bien cadré avant de shooter
        private const val MAX_WAIT_MS = 6000L     // secours : on capture quand même
        private const val PROMPT_COOLDOWN_MS = 2500L
        private const val STEP_DELAY_MS = 2500L   // délai des étapes d'angle suivantes
    }

    // (phrase TTS, texte à l'écran) pour chaque angle
    private val captureSteps = listOf(
        "Look straight at me." to "Look straight at me 🙂",
        "Now turn slightly to your left." to "Turn to YOUR left ⬅️",
        "Now turn slightly to your right." to "Turn to YOUR right ➡️",
        "Now lift your chin a little." to "Chin up a little ⬆️",
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.layout_enroll_page, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Libère la caméra frontale (sinon CameraX échoue en silence)
        RobotController.setDetectionModeOn(false, 0.5f)

        val previewView = view.findViewById<PreviewView>(R.id.previewView)
        val namePanel = view.findViewById<View>(R.id.namePanel)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val guidanceText = view.findViewById<TextView>(R.id.guidanceText)

        faceCamera = FaceCamera(requireContext(), viewLifecycleOwner).also { cam ->
            cam.start(
                surfaceProvider = previewView.surfaceProvider,
                onProximity = { p -> currentProximity = p },
            ) { ok ->
                if (!ok && isAdded) {
                    RobotController.speak("Sorry, I can't access my camera right now.")
                    goBack()
                }
            }
        }

        view.findViewById<Button>(R.id.cancelButton).setOnClickListener { goBack() }

        view.findViewById<Button>(R.id.startButton).setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                RobotController.speak("Please type your name first.")
                return@setOnClickListener
            }
            hideKeyboard(nameInput)
            namePanel.visibility = View.GONE
            captureStep(name, 0, guidanceText)
        }

        RobotController.speak("Let's get to know each other! Please type your name, then press Start.")
    }

    /** Guide la personne angle par angle, en capturant une photo à chaque étape. */
    private fun captureStep(name: String, index: Int, guidanceText: TextView) {
        if (!isAdded) return
        if (index >= captureSteps.size) {
            finishEnroll(name, guidanceText)
            return
        }
        val (speech, label) = captureSteps[index]
        guidanceText.text = label
        RobotController.speak(speech)

        if (index == 0) {
            // Première photo (de face) : on attend que la personne soit à bonne
            // distance et bien cadrée avant de capturer, au lieu de shooter trop
            // vite. On laisse d'abord entendre la consigne.
            stepStartMs = System.currentTimeMillis()
            goodSinceMs = 0L
            lastPrompt = null
            lastPromptMs = 0L
            handler.postDelayed({ gateThenCapture(name, index, guidanceText, label) }, 1000)
        } else {
            // Étapes d'angle suivantes : la personne est déjà à la bonne distance,
            // on garde un délai fixe pour lui laisser le temps de tourner la tête.
            handler.postDelayed({ doCapture(name, index, guidanceText) }, STEP_DELAY_MS)
        }
    }

    /**
     * Attend que la personne soit bien cadrée (taille de visage correcte) avant
     * de capturer, en la guidant "rapprochez-vous / reculez". Filet de sécurité :
     * après [MAX_WAIT_MS] on capture quand même pour ne jamais bloquer.
     */
    private fun gateThenCapture(name: String, index: Int, guidanceText: TextView, label: String) {
        if (!isAdded) return
        val now = System.currentTimeMillis()
        val elapsed = now - stepStartMs

        if (currentProximity == FaceProximityAnalyzer.Proximity.GOOD) {
            if (goodSinceMs == 0L) goodSinceMs = now
            guidanceText.text = label
            if (now - goodSinceMs >= HOLD_MS || elapsed >= MAX_WAIT_MS) {
                doCapture(name, index, guidanceText)
                return
            }
        } else {
            goodSinceMs = 0L
            if (elapsed >= MAX_WAIT_MS) {
                doCapture(name, index, guidanceText)
                return
            }
            guide(currentProximity, guidanceText)
        }
        handler.postDelayed({ gateThenCapture(name, index, guidanceText, label) }, POLL_MS)
    }

    /** Consigne de repositionnement, dite sans être répétée trop souvent. */
    private fun guide(p: FaceProximityAnalyzer.Proximity, guidanceText: TextView) {
        val now = System.currentTimeMillis()
        if (p == lastPrompt && now - lastPromptMs < PROMPT_COOLDOWN_MS) return
        lastPrompt = p
        lastPromptMs = now
        val (label, speech) = when (p) {
            FaceProximityAnalyzer.Proximity.TOO_FAR -> "Come closer ➡️" to "Come a little closer."
            FaceProximityAnalyzer.Proximity.TOO_CLOSE -> "Step back ⬅️" to "Please step back a little."
            else -> "Face me 🙂" to "Please look straight at me." // NO_FACE
        }
        guidanceText.text = label
        RobotController.speak(speech)
    }

    private fun doCapture(name: String, index: Int, guidanceText: TextView) {
        faceCamera?.captureJpeg { jpeg ->
            if (jpeg != null) frames.add(jpeg)
            captureStep(name, index + 1, guidanceText)
        }
    }

    private fun finishEnroll(name: String, guidanceText: TextView) {
        guidanceText.text = "Saving…"
        if (frames.isEmpty()) {
            RobotController.speak("Sorry, I couldn't capture you well. Let's try again later.")
            goBack()
            return
        }
        FaceClient.enroll(name, frames) { res ->
            if (!isAdded) return@enroll
            if (res.success && res.enrolledPhotos >= 1) {
                RobotController.speak("Nice to meet you, $name! Next time I'll greet you by name.")
                TelemetryClient.track("greeting")
            } else {
                RobotController.speak("Sorry $name, I couldn't save your face this time.")
            }
            goBack()
        }
    }

    private fun goBack() {
        if (isAdded) parentFragmentManager.popBackStack()
    }

    private fun hideKeyboard(focused: View) {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(focused.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        faceCamera?.stop()
        faceCamera = null
    }
}
